package com.fantasyailab.agentrest.article;

import com.fantasyailab.agentrest.article.ArticleDtos.ArticleRequest;
import com.fantasyailab.agentrest.article.ArticleDtos.ArticleResponse;
import com.fantasyailab.agentrest.article.ArticleDtos.ArticleSearch;
import com.fantasyailab.agentrest.article.ArticleDtos.ArticleSummary;
import com.fantasyailab.agentrest.common.api.PageResponse;
import com.fantasyailab.agentrest.common.api.Pageables;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Set;

@RestController
@RequestMapping("/api/articles")
@Tag(name = "Articles", description = "Example domain module: CRUD, validation, search, paging/sorting, ownership")
public class ArticleController {

    static final Set<String> SORTABLE = Set.of("id", "title", "createdAt", "updatedAt", "publishedAt");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    @Operation(summary = "Search articles",
            description = "Published articles plus your own drafts. Sort by: id, title, createdAt, updatedAt, publishedAt. Max page size 100.")
    public PageResponse<ArticleSummary> search(@ParameterObject ArticleSearch search,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Pageable safe = Pageables.restrictSort(pageable, SORTABLE, DEFAULT_SORT);
        return PageResponse.of(articleService.search(search, safe));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an article")
    public ArticleResponse get(@PathVariable long id) {
        return articleService.get(id);
    }

    @PostMapping
    @Operation(summary = "Create a draft article (requires article:write)")
    public ResponseEntity<ArticleResponse> create(@Valid @RequestBody ArticleRequest request) {
        ArticleResponse created = articleService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an article (author or article:manage)")
    public ArticleResponse update(@PathVariable long id, @Valid @RequestBody ArticleRequest request) {
        return articleService.update(id, request);
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish an article (author with article:publish, or article:manage)")
    public ArticleResponse publish(@PathVariable long id) {
        return articleService.publish(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an article (author or article:manage)")
    public void delete(@PathVariable long id) {
        articleService.delete(id);
    }
}
