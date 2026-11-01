package com.fantasyailab.agentrest.auth.apikey;

import com.fantasyailab.agentrest.audit.Audited;
import com.fantasyailab.agentrest.common.error.BadRequestException;
import com.fantasyailab.agentrest.common.error.ResourceNotFoundException;
import com.fantasyailab.agentrest.common.security.SecureTokens;
import com.fantasyailab.agentrest.common.security.SecurityUtils;
import com.fantasyailab.agentrest.user.User;
import com.fantasyailab.agentrest.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ApiKeyService {

    /** Newly issued AgentREST API keys start with this prefix, which lets the bearer resolver tell them apart from JWTs. */
    public static final String KEY_PREFIX = "ar_";
    /** Prefix of keys issued before the rename (LazyREST, &lt;= 2.0.x); still accepted so existing keys keep working. */
    public static final String LEGACY_KEY_PREFIX = "lr_";
    static final int MAX_KEYS_PER_USER = 20;
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final ApiKeyRepository apiKeys;
    private final UserRepository users;

    public ApiKeyService(ApiKeyRepository apiKeys, UserRepository users) {
        this.apiKeys = apiKeys;
        this.users = users;
    }

    @Transactional
    @Audited(action = "API_KEY_CREATE", resourceType = "api-key", resourceId = "#result.id")
    public ApiKeyDtos.CreatedApiKey create(String name, boolean readOnly) {
        String username = SecurityUtils.requireUsername();
        User user = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User", username));
        long active = apiKeys.findByUserUsernameOrderByCreatedAtDesc(username).stream().filter(ApiKey::isActive).count();
        if (active >= MAX_KEYS_PER_USER) {
            throw new BadRequestException("You already have " + MAX_KEYS_PER_USER + " active API keys; revoke one first");
        }
        String rawKey = KEY_PREFIX + SecureTokens.randomToken();
        ApiKey key = store(user, name, rawKey, readOnly);
        return new ApiKeyDtos.CreatedApiKey(key.getId(), key.getName(), key.getKeyPrefix(), key.isReadOnly(),
                key.getCreatedAt(), rawKey);
    }

    /** Stores a key with a caller-supplied value (dev seeding). */
    @Transactional
    public ApiKey store(User user, String name, String rawKey, boolean readOnly) {
        if (!hasApiKeyPrefix(rawKey) || rawKey.length() < 24) {
            throw new IllegalArgumentException("API keys must start with '" + KEY_PREFIX + "' (or legacy '"
                    + LEGACY_KEY_PREFIX + "') and be >= 24 chars");
        }
        return apiKeys.save(new ApiKey(user, name, rawKey.substring(0, 10), SecureTokens.sha256Hex(rawKey), readOnly));
    }

    /** True for tokens that look like an API key (current {@code ar_} or legacy {@code lr_} prefix) rather than a JWT. */
    public static boolean hasApiKeyPrefix(String token) {
        return token != null && (token.startsWith(KEY_PREFIX) || token.startsWith(LEGACY_KEY_PREFIX));
    }

    @Transactional(readOnly = true)
    public List<ApiKeyDtos.ApiKeyResponse> listMine() {
        return apiKeys.findByUserUsernameOrderByCreatedAtDesc(SecurityUtils.requireUsername()).stream()
                .map(ApiKeyDtos.ApiKeyResponse::from)
                .toList();
    }

    @Transactional
    @Audited(action = "API_KEY_REVOKE", resourceType = "api-key", resourceId = "#id")
    public void revokeMine(long id) {
        ApiKey key = apiKeys.findByIdAndUserUsername(id, SecurityUtils.requireUsername())
                .orElseThrow(() -> new ResourceNotFoundException("API key", id));
        key.revoke();
    }

    /**
     * Resolves a raw API key to an authentication carrying the owner's authorities. A read-only
     * key keeps only {@code *:read} permissions, so it can never reach a write tool or endpoint.
     */
    @Transactional
    public ApiKeyAuthenticationToken authenticate(String rawKey) {
        ApiKey key = apiKeys.findByKeyHash(SecureTokens.sha256Hex(rawKey))
                .orElseThrow(() -> new BadCredentialsException("Invalid API key"));
        User user = key.getUser();
        if (!key.isActive() || !user.isEnabled()) {
            throw new BadCredentialsException("Invalid API key");
        }
        Instant now = Instant.now();
        if (key.getLastUsedAt() == null || key.getLastUsedAt().isBefore(now.minus(TOUCH_INTERVAL))) {
            apiKeys.touch(key.getId(), now);
        }
        List<SimpleGrantedAuthority> authorities = user.authorities().stream()
                .filter(a -> !key.isReadOnly() || a.endsWith(":read"))
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new ApiKeyAuthenticationToken(user.getUsername(), key.getId(), key.isReadOnly(), authorities);
    }
}
