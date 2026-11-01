package com.lazy.rest.auth.apikey;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/** Authentication produced by a valid API key. The principal is the key owner's username. */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final String username;
    private final Long apiKeyId;
    private final boolean readOnly;

    public ApiKeyAuthenticationToken(String username, Long apiKeyId, boolean readOnly,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.username = username;
        this.apiKeyId = apiKeyId;
        this.readOnly = readOnly;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    @Override
    public String getName() {
        return username;
    }

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public boolean isReadOnly() {
        return readOnly;
    }
}
