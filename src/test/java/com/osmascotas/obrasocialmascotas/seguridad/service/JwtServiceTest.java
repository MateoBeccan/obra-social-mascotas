package com.osmascotas.obrasocialmascotas.seguridad.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String ISSUER = "obra-social-mascotas-test";
    private static final String SECRET = "01234567890123456789012345678901";
    private static final Instant AHORA = Instant.parse("2026-09-05T12:00:00Z");

    @Test
    void generaTokenValido() {
        JwtService jwtService = crearJwtService(Duration.ofMinutes(30), AHORA);

        String token = jwtService.generarAccessToken(authentication());

        assertThat(jwtService.esTokenValido(token)).isTrue();
    }

    @Test
    void extraeSubject() {
        JwtService jwtService = crearJwtService(Duration.ofMinutes(30), AHORA);

        String token = jwtService.generarAccessToken(authentication());

        assertThat(jwtService.extraerSubject(token)).isEqualTo("cliente@osmascotas.com");
    }

    @Test
    void extraeAuthorities() {
        JwtService jwtService = crearJwtService(Duration.ofMinutes(30), AHORA);

        String token = jwtService.generarAccessToken(authentication());

        assertThat(jwtService.extraerAuthorities(token)).containsExactly("ROLE_CLIENTE");
    }

    @Test
    void tokenExpiradoEsRechazado() {
        JwtService jwtService = crearJwtService(Duration.ofMinutes(30), AHORA);
        String token = jwtService.generarAccessToken(authentication());
        JwtService jwtServiceDespuesDeExpirar = crearJwtService(Duration.ofMinutes(30), AHORA.plus(Duration.ofMinutes(31)));

        assertThat(jwtServiceDespuesDeExpirar.esTokenValido(token)).isFalse();
    }

    @Test
    void tokenInvalidoEsRechazado() {
        JwtService jwtService = crearJwtService(Duration.ofMinutes(30), AHORA);

        assertThat(jwtService.esTokenValido("token-invalido")).isFalse();
    }

    private JwtService crearJwtService(Duration expiration, Instant instant) {
        return new JwtService(
                ISSUER,
                expiration,
                SECRET,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(
                "cliente@osmascotas.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))
        );
    }
}
