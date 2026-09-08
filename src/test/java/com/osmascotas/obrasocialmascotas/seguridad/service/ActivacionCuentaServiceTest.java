package com.osmascotas.obrasocialmascotas.seguridad.service;

import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.seguridad.domain.ActivacionCuentaToken;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.ActivacionCuentaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivacionCuentaServiceTest {

    private static final Instant FECHA_HORA = Instant.parse("2026-09-07T12:00:00Z");
    private static final String TOKEN_ORIGINAL = "token-original-activacion";

    @Mock
    private ActivacionCuentaTokenRepository tokenRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditoriaService auditoriaService;

    @Test
    void activarCuentaBloqueaTokenYLuegoUsuarioParaActualizar() {
        Usuario usuarioAsociado = new Usuario(
                "cliente@osmascotas.com",
                "$2a$10$placeholder",
                RolUsuario.CLIENTE,
                EstadoUsuario.INACTIVO,
                "cliente@test.local"
        );
        Usuario usuarioBloqueado = new Usuario(
                "cliente@osmascotas.com",
                "$2a$10$placeholder",
                RolUsuario.CLIENTE,
                EstadoUsuario.INACTIVO,
                "cliente@test.local"
        );
        ActivacionCuentaToken token = new ActivacionCuentaToken(
                usuarioAsociado,
                sha256Hex(TOKEN_ORIGINAL),
                FECHA_HORA.minusSeconds(60),
                FECHA_HORA.plusSeconds(60)
        );
        asignarId(usuarioAsociado, 42L);
        asignarId(usuarioBloqueado, 42L);

        when(tokenRepository.buscarPorTokenHashParaActualizar(sha256Hex(TOKEN_ORIGINAL)))
                .thenReturn(Optional.of(token));
        when(usuarioRepository.buscarPorIdParaActualizar(42L))
                .thenReturn(Optional.of(usuarioBloqueado));
        when(passwordEncoder.encode("NuevaPassword123!"))
                .thenReturn("$2a$10$nueva");

        ActivacionCuentaService service = new ActivacionCuentaService(
                tokenRepository,
                usuarioRepository,
                passwordEncoder,
                Clock.fixed(FECHA_HORA, ZoneOffset.UTC),
                auditoriaService
        );

        service.activarCuenta(TOKEN_ORIGINAL, "NuevaPassword123!");

        InOrder orden = inOrder(tokenRepository, usuarioRepository);
        orden.verify(tokenRepository).buscarPorTokenHashParaActualizar(sha256Hex(TOKEN_ORIGINAL));
        orden.verify(usuarioRepository).buscarPorIdParaActualizar(42L);
    }

    private static void asignarId(Usuario usuario, Long id) {
        try {
            var field = Usuario.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(usuario, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String sha256Hex(String tokenOriginal) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(tokenOriginal.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
