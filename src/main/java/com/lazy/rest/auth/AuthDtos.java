package com.lazy.rest.auth;

import com.lazy.rest.user.UserDtos;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Request/response records of the auth module. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 50) @Schema(example = "admin") String username,
            @NotBlank @Size(max = 72) @Schema(example = "admin123") String password) {

        @Override
        public String toString() {
            return "LoginRequest[username=" + username + "]";
        }
    }

    public record RefreshRequest(@NotBlank @Size(max = 128) @Schema(description = "Refresh token from login") String refreshToken) {

        @Override
        public String toString() {
            return "RefreshRequest[***]";
        }
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = UserDtos.USERNAME_REGEX, message = "3-50 chars: letters, digits, . _ -")
            @Schema(example = "dave") String username,
            @NotBlank @Email @Size(max = 255) @Schema(example = "dave@example.com") String email,
            @NotBlank @Size(min = 8, max = 72) @Schema(example = "dave-pass-123") String password,
            @Size(max = 100) @Schema(example = "Dave") String displayName) {

        @Override
        public String toString() {
            return "RegisterRequest[username=" + username + "]";
        }
    }

    @Schema(description = "Token pair. Send the access token as `Authorization: Bearer <accessToken>`.")
    public record TokenResponse(
            @Schema(example = "Bearer") String tokenType,
            String accessToken,
            @Schema(example = "900", description = "Access token lifetime in seconds") long expiresIn,
            String refreshToken,
            @Schema(example = "1209600", description = "Refresh token lifetime in seconds") long refreshExpiresIn) {
    }

    public record MeResponse(Long id, String username, String email, String displayName, Set<String> roles,
            Set<String> permissions, @Schema(example = "jwt", description = "jwt or api-key") String authMethod) {
    }
}
