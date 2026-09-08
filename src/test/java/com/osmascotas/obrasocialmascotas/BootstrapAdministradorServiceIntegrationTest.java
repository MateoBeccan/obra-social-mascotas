package com.osmascotas.obrasocialmascotas;

import com.osmascotas.obrasocialmascotas.auditoria.domain.OrigenOperacion;
import com.osmascotas.obrasocialmascotas.auditoria.domain.RegistroAuditoria;
import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.ActivacionCuentaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.RecuperacionContrasenaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.BootstrapAdministradorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.password-recovery.expiration=PT15M",
        "app.security.bootstrap-admin.enabled=false"
})
class BootstrapAdministradorServiceIntegrationTest {

    private static final String IDENTIFICADOR = "bootstrap-admin@osmascotas.com";
    private static final String EMAIL = "bootstrap-admin@test.local";
    private static final String PASSWORD = "BootstrapPassword123!";
    private static final AtomicInteger PROPERTY_SOURCE_SEQUENCE = new AtomicInteger();

    @Autowired
    private BootstrapAdministradorService bootstrapAdministradorService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ActivacionCuentaTokenRepository activacionCuentaTokenRepository;

    @Autowired
    private RecuperacionContrasenaTokenRepository recuperacionContrasenaTokenRepository;

    @Autowired
    private RegistroAuditoriaRepository registroAuditoriaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ConfigurableEnvironment environment;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        registroAuditoriaRepository.deleteAll();
        activacionCuentaTokenRepository.deleteAll();
        recuperacionContrasenaTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
        configurarBootstrap(false, "", "", "");
    }

    @Test
    void bootstrapDeshabilitadoNoCreaUsuarioAuditoriaNiToken() {
        bootstrapAdministradorService.inicializarSiCorresponde();

        assertThat(usuarioRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
    }

    @Test
    void bootstrapHabilitadoSinAdministradorCreaUnAdministradorActivoConAuditoriaSistema() {
        configurarBootstrap(true, "  " + IDENTIFICADOR + "  ", "  " + EMAIL + "  ", PASSWORD);

        bootstrapAdministradorService.inicializarSiCorresponde();

        Usuario usuario = usuarioRepository.buscarPorIdentificadorAcceso(IDENTIFICADOR).orElseThrow();
        assertThat(usuarioRepository.count()).isEqualTo(1);
        assertThat(usuario.getRolUsuario()).isEqualTo(RolUsuario.ADMINISTRADOR);
        assertThat(usuario.getEstadoUsuario()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(usuario.getIdentificadorAcceso()).isEqualTo(IDENTIFICADOR);
        assertThat(usuario.getEmailRecuperacion()).isEqualTo(EMAIL);
        assertThat(usuario.getContrasenaHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, usuario.getContrasenaHash())).isTrue();
        assertThat(activacionCuentaTokenRepository.findByUsuario_IdOrderByIdAsc(usuario.getId())).isEmpty();

        RegistroAuditoria auditoria = registroAuditoriaRepository.findAll().getFirst();
        assertThat(registroAuditoriaRepository.findAll()).hasSize(1);
        assertThat(auditoria.getOrigenOperacion()).isEqualTo(OrigenOperacion.SISTEMA);
        assertThat(auditoria.getUsuarioResponsable()).isNull();
        assertThat(auditoria.getOperacion()).isEqualTo("BOOTSTRAP_ADMINISTRADOR");
        assertThat(auditoria.getEntidadAfectada()).isEqualTo("USUARIO");
        assertThat(auditoria.getIdentificadorRegistroAfectado()).isEqualTo(usuario.getId().toString());
        assertThat(auditoria.getEstadoAnterior()).isNull();
        assertThat(auditoria.getEstadoNuevo()).isEqualTo("ACTIVO");
        assertThat(auditoria.getDetalleCambio()).isNull();
        assertThat(auditoria.getMotivo()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoUsuario.class, names = {"ACTIVO", "BLOQUEADO", "INACTIVO"})
    void bootstrapConAdministradorExistenteOmiteSinModificarloAunqueEsteNoActivo(EstadoUsuario estadoUsuario) {
        Usuario administrador = guardarUsuario(IDENTIFICADOR, PASSWORD, RolUsuario.ADMINISTRADOR, estadoUsuario, EMAIL);
        String hashOriginal = administrador.getContrasenaHash();
        configurarBootstrap(true, "otro-admin@osmascotas.com", "otro-admin@test.local", "OtraPassword123!");

        bootstrapAdministradorService.inicializarSiCorresponde();

        Usuario administradorLuego = usuarioRepository.findById(administrador.getId()).orElseThrow();
        assertThat(usuarioRepository.findAll())
                .filteredOn(usuario -> usuario.getRolUsuario() == RolUsuario.ADMINISTRADOR)
                .hasSize(1);
        assertThat(administradorLuego.getEstadoUsuario()).isEqualTo(estadoUsuario);
        assertThat(administradorLuego.getContrasenaHash()).isEqualTo(hashOriginal);
        assertThat(registroAuditoriaRepository.count()).isZero();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "CLIENTE, IDENTIFICADOR",
            "VETERINARIO, EMAIL"
    })
    void bootstrapConCuentaConfiguradaEnConflictoFallaSinModificarUsuarioExistente(
            RolUsuario rolUsuario,
            String tipoConflicto
    ) {
        Usuario existente = guardarUsuario("existente@osmascotas.com", PASSWORD, rolUsuario, EstadoUsuario.ACTIVO, "existente@test.local");
        String hashOriginal = existente.getContrasenaHash();
        String identificadorConfigurado = "IDENTIFICADOR".equals(tipoConflicto) ? " existente@osmascotas.com " : IDENTIFICADOR;
        String emailConfigurado = "EMAIL".equals(tipoConflicto) ? " existente@test.local " : EMAIL;
        configurarBootstrap(true, identificadorConfigurado, emailConfigurado, "OtraPassword123!");

        assertThatThrownBy(() -> bootstrapAdministradorService.inicializarSiCorresponde())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No se pudo crear el Administrador bootstrap porque la cuenta configurada entra en conflicto con un Usuario existente");

        Usuario usuarioLuego = usuarioRepository.findById(existente.getId()).orElseThrow();
        assertThat(usuarioLuego.getRolUsuario()).isEqualTo(rolUsuario);
        assertThat(usuarioLuego.getContrasenaHash()).isEqualTo(hashOriginal);
        assertThat(registroAuditoriaRepository.count()).isZero();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "'', bootstrap-admin@test.local, BootstrapPassword123!",
            "bootstrap-admin@osmascotas.com, '', BootstrapPassword123!",
            "bootstrap-admin@osmascotas.com, email-invalido, BootstrapPassword123!",
            "bootstrap-admin@osmascotas.com, bootstrap-admin@test.local, ''"
    })
    void bootstrapConConfiguracionInvalidaFallaSinPersistirDatos(
            String identificador,
            String email,
            String password
    ) {
        configurarBootstrap(true, identificador, email, password);

        assertThatThrownBy(() -> bootstrapAdministradorService.inicializarSiCorresponde())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Configuracion de Administrador bootstrap invalida.")
                .hasMessageNotContaining(PASSWORD);

        assertThat(usuarioRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
    }

    @Test
    void rollbackDeTransaccionExteriorRevierteUsuarioBootstrapYAuditoria() {
        configurarBootstrap(true, IDENTIFICADOR, EMAIL, PASSWORD);

        transactionTemplate.executeWithoutResult(status -> {
            bootstrapAdministradorService.inicializarSiCorresponde();
            status.setRollbackOnly();
        });

        assertThat(usuarioRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
    }

    private void configurarBootstrap(
            boolean enabled,
            String identificador,
            String email,
            String password
    ) {
        environment.getPropertySources().addFirst(new MapPropertySource(
                "bootstrap-admin-test-" + PROPERTY_SOURCE_SEQUENCE.incrementAndGet(),
                Map.of(
                        "app.security.bootstrap-admin.enabled", Boolean.toString(enabled),
                        "app.security.bootstrap-admin.identificador-acceso", identificador,
                        "app.security.bootstrap-admin.email-recuperacion", email,
                        "app.security.bootstrap-admin.password", password
                )
        ));
    }

    private Usuario guardarUsuario(
            String identificadorAcceso,
            String contrasena,
            RolUsuario rolUsuario,
            EstadoUsuario estadoUsuario,
            String emailRecuperacion
    ) {
        return usuarioRepository.saveAndFlush(new Usuario(
                identificadorAcceso,
                passwordEncoder.encode(contrasena),
                rolUsuario,
                estadoUsuario,
                emailRecuperacion
        ));
    }
}
