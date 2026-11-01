package com.lazy.rest.auth.apikey;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ApiKeyDtos {

    private ApiKeyDtos() {
    }

    public record CreateApiKeyRequest(
            @NotBlank @Size(max = 100) @Schema(example = "claude-code on my laptop") String name,
            @Schema(description = "Read-only keys only get *:read permissions (default true)", example = "true")
            Boolean readOnly) {
    }

    @Schema(description = "The plaintext `apiKey` is shown exactly once. Store it securely.")
    public record CreatedApiKey(Long id, String name, @Schema(example = "lr_AbCdEfG") String prefix, boolean readOnly,
            Instant createdAt, String apiKey) {
    }

    public record ApiKeyResponse(Long id, String name, String prefix, boolean readOnly, Instant createdAt,
            Instant lastUsedAt, Instant revokedAt) {

        static ApiKeyResponse from(ApiKey key) {
            return new ApiKeyResponse(key.getId(), key.getName(), key.getKeyPrefix(), key.isReadOnly(),
                    key.getCreatedAt(), key.getLastUsedAt(), key.getRevokedAt());
        }
    }
}
