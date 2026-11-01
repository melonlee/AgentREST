package com.lazy.rest.mcp;

import com.lazy.rest.article.Article;
import com.lazy.rest.article.ArticleDtos.ArticleSearch;
import com.lazy.rest.article.ArticleService;
import com.lazy.rest.common.api.PageResponse;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only MCP tools. They are thin adapters over {@link ArticleService}: the same permission,
 * visibility and validation rules apply as for the REST API.
 */
@Component
public class ArticleMcpTools {

    static final int MAX_PAGE_SIZE = 50;

    private final ArticleService articleService;
    private final McpToolExecutor executor;

    public ArticleMcpTools(ArticleService articleService, McpToolExecutor executor) {
        this.articleService = articleService;
        this.executor = executor;
    }

    @McpTool(name = "searchArticles", title = "Search articles",
            description = """
                    Search the article knowledge base. Returns published articles plus the caller's own drafts, \
                    newest first, as a page of summaries (id, title, summary, status, author, timestamps). \
                    Use getArticle with an id to read the full content.""",
            annotations = @McpTool.McpAnnotations(title = "Search articles", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult searchArticles(McpTransportContext transportContext,
            @McpToolParam(description = "Free-text query matched case-insensitively against title and summary. Omit to list everything.",
                    required = false) String query,
            @McpToolParam(description = "Only return articles with this status: PUBLISHED or DRAFT.", required = false)
            Article.Status status,
            @McpToolParam(description = "Zero-based page number (default 0).", required = false) Integer page,
            @McpToolParam(description = "Page size, 1-50 (default 10).", required = false) Integer size) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        return executor.call("searchArticles", transportContext, args("query", query, "status", status, "page", p, "size", s),
                null, () -> PageResponse.of(articleService.search(new ArticleSearch(query, status, null, null),
                        PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt", "id")))));
    }

    @McpTool(name = "getArticle", title = "Get article",
            description = "Get one article by id, including its full content, status, author and version.",
            annotations = @McpTool.McpAnnotations(title = "Get article", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getArticle(McpTransportContext transportContext,
            @McpToolParam(description = "Article id, as returned by searchArticles.") long id) {
        return executor.call("getArticle", transportContext, args("id", id), null, () -> articleService.get(id));
    }

    static Map<String, Object> args(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
