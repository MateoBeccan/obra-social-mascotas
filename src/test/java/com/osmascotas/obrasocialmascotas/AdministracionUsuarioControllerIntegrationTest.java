package com.osmascotas.obrasocialmascotas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmascotas.obrasocialmascotas.auditoria.domain.OrigenOperacion;
import com.osmascotas.obrasocialmascotas.auditoria.domain.RegistroAuditoria;
import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.seguridad.domain.ActivacionCuentaToken;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.OperacionEstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.dto.CrearAdministradorRequest;
import com.osmascotas.obrasocialmascotas.seguridad.repository.ActivacionCuentaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.RecuperacionContrasenaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.ActivacionCuentaNotifier;
import com.osmascotas.obrasocialmascotas.seguridad.service.AdministracionUsuarioService;
import com.osmascotas.obrasocialmascotas.seguridad.service.JwtService;
import com.osmascotas.obrasocialmascotas.seguridad.service.ProvisionamientoCuentaService;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({
        TestcontainersConfiguration.class,
        AdministracionUsuarioControllerIntegrationTest.NotifierTestConfiguration.class,
        AdministracionUsuarioControllerIntegrationTest.ClockTestConfiguration.class
})
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.password-recovery.expiration=PT15M",
        "app.security.account-activation.expiration=PT24H"
})
class AdministracionUsuarioControllerIntegrationTest {

