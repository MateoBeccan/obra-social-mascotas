package com.osmascotas.obrasocialmascotas.seguridad.service;

import com.osmascotas.obrasocialmascotas.seguridad.domain.RecuperacionContrasenaToken;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.RecuperacionContrasenaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
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
public class RecuperacionContrasenaService {

    private static final int TOKEN_BYTES = 32;
    private static final String SHA_256 = "SHA-256";

    private final UsuarioRepository usuarioRepository;
    private final RecuperacionContrasenaTokenRepository tokenRepository;
    private final RecuperacionContrasenaNotifier notifier;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final Duration expiration;

    public RecuperacionContrasenaService(
            UsuarioRepository usuarioRepository,
            RecuperacionContrasenaTokenRepository tokenRepository,
            RecuperacionContrasenaNotifier notifier,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.security.password-recovery.expiration}") Duration expiration
    ) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.notifier = notifier;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.expiration = expiration;
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public void solicitarRecuperacion(String identificadorAcceso) {
        Objects.requireNonNull(identificadorAcceso, "El identificador de acceso es obligatorio.");

        usuarioRepository.buscarPorIdentificadorAcceso(identificadorAcceso)
                .ifPresent(this::crearTokenYNotificar);
    }

    @Transactional
    public void restablecerContrasena(String tokenOriginal, String nuevaContrasena) {
        Objects.requireNonNull(tokenOriginal, "El token de recuperacion es obligatorio.");
        Objects.requireNonNull(nuevaContrasena, "La nueva contrasena es obligatoria.");

        String tokenHash = calcularSha256Hex(tokenOriginal);
        RecuperacionContrasenaToken token = tokenRepository.buscarPorTokenHashParaActualizar(tokenHash)
                .orElseThrow(TokenRecuperacionInvalidoException::new);

        Instant instanteActual = clock.instant();
        validarTokenVigente(token, instanteActual);

        Usuario usuario = token.getUsuario();
        usuario.cambiarContrasenaHash(passwordEncoder.encode(nuevaContrasena));
        token.marcarUsado(instanteActual);
    }

    private void crearTokenYNotificar(Usuario usuario) {
        Instant fechaCreacion = clock.instant();
        Instant fechaExpiracion = fechaCreacion.plus(expiration);

        tokenRepository.invalidarTokensActivosDelUsuario(usuario.getId(), fechaCreacion);

        String tokenOriginal = generarTokenOriginal();
        String tokenHash = calcularSha256Hex(tokenOriginal);
        RecuperacionContrasenaToken token = new RecuperacionContrasenaToken(
                usuario,
                tokenHash,
                fechaCreacion,
                fechaExpiracion
        );

        tokenRepository.saveAndFlush(token);
        notifier.notificar(usuario.getEmailRecuperacion(), tokenOriginal, fechaExpiracion);
    }

    private String generarTokenOriginal() {
        byte[] bytes = new byte[TOKEN_BYTES];
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

    private void validarTokenVigente(RecuperacionContrasenaToken token, Instant instanteActual) {
        if (token.getFechaUso() != null
                || token.getFechaInvalidacion() != null
                || !instanteActual.isBefore(token.getFechaExpiracion())) {
            throw new TokenRecuperacionInvalidoException();
        }
    }
}
