package org.thomcgn.backend.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.thomcgn.backend.reservation.api.dto.CreateReservationRequest;
import org.thomcgn.backend.reservation.service.ReservationService;
import org.thomcgn.backend.reservation.domain.ReservationStatus;
import org.thomcgn.backend.table.service.TableService;
import org.thomcgn.backend.table.api.dto.TableRequest;
import org.thomcgn.backend.table.domain.TableStatus;
import org.thomcgn.backend.common.exception.ConflictException;
import org.thomcgn.backend.support.PostgresIntegrationTest;
import java.time.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties="app.reservation.mail.enabled=true")
@ActiveProfiles("test")
class ReservationLifecycleIntegrationTest extends PostgresIntegrationTest {
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired org.thomcgn.backend.auth.repository.AppUserRepository users;
    @Autowired org.thomcgn.backend.auth.JwtTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService service;
    @Autowired TableService tables;
    @Autowired org.thomcgn.backend.billing.service.TableOrderService orders;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean(name="reservationClock") Clock clock;
    @MockitoBean JavaMailSenderImpl mail;
    private final LocalDate date=LocalDate.of(2035,6,1);

    @BeforeEach void fixtures() {
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Berlin"));
        when(clock.instant()).thenReturn(Instant.parse("2035-06-01T10:00:00Z"));
        jdbc.execute("truncate table tables restart identity cascade");
        jdbc.update("insert into tables(name,area,status,active,seats) values('A','ROOM','FREE',true,4),('B','ROOM','FREE',true,4)");
        jdbc.update("update opening_hours set open=true,open_time='17:00',close_time='00:00',second_open_time=null,second_close_time=null");
        jdbc.update("update booking_slot_config set slot_duration_minutes=30,max_reservation_duration_minutes=180,no_show_grace_period_minutes=15,booking_interval_mode='FIXED'");
    }

    @Test void groupReservesItsBusinessDayWithoutInventingTurnover() {
        var first=service.createReservation(request("18:00",6));
        assertThat(first.getAssignedTables()).hasSize(2);
        assertThatThrownBy(() -> service.createReservation(request("18:30",1))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.createReservation(request("21:00",6))).isInstanceOf(ConflictException.class);
        assertThat(service.createReservation(new CreateReservationRequest("Next day",null,null,date.plusDays(1),LocalTime.of(18,0),6))
                .getAssignedTables()).hasSize(2);
    }

