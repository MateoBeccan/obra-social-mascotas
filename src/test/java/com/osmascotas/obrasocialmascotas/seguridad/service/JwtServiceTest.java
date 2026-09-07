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
    void loginClienteGeneraTokenConSubjectDelIdentificadorAcceso() {
        JwtService jwtService = crearJwtService(Duration.ofMinutes(30), AHORA);

        String token = jwtService.generarAccessToken(authentication("cliente@osmascotas.com", "ROLE_CLIENTE"));

        assertThat(jwtService.extraerSubject(token)).isEqualTo("cliente@osmascotas.com");
    }

    @Test
    void loginVeterinarioGeneraTokenConSubjectDelIdentificadorAcceso() {
        JwtService jwtService = crearJwtService(Duration.ofMinutes(30), AHORA);

        String token = jwtService.generarAccessToken(authentication("vet@osmascotas.com", "ROLE_VETERINARIO"));

        assertThat(jwtService.extraerSubject(token)).isEqualTo("vet@osmascotas.com");
    }

    @Test
    void loginAdministradorGeneraTokenConSubjectDelIdentificadorAcceso() {
        JwtService jwtService = crearJwtService(Duration.ofMinutes(30), AHORA);

        String token = jwtService.generarAccessToken(authentication("admin@osmascotas.com", "ROLE_ADMINISTRADOR"));

        assertThat(jwtService.extraerSubject(token)).isEqualTo("admin@osmascotas.com");
    }

    @Test
    void extraeAuthorities() {
        JwtService jwtService = crearJwtService(Duration.ofMinutes(30), AHORA);

        String token = jwtService.generarAccessToken(authentication());

        assertThat(jwtService.extraerAuthorities(token)).containsExactly("ROLE_CLIENTE");
    }

    @Test
    void tokenClienteConservaAuthorityRoleCliente() {
        JwtService jwtService = crearJwtService(Duration.ofMinutes(30), AHORA);

        String token = jwtService.generarAccessToken(authentication("cliente@osmascotas.com", "ROLE_CLIENTE"));

        assertThat(jwtService.extraerAuthorities(token)).containsExactly("ROLE_CLIENTE");
    }

    @Test
    void tokenVeterinarioConservaAuthorityRoleVeterinario() {
        JwtService jwtService = crearJwtService(Duration.ofMinutes(30), AHORA);

        String token = jwtService.generarAccessToken(authentication("vet@osmascotas.com", "ROLE_VETERINARIO"));

        assertThat(jwtService.extraerAuthorities(token)).containsExactly("ROLE_VETERINARIO");
    }

    @Test
    void tokenAdministradorConservaAuthorityRoleAdministrador() {
        JwtService jwtService = crearJwtService(Duration.ofMinutes(30), AHORA);

        String token = jwtService.generarAccessToken(authentication("admin@osmascotas.com", "ROLE_ADMINISTRADOR"));

        assertThat(jwtService.extraerAuthorities(token)).containsExactly("ROLE_ADMINISTRADOR");
    }

    @Test
    void tokenSoloConservaAuthoritiesDeRol() {
        JwtService jwtService = crearJwtService(Duration.ofMinutes(30), AHORA);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "cliente@osmascotas.com",
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_CLIENTE"),
                        new SimpleGrantedAuthority("FACTOR_PASSWORD")
                )
        );

        String token = jwtService.generarAccessToken(authentication);

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
        return authentication("cliente@osmascotas.com", "ROLE_CLIENTE");
    }

    private UsernamePasswordAuthenticationToken authentication(String identificadorAcceso, String authority) {
        return UsernamePasswordAuthenticationToken.authenticated(
                identificadorAcceso,
                null,
                List.of(new SimpleGrantedAuthority(authority))
        );
    }
}
