package org.thomcgn.backend.persistence;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.billing.domain.TableOrderStatus;
import org.thomcgn.backend.billing.repository.TableOrderRepository;
import org.thomcgn.backend.support.PostgresIntegrationTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ArchiveRepositoryIntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TableOrderRepository orders;

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"terrasse", "TERRASSE", "absent"})
    void archiveFiltersHandleNullableParametersAndCaseInsensitiveTextOnPostgres(String query) {
        long table = jdbc.queryForObject("""
                insert into tables(name,status,active) values('Repository Terrasse','FREE',true) returning id
                """, Long.class);
        long order = jdbc.queryForObject("""
                insert into table_orders(table_id,status,paid,opened_at,closed_at)
                values(?,'CLOSED',false,'2040-01-01 18:00','2040-01-01 19:00') returning id
                """, Long.class, table);
        var start = LocalDateTime.of(2040, 1, 1, 0, 0);
        var archived = orders.searchArchive(TableOrderStatus.CLOSED, start, start.plusDays(1), query, null);
        var unpaid = orders.searchUnpaidArchive(TableOrderStatus.CLOSED, query);
        if ("absent".equals(query)) {
            assertThat(archived).isEmpty();
            assertThat(unpaid).isEmpty();
        } else {
            assertThat(archived).extracting(item -> item.getId()).contains(order);
            assertThat(unpaid).extracting(item -> item.getId()).contains(order);
        }
        assertThat(orders.searchArchive(TableOrderStatus.CLOSED, start, start.plusDays(1), query, true)).isEmpty();
    }
}
