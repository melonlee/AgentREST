package com.fantasyailab.agentrest.common.api;

import com.fantasyailab.agentrest.common.error.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryHelpersTest {

    @Test
    void likePatternEscapesWildcardsAndLowercases() {
        assertThat(LikePatterns.contains("  50%_Off! ")).isEqualTo("%50!%!_off!!%");
    }

    @Test
    void restrictSortAppliesFallbackWhenUnsorted() {
        Pageable result = Pageables.restrictSort(PageRequest.of(2, 10), Set.of("title"), Sort.by("id"));

        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getSort()).isEqualTo(Sort.by("id"));
    }

    @Test
    void restrictSortRejectsUnknownProperties() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("passwordHash"));

        assertThatThrownBy(() -> Pageables.restrictSort(pageable, Set.of("title", "id"), Sort.unsorted()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("passwordHash")
                .hasMessageContaining("id, title");
    }
}