    private static final String CONTRASENA = "Password123!";
    private static final String ADMIN_IDENTIFICADOR = "admin@osmascotas.com";
    private static final String NUEVO_ADMIN_IDENTIFICADOR = "nuevo-admin@osmascotas.com";
    private static final String NUEVO_ADMIN_EMAIL = "nuevo-admin@test.local";
    private static final String NUEVA_CONTRASENA = "NuevaPassword123!";
    private static final Instant FECHA_HORA = Instant.parse("2026-09-07T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

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
    private JwtService jwtService;

    @Autowired
    private AdministracionUsuarioService administracionUsuarioService;

    @Autowired
    private ProvisionamientoCuentaService provisionamientoCuentaService;

    @Autowired
    private ActivacionCuentaNotifier activacionCuentaNotifier;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        reset(activacionCuentaNotifier);
        registroAuditoriaRepository.deleteAll();
        activacionCuentaTokenRepository.deleteAll();
        recuperacionContrasenaTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void listarUsuariosSinAutenticacionDevuelveUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"CLIENTE", "VETERINARIO"})
    void listarUsuariosConRolNoAdministradorDevuelveForbidden(RolUsuario rolUsuario) throws Exception {
        Usuario usuario = guardarUsuario(rolUsuario.name().toLowerCase() + "@osmascotas.com", rolUsuario, EstadoUsuario.ACTIVO);

        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarUsuariosConAdministradorDevuelveDatosSegurosOrdenados() throws Exception {
        Usuario cliente = guardarUsuario("cliente@osmascotas.com", RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);

        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(administrador.getId()))
                .andExpect(jsonPath("$[0].identificadorAcceso").value(ADMIN_IDENTIFICADOR))
                .andExpect(jsonPath("$[0].rolUsuario").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$[0].estadoUsuario").value("ACTIVO"))
                .andExpect(jsonPath("$[1].id").value(cliente.getId()))
                .andExpect(jsonPath("$[1].identificadorAcceso").value("cliente@osmascotas.com"))
                .andExpect(jsonPath("$[1].rolUsuario").value("CLIENTE"))
                .andExpect(jsonPath("$[1].estadoUsuario").value("ACTIVO"))
                .andExpect(jsonPath("$[0].contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].emailRecuperacion").doesNotExist())
                .andExpect(jsonPath("$[0].tokens").doesNotExist())
                .andExpect(cookie().doesNotExist("JSESSIONID"));

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void cambiarEstadoSinAutenticacionDevuelveUnauthorizedYNoModificaNiAudita() throws Exception {
        Usuario objetivo = guardarUsuario("cliente@osmascotas.com", RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);

        mockMvc.perform(patch("/api/admin/usuarios/{usuarioId}/estado", objetivo.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(OperacionEstadoUsuario.BLOQUEAR))))
                .andExpect(status().isUnauthorized());

        assertThat(buscarUsuario(objetivo.getId()).getEstadoUsuario()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"CLIENTE", "VETERINARIO"})
    void cambiarEstadoConRolNoAdministradorDevuelveForbiddenYNoModificaNiAudita(RolUsuario rolUsuario) throws Exception {
        Usuario autenticado = guardarUsuario(rolUsuario.name().toLowerCase() + "@osmascotas.com", rolUsuario, EstadoUsuario.ACTIVO);
        Usuario objetivo = guardarUsuario("objetivo@osmascotas.com", RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);

        mockMvc.perform(patch("/api/admin/usuarios/{usuarioId}/estado", objetivo.getId())
                        .header("Authorization", "Bearer " + tokenValido(autenticado))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(OperacionEstadoUsuario.BLOQUEAR))))
                .andExpect(status().isForbidden());

        assertThat(buscarUsuario(objetivo.getId()).getEstadoUsuario()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "INACTIVO, HABILITAR, ACTIVO, HABILITAR_USUARIO",
            "ACTIVO, BLOQUEAR, BLOQUEADO, BLOQUEAR_USUARIO",
            "BLOQUEADO, DESBLOQUEAR, ACTIVO, DESBLOQUEAR_USUARIO",
            "ACTIVO, INACTIVAR, INACTIVO, INACTIVAR_USUARIO",
            "BLOQUEADO, INACTIVAR, INACTIVO, INACTIVAR_USUARIO"
    })
    void cambiarEstadoConAdministradorAplicaTransicionYRegistraAuditoria(
            EstadoUsuario estadoInicial,
            OperacionEstadoUsuario operacion,
            EstadoUsuario estadoEsperado,
            String codigoAuditoria
    ) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Usuario objetivo = guardarUsuario("objetivo@osmascotas.com", RolUsuario.CLIENTE, estadoInicial);
        String hashOriginal = objetivo.getContrasenaHash();
        String emailOriginal = objetivo.getEmailRecuperacion();

        mockMvc.perform(patch("/api/admin/usuarios/{usuarioId}/estado", objetivo.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(operacion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(objetivo.getId()))
                .andExpect(jsonPath("$.identificadorAcceso").value("objetivo@osmascotas.com"))
                .andExpect(jsonPath("$.rolUsuario").value("CLIENTE"))
                .andExpect(jsonPath("$.estadoUsuario").value(estadoEsperado.name()))
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.emailRecuperacion").doesNotExist());

        Usuario usuarioActualizado = buscarUsuario(objetivo.getId());
        assertThat(usuarioActualizado.getEstadoUsuario()).isEqualTo(estadoEsperado);
        assertThat(usuarioActualizado.getRolUsuario()).isEqualTo(RolUsuario.CLIENTE);
        assertThat(usuarioActualizado.getIdentificadorAcceso()).isEqualTo("objetivo@osmascotas.com");
        assertThat(usuarioActualizado.getContrasenaHash()).isEqualTo(hashOriginal);
        assertThat(usuarioActualizado.getEmailRecuperacion()).isEqualTo(emailOriginal);

        List<RegistroAuditoria> registros = registroAuditoriaRepository.findAll();
        assertThat(registros).hasSize(1);
        RegistroAuditoria registro = registros.getFirst();
        assertThat(registro.getOrigenOperacion()).isEqualTo(OrigenOperacion.USUARIO);
        assertThat(registro.getUsuarioResponsable().getId()).isEqualTo(administrador.getId());
        assertThat(registro.getEntidadAfectada()).isEqualTo("USUARIO");
        assertThat(registro.getIdentificadorRegistroAfectado()).isEqualTo(objetivo.getId().toString());
        assertThat(registro.getEstadoAnterior()).isEqualTo(estadoInicial.name());
        assertThat(registro.getEstadoNuevo()).isEqualTo(estadoEsperado.name());
        assertThat(registro.getOperacion()).isEqualTo(codigoAuditoria);
        assertThat(registro.getDetalleCambio()).isNull();
        assertThat(registro.getMotivo()).isNull();
    }

    @Test
    void cambiarEstadoConTransicionInvalidaDevuelveConflictYNoModificaNiAudita() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Usuario objetivo = guardarUsuario("cliente@osmascotas.com", RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);

        mockMvc.perform(patch("/api/admin/usuarios/{usuarioId}/estado", objetivo.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(OperacionEstadoUsuario.HABILITAR))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Transicion de estado de Usuario no permitida"));

        assertThat(buscarUsuario(objetivo.getId()).getEstadoUsuario()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void cambiarEstadoConUsuarioInexistenteDevuelveNotFoundYNoAudita() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);

        mockMvc.perform(patch("/api/admin/usuarios/{usuarioId}/estado", 999_999L)
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(OperacionEstadoUsuario.BLOQUEAR))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Usuario no encontrado"));

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "ACTIVO, BLOQUEAR, Password123!, 401",
            "BLOQUEADO, DESBLOQUEAR, Password123!, 200",
            "ACTIVO, INACTIVAR, Password123!, 401",
            "INACTIVO, HABILITAR, Password123!, 200"
    })
    void loginPosteriorRespetaElEstadoActualizado(
            EstadoUsuario estadoInicial,
            OperacionEstadoUsuario operacion,
            String contrasena,
            int statusEsperado
    ) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Usuario objetivo = guardarUsuario("cliente@osmascotas.com", RolUsuario.CLIENTE, estadoInicial);

        mockMvc.perform(patch("/api/admin/usuarios/{usuarioId}/estado", objetivo.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(operacion))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest("cliente@osmascotas.com", contrasena))))
                .andExpect(status().is(statusEsperado))
                .andExpect(statusEsperado == 200
                        ? jsonPath("$.accessToken", not(nullValue()))
                        : jsonPath("$.mensaje").value("Credenciales invalidas"));
    }

    @Test
    void rollbackDeTransaccionExteriorRevierteCambioDeEstadoYAuditoria() {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Usuario objetivo = guardarUsuario("cliente@osmascotas.com", RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        autenticar(administrador);

        transactionTemplate.executeWithoutResult(status -> {
            administracionUsuarioService.cambiarEstado(objetivo.getId(), OperacionEstadoUsuario.BLOQUEAR);
            status.setRollbackOnly();
        });

        assertThat(buscarUsuario(objetivo.getId()).getEstadoUsuario()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void buscarPorIdParaActualizarUsaBloqueoPesimista() throws Exception {
        Method method = UsuarioRepository.class.getMethod("buscarPorIdParaActualizar", Long.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void crearAdministradorConAdministradorAutenticadoDevuelveCreatedYGeneraTokenActivacion() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);

        var result = mockMvc.perform(post("/api/admin/usuarios/administradores")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crearAdministradorRequest(
                                "  " + NUEVO_ADMIN_IDENTIFICADOR + "  ",
                                "  " + NUEVO_ADMIN_EMAIL + "  "
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(nullValue())))
                .andExpect(jsonPath("$.identificadorAcceso").value(NUEVO_ADMIN_IDENTIFICADOR))
                .andExpect(jsonPath("$.rolUsuario").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.estadoUsuario").value("INACTIVO"))
                .andExpect(jsonPath("$.emailRecuperacion").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andReturn();

        Usuario nuevoAdmin = usuarioRepository.buscarPorIdentificadorAcceso(NUEVO_ADMIN_IDENTIFICADOR).orElseThrow();
        List<ActivacionCuentaToken> tokens = activacionCuentaTokenRepository
                .findByUsuario_IdOrderByIdAsc(nuevoAdmin.getId());

        assertThat(nuevoAdmin.getRolUsuario()).isEqualTo(RolUsuario.ADMINISTRADOR);
        assertThat(nuevoAdmin.getEstadoUsuario()).isEqualTo(EstadoUsuario.INACTIVO);
        assertThat(nuevoAdmin.getIdentificadorAcceso()).isEqualTo(NUEVO_ADMIN_IDENTIFICADOR);
        assertThat(nuevoAdmin.getEmailRecuperacion()).isEqualTo(NUEVO_ADMIN_EMAIL);
        assertThat(nuevoAdmin.getContrasenaHash()).isNotEqualTo(CONTRASENA);
        assertThat(nuevoAdmin.getContrasenaHash()).startsWith("$2");
        assertThat(tokens).hasSize(1);

        ActivacionCuentaToken token = tokens.getFirst();
        assertThat(token.getTokenHash()).matches("[0-9a-f]{64}");
        assertThat(token.getFechaCreacion()).isEqualTo(FECHA_HORA);
        assertThat(token.getFechaExpiracion()).isEqualTo(FECHA_HORA.plus(Duration.ofHours(24)));
        assertThat(token.getFechaUso()).isNull();
        assertThat(token.getFechaInvalidacion()).isNull();

        ArgumentCaptor<String> tokenOriginalCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> fechaExpiracionCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(activacionCuentaNotifier).notificar(
                emailCaptor.capture(),
                tokenOriginalCaptor.capture(),
                fechaExpiracionCaptor.capture()
        );

        String tokenOriginal = tokenOriginalCaptor.getValue();
        assertThat(emailCaptor.getValue()).isEqualTo(NUEVO_ADMIN_EMAIL);
        assertThat(fechaExpiracionCaptor.getValue()).isEqualTo(token.getFechaExpiracion());
        assertThat(sha256Hex(tokenOriginal)).isEqualTo(token.getTokenHash());
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(tokenOriginal)
                .doesNotContain(token.getTokenHash())
                .doesNotContain(nuevoAdmin.getContrasenaHash());

        RegistroAuditoria registro = registroAuditoriaRepository.findAll().getFirst();
        assertThat(registroAuditoriaRepository.findAll()).hasSize(1);
        assertThat(registro.getOrigenOperacion()).isEqualTo(OrigenOperacion.USUARIO);
        assertThat(registro.getUsuarioResponsable().getId()).isEqualTo(administrador.getId());
        assertThat(registro.getOperacion()).isEqualTo("CREAR_CUENTA_ADMINISTRADOR");
        assertThat(registro.getEntidadAfectada()).isEqualTo("USUARIO");
        assertThat(registro.getIdentificadorRegistroAfectado()).isEqualTo(nuevoAdmin.getId().toString());
        assertThat(registro.getEstadoAnterior()).isNull();
        assertThat(registro.getEstadoNuevo()).isEqualTo("INACTIVO");
        assertThat(registro.getDetalleCambio()).isNull();
        assertThat(registro.getMotivo()).isNull();
    }

    @Test
    void crearAdministradorPermiteFlujoCompletoCreacionActivacionYLogin() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);

        mockMvc.perform(post("/api/admin/usuarios/administradores")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crearAdministradorRequest(
                                NUEVO_ADMIN_IDENTIFICADOR,
                                NUEVO_ADMIN_EMAIL
                        ))))
                .andExpect(status().isCreated());

        Usuario nuevoAdminInactivo = usuarioRepository.buscarPorIdentificadorAcceso(NUEVO_ADMIN_IDENTIFICADOR).orElseThrow();
        assertThat(nuevoAdminInactivo.getEstadoUsuario()).isEqualTo(EstadoUsuario.INACTIVO);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(NUEVO_ADMIN_IDENTIFICADOR, NUEVA_CONTRASENA))))
                .andExpect(status().isUnauthorized());

        ArgumentCaptor<String> tokenOriginalCaptor = ArgumentCaptor.forClass(String.class);
        verify(activacionCuentaNotifier).notificar(any(), tokenOriginalCaptor.capture(), any());

        mockMvc.perform(post("/api/auth/activate-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activarCuentaRequest(
                                tokenOriginalCaptor.getValue(),
                                NUEVA_CONTRASENA
                        ))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Usuario nuevoAdminActivo = usuarioRepository.buscarPorIdentificadorAcceso(NUEVO_ADMIN_IDENTIFICADOR).orElseThrow();
        assertThat(nuevoAdminActivo.getEstadoUsuario()).isEqualTo(EstadoUsuario.ACTIVO);

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(NUEVO_ADMIN_IDENTIFICADOR, NUEVA_CONTRASENA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(nullValue())))
                .andReturn();

        String accessToken = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
        assertThat(jwtService.extraerAuthorities(accessToken)).containsExactly("ROLE_ADMINISTRADOR");
        assertThat(registroAuditoriaRepository.findAll())
                .filteredOn(registro -> "CREAR_CUENTA_ADMINISTRADOR".equals(registro.getOperacion()))
                .hasSize(1);
        assertThat(registroAuditoriaRepository.findAll())
                .filteredOn(registro -> "ACTIVAR_CUENTA".equals(registro.getOperacion()))
                .hasSize(1);
    }

    @Test
    void crearAdministradorConIdentificadorDuplicadoDevuelveConflictYNoCreaDatos() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        guardarUsuario("Admin@Test.com", RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        long usuariosAntes = usuarioRepository.count();

        mockMvc.perform(post("/api/admin/usuarios/administradores")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crearAdministradorRequest(
                                " admin@test.com ",
                                NUEVO_ADMIN_EMAIL
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya existe una cuenta equivalente"));

        assertThat(usuarioRepository.count()).isEqualTo(usuariosAntes);
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        verifyNoInteractions(activacionCuentaNotifier);
    }

    @Test
    void crearAdministradorConEmailDuplicadoDevuelveConflictYNoCreaDatos() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        guardarUsuario("existente@osmascotas.com", RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO, "EMAIL@test.com");
        long usuariosAntes = usuarioRepository.count();

        mockMvc.perform(post("/api/admin/usuarios/administradores")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crearAdministradorRequest(
                                NUEVO_ADMIN_IDENTIFICADOR,
                                " email@test.com "
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya existe una cuenta equivalente"));

        assertThat(usuarioRepository.count()).isEqualTo(usuariosAntes);
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        verifyNoInteractions(activacionCuentaNotifier);
    }

    @Test
    void crearAdministradorConCampoNoPermitidoDevuelveBadRequestYNoCreaDatos() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);

        mockMvc.perform(post("/api/admin/usuarios/administradores")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identificadorAcceso": "nuevo-admin@osmascotas.com",
                                  "emailRecuperacion": "nuevo-admin@test.local",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(usuarioRepository.count()).isEqualTo(1);
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        verifyNoInteractions(activacionCuentaNotifier);
    }

    @Test
    void crearAdministradorSinJwtDevuelveUnauthorizedYNoCreaDatos() throws Exception {
        mockMvc.perform(post("/api/admin/usuarios/administradores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crearAdministradorRequest(
                                NUEVO_ADMIN_IDENTIFICADOR,
                                NUEVO_ADMIN_EMAIL
                        ))))
                .andExpect(status().isUnauthorized());

        assertNoProvisionoCuenta();
    }

    @ParameterizedTest
    @CsvSource({"CLIENTE", "VETERINARIO"})
    void crearAdministradorConRolNoAdministradorDevuelveForbiddenYNoCreaDatos(RolUsuario rolUsuario) throws Exception {
        Usuario usuario = guardarUsuario(rolUsuario.name().toLowerCase() + "@osmascotas.com", rolUsuario, EstadoUsuario.ACTIVO);

        mockMvc.perform(post("/api/admin/usuarios/administradores")
                        .header("Authorization", "Bearer " + tokenValido(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crearAdministradorRequest(
                                NUEVO_ADMIN_IDENTIFICADOR,
                                NUEVO_ADMIN_EMAIL
                        ))))
                .andExpect(status().isForbidden());

        assertThat(usuarioRepository.count()).isEqualTo(1);
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        verifyNoInteractions(activacionCuentaNotifier);
    }

    @Test
    void crearAdministradorDentroDeTransaccionExteriorConRollbackNoPersisteDatos() {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        autenticar(administrador);

        transactionTemplate.executeWithoutResult(status -> {
            provisionamientoCuentaService.crearAdministrador(new CrearAdministradorRequest(
                    NUEVO_ADMIN_IDENTIFICADOR,
                    NUEVO_ADMIN_EMAIL
            ));
            status.setRollbackOnly();
        });

        assertThat(usuarioRepository.buscarPorIdentificadorAcceso(NUEVO_ADMIN_IDENTIFICADOR)).isEmpty();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void crearAdministradorSiFallaNotifierHaceRollbackCompleto() {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        autenticar(administrador);
        doThrow(new IllegalStateException("Fallo notificacion"))
                .when(activacionCuentaNotifier)
                .notificar(any(), any(), any());

        assertThatThrownBy(() -> provisionamientoCuentaService.crearAdministrador(new CrearAdministradorRequest(
                NUEVO_ADMIN_IDENTIFICADOR,
                NUEVO_ADMIN_EMAIL
        ))).isInstanceOf(IllegalStateException.class);

        assertThat(usuarioRepository.buscarPorIdentificadorAcceso(NUEVO_ADMIN_IDENTIFICADOR)).isEmpty();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    private Usuario guardarUsuario(
            String identificadorAcceso,
            RolUsuario rolUsuario,
            EstadoUsuario estadoUsuario
    ) {
        return guardarUsuario(identificadorAcceso, rolUsuario, estadoUsuario, identificadorAcceso);
    }

    private Usuario guardarUsuario(
            String identificadorAcceso,
            RolUsuario rolUsuario,
            EstadoUsuario estadoUsuario,
            String emailRecuperacion
    ) {
        Usuario usuario = new Usuario(
                identificadorAcceso,
                passwordEncoder.encode(CONTRASENA),
                rolUsuario,
                estadoUsuario,
                emailRecuperacion
        );

        return usuarioRepository.saveAndFlush(usuario);
    }

    private void assertNoProvisionoCuenta() {
        assertThat(usuarioRepository.count()).isZero();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        verifyNoInteractions(activacionCuentaNotifier);
    }

    private Usuario buscarUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId).orElseThrow();
    }

    private String tokenValido(Usuario usuario) {
        return jwtService.generarAccessToken(UsernamePasswordAuthenticationToken.authenticated(
                usuario.getIdentificadorAcceso(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRolUsuario().name()))
        ));
    }

    private void autenticar(Usuario usuario) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                usuario.getIdentificadorAcceso(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRolUsuario().name()))
        ));
    }

    private Map<String, String> request(OperacionEstadoUsuario operacion) {
        return Map.of("operacion", operacion.name());
    }

    private Map<String, String> crearAdministradorRequest(String identificadorAcceso, String emailRecuperacion) {
        return Map.of(
                "identificadorAcceso", identificadorAcceso,
                "emailRecuperacion", emailRecuperacion
        );
    }

    private Map<String, String> activarCuentaRequest(String token, String nuevaContrasena) {
        return Map.of(
                "token", token,
                "nuevaContrasena", nuevaContrasena
        );
    }

    private Map<String, String> loginRequest(String identificadorAcceso, String contrasena) {
        return Map.of(
                "identificadorAcceso", identificadorAcceso,
                "contrasena", contrasena
        );
    }

    private String sha256Hex(String tokenOriginal) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(tokenOriginal.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @TestConfiguration
    static class NotifierTestConfiguration {

        @Bean
        @Primary
        ActivacionCuentaNotifier activacionCuentaNotifier() {
            return mock(ActivacionCuentaNotifier.class);
        }
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
