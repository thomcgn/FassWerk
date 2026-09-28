package org.thomcgn.backend.common.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes booking and table-capacity mutations across instances, including empty calendars. */
@Component
@RequiredArgsConstructor
public class BookingMutationLock {
    private final JdbcTemplate jdbc;
    private final org.thomcgn.backend.common.audit.AuditContext auditContext;

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        auditContext.bind();
        jdbc.execute("select pg_advisory_xact_lock(7100701)");
    }
}
