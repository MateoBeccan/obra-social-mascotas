package com.osmascotas.obrasocialmascotas.seguridad.service;

import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.seguridad.domain.ActivacionCuentaToken;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.OperacionEstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.TransicionEstadoUsuarioInvalidaException;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.ActivacionCuentaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

@Service
public class ActivacionCuentaService {

    private static final String SHA_256 = "SHA-256";
    private static final String OPERACION_ACTIVAR_CUENTA = "ACTIVAR_CUENTA";
    private static final String ENTIDAD_USUARIO = "USUARIO";

    private final ActivacionCuentaTokenRepository tokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AuditoriaService auditoriaService;

    public ActivacionCuentaService(
            ActivacionCuentaTokenRepository tokenRepository,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            AuditoriaService auditoriaService
    ) {
        this.tokenRepository = tokenRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public void activarCuenta(String tokenOriginal, String nuevaContrasena) {
        Objects.requireNonNull(tokenOriginal, "El token de activacion es obligatorio.");
        Objects.requireNonNull(nuevaContrasena, "La nueva contrasena es obligatoria.");

        String tokenHash = calcularSha256Hex(tokenOriginal);
        ActivacionCuentaToken token = tokenRepository.buscarPorTokenHashParaActualizar(tokenHash)
                .orElseThrow(TokenActivacionInvalidoException::new);

        Instant instanteActual = clock.instant();
        validarTokenVigente(token, instanteActual);

        Long usuarioId = token.getUsuario().getId();
        Usuario usuario = usuarioRepository.buscarPorIdParaActualizar(usuarioId)
                .orElseThrow(TokenActivacionInvalidoException::new);

        if (usuario.getEstadoUsuario() != EstadoUsuario.INACTIVO) {
            throw new TokenActivacionInvalidoException();
        }

        usuario.cambiarContrasenaHash(passwordEncoder.encode(nuevaContrasena));

        try {
            usuario.aplicarOperacionEstado(OperacionEstadoUsuario.HABILITAR);
        } catch (TransicionEstadoUsuarioInvalidaException ex) {
            throw new TokenActivacionInvalidoException();
        }

        token.marcarUsado(instanteActual);

        auditoriaService.registrarOperacionSistema(
                OPERACION_ACTIVAR_CUENTA,
                ENTIDAD_USUARIO,
                usuario.getId().toString(),
                EstadoUsuario.INACTIVO.name(),
                EstadoUsuario.ACTIVO.name(),
                null,
                null
        );
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

    private void validarTokenVigente(ActivacionCuentaToken token, Instant instanteActual) {
        if (token.getFechaUso() != null
                || token.getFechaInvalidacion() != null
                || !instanteActual.isBefore(token.getFechaExpiracion())) {
            throw new TokenActivacionInvalidoException();
        }
    }
}
