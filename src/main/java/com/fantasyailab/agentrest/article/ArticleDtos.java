package com.fantasyailab.agentrest.article;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ArticleDtos {

    private ArticleDtos() {
    }

    @Schema(description = "Article content. New articles always start as DRAFT.")
    public record ArticleRequest(
            @NotBlank @Size(max = 200) @Schema(example = "Exposing Spring services as MCP tools") String title,
            @Size(max = 500) @Schema(example = "How AgentREST lets AI agents call your business API safely.") String summary,
            @NotBlank @Size(max = 20_000) @Schema(example = "Model Context Protocol (MCP) is an open protocol...") String content) {
    }

    @Schema(description = "Search filters (all optional)")
    public record ArticleSearch(
            @Schema(description = "Case-insensitive match on title or summary", example = "mcp") String q,
            Article.Status status,
            @Schema(description = "Author username", example = "editor") String author,
            @Schema(description = "Only my own articles") Boolean mine) {

        public static ArticleSearch empty() {
            return new ArticleSearch(null, null, null, null);
        }
    }

    public record ArticleSummary(Long id, String title, String summary, Article.Status status, String author,
            Instant createdAt, Instant updatedAt, Instant publishedAt) {

        static ArticleSummary from(Article a) {
            return new ArticleSummary(a.getId(), a.getTitle(), a.getSummary(), a.getStatus(),
                    a.getAuthor().getUsername(), a.getCreatedAt(), a.getUpdatedAt(), a.getPublishedAt());
        }
    }

    public record ArticleResponse(Long id, String title, String summary, String content, Article.Status status,
            String author, Instant createdAt, Instant updatedAt, Instant publishedAt, long version) {

        static ArticleResponse from(Article a) {
            return new ArticleResponse(a.getId(), a.getTitle(), a.getSummary(), a.getContent(), a.getStatus(),
                    a.getAuthor().getUsername(), a.getCreatedAt(), a.getUpdatedAt(), a.getPublishedAt(),
                    a.getVersion());
        }
    }
}
