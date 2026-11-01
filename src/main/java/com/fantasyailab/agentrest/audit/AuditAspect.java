package com.fantasyailab.agentrest.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Around advice for {@link Audited}. Ordered first so it wraps method security and transactions:
 * denied calls ({@code @PreAuthorize}) are recorded as DENIED, exceptions as FAILURE.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(com.fantasyailab.agentrest.audit.Audited)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Audited audited = AnnotationUtils.findAnnotation(
                AopUtils.getMostSpecificMethod(method, pjp.getTarget().getClass()), Audited.class);
        if (audited == null) {
            return pjp.proceed();
        }
        long start = System.nanoTime();
        AuditLog.Outcome outcome = AuditLog.Outcome.SUCCESS;
        String detail = null;
        Object result = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (AccessDeniedException e) {
            outcome = AuditLog.Outcome.DENIED;
            detail = "Access denied";
            throw e;
        } catch (AuthenticationException e) {
            outcome = AuditLog.Outcome.DENIED;
            detail = e.getClass().getSimpleName();
            throw e;
        } catch (Throwable t) {
            outcome = AuditLog.Outcome.FAILURE;
            detail = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
            throw t;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String resourceId = evaluate(audited.resourceId(), pjp, result, outcome == AuditLog.Outcome.SUCCESS);
            String actor = evaluate(audited.actor(), pjp, result, true);
            auditService.record(actor, audited.action(), emptyToNull(audited.resourceType()), resourceId,
                    outcome, detail, durationMs);
        }
    }

    private String evaluate(String expression, ProceedingJoinPoint pjp, Object result, boolean resultAvailable) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        if (!resultAvailable && expression.contains("#result")) {
            return null;
        }
        try {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    pjp.getTarget(), signature.getMethod(), pjp.getArgs(), parameterNames);
            context.setVariable("result", result);
            Object value = cache.computeIfAbsent(expression, parser::parseExpression).getValue(context);
            return value != null ? value.toString() : null;
        } catch (RuntimeException e) {
            log.warn("Could not evaluate audit expression '{}': {}", expression, e.getMessage());
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
