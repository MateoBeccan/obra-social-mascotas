package com.osmascotas.obrasocialmascotas.seguridad.service;

import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioUserDetailsServiceTest {

    private static final String IDENTIFICADOR_ACCESO = "cliente@osmascotas.com";
    private static final String CONTRASENA_HASH = "$2a$10$hash";
    private static final String EMAIL_RECUPERACION = "cliente@test.local";

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UsuarioUserDetailsService usuarioUserDetailsService;

    @Test
    void usuarioActivoEstaHabilitadoYNoBloqueado() {
        UserDetails userDetails = cargarUsuario(RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);

        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
    }

    @Test
    void usuarioBloqueadoEstaHabilitadoYBloqueado() {
        UserDetails userDetails = cargarUsuario(RolUsuario.CLIENTE, EstadoUsuario.BLOQUEADO);

        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isFalse();
    }

    @Test
    void usuarioInactivoEstaDeshabilitadoYNoBloqueado() {
        UserDetails userDetails = cargarUsuario(RolUsuario.CLIENTE, EstadoUsuario.INACTIVO);

        assertThat(userDetails.isEnabled()).isFalse();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
    }

    @Test
    void usuarioClienteTieneAuthorityRoleCliente() {
        UserDetails userDetails = cargarUsuario(RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);

        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CLIENTE");
    }

    @Test
    void usuarioVeterinarioTieneAuthorityRoleVeterinario() {
        UserDetails userDetails = cargarUsuario(RolUsuario.VETERINARIO, EstadoUsuario.ACTIVO);

        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_VETERINARIO");
    }

    @Test
    void usuarioAdministradorTieneAuthorityRoleAdministrador() {
        UserDetails userDetails = cargarUsuario(RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);

        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMINISTRADOR");
    }

    @Test
    void usuarioInexistenteLanzaUsernameNotFoundException() {
        when(usuarioRepository.buscarPorIdentificadorAcceso(IDENTIFICADOR_ACCESO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioUserDetailsService.loadUserByUsername(IDENTIFICADOR_ACCESO))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private UserDetails cargarUsuario(RolUsuario rolUsuario, EstadoUsuario estadoUsuario) {
        Usuario usuario = new Usuario(
                IDENTIFICADOR_ACCESO,
                CONTRASENA_HASH,
                rolUsuario,
                estadoUsuario,
                EMAIL_RECUPERACION
        );

        when(usuarioRepository.buscarPorIdentificadorAcceso(IDENTIFICADOR_ACCESO))
                .thenReturn(Optional.of(usuario));

        return usuarioUserDetailsService.loadUserByUsername(IDENTIFICADOR_ACCESO);
    }
}
