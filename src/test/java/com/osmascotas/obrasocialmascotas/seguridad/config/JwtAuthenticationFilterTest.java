package com.osmascotas.obrasocialmascotas.seguridad.config;

import com.osmascotas.obrasocialmascotas.seguridad.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "01234567890123456789012345678901";
    private static final Instant AHORA = Instant.parse("2026-09-06T12:00:00Z");

    @AfterEach
    void limpiarSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bearerValidoReconstruyeAuthenticationConIdentificadorYAuthority() throws Exception {
        JwtService jwtService = jwtService();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
        String token = jwtService.generarAccessToken(UsernamePasswordAuthenticationToken.authenticated(
                "vet@osmascotas.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_VETERINARIO"))
        ));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("vet@osmascotas.com");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_VETERINARIO");
    }

    @Test
    void bearerInvalidoNoCreaAuthentication() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader("Authorization", "Bearer token-invalido");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void securityConfigHabilitaMethodSecurity() {
        assertThat(SecurityConfig.class.getAnnotation(EnableMethodSecurity.class)).isNotNull();
    }

    private JwtService jwtService() {
        return new JwtService(
                "obra-social-mascotas-test",
                Duration.ofMinutes(30),
                SECRET,
                Clock.fixed(AHORA, ZoneOffset.UTC)
        );
    }
}
