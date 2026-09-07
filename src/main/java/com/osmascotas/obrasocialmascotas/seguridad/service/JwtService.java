package com.osmascotas.obrasocialmascotas.seguridad.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private static final String AUTHORITIES_CLAIM = "authorities";
    private static final String TOKEN_TYPE = "access";

    private final String issuer;
    private final Duration expiration;
    private final SecretKey secretKey;
    private final Clock clock;

    public JwtService(
            @Value("${app.security.jwt.issuer}") String issuer,
            @Value("${app.security.jwt.expiration}") Duration expiration,
            @Value("${app.security.jwt.secret}") String secret,
            Clock clock
    ) {
        this.issuer = issuer;
        this.expiration = expiration;
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String generarAccessToken(Authentication authentication) {
        Instant emitidoEn = clock.instant();
        Instant expiraEn = emitidoEn.plus(expiration);
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .issuer(issuer)
                .subject(authentication.getName())
                .issuedAt(Date.from(emitidoEn))
                .expiration(Date.from(expiraEn))
                .claim("typ", TOKEN_TYPE)
                .claim(AUTHORITIES_CLAIM, authorities)
                .signWith(secretKey)
                .compact();
    }

    public boolean esTokenValido(String token) {
        try {
            extraerClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public String extraerSubject(String token) {
        return extraerClaims(token).getSubject();
    }

    public List<String> extraerAuthorities(String token) {
        Object authorities = extraerClaims(token).get(AUTHORITIES_CLAIM);
        if (!(authorities instanceof List<?> authoritiesList)) {
            return List.of();
        }

        return authoritiesList.stream()
                .map(String::valueOf)
                .toList();
    }

    public long getExpiresIn() {
        return expiration.toSeconds();
    }

    private Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
