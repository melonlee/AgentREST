package com.lazy.rest.mcp;

import com.lazy.rest.article.ArticleDtos.ArticleRequest;
import com.lazy.rest.article.ArticleService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/**
 * Write tools. Three independent gates must all pass:
 * <ol>
 *   <li>{@code lazyrest.mcp.write-tools-enabled=true} - otherwise this bean (and the tool) does not exist,</li>
 *   <li>the caller holds {@code mcp:write} (ROLE_EDITOR / ROLE_ADMIN) and is not using a read-only API key,</li>
 *   <li>the service-level {@code article:write} check.</li>
 * </ol>
 * Agents can only create drafts; publishing stays a human decision (REST only).
 */
@Component
@ConditionalOnBooleanProperty("lazyrest.mcp.write-tools-enabled")
public class ArticleWriteMcpTools {

    public static final String REQUIRED_AUTHORITY = "mcp:write";

    private final ArticleService articleService;
    private final McpToolExecutor executor;

    public ArticleWriteMcpTools(ArticleService articleService, McpToolExecutor executor) {
        this.articleService = articleService;
        this.executor = executor;
    }

    @McpTool(name = "createArticleDraft", title = "Create article draft",
            description = """
                    Create a new article as a DRAFT owned by the caller. Drafts are not visible to other readers \
                    and are never published automatically - a human reviews and publishes them. \
                    Returns the created article including its id.""",
            annotations = @McpTool.McpAnnotations(title = "Create article draft", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CallToolResult createArticleDraft(McpTransportContext transportContext,
            @McpToolParam(description = "Title, 1-200 characters.") String title,
            @McpToolParam(description = "One or two sentence summary, max 500 characters.", required = false) String summary,
            @McpToolParam(description = "Full article body (Markdown allowed), max 20000 characters.") String content) {
        return executor.call("createArticleDraft", transportContext,
                ArticleMcpTools.args("title", title, "summaryLength", summary == null ? 0 : summary.length(),
                        "contentLength", content == null ? 0 : content.length()),
                REQUIRED_AUTHORITY, () -> articleService.create(new ArticleRequest(title, summary, content)));
    }
}
