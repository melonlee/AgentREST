package com.lazy.rest.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

/** Request/response records of the user module. */
public final class UserDtos {

    private UserDtos() {
    }

    public static final String USERNAME_REGEX = "^[a-zA-Z0-9._-]{3,50}$";

    @Schema(description = "Create a user (admin)")
    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = USERNAME_REGEX, message = "3-50 chars: letters, digits, . _ -")
            @Schema(example = "carol") String username,
            @NotBlank @Email @Size(max = 255) @Schema(example = "carol@example.com") String email,
            @NotBlank @Size(min = 8, max = 72) @Schema(example = "carol-pass-123") String password,
            @Size(max = 100) @Schema(example = "Carol") String displayName,
            @Schema(example = "[\"ROLE_EDITOR\"]", description = "Defaults to ROLE_USER") Set<String> roles) {
    }

    @Schema(description = "Partial update of a user (admin). Null fields are left unchanged.")
    public record UpdateUserRequest(
            @Size(max = 100) String displayName,
            Boolean enabled,
            @Schema(example = "[\"ROLE_USER\",\"ROLE_EDITOR\"]") Set<String> roles) {
    }

    public record UserResponse(Long id, String username, String email, String displayName, boolean enabled,
            Set<String> roles, Instant createdAt) {

        public static UserResponse from(User user) {
            Set<String> roles = new java.util.TreeSet<>();
            user.getRoles().forEach(r -> roles.add(r.getName()));
            return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(),
                    user.isEnabled(), roles, user.getCreatedAt());
        }
    }
}
