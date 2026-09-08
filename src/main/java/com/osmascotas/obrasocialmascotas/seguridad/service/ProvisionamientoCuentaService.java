package com.osmascotas.obrasocialmascotas.seguridad.service;

import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.seguridad.domain.ActivacionCuentaToken;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.dto.CrearAdministradorRequest;
import com.osmascotas.obrasocialmascotas.seguridad.dto.UsuarioAdministracionResponse;
import com.osmascotas.obrasocialmascotas.seguridad.repository.ActivacionCuentaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

@Service
public class ProvisionamientoCuentaService {

    private static final int TOKEN_BYTES = 32;
    private static final int PLACEHOLDER_BYTES = 32;
    private static final String SHA_256 = "SHA-256";
    private static final String OPERACION_CREAR_CUENTA_ADMINISTRADOR = "CREAR_CUENTA_ADMINISTRADOR";
    private static final String ENTIDAD_USUARIO = "USUARIO";

    private final UsuarioRepository usuarioRepository;
    private final ActivacionCuentaTokenRepository activacionCuentaTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration expiration;
    private final AuditoriaService auditoriaService;
    private final ActivacionCuentaNotifier activacionCuentaNotifier;
    private final SecureRandom secureRandom;

    public ProvisionamientoCuentaService(
            UsuarioRepository usuarioRepository,
            ActivacionCuentaTokenRepository activacionCuentaTokenRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.security.account-activation.expiration}") Duration expiration,
            AuditoriaService auditoriaService,
            ActivacionCuentaNotifier activacionCuentaNotifier
    ) {
        this.usuarioRepository = usuarioRepository;
        this.activacionCuentaTokenRepository = activacionCuentaTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.expiration = expiration;
        this.auditoriaService = auditoriaService;
        this.activacionCuentaNotifier = activacionCuentaNotifier;
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public UsuarioAdministracionResponse crearAdministrador(CrearAdministradorRequest request) {
        Objects.requireNonNull(request, "La solicitud de creacion de administrador es obligatoria.");

        String identificadorAcceso = request.identificadorAcceso().trim();
        String emailRecuperacion = request.emailRecuperacion().trim();

        validarDuplicados(identificadorAcceso, emailRecuperacion);

        Usuario usuario = new Usuario(
                identificadorAcceso,
                passwordEncoder.encode(generarSecretoAleatorio(PLACEHOLDER_BYTES)),
                RolUsuario.ADMINISTRADOR,
                EstadoUsuario.INACTIVO,
                emailRecuperacion
        );

        Usuario usuarioGuardado = guardarUsuario(usuario);
        Instant fechaCreacion = clock.instant();
        Instant fechaExpiracion = fechaCreacion.plus(expiration);
        String tokenOriginal = generarSecretoAleatorio(TOKEN_BYTES);
        String tokenHash = calcularSha256Hex(tokenOriginal);
        ActivacionCuentaToken token = new ActivacionCuentaToken(
                usuarioGuardado,
                tokenHash,
                fechaCreacion,
                fechaExpiracion
        );

        activacionCuentaTokenRepository.saveAndFlush(token);

        auditoriaService.registrarOperacionUsuario(
                OPERACION_CREAR_CUENTA_ADMINISTRADOR,
                ENTIDAD_USUARIO,
                usuarioGuardado.getId().toString(),
                null,
                EstadoUsuario.INACTIVO.name(),
                null,
                null
        );

        activacionCuentaNotifier.notificar(emailRecuperacion, tokenOriginal, fechaExpiracion);

        return toResponse(usuarioGuardado);
    }

    private void validarDuplicados(String identificadorAcceso, String emailRecuperacion) {
        if (usuarioRepository.existeIdentificadorAcceso(identificadorAcceso)
                || usuarioRepository.existeEmailRecuperacion(emailRecuperacion)) {
            throw new CuentaUsuarioDuplicadaException();
        }
    }

    private Usuario guardarUsuario(Usuario usuario) {
        try {
            return usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException ex) {
            throw new CuentaUsuarioDuplicadaException();
        }
    }

    private String generarSecretoAleatorio(int cantidadBytes) {
        byte[] bytes = new byte[cantidadBytes];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String calcularSha256Hex(String tokenOriginal) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(tokenOriginal.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no esta disponible.", ex);
        }
    }

    private UsuarioAdministracionResponse toResponse(Usuario usuario) {
        return new UsuarioAdministracionResponse(
                usuario.getId(),
                usuario.getIdentificadorAcceso(),
                usuario.getRolUsuario(),
                usuario.getEstadoUsuario()
        );
    }
}
