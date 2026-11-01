package com.fantasyailab.agentrest.article;

import com.fantasyailab.agentrest.article.ArticleDtos.ArticleRequest;
import com.fantasyailab.agentrest.article.ArticleDtos.ArticleResponse;
import com.fantasyailab.agentrest.article.ArticleDtos.ArticleSearch;
import com.fantasyailab.agentrest.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web slice: request mapping, binding, validation and ProblemDetail rendering (security is tested end-to-end elsewhere). */
@WebMvcTest(ArticleController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArticleControllerWebMvcTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ArticleService articleService;

    private static ArticleResponse response(long id) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new ArticleResponse(id, "Title", null, "Body", Article.Status.DRAFT, "alice", now, now, null, 0);
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        given(articleService.create(any(ArticleRequest.class))).willReturn(response(42));

        mvc.perform(post("/api/articles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Title\",\"content\":\"Body\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/articles/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void invalidBodyIsRejectedBeforeReachingTheService() throws Exception {
        mvc.perform(post("/api/articles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"summary\":\"" + "s".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.instance").value("/api/articles"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("title", "summary", "content")));
        verifyNoInteractions(articleService);
    }

    @Test
    void searchBindsFiltersAndPaging() throws Exception {
        Page<ArticleDtos.ArticleSummary> page = new PageImpl<>(List.of(), PageRequest.of(1, 5), 6);
        given(articleService.search(eq(new ArticleSearch("mcp", Article.Status.PUBLISHED, "editor", null)),
                argThat((Pageable p) -> p.getPageNumber() == 1 && p.getPageSize() == 5
                        && p.getSort().getOrderFor("title") != null))).willReturn(page);

        mvc.perform(get("/api/articles?q=mcp&status=PUBLISHED&author=editor&page=1&size=5&sort=title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void unknownSortPropertyIs400() throws Exception {
        mvc.perform(get("/api/articles?sort=content,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(articleService);
    }

    @Test
    void invalidEnumValueIs400() throws Exception {
        mvc.perform(get("/api/articles?status=ARCHIVED"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void notFoundAndForbiddenAreProblemDetails() throws Exception {
        given(articleService.get(7)).willThrow(new ResourceNotFoundException("Article", 7));
        given(articleService.update(anyLong(), any())).willThrow(new AccessDeniedException("nope"));

        mvc.perform(get("/api/articles/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Article 7 not found"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(put("/api/articles/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"content\":\"B\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unexpectedErrorsDoNotLeakDetails() throws Exception {
        given(articleService.get(9)).willThrow(new IllegalStateException("database password is hunter2"));

        mvc.perform(get("/api/articles/9"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hunter2"))));
    }
}
