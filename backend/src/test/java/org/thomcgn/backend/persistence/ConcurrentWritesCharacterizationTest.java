package org.thomcgn.backend.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.thomcgn.backend.auth.api.dto.LoginRequest;
import org.thomcgn.backend.auth.api.dto.RefreshTokenRequest;
import org.thomcgn.backend.auth.repository.RefreshTokenRepository;
import org.thomcgn.backend.auth.service.AuthService;
import org.thomcgn.backend.auth.service.ClientMetadata;
import org.thomcgn.backend.billing.api.dto.OpenTableOrderRequest;
import org.thomcgn.backend.billing.domain.TableOrderStatus;
import org.thomcgn.backend.billing.repository.TableOrderRepository;
import org.thomcgn.backend.billing.service.TableOrderService;
import org.thomcgn.backend.inventory.api.dto.InventoryAdjustmentRequest;
import org.thomcgn.backend.inventory.repository.InventoryItemRepository;
import org.thomcgn.backend.inventory.service.InventoryService;
import org.thomcgn.backend.reservation.api.dto.CreateReservationRequest;
import org.thomcgn.backend.reservation.repository.ReservationRepository;
import org.thomcgn.backend.reservation.service.ReservationService;
import org.thomcgn.backend.support.PostgresIntegrationTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.thomcgn.backend.billing.repository.TableOrderItemRepository;
import org.thomcgn.backend.billing.api.dto.SplitTableOrderPaymentRequest;
import org.thomcgn.backend.billing.api.dto.SplitTableOrderItemRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.dao.DataIntegrityViolationException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

