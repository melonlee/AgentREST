package com.lazy.rest.common.api;

import com.lazy.rest.common.error.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Guards list endpoints against sorting by arbitrary (possibly non-indexed or internal) properties. */
public final class Pageables {

    private Pageables() {
    }

    public static Pageable restrictSort(Pageable pageable, Set<String> allowed, Sort fallback) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new BadRequestException("Unsupported sort property '" + order.getProperty()
                        + "'. Allowed: " + String.join(", ", allowed.stream().sorted().toList()));
            }
        }
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : fallback;
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
