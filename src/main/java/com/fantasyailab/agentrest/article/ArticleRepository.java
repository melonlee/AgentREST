package com.fantasyailab.agentrest.article;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {

    @Override
    @EntityGraph(attributePaths = "author")
    Page<Article> findAll(Specification<Article> spec, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Optional<Article> findWithAuthorById(Long id);
}
