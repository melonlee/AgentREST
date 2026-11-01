package com.fantasyailab.agentrest.auth.apikey;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    @EntityGraph(attributePaths = {"user", "user.roles", "user.roles.permissions"})
    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByUserUsernameOrderByCreatedAtDesc(String username);

    Optional<ApiKey> findByIdAndUserUsername(Long id, String username);

    @Modifying
    @Query("update ApiKey k set k.lastUsedAt = :now where k.id = :id")
    void touch(@Param("id") Long id, @Param("now") Instant now);
}
