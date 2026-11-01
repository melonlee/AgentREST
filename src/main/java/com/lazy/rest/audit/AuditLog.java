package com.lazy.rest.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Append-only audit record. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    public enum Channel { REST, MCP, SYSTEM }

    public enum Outcome { SUCCESS, DENIED, FAILURE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 100, updatable = false)
    private String actor;

    @Column(nullable = false, length = 64, updatable = false)
    private String action;

    @Column(name = "resource_type", length = 64, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", length = 64, updatable = false)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Outcome outcome;

    @Column(length = 1000, updatable = false)
    private String detail;

    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;

    @Column(name = "client_ip", length = 64, updatable = false)
    private String clientIp;

    @Column(name = "duration_ms", updatable = false)
    private Long durationMs;

    protected AuditLog() {
    }

    public AuditLog(String actor, String action, String resourceType, String resourceId, Channel channel,
            Outcome outcome, String detail, String requestId, String clientIp, Long durationMs) {
        this.occurredAt = Instant.now();
        this.actor = truncate(actor, 100);
        this.action = truncate(action, 64);
        this.resourceType = truncate(resourceType, 64);
        this.resourceId = truncate(resourceId, 64);
        this.channel = channel;
        this.outcome = outcome;
        this.detail = truncate(detail, 1000);
        this.requestId = truncate(requestId, 64);
        this.clientIp = truncate(clientIp, 64);
        this.durationMs = durationMs;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public Channel getChannel() {
        return channel;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getDetail() {
        return detail;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public Long getDurationMs() {
        return durationMs;
    }
}
