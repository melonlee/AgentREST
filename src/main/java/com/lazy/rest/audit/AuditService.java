package com.lazy.rest.audit;

import com.lazy.rest.common.security.SecurityUtils;
import com.lazy.rest.common.web.RequestIdFilter;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Persists an audit entry in its own transaction, so it survives a rollback of the audited
     * operation. Auditing must never break the business call, so failures are logged and swallowed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, String action, String resourceType, String resourceId,
            AuditLog.Outcome outcome, String detail, Long durationMs) {
        try {
            String who = actor != null ? actor : SecurityUtils.currentUsername().orElse("anonymous");
            repository.save(new AuditLog(who, action, resourceType, resourceId, AuditContext.currentChannel(),
                    outcome, detail, RequestIdFilter.currentRequestId(), RequestIdFilter.currentClientIp(),
                    durationMs));
        } catch (RuntimeException e) {
            log.error("Failed to write audit log for action {}", action, e);
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('audit:read')")
    public Page<AuditLog> search(AuditQuery query, Pageable pageable) {
        Specification<AuditLog> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.actor() != null && !query.actor().isBlank()) {
                predicates.add(cb.equal(root.get("actor"), query.actor()));
            }
            if (query.action() != null && !query.action().isBlank()) {
                predicates.add(cb.equal(root.get("action"), query.action()));
            }
            if (query.channel() != null) {
                predicates.add(cb.equal(root.get("channel"), query.channel()));
            }
            if (query.outcome() != null) {
                predicates.add(cb.equal(root.get("outcome"), query.outcome()));
            }
            if (query.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), query.from()));
            }
            if (query.to() != null) {
                predicates.add(cb.lessThan(root.<Instant>get("occurredAt"), query.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable);
    }

    public record AuditQuery(String actor, String action, AuditLog.Channel channel, AuditLog.Outcome outcome,
            Instant from, Instant to) {
    }
}
