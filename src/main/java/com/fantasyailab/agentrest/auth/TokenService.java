package com.fantasyailab.agentrest.auth;

import com.fantasyailab.agentrest.common.security.SecureTokens;
import com.fantasyailab.agentrest.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Issues short-lived JWT access tokens and long-lived opaque refresh tokens. */
@Service
public class TokenService {

    public static final String TOKEN_TYPE_CLAIM = "typ";
    public static final String ACCESS_TOKEN_TYPE = "access";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final RefreshTokenRepository refreshTokens;

    public TokenService(JwtEncoder jwtEncoder, JwtProperties properties, RefreshTokenRepository refreshTokens) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.refreshTokens = refreshTokens;
    }

    /** Must be called inside a transaction (persists the refresh token). */
    public AuthDtos.TokenResponse issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getUsername())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .claim("uid", user.getId())
                .claim(JwtConfig.AUTHORITIES_CLAIM, List.copyOf(user.authorities()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        String refreshToken = SecureTokens.randomToken();
        refreshTokens.save(new RefreshToken(user, SecureTokens.sha256Hex(refreshToken),
                now.plus(properties.refreshTokenTtl())));

        return new AuthDtos.TokenResponse("Bearer", accessToken, properties.accessTokenTtl().toSeconds(),
                refreshToken, properties.refreshTokenTtl().toSeconds());
    }
}