    @Test void cancellationReleasesTablesAndTerminalChangesAreRejected() {
        var first=service.createReservation(request("18:00",6));
        service.confirm(first.getId());
        assertThat(service.cancel(first.getId(),"Changed plans").getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(service.cancel(first.getId(),"Retry").getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(service.createReservation(request("18:00",6))).isNotNull();
        assertThatThrownBy(() -> service.update(first.getId(),request("19:00",2))).isInstanceOf(ConflictException.class);
        verify(mail,times(2)).send(any(SimpleMailMessage.class));
    }

    @Test void failedUpdateRollsBackOldAllocationAndSuccessfulUpdateRechecksCapacity() {
        var first=service.createReservation(request("18:00",4));
        var second=service.createReservation(request("18:00",4));
        assertThatThrownBy(() -> service.update(first.getId(),request("18:30",6))).isInstanceOf(ConflictException.class);
        assertThat(service.getById(first.getId()).getReservationTime()).isEqualTo(LocalTime.of(18,0));
        assertThat(service.getById(first.getId()).getAssignedTables()).hasSize(1);
        service.cancel(second.getId(),"Release capacity");
        assertThat(service.update(first.getId(),request("21:00",6)).getAssignedTables()).hasSize(2);
    }

    @Test void checkInUsesPersistedInstantWindowAndIsIdempotent() {
        var r=service.createReservation(request("18:00",2));
        service.confirm(r.getId());
        assertThat(service.scanToken(r.getQrCodeToken()).checkInAllowed()).isFalse();
        when(clock.instant()).thenReturn(r.getCheckInDeadline().minusSeconds(1));
        assertThat(service.scanToken(r.getQrCodeToken()).checkInAllowed()).isTrue();
        assertThat(service.checkIn(r.getId()).getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThat(service.checkIn(r.getId()).getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        service.markNoShows();
        assertThat(service.getById(r.getId()).getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThatThrownBy(() -> service.complete(r.getId())).isInstanceOf(ConflictException.class);
        orders.close(orders.getOpenByTable(r.getAssignedTables().getFirst().getId()).id());
        assertThat(service.getById(r.getId()).getStatus()).isEqualTo(ReservationStatus.COMPLETED);
    }

    @Test void missedNoShowsUsePersistedDeadlineAfterConfigurationChange() {
        var r=service.createReservation(request("18:00",2));
        service.confirm(r.getId());
        jdbc.update("update booking_slot_config set no_show_grace_period_minutes=60");
        when(clock.instant()).thenReturn(r.getCheckInDeadline().plusSeconds(1));
        assertThatThrownBy(() -> service.checkIn(r.getId())).isInstanceOf(ConflictException.class);
        service.markNoShows();
        assertThat(service.getById(r.getId()).getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
    }

    @Test void mailIsSentOnlyAfterCommitAndContainsStoredDeadline() {
        var r=service.createReservation(request("18:00",2));
        new TransactionTemplate(transactions).execute(status -> {
            service.confirm(r.getId());
            verifyNoInteractions(mail);
            status.setRollbackOnly();
            return null;
        });
        verifyNoInteractions(mail);
        service.confirm(r.getId());
        service.confirm(r.getId());
        var capture=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(capture.capture());
        assertThat(capture.getValue().getText()).contains("18:30");
    }

    @Test void reservedTableCapacityAndAreaCannotBeChangedUnderExistingBooking() {
        var r=service.createReservation(request("18:00",6));
        long id=r.getAssignedTables().getFirst().getId();
        assertThatThrownBy(() -> tables.update(id,new TableRequest("A","OTHER",TableStatus.FREE,true,1)))
                .isInstanceOf(ConflictException.class);
        assertThat(jdbc.queryForObject("select seats from tables where id=?",Integer.class,id)).isEqualTo(4);
    }

    @Test void parallelGroupsCannotConsumeSameTables() throws Exception {
        var barrier=new CyclicBarrier(2);
        var executor=Executors.newFixedThreadPool(2);
        Callable<Boolean> booking=() -> {
            barrier.await(10,TimeUnit.SECONDS);
            try { service.createReservation(request("18:00",6)); return true; }
            catch(ConflictException expected) { return false; }
        };
        try {
            var first=executor.submit(booking);
            var second=executor.submit(booking);
            assertThat(java.util.List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("select count(*) from reservation_tables",Integer.class)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5,TimeUnit.SECONDS)).isTrue();
        }
    }


    @Test void smtpFailureDoesNotUndoCommittedConfirmation() {
        var r=service.createReservation(request("18:00",2));
        doThrow(new org.springframework.mail.MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        service.confirm(r.getId());
        assertThat(service.getById(r.getId()).getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test void olderTableClientsDoNotEraseConfiguredCapacity() {
        var r=service.createReservation(request("18:00",6));
        long id=r.getAssignedTables().getFirst().getId();
        assertThat(tables.update(id,new TableRequest("Renamed","ROOM",TableStatus.FREE,true)).seats()).isEqualTo(4);
    }


    @Test void httpContractSupportsMultipleTablesAndDetachedDtoMapping() throws Exception {
        var user=new org.thomcgn.backend.auth.domain.AppUser();
        user.setName("Reservation HTTP test");
        user.setEmail("reservation-"+java.util.UUID.randomUUID()+"@example.test");
        user.setPasswordHash("not-a-login-hash");
        user.setActive(true);
        user.setRole(org.thomcgn.backend.auth.domain.UserRole.BARCHEF);
        String token=tokens.createAccessToken(users.save(user)).token();
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        String body="{\"guestName\":\"HTTP group\",\"reservationDate\":\"2035-06-01\",\"reservationTime\":\"18:00\",\"guestCount\":6}";
        var settings=http("GET","/api/reservations/settings",null,null);
        assertThat(settings.statusCode()).isEqualTo(200);
        assertThat(json.readTree(settings.body()).path("timezone").asText()).isEqualTo("Europe/Berlin");
        var created=http("POST","/api/reservations",body,null);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        var result=json.readTree(created.body());
        assertThat(result.path("assignedTableIds").size()).isEqualTo(2);
        assertThat(result.path("durationMinutes").isNull()).isTrue();
        assertThat(json.readTree(settings.body()).path("graceMinutes").asInt()).isEqualTo(30);
        long id=result.path("id").asLong();
        assertThat(http("GET","/api/reservations/"+id,null,token).statusCode()).isEqualTo(200);
        assertThat(http("GET","/api/reservations?date=2035-06-01",null,token).statusCode()).isEqualTo(200);
        assertThat(http("PUT","/api/reservations/"+id,body,null).statusCode()).isEqualTo(401);
        var updated=http("PUT","/api/reservations/"+id,body.replace("18:00","21:00"),token);
        assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);
        assertThat(json.readTree(updated.body()).path("assignedTableIds").size()).isEqualTo(2);
        assertThat(http("POST","/api/reservations/"+id+"/confirm",null,token).statusCode()).isEqualTo(200);
        var scan=http("POST","/api/reservations/scan/"+result.path("qrCodeToken").asText(),null,token);
        assertThat(scan.statusCode()).as(scan.body()).isEqualTo(200);
        assertThat(json.readTree(scan.body()).path("checkInAllowed").asBoolean()).isFalse();
        when(clock.instant()).thenReturn(Instant.parse("2035-06-01T19:00:00Z"));
        assertThat(http("POST","/api/reservations/"+id+"/check-in",null,token).statusCode()).isEqualTo(200);
        for (var tableId : result.path("assignedTableIds")) {
            var bill=http("GET","/api/table-orders/open/table/"+tableId.asLong(),null,token);
            assertThat(bill.statusCode()).as(bill.body()).isEqualTo(200);
            long billId=json.readTree(bill.body()).path("id").asLong();
            assertThat(json.readTree(bill.body()).path("reservationId").asLong()).isEqualTo(id);
            assertThat(http("POST","/api/table-orders/"+billId+"/close",null,token).statusCode()).isEqualTo(200);
        }
        var completed=http("GET","/api/reservations/"+id,null,token);
        assertThat(json.readTree(completed.body()).path("status").asText()).isEqualTo("COMPLETED");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void failedPaymentKeepsReservationAndTableOccupiedUntilRetryCommits(boolean splitAll) {
        var reservation = service.createReservation(request("18:00", 4));
        service.confirm(reservation.getId());
        when(clock.instant()).thenReturn(reservation.getStartsAt());
        service.checkIn(reservation.getId());
        long tableId = reservation.getAssignedTables().getFirst().getId();
        var order = orders.getOpenByTable(tableId);
        long variant = stockedVariant();
        var populated = orders.addItem(order.id(),
                new org.thomcgn.backend.billing.api.dto.AddTableOrderItemRequest(variant, 2));
        var persistedItems = orders.getById(order.id()).items();
        String key = "phase3-reservation-payment";
        Runnable payment = splitAll
                ? () -> orders.splitPayment(order.id(), split(populated.items().getFirst().id(), 2), key)
                : () -> orders.close(order.id(), key);
        jdbc.execute("alter table billing_operations add constraint phase3_visit_failure check(operation_key <> 'phase3-reservation-payment')");
        try {
            assertThatThrownBy(payment::run).hasStackTraceContaining("phase3_visit_failure");
            assertThat(service.getById(reservation.getId()).getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
            assertThat(tableStatus(tableId)).isEqualTo("OCCUPIED");
            assertThat(orders.getOpenByTable(tableId).items()).isEqualTo(persistedItems);
            assertThat(jdbc.queryForObject("select count(*) from table_orders where paid", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from billing_operations where operation_key=?",
                    Integer.class, key)).isZero();
        } finally {
            jdbc.execute("alter table billing_operations drop constraint phase3_visit_failure");
        }
        payment.run();
        payment.run();
        assertThat(service.getById(reservation.getId()).getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(tableStatus(tableId)).isEqualTo("FREE");
        assertThat(jdbc.queryForObject("select count(*) from billing_operations where operation_key=?",
                Integer.class, key)).isEqualTo(1);
    }

    @Test void checkInOpensBookableBillsAndOnlyFullPaymentReleasesEachTable() {
        var reservation=service.createReservation(request("18:00",6));
        service.confirm(reservation.getId());
        when(clock.instant()).thenReturn(reservation.getStartsAt());
        service.checkIn(reservation.getId());
        var ids=reservation.getAssignedTables().stream().map(t -> t.getId()).toList();
        var first=orders.getOpenByTable(ids.get(0));
        var second=orders.getOpenByTable(ids.get(1));
        assertThat(first.reservationId()).isEqualTo(reservation.getId());
        assertThat(second.reservationId()).isEqualTo(reservation.getId());
        long variant=stockedVariant();
        first=orders.addItem(first.id(),new org.thomcgn.backend.billing.api.dto.AddTableOrderItemRequest(variant,2));
        assertThat(first.items()).hasSize(1);
        assertThat(first.items().getFirst().quantity()).isEqualTo(2);
        var split=orders.splitPayment(first.id(),split(first.items().getFirst().id(),1));
        assertThat(split.openOrder().status()).isEqualTo(org.thomcgn.backend.billing.domain.TableOrderStatus.OPEN);
        assertThat(tableStatus(ids.get(0))).isEqualTo("OCCUPIED");
        orders.close(second.id());
        assertThat(tableStatus(ids.get(1))).isEqualTo("FREE");
        assertThat(service.getById(reservation.getId()).getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThat(service.createReservation(request("21:00",4)).getAssignedTables().getFirst().getId()).isEqualTo(ids.get(1));
        assertThatThrownBy(() -> service.createReservation(request("21:00",1))).isInstanceOf(ConflictException.class);
        long count=jdbc.queryForObject("select count(*) from table_orders",Long.class);
        service.checkIn(reservation.getId());
        assertThat(jdbc.queryForObject("select count(*) from table_orders",Long.class)).isEqualTo(count);
        orders.splitPayment(first.id(),split(first.items().getFirst().id(),1));
        assertThat(tableStatus(ids.get(0))).isEqualTo("FREE");
        assertThat(service.getById(reservation.getId()).getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(jdbc.queryForObject("select total_stock_amount from inventory_items where linked_drink_variant_id=?",java.math.BigDecimal.class,variant))
                .isEqualByComparingTo("99.50");
    }

    @Test void elapsedTimeDoesNotReleaseButArchivingDoes() {
        var r=service.createReservation(request("18:00",6));
        service.confirm(r.getId());
        when(clock.instant()).thenReturn(r.getStartsAt());
        service.checkIn(r.getId());
        long table=r.getAssignedTables().getFirst().getId();
        var order=orders.getOpenByTable(table);
        when(clock.instant()).thenReturn(r.getStartsAt().plusSeconds(24*3600));
        service.markNoShows();
        assertThat(tableStatus(table)).isEqualTo("OCCUPIED");
        assertThat(service.getById(r.getId()).getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThatThrownBy(() -> service.complete(r.getId())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> tables.update(table,new TableRequest("A","ROOM",TableStatus.FREE,true,4))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.createReservation(new CreateReservationRequest("Blocked",null,null,date.plusDays(2),LocalTime.of(18,0),1)))
                .isInstanceOf(ConflictException.class);
        orders.markUnpaid(order.id());
        assertThat(tableStatus(table)).isEqualTo("FREE");
        assertThat(orders.getById(order.id()).paid()).isFalse();
        assertThat(service.getById(r.getId()).getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        orders.reopenUnpaid(order.id());
        assertThat(tableStatus(table)).isEqualTo("OCCUPIED");
        orders.close(order.id());
        assertThat(tableStatus(table)).isEqualTo("FREE");
    }

    @Test void exactlyThirtyMinutesExpiresNoShowWithoutOpeningBills() {
        var r=service.createReservation(request("18:00",6));
        service.confirm(r.getId());
        when(clock.instant()).thenReturn(r.getStartsAt().plusSeconds(1799));
        assertThat(service.scanToken(r.getQrCodeToken()).checkInAllowed()).isTrue();
        when(clock.instant()).thenReturn(r.getStartsAt().plusSeconds(1800));
        assertThat(service.scanToken(r.getQrCodeToken()).checkInAllowed()).isFalse();
        assertThatThrownBy(() -> service.checkIn(r.getId())).isInstanceOf(ConflictException.class);
        // Expired holds must not depend on waiting for the next scheduler tick.
        assertThat(service.createReservation(request("19:00",6)).getAssignedTables()).hasSize(2);
        service.markNoShows();
        assertThat(service.getById(r.getId()).getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        assertThat(jdbc.queryForObject("select count(*) from table_orders",Integer.class)).isZero();
    }

    @Test void olderFifteenMinuteSnapshotDoesNotOverrideNewThirtyMinuteRule() {
        var r=service.createReservation(request("18:00",2));
        service.confirm(r.getId());
        jdbc.update("update reservations set check_in_deadline=starts_at+interval '15 minutes', expires_at=reservation_date+reservation_time+interval '15 minutes' where id=?",r.getId());
        when(clock.instant()).thenReturn(r.getStartsAt().plusSeconds(20*60));
        assertThat(service.checkIn(r.getId()).getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
    }

    @Test void conflictingSecondTableRollsBackEntireGroupCheckIn() {
        var r=service.createReservation(request("18:00",6));
        service.confirm(r.getId());
        when(clock.instant()).thenReturn(r.getStartsAt());
        var ids=r.getAssignedTables().stream().map(t -> t.getId()).toList();
        jdbc.update("insert into table_orders(table_id,status,paid,opened_at) values(?,'OPEN',false,now())",ids.get(1));
        assertThatThrownBy(() -> service.checkIn(r.getId())).isInstanceOf(ConflictException.class);
        assertThat(service.getById(r.getId()).getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(tableStatus(ids.get(0))).isEqualTo("FREE");
        assertThat(jdbc.queryForObject("select count(*) from table_orders where reservation_id=?",Integer.class,r.getId())).isZero();
    }

    @Test void paymentRollbackAlsoRollsBackTableReleaseAndReservationCompletion() {
        var r=service.createReservation(request("18:00",2));
        service.confirm(r.getId());
        when(clock.instant()).thenReturn(r.getStartsAt());
        service.checkIn(r.getId());
        long table=r.getAssignedTables().getFirst().getId();
        long bill=orders.getOpenByTable(table).id();
        new TransactionTemplate(transactions).execute(status -> {
            orders.close(bill);
            assertThat(service.getById(r.getId()).getStatus()).isEqualTo(ReservationStatus.COMPLETED);
            status.setRollbackOnly();
            return null;
        });
        assertThat(service.getById(r.getId()).getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThat(tableStatus(table)).isEqualTo("OCCUPIED");
        assertThat(orders.getOpenByTable(table).id()).isEqualTo(bill);
    }

    @Test void concurrentCheckInsCreateOnlyOneBillPerGroupTable() throws Exception {
        var r=service.createReservation(request("18:00",6));
        service.confirm(r.getId());
        when(clock.instant()).thenReturn(r.getStartsAt());
        var barrier=new CyclicBarrier(2);
        var executor=Executors.newFixedThreadPool(2);
        Callable<ReservationStatus> checkIn=() -> {
            barrier.await(10,TimeUnit.SECONDS);
            return service.checkIn(r.getId()).getStatus();
        };
        try {
            var one=executor.submit(checkIn);
            var two=executor.submit(checkIn);
            assertThat(one.get(20,TimeUnit.SECONDS)).isEqualTo(ReservationStatus.CHECKED_IN);
            assertThat(two.get(20,TimeUnit.SECONDS)).isEqualTo(ReservationStatus.CHECKED_IN);
            assertThat(jdbc.queryForObject("select count(*) from table_orders where reservation_id=?",Integer.class,r.getId())).isEqualTo(2);
        } finally { executor.shutdownNow(); }
    }

    @Test void archivedDebtDoesNotBlockNewGroupOrBecomePaidWhenNewGroupPays() {
        var r=service.createReservation(request("18:00",2));
        service.confirm(r.getId());
        when(clock.instant()).thenReturn(r.getStartsAt());
        service.checkIn(r.getId());
        long table=r.getAssignedTables().getFirst().getId();
        var bill=orders.addItem(orders.getOpenByTable(table).id(),
                new org.thomcgn.backend.billing.api.dto.AddTableOrderItemRequest(stockedVariant(),2));
        var archived=orders.markUnpaid(bill.id());
        assertThat(archived.paid()).isFalse();
        assertThat(archived.total()).isEqualByComparingTo("6");
        assertThat(tableStatus(table)).isEqualTo("FREE");
        assertThat(service.getById(r.getId()).getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        var next=service.createReservation(request("18:00",2));
        assertThat(next.getAssignedTables().getFirst().getId()).isEqualTo(table);
        service.confirm(next.getId());
        // Even before check-in, the old deckel must not displace the new reservation.
        assertThatThrownBy(() -> orders.reopenUnpaid(bill.id())).isInstanceOf(ConflictException.class);
        service.checkIn(next.getId());
        long newBill=orders.getOpenByTable(table).id();
        assertThat(newBill).isNotEqualTo(bill.id());
        assertThatThrownBy(() -> orders.reopenUnpaid(bill.id())).isInstanceOf(ConflictException.class);
        orders.close(newBill);
        assertThat(tableStatus(table)).isEqualTo("FREE");
        assertThat(orders.getById(bill.id()).paid()).isFalse();
        assertThat(orders.getById(bill.id()).total()).isEqualByComparingTo("6");
        assertThat(orders.searchArchive(null,null,"UNPAID")).extracting(o -> o.id()).contains(bill.id());
    }

    @Test void archivingRollbackRetainsBillAndGroupOccupancy() {
        var r=service.createReservation(request("18:00",2));
        service.confirm(r.getId());
        when(clock.instant()).thenReturn(r.getStartsAt());
        service.checkIn(r.getId());
        long table=r.getAssignedTables().getFirst().getId();
        long bill=orders.getOpenByTable(table).id();
        new TransactionTemplate(transactions).execute(status -> {
            orders.markUnpaid(bill);
            assertThat(service.getById(r.getId()).getStatus()).isEqualTo(ReservationStatus.COMPLETED);
            status.setRollbackOnly();
            return null;
        });
        assertThat(tableStatus(table)).isEqualTo("OCCUPIED");
        assertThat(orders.getOpenByTable(table).id()).isEqualTo(bill);
        assertThat(service.getById(r.getId()).getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
    }

    @Test void walkInCanStartANewBillWhilePreviousDeckelRemainsInArchive() {
        var first=orders.open(new org.thomcgn.backend.billing.api.dto.OpenTableOrderRequest(1L,null));
        orders.markUnpaid(first.id());
        var next=orders.open(new org.thomcgn.backend.billing.api.dto.OpenTableOrderRequest(1L,null));
        assertThat(next.id()).isNotEqualTo(first.id());
        assertThat(tableStatus(1L)).isEqualTo("OCCUPIED");
        assertThatThrownBy(() -> orders.reopenUnpaid(first.id())).isInstanceOf(ConflictException.class);
        orders.close(next.id());
        assertThat(tableStatus(1L)).isEqualTo("FREE");
        assertThat(orders.getById(first.id()).paid()).isFalse();
    }

    private String tableStatus(long table) {
        return jdbc.queryForObject("select status from tables where id=?",String.class,table);
    }

    private org.thomcgn.backend.billing.api.dto.SplitTableOrderPaymentRequest split(long item,int quantity) {
        return new org.thomcgn.backend.billing.api.dto.SplitTableOrderPaymentRequest(
                java.util.List.of(new org.thomcgn.backend.billing.api.dto.SplitTableOrderItemRequest(item,quantity)));
    }

    private long stockedVariant() {
        long category=jdbc.queryForObject("insert into drink_categories(name,sort_order,active) values(?,0,true) returning id",Long.class,"Payment-"+java.util.UUID.randomUUID());
        long drink=jdbc.queryForObject("insert into drinks(category_id,name,active) values(?,'Group drink',true) returning id",Long.class,category);
        long variant=jdbc.queryForObject("insert into drink_variants(drink_id,display_volume_name,volume_ml,price,active,use_volume_standard_price) values(?,'Glass',250,3,true,false) returning id",Long.class,drink);
        jdbc.update("""
                insert into inventory_items(name,linked_drink_variant_id,package_type,packages_in_stock,content_per_package,
                content_unit,total_stock_amount,reorder_threshold,minimum_stock,recommended_reorder_amount,active)
                values('Group stock',?,'BARREL',1,100,'LITER',100,0,0,0,true)
                """,variant);
        return variant;
    }

    private java.net.http.HttpResponse<String> http(String method,String path,String body,String token) throws Exception {
        var builder=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+path))
            .header("Content-Type","application/json")
            .method(method,body==null ? java.net.http.HttpRequest.BodyPublishers.noBody() : java.net.http.HttpRequest.BodyPublishers.ofString(body));
        if(token!=null) builder.header("Authorization","Bearer "+token);
        return java.net.http.HttpClient.newHttpClient().send(builder.build(),java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private CreateReservationRequest request(String time,int guests) {
        return new CreateReservationRequest("Guest","guest@example.test",null,date,LocalTime.parse(time),guests);
    }
}