/** Documents existing defects, NOT desired invariants. Replace with safety assertions in phases 6-8. */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentWritesCharacterizationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService reservations;
    @Autowired TableOrderService orders;
    @Autowired InventoryService inventory;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean ReservationRepository reservationRepository;
    @MockitoSpyBean TableOrderRepository orderRepository;
    @MockitoSpyBean TableOrderItemRepository itemRepository;
    @MockitoSpyBean InventoryItemRepository inventoryRepository;
    @MockitoSpyBean RefreshTokenRepository refreshRepository;

    @BeforeEach
    void resetFixtures() {
        jdbc.execute("truncate table tables, inventory_items, app_users, drink_categories restart identity cascade");
        jdbc.update("insert into tables(name,status,active,seats) values('Concurrent table','FREE',true,4)");
    }

    // Spring repository proxies delegate through the spy default answer, not callRealMethod().
    @Test
    void parallelReservationsCannotConsumeTheSameLastTable() throws Exception {
        var barrier = new CyclicBarrier(2);
        var successes = new java.util.concurrent.atomic.AtomicInteger();
        var conflicts = new java.util.concurrent.atomic.AtomicInteger();
        var request = new CreateReservationRequest("Concurrent guest", null, null,
                LocalDate.now().plusDays(7), LocalTime.of(18, 0), 1);
        race(() -> {
            barrier.await(10, TimeUnit.SECONDS);
            try { reservations.createReservation(request); successes.incrementAndGet(); }
            catch (org.thomcgn.backend.common.exception.ConflictException expected) { conflicts.incrementAndGet(); }
            return null;
        });
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from reservations", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentOpenCreatesExactlyOneBill() throws Exception {
        var barrier = new CyclicBarrier(2);
        var successes = new AtomicInteger();
        var conflicts = new AtomicInteger();
        race(() -> {
            barrier.await(10, TimeUnit.SECONDS);
            try { orders.open(new OpenTableOrderRequest(1L, null)); successes.incrementAndGet(); }
            catch (org.thomcgn.backend.common.exception.ConflictException expected) { conflicts.incrementAndGet(); }
            return null;
        });
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from table_orders where status='OPEN'", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentStockAdjustmentsCannotLoseAnUpdateOrGoNegative() throws Exception {
        jdbc.update("""
                insert into inventory_items(name,package_type,packages_in_stock,content_per_package,
                  content_unit,total_stock_amount,reorder_threshold,minimum_stock,recommended_reorder_amount,active)
                values('Concurrent stock','BARREL',1,10,'LITER',10,0,0,0,true)
                """);
        var barrier = new CyclicBarrier(2);
        var successes = new AtomicInteger();
        var conflicts = new AtomicInteger();
        race(() -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                inventory.adjust(1L, new InventoryAdjustmentRequest(new BigDecimal("6"), "Race test", false), "test");
                successes.incrementAndGet();
            } catch (org.thomcgn.backend.common.exception.ConflictException expected) {
                conflicts.incrementAndGet();
            }
            return null;
        });
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select total_stock_amount from inventory_items where id=1", BigDecimal.class))
                .isEqualByComparingTo("4");
        assertThat(jdbc.queryForObject("select sum(amount) from inventory_movements", BigDecimal.class))
                .isEqualByComparingTo("-6");
    }

    @Test
    void concurrentRefreshHasOneSuccessorAndReplayRevokesIt() throws Exception {
        jdbc.update("insert into app_users(name,email,password_hash,role,active) values('Race','race@example.test',?,'ADMIN',true)",
                encoder.encode("Only-a-test-password"));
        var metadata = new ClientMetadata("Concurrency test", "127.0.0.1");
        var login = auth.login(new LoginRequest("race@example.test", "Only-a-test-password"), metadata);
        var barrier = new CyclicBarrier(2);
        var successes = new java.util.concurrent.atomic.AtomicInteger();
        var rejections = new java.util.concurrent.atomic.AtomicInteger();
        race(() -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                auth.refresh(new RefreshTokenRequest(login.refreshToken()), metadata);
                successes.incrementAndGet();
            } catch (org.thomcgn.backend.auth.AuthenticationRejectedException expected) {
                rejections.incrementAndGet();
            }
            return null;
        });
        assertThat(successes.get()).isEqualTo(1);
        assertThat(rejections.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where revoked_at is null", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where revoked_reason='ROTATED'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where revoked_reason='REFRESH_REPLAY'", Integer.class)).isEqualTo(1);
    }

    @Test
    void parallelPartialPaymentsCannotDuplicatePaidQuantity() throws Exception {
        jdbc.update("insert into drink_categories(name,sort_order,active) values('Race',0,true)");
        jdbc.update("insert into drinks(category_id,name,active) values(1,'Race drink',true)");
        jdbc.update("insert into drink_variants(drink_id,display_volume_name,volume_ml,price,active) values(1,'Glass',250,3,true)");
        jdbc.update("insert into table_orders(table_id,status,paid,opened_at) values(1,'OPEN',false,now())");
        jdbc.update("""
                insert into table_order_items(table_order_id,drink_variant_id,quantity,unit_price,total_price,deducted_volume_ml)
                values(1,1,3,3,9,750)
                """);
        var barrier = new CyclicBarrier(2);
        var successes = new AtomicInteger();
        var rejections = new AtomicInteger();
        var request = new SplitTableOrderPaymentRequest(List.of(new SplitTableOrderItemRequest(1L, 2)));
        race(() -> {
            barrier.await(10, TimeUnit.SECONDS);
            try { orders.splitPayment(1L, request); successes.incrementAndGet(); }
            catch (org.thomcgn.backend.common.exception.BadRequestException expected) { rejections.incrementAndGet(); }
            return null;
        });
        assertThat(successes.get()).isEqualTo(1);
        assertThat(rejections.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select sum(i.quantity) from table_order_items i join table_orders o on o.id=i.table_order_id where o.paid
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select quantity from table_order_items where table_order_id=1", Integer.class)).isEqualTo(1);
    }

    @Test
    void movementConstraintFailureRollsBackStockMutation() {
        jdbc.update("""
                insert into inventory_items(name,package_type,packages_in_stock,content_per_package,
                  content_unit,total_stock_amount,reorder_threshold,minimum_stock,recommended_reorder_amount,active)
                values('Rollback stock','BARREL',1,10,'LITER',10,0,0,0,true)
                """);
        // The movement is inserted after the stock mutation, but must commit atomically with it.
        assertThatThrownBy(() -> inventory.adjust(1L,
                new InventoryAdjustmentRequest(BigDecimal.ONE, null, false), "test"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select total_stock_amount from inventory_items where id=1", BigDecimal.class))
                .isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements", Integer.class)).isZero();
    }

    private static void race(Callable<?> action) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(action);
            var second = executor.submit(action);
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
