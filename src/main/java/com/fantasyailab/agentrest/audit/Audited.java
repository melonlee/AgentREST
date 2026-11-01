package com.fantasyailab.agentrest.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records an {@link AuditLog} entry (who / what / when / outcome / channel) every time the
 * annotated method is invoked - including when it is denied by method security or fails.
 *
 * <pre>{@code
 * @Audited(action = "ARTICLE_UPDATE", resourceType = "article", resourceId = "#id")
 * public ArticleResponse update(long id, ArticleRequest request) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

    /** Stable action name, e.g. {@code ARTICLE_CREATE}. */
    String action();

    String resourceType() default "";

    /** SpEL evaluated against method parameters ({@code #id}) and the return value ({@code #result.id}). */
    String resourceId() default "";

    /** SpEL for the actor when the caller is not authenticated yet (e.g. login: {@code #request.username}). */
    String actor() default "";
}
