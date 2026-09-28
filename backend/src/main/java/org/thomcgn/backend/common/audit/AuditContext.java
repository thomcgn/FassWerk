package org.thomcgn.backend.common.audit;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Transaction-local attribution; connection-pool reuse must never carry another actor. */
@Component
@RequiredArgsConstructor
public class AuditContext {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void bind() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = "system:internal";
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            var ids = jdbc.queryForList("select id from app_users where lower(email)=lower(?)",
                    Long.class, authentication.getName());
            if (ids.size() != 1) throw new IllegalStateException("Audit actor could not be resolved");
            actor = "user:" + ids.getFirst();
        }
        jdbc.queryForObject("select set_config('fasswerk.audit_actor', ?, true)", String.class, actor);
        String requestId = MDC.get("requestId");
        jdbc.queryForObject("select set_config('fasswerk.audit_request', ?, true)", String.class,
                requestId != null && requestId.matches("[A-Za-z0-9._-]{1,128}") ? requestId : "");
    }
}
