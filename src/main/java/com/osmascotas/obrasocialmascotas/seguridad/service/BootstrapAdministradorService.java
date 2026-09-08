package com.osmascotas.obrasocialmascotas.seguridad.service;

import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class BootstrapAdministradorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdministradorService.class);
    private static final String PROPIEDAD_ENABLED = "app.security.bootstrap-admin.enabled";
    private static final String PROPIEDAD_IDENTIFICADOR = "app.security.bootstrap-admin.identificador-acceso";
    private static final String PROPIEDAD_EMAIL = "app.security.bootstrap-admin.email-recuperacion";
    private static final String PROPIEDAD_PASSWORD = "app.security.bootstrap-admin.password";
    private static final String OPERACION_BOOTSTRAP_ADMINISTRADOR = "BOOTSTRAP_ADMINISTRADOR";
    private static final String ENTIDAD_USUARIO = "USUARIO";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final Environment environment;
    private final Validator validator;

    public BootstrapAdministradorService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService,
            Environment environment,
            Validator validator
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.environment = environment;
        this.validator = validator;
    }

    @Transactional
    public void inicializarSiCorresponde() {
        Boolean enabled = environment.getProperty(PROPIEDAD_ENABLED, Boolean.class, false);
        if (!Boolean.TRUE.equals(enabled)) {
            LOGGER.info("Bootstrap de Administrador deshabilitado.");
            return;
        }

        if (usuarioRepository.existsByRolUsuario(RolUsuario.ADMINISTRADOR)) {
            LOGGER.info("Bootstrap de Administrador omitido porque ya existe un Administrador.");
            return;
        }

        ConfiguracionBootstrap configuracion = obtenerConfiguracion();
        validarConfiguracion(configuracion);

        String identificadorNormalizado = configuracion.identificadorAcceso().trim();
        String emailNormalizado = configuracion.emailRecuperacion().trim();

        validarDuplicados(identificadorNormalizado, emailNormalizado);

        Usuario usuario = new Usuario(
                identificadorNormalizado,
                passwordEncoder.encode(configuracion.password()),
                RolUsuario.ADMINISTRADOR,
                EstadoUsuario.ACTIVO,
                emailNormalizado
        );

        Usuario usuarioGuardado = usuarioRepository.saveAndFlush(usuario);
        auditoriaService.registrarOperacionSistema(
                OPERACION_BOOTSTRAP_ADMINISTRADOR,
                ENTIDAD_USUARIO,
                usuarioGuardado.getId().toString(),
                null,
                EstadoUsuario.ACTIVO.name(),
                null,
                null
        );

        LOGGER.info("Administrador bootstrap creado correctamente.");
    }

    private ConfiguracionBootstrap obtenerConfiguracion() {
        return new ConfiguracionBootstrap(
                environment.getProperty(PROPIEDAD_IDENTIFICADOR),
                environment.getProperty(PROPIEDAD_EMAIL),
                environment.getProperty(PROPIEDAD_PASSWORD)
        );
    }

    private void validarConfiguracion(ConfiguracionBootstrap configuracion) {
        ConfiguracionBootstrap normalizadaParaValidar = new ConfiguracionBootstrap(
                trimONull(configuracion.identificadorAcceso()),
                trimONull(configuracion.emailRecuperacion()),
                trimONull(configuracion.password())
        );

        Set<ConstraintViolation<ConfiguracionBootstrap>> violations = validator.validate(normalizadaParaValidar);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Configuracion de Administrador bootstrap invalida.");
        }
    }

    private void validarDuplicados(String identificadorAcceso, String emailRecuperacion) {
        if (usuarioRepository.existeIdentificadorAcceso(identificadorAcceso)
                || usuarioRepository.existeEmailRecuperacion(emailRecuperacion)) {
            throw new IllegalStateException(
                    "No se pudo crear el Administrador bootstrap porque la cuenta configurada entra en conflicto con un Usuario existente"
            );
        }
    }

    private String trimONull(String valor) {
        return valor == null ? null : valor.trim();
    }

    private record ConfiguracionBootstrap(
            @NotBlank String identificadorAcceso,
            @NotBlank @Email String emailRecuperacion,
            @NotBlank String password
    ) {
    }
}
