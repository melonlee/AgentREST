package com.lazy.rest.audit;

import org.springframework.web.context.request.RequestContextHolder;

/**
 * Tells the audit layer through which channel the current call arrived. The MCP tool executor
 * marks its calls as {@link AuditLog.Channel#MCP}; anything else inside an HTTP request is REST,
 * and work outside a request (startup, schedulers) is SYSTEM.
 */
public final class AuditContext {

    private static final ThreadLocal<AuditLog.Channel> CHANNEL = new ThreadLocal<>();

    private AuditContext() {
    }

    public static AuditLog.Channel currentChannel() {
        AuditLog.Channel channel = CHANNEL.get();
        if (channel != null) {
            return channel;
        }
        return RequestContextHolder.getRequestAttributes() != null ? AuditLog.Channel.REST : AuditLog.Channel.SYSTEM;
    }

    public static void set(AuditLog.Channel channel) {
        CHANNEL.set(channel);
    }

    public static void clear() {
        CHANNEL.remove();
    }
}
