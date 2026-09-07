package com.osmascotas.obrasocialmascotas;

import com.osmascotas.obrasocialmascotas.auditoria.domain.OrigenOperacion;
import com.osmascotas.obrasocialmascotas.auditoria.domain.RegistroAuditoria;
import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.UsuarioAutenticadoNoEncontradoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({TestcontainersConfiguration.class, AuditoriaServiceIntegrationTest.ClockTestConfiguration.class})
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.password-recovery.expiration=PT15M"
})
class AuditoriaServiceIntegrationTest {

    private static final Instant FECHA_HORA = Instant.parse("2026-09-07T12:00:00Z");
    private static final String IDENTIFICADOR_ACCESO = "admin@osmascotas.com";

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private RegistroAuditoriaRepository registroAuditoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        registroAuditoriaRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registrarOperacionUsuarioDentroDeTransaccionPersisteRegistroConUsuarioYFechaDelClock() {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO);
        autenticar(IDENTIFICADOR_ACCESO, RolUsuario.ADMINISTRADOR);

        transactionTemplate.executeWithoutResult(status -> auditoriaService.registrarOperacionUsuario(
                "CAMBIO_ESTADO",
                "Usuario",
                String.valueOf(usuario.getId()),
                "ACTIVO",
                "BLOQUEADO",
                "Cambio administrativo",
                "Solicitud interna"
        ));

        RegistroAuditoria registro = registroAuditoriaRepository.findAll().getFirst();
        assertThat(registro.getOrigenOperacion()).isEqualTo(OrigenOperacion.USUARIO);
        assertThat(registro.getUsuarioResponsable().getId()).isEqualTo(usuario.getId());
        assertThat(registro.getFechaHora()).isEqualTo(FECHA_HORA);
        assertThat(registro.getOperacion()).isEqualTo("CAMBIO_ESTADO");
        assertThat(registro.getEntidadAfectada()).isEqualTo("Usuario");
        assertThat(registro.getIdentificadorRegistroAfectado()).isEqualTo(String.valueOf(usuario.getId()));
        assertThat(registro.getEstadoAnterior()).isEqualTo("ACTIVO");
        assertThat(registro.getEstadoNuevo()).isEqualTo("BLOQUEADO");
        assertThat(registro.getDetalleCambio()).isEqualTo("Cambio administrativo");
        assertThat(registro.getMotivo()).isEqualTo("Solicitud interna");
    }

    @Test
    void registrarOperacionSistemaPersisteRegistroSinUsuarioYNoDependeDeAuthentication() {
        autenticar("usuario-ignorado@osmascotas.com", RolUsuario.CLIENTE);

        transactionTemplate.executeWithoutResult(status -> auditoriaService.registrarOperacionSistema(
                "JOB_NOCTURNO",
                "Afiliacion",
                "AF-123",
                null,
                null,
                null,
                null
        ));

        RegistroAuditoria registro = registroAuditoriaRepository.findAll().getFirst();
        assertThat(registro.getOrigenOperacion()).isEqualTo(OrigenOperacion.SISTEMA);
        assertThat(registro.getUsuarioResponsable()).isNull();
        assertThat(registro.getFechaHora()).isEqualTo(FECHA_HORA);
        assertThat(registro.getOperacion()).isEqualTo("JOB_NOCTURNO");
        assertThat(registro.getEntidadAfectada()).isEqualTo("Afiliacion");
        assertThat(registro.getIdentificadorRegistroAfectado()).isEqualTo("AF-123");
    }

    @Test
    void registrarOperacionUsuarioSinAuthenticationFallaYNoInsertaAuditoria() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                auditoriaService.registrarOperacionUsuario(
                        "CAMBIO_ESTADO",
                        "Usuario",
                        "123",
                        null,
                        null,
                        null,
                        null
                )))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void registrarOperacionUsuarioConUsuarioInexistenteFallaYNoInsertaAuditoria() {
        autenticar("inexistente@osmascotas.com", RolUsuario.ADMINISTRADOR);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                auditoriaService.registrarOperacionUsuario(
                        "CAMBIO_ESTADO",
                        "Usuario",
                        "123",
                        null,
                        null,
                        null,
                        null
                )))
                .isInstanceOf(UsuarioAutenticadoNoEncontradoException.class);

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void registrarOperacionFueraDeTransaccionFallaPorPropagationMandatory() {
        guardarUsuario(IDENTIFICADOR_ACCESO);
        autenticar(IDENTIFICADOR_ACCESO, RolUsuario.ADMINISTRADOR);

        assertThatThrownBy(() -> auditoriaService.registrarOperacionUsuario(
                "CAMBIO_ESTADO",
                "Usuario",
                "123",
                null,
                null,
                null,
                null
        )).isInstanceOf(IllegalTransactionStateException.class);

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void rollbackDeTransaccionExteriorTambienRevierteRegistroAuditoria() {
        guardarUsuario(IDENTIFICADOR_ACCESO);
        autenticar(IDENTIFICADOR_ACCESO, RolUsuario.ADMINISTRADOR);

        transactionTemplate.executeWithoutResult(status -> {
            auditoriaService.registrarOperacionUsuario(
                    "CAMBIO_ESTADO",
                    "Usuario",
                    "123",
                    null,
                    null,
                    null,
                    null
            );
            status.setRollbackOnly();
        });

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    private Usuario guardarUsuario(String identificadorAcceso) {
        Usuario usuario = new Usuario(
                identificadorAcceso,
                "$2a$10$hash",
                RolUsuario.ADMINISTRADOR,
                EstadoUsuario.ACTIVO,
                "admin@test.local"
        );

        return usuarioRepository.save(usuario);
    }

    private void autenticar(String identificadorAcceso, RolUsuario rolUsuario) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                identificadorAcceso,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rolUsuario.name()))
        ));
    }

    @TestConfiguration
    static class ClockTestConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FECHA_HORA, ZoneOffset.UTC);
        }
    }
}
