package com.fantasyailab.agentrest.article;

import com.fantasyailab.agentrest.article.ArticleDtos.ArticleRequest;
import com.fantasyailab.agentrest.article.ArticleDtos.ArticleResponse;
import com.fantasyailab.agentrest.article.ArticleDtos.ArticleSearch;
import com.fantasyailab.agentrest.article.ArticleDtos.ArticleSummary;
import com.fantasyailab.agentrest.audit.Audited;
import com.fantasyailab.agentrest.common.api.LikePatterns;
import com.fantasyailab.agentrest.common.error.ResourceNotFoundException;
import com.fantasyailab.agentrest.common.security.SecurityUtils;
import com.fantasyailab.agentrest.user.User;
import com.fantasyailab.agentrest.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Article use cases. This is the single place where permissions, ownership and validation are
 * enforced - the REST controller and the MCP tools both call this service, so an AI agent can
 * never do more than the user it acts for.
 *
 * <p>Visibility: published articles are visible to every reader; drafts only to their author and
 * to holders of {@code article:manage}. Invisible articles are reported as 404 (not 403) so their
 * existence is not leaked.
 */
@Service
@Validated
@Transactional(readOnly = true)
public class ArticleService {

    static final String MANAGE = "article:manage";

    private final ArticleRepository articles;
    private final UserRepository users;

    public ArticleService(ArticleRepository articles, UserRepository users) {
        this.articles = articles;
        this.users = users;
    }

    @PreAuthorize("hasAuthority('article:read')")
    public Page<ArticleSummary> search(@NotNull ArticleSearch search, Pageable pageable) {
        String me = SecurityUtils.requireUsername();
        boolean manager = SecurityUtils.hasAuthority(MANAGE);
        Specification<Article> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            var authorName = root.get("author").<String>get("username");
            if (!manager) {
                predicates.add(cb.or(cb.equal(root.get("status"), Article.Status.PUBLISHED), cb.equal(authorName, me)));
            }
            if (search.q() != null && !search.q().isBlank()) {
                String pattern = LikePatterns.contains(search.q());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("summary")), pattern, LikePatterns.ESCAPE)));
            }
            if (search.status() != null) {
                predicates.add(cb.equal(root.get("status"), search.status()));
            }
            if (search.author() != null && !search.author().isBlank()) {
                predicates.add(cb.equal(authorName, search.author()));
            }
            if (Boolean.TRUE.equals(search.mine())) {
                predicates.add(cb.equal(authorName, me));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return articles.findAll(spec, pageable).map(ArticleSummary::from);
    }

    @PreAuthorize("hasAuthority('article:read')")
    public ArticleResponse get(long id) {
        return ArticleResponse.from(findVisible(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('article:write')")
    @Audited(action = "ARTICLE_CREATE", resourceType = "article", resourceId = "#result.id")
    public ArticleResponse create(@Valid @NotNull ArticleRequest request) {
        String me = SecurityUtils.requireUsername();
        User author = users.findByUsername(me).orElseThrow(() -> new ResourceNotFoundException("User", me));
        Article article = new Article(request.title().trim(), trimToNull(request.summary()), request.content(), author);
        return ArticleResponse.from(articles.save(article));
    }

    @Transactional
    @PreAuthorize("hasAuthority('article:write')")
    @Audited(action = "ARTICLE_UPDATE", resourceType = "article", resourceId = "#id")
    public ArticleResponse update(long id, @Valid @NotNull ArticleRequest request) {
        Article article = findVisible(id);
        requireOwnerOrManager(article);
        article.edit(request.title().trim(), trimToNull(request.summary()), request.content());
        articles.flush(); // surface optimistic-lock conflicts inside this call
        return ArticleResponse.from(article);
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('article:publish', 'article:manage')")
    @Audited(action = "ARTICLE_PUBLISH", resourceType = "article", resourceId = "#id")
    public ArticleResponse publish(long id) {
        Article article = findVisible(id);
        requireOwnerOrManager(article);
        article.publish();
        articles.flush();
        return ArticleResponse.from(article);
    }

    @Transactional
    @PreAuthorize("hasAuthority('article:write')")
    @Audited(action = "ARTICLE_DELETE", resourceType = "article", resourceId = "#id")
    public void delete(long id) {
        Article article = findVisible(id);
        requireOwnerOrManager(article);
        articles.delete(article);
    }

    private Article findVisible(long id) {
        Article article = articles.findWithAuthorById(id).orElseThrow(() -> new ResourceNotFoundException("Article", id));
        boolean visible = article.getStatus() == Article.Status.PUBLISHED
                || article.isAuthoredBy(SecurityUtils.requireUsername())
                || SecurityUtils.hasAuthority(MANAGE);
        if (!visible) {
            throw new ResourceNotFoundException("Article", id);
        }
        return article;
    }

    private static void requireOwnerOrManager(Article article) {
        if (!article.isAuthoredBy(SecurityUtils.requireUsername()) && !SecurityUtils.hasAuthority(MANAGE)) {
            throw new AccessDeniedException("Only the author or an article manager can modify this article");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
