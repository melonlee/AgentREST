package com.fantasyailab.agentrest.audit;

import com.fantasyailab.agentrest.common.api.PageResponse;
import com.fantasyailab.agentrest.common.api.Pageables;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit", description = "Who did what, when, through which channel (requires audit:read)")
public class AuditController {

    private static final Set<String> SORTABLE = Set.of("occurredAt", "actor", "action");

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Query the audit log", description = "Filter by actor, action, channel (REST/MCP/SYSTEM), outcome and time range.")
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @Parameter(example = "ARTICLE_CREATE") String action,
            @RequestParam(required = false) AuditLog.Channel channel,
            @RequestParam(required = false) AuditLog.Outcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Pageable safe = Pageables.restrictSort(pageable, SORTABLE, Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        var query = new AuditService.AuditQuery(actor, action, channel, outcome, from, to);
        return PageResponse.of(auditService.search(query, safe), AuditLogResponse::from);
    }

    public record AuditLogResponse(Long id, Instant occurredAt, String actor, String action, String resourceType,
            String resourceId, AuditLog.Channel channel, AuditLog.Outcome outcome, String detail, String requestId,
            String clientIp, Long durationMs) {

        static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(log.getId(), log.getOccurredAt(), log.getActor(), log.getAction(),
                    log.getResourceType(), log.getResourceId(), log.getChannel(), log.getOutcome(), log.getDetail(),
                    log.getRequestId(), log.getClientIp(), log.getDurationMs());
        }
    }
}
