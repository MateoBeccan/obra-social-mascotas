package com.osmascotas.obrasocialmascotas.seguridad.service;

import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BootstrapAdministradorServiceTest {

    private static final String PROPIEDAD_ENABLED = "app.security.bootstrap-admin.enabled";

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final Environment environment = mock(Environment.class);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private final BootstrapAdministradorService service = new BootstrapAdministradorService(
            usuarioRepository,
            passwordEncoder,
            auditoriaService,
            environment,
            validator
    );

    @Test
    void bootstrapDeshabilitadoNoConsultaSecretosNiRepositorios() {
        when(environment.getProperty(PROPIEDAD_ENABLED, Boolean.class, false)).thenReturn(false);

        service.inicializarSiCorresponde();

        verify(environment).getProperty(PROPIEDAD_ENABLED, Boolean.class, false);
        verifyNoMoreInteractions(environment);
        verifyNoInteractions(usuarioRepository, passwordEncoder, auditoriaService);
    }

    @Test
    void bootstrapHabilitadoConAdministradorExistenteNoConsultaSecretos() {
        when(environment.getProperty(PROPIEDAD_ENABLED, Boolean.class, false)).thenReturn(true);
        when(usuarioRepository.existsByRolUsuario(RolUsuario.ADMINISTRADOR)).thenReturn(true);

        service.inicializarSiCorresponde();

        verify(environment).getProperty(PROPIEDAD_ENABLED, Boolean.class, false);
        verifyNoMoreInteractions(environment);
        verify(usuarioRepository).existsByRolUsuario(RolUsuario.ADMINISTRADOR);
        verifyNoMoreInteractions(usuarioRepository);
        verifyNoInteractions(passwordEncoder, auditoriaService);
    }
}
