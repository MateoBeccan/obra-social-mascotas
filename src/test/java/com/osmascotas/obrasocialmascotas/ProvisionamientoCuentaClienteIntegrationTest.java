package com.osmascotas.obrasocialmascotas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmascotas.obrasocialmascotas.auditoria.domain.OrigenOperacion;
import com.osmascotas.obrasocialmascotas.auditoria.domain.RegistroAuditoria;
import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.ClienteRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.MascotaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.TitularidadMascotaRepository;
import com.osmascotas.obrasocialmascotas.seguridad.domain.ActivacionCuentaToken;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.dto.CrearCuentaClienteRequest;
import com.osmascotas.obrasocialmascotas.seguridad.repository.ActivacionCuentaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.RecuperacionContrasenaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.ActivacionCuentaNotifier;
import com.osmascotas.obrasocialmascotas.seguridad.service.JwtService;
import com.osmascotas.obrasocialmascotas.seguridad.service.ProvisionamientoCuentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({
        TestcontainersConfiguration.class,
        ProvisionamientoCuentaClienteIntegrationTest.AuditoriaFailureConfiguration.class,
        ProvisionamientoCuentaClienteIntegrationTest.ClockTestConfiguration.class
})
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.password-recovery.expiration=PT15M",
        "app.security.account-activation.expiration=PT24H"
})
class ProvisionamientoCuentaClienteIntegrationTest {

    private static final String CONTRASENA = "Password123!";
    private static final String ADMIN_IDENTIFICADOR = "admin-cuenta-cliente@osmascotas.com";
    private static final String CLIENTE_IDENTIFICADOR = "cliente-cuenta@osmascotas.com";
    private static final String CLIENTE_EMAIL = "cliente-cuenta@test.local";
    private static final String NUEVA_CONTRASENA = "NuevaPassword123!";
    private static final Instant FECHA_HORA = Instant.parse("2026-09-08T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Autowired
    private TitularidadMascotaRepository titularidadMascotaRepository;

    @Autowired
    private ActivacionCuentaTokenRepository activacionCuentaTokenRepository;

    @Autowired
    private RecuperacionContrasenaTokenRepository recuperacionContrasenaTokenRepository;

    @Autowired
    private RegistroAuditoriaRepository registroAuditoriaRepository;

    @Autowired
    private ProvisionamientoCuentaService provisionamientoCuentaService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ActivacionCuentaNotifier activacionCuentaNotifier;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        AuditoriaFailureConfiguration.fallarAuditoria.set(false);
        reset(activacionCuentaNotifier);
        titularidadMascotaRepository.deleteAll();
        mascotaRepository.deleteAll();
        clienteRepository.deleteAll();
        registroAuditoriaRepository.deleteAll();
        activacionCuentaTokenRepository.deleteAll();
        recuperacionContrasenaTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void crearCuentaClienteConAdministradorDevuelveCreatedAsociaUsuarioTokenAuditaYNoTocaDominio() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente("12345678", "contacto-cliente@test.local");
        long usuariosAntes = usuarioRepository.count();
        long mascotasAntes = mascotaRepository.count();
        long titularidadesAntes = titularidadMascotaRepository.count();

        var result = mockMvc.perform(post("/api/admin/usuarios/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuentaClienteRequest(
                                "  " + CLIENTE_IDENTIFICADOR + "  ",
                                "  " + CLIENTE_EMAIL + "  "
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(nullValue())))
                .andExpect(jsonPath("$.identificadorAcceso").value(CLIENTE_IDENTIFICADOR))
                .andExpect(jsonPath("$.rolUsuario").value("CLIENTE"))
                .andExpect(jsonPath("$.estadoUsuario").value("INACTIVO"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andExpect(jsonPath("$.cliente").doesNotExist())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andReturn();

        Long usuarioClienteId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        Usuario usuarioCliente = usuarioRepository.findById(usuarioClienteId).orElseThrow();
        Cliente clienteActualizado = clienteRepository.findById(cliente.getId()).orElseThrow();

        assertThat(usuarioRepository.count()).isEqualTo(usuariosAntes + 1);
        assertThat(usuarioCliente.getRolUsuario()).isEqualTo(RolUsuario.CLIENTE);
        assertThat(usuarioCliente.getEstadoUsuario()).isEqualTo(EstadoUsuario.INACTIVO);
        assertThat(usuarioCliente.getIdentificadorAcceso()).isEqualTo(CLIENTE_IDENTIFICADOR);
        assertThat(usuarioCliente.getEmailRecuperacion()).isEqualTo(CLIENTE_EMAIL);
        assertThat(usuarioCliente.getContrasenaHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(CONTRASENA, usuarioCliente.getContrasenaHash())).isFalse();
        assertThat(clienteActualizado.getUsuario().getId()).isEqualTo(usuarioClienteId);
        assertThat(clienteActualizado.getCorreoElectronico()).isEqualTo("contacto-cliente@test.local");

        List<ActivacionCuentaToken> tokens = activacionCuentaTokenRepository.findByUsuario_IdOrderByIdAsc(usuarioClienteId);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().getTokenHash()).matches("[0-9a-f]{64}");
        assertThat(tokens.getFirst().getFechaCreacion()).isEqualTo(FECHA_HORA);
        assertThat(tokens.getFirst().getFechaExpiracion()).isEqualTo(FECHA_HORA.plus(Duration.ofHours(24)));
        assertThat(tokens.getFirst().getFechaUso()).isNull();
        assertThat(tokens.getFirst().getFechaInvalidacion()).isNull();

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> expiracionCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(activacionCuentaNotifier).notificar(emailCaptor.capture(), tokenCaptor.capture(), expiracionCaptor.capture());
        assertThat(emailCaptor.getValue()).isEqualTo(CLIENTE_EMAIL);
        assertThat(expiracionCaptor.getValue()).isEqualTo(tokens.getFirst().getFechaExpiracion());
        assertThat(sha256Hex(tokenCaptor.getValue())).isEqualTo(tokens.getFirst().getTokenHash());

        RegistroAuditoria auditoria = registroAuditoriaRepository.findAll().getFirst();
        assertThat(registroAuditoriaRepository.findAll()).hasSize(1);
        assertThat(auditoria.getOrigenOperacion()).isEqualTo(OrigenOperacion.USUARIO);
        assertThat(auditoria.getUsuarioResponsable().getId()).isEqualTo(administrador.getId());
        assertThat(auditoria.getOperacion()).isEqualTo("CREAR_CUENTA_CLIENTE");
        assertThat(auditoria.getEntidadAfectada()).isEqualTo("USUARIO");
        assertThat(auditoria.getIdentificadorRegistroAfectado()).isEqualTo(usuarioClienteId.toString());
        assertThat(auditoria.getEstadoAnterior()).isNull();
        assertThat(auditoria.getEstadoNuevo()).isEqualTo("INACTIVO");
        assertThat(auditoria.getDetalleCambio()).isNull();
        assertThat(auditoria.getMotivo()).isNull();

        assertThat(mascotaRepository.count()).isEqualTo(mascotasAntes);
        assertThat(titularidadMascotaRepository.count()).isEqualTo(titularidadesAntes);
        assertThat(existeTabla("afiliacion")).isFalse();
    }

    @Test
    void cuentaClienteProvisionadaPuedeActivarseYLoguearComoCliente() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente("22345678", null);

        mockMvc.perform(post("/api/admin/usuarios/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuentaClienteRequest(CLIENTE_IDENTIFICADOR, CLIENTE_EMAIL))))
                .andExpect(status().isCreated());

        Usuario usuarioCliente = usuarioRepository.buscarPorIdentificadorAcceso(CLIENTE_IDENTIFICADOR).orElseThrow();
        ActivacionCuentaToken token = activacionCuentaTokenRepository
                .findByUsuario_IdOrderByIdAsc(usuarioCliente.getId())
                .getFirst();
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(activacionCuentaNotifier).notificar(any(), tokenCaptor.capture(), any());
        String tokenOriginal = tokenCaptor.getValue();

        assertThat(token.getTokenHash()).isEqualTo(sha256Hex(tokenOriginal));
        assertThat(token.getTokenHash()).isNotEqualTo(tokenOriginal);

        mockMvc.perform(post("/api/auth/activate-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", tokenOriginal,
                                "nuevaContrasena", NUEVA_CONTRASENA
                        ))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        var login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identificadorAcceso", CLIENTE_IDENTIFICADOR,
                                "contrasena", NUEVA_CONTRASENA
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(nullValue())))
                .andReturn();

        String accessToken = objectMapper
                .readTree(login.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
        assertThat(jwtService.extraerAuthorities(accessToken)).containsExactly("ROLE_CLIENTE");
        assertThat(usuarioRepository.findById(usuarioCliente.getId()).orElseThrow().getEstadoUsuario())
                .isEqualTo(EstadoUsuario.ACTIVO);
    }

    @Test
    void clienteYaTieneCuentaDevuelveConflictYNoCreaSegundoUsuarioTokenNiAuditoria() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente("32345678", null);

        mockMvc.perform(post("/api/admin/usuarios/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuentaClienteRequest(CLIENTE_IDENTIFICADOR, CLIENTE_EMAIL))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/usuarios/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuentaClienteRequest(
                                "otro-cliente@test.local",
                                "otro-cliente@test.local"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("El Cliente ya posee una cuenta asociada"));

        Cliente clienteActualizado = clienteRepository.findById(cliente.getId()).orElseThrow();
        assertThat(clienteActualizado.getUsuario()).isNotNull();
        assertThat(usuarioRepository.count()).isEqualTo(2);
        assertThat(activacionCuentaTokenRepository.count()).isEqualTo(1);
        assertThat(registroAuditoriaRepository.findAll())
                .filteredOn(registro -> "CREAR_CUENTA_CLIENTE".equals(registro.getOperacion()))
                .hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({
            "identificador",
            "email"
    })
    void cuentaDuplicadaDevuelveConflictYNoAsociaClienteNiCreaTokenAuditoria(String tipoDuplicado) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        guardarUsuario("existente@test.local", RolUsuario.CLIENTE, EstadoUsuario.ACTIVO, "existente-email@test.local");
        Cliente cliente = guardarCliente("42345678", null);

        String identificador = "identificador".equals(tipoDuplicado) ? " existente@test.local " : CLIENTE_IDENTIFICADOR;
        String email = "email".equals(tipoDuplicado) ? " existente-email@test.local " : CLIENTE_EMAIL;

        mockMvc.perform(post("/api/admin/usuarios/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuentaClienteRequest(identificador, email))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya existe una cuenta equivalente"));

        assertThat(clienteRepository.findById(cliente.getId()).orElseThrow().getUsuario()).isNull();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        verifyNoInteractions(activacionCuentaNotifier);
    }

    @Test
    void clienteInexistenteDevuelveNotFoundYNoCreaDatos() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        long usuariosAntes = usuarioRepository.count();

        mockMvc.perform(post("/api/admin/usuarios/clientes/{clienteId}", 999_999L)
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuentaClienteRequest(CLIENTE_IDENTIFICADOR, CLIENTE_EMAIL))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe el Cliente indicado"));

        assertThat(usuarioRepository.count()).isEqualTo(usuariosAntes);
        assertThat(clienteRepository.count()).isZero();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        verifyNoInteractions(activacionCuentaNotifier);
    }

    @ParameterizedTest
    @CsvSource({"CLIENTE", "VETERINARIO"})
    void rolNoAdministradorDevuelveForbiddenYNoCreaDatos(RolUsuario rolUsuario) throws Exception {
        Usuario usuario = guardarUsuario(rolUsuario.name().toLowerCase() + "-provision@test.local", rolUsuario, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente("52345678", null);

        mockMvc.perform(post("/api/admin/usuarios/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuentaClienteRequest(CLIENTE_IDENTIFICADOR, CLIENTE_EMAIL))))
                .andExpect(status().isForbidden());

        assertThat(clienteRepository.findById(cliente.getId()).orElseThrow().getUsuario()).isNull();
        assertThat(usuarioRepository.count()).isEqualTo(1);
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        verifyNoInteractions(activacionCuentaNotifier);
    }

    @Test
    void sinJwtDevuelveUnauthorizedYNoCreaDatos() throws Exception {
        Cliente cliente = guardarCliente("62345678", null);

        mockMvc.perform(post("/api/admin/usuarios/clientes/{clienteId}", cliente.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuentaClienteRequest(CLIENTE_IDENTIFICADOR, CLIENTE_EMAIL))))
                .andExpect(status().isUnauthorized());

        assertThat(clienteRepository.findById(cliente.getId()).orElseThrow().getUsuario()).isNull();
        assertThat(usuarioRepository.count()).isZero();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        verifyNoInteractions(activacionCuentaNotifier);
    }

    @ParameterizedTest
    @CsvSource({"password", "rol", "estado", "clienteId", "usuarioId", "campoDesconocido"})
    void requestConCampoNoPermitidoDevuelveBadRequest(String campoNoPermitido) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente("72345678", null);
        Map<String, Object> request = new HashMap<>(cuentaClienteRequest(CLIENTE_IDENTIFICADOR, CLIENTE_EMAIL));
        request.put(campoNoPermitido, "valor-no-permitido");

        mockMvc.perform(post("/api/admin/usuarios/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(clienteRepository.findById(cliente.getId()).orElseThrow().getUsuario()).isNull();
        assertThat(usuarioRepository.count()).isEqualTo(1);
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        verifyNoInteractions(activacionCuentaNotifier);
    }

    @Test
    void siFallaAuditoriaRollbackRevierteUsuarioTokenYAsociacionCliente() {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente("82345678", "contacto-original@test.local");
        autenticar(administrador);
        AuditoriaFailureConfiguration.fallarAuditoria.set(true);

        assertThatThrownBy(() -> provisionamientoCuentaService.crearCliente(
                cliente.getId(),
                new CrearCuentaClienteRequest(CLIENTE_IDENTIFICADOR, CLIENTE_EMAIL)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Fallo auditoria");

        Cliente clienteLuego = clienteRepository.findById(cliente.getId()).orElseThrow();
        assertThat(usuarioRepository.buscarPorIdentificadorAcceso(CLIENTE_IDENTIFICADOR)).isEmpty();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(clienteLuego.getUsuario()).isNull();
        assertThat(clienteLuego.getCorreoElectronico()).isEqualTo("contacto-original@test.local");
        assertThat(registroAuditoriaRepository.count()).isZero();
        verifyNoInteractions(activacionCuentaNotifier);
    }

    @Test
    void buscarPorIdParaProvisionarUsaBloqueoPesimista() throws Exception {
        var method = ClienteRepository.class.getMethod("buscarPorIdParaProvisionar", Long.class);
        var springLock = method.getAnnotation(org.springframework.data.jpa.repository.Lock.class);
        assertThat(springLock).isNotNull();
        assertThat(springLock.value()).isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
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

    private Cliente guardarCliente(String dni, String correoElectronico) {
        return clienteRepository.saveAndFlush(new Cliente(
                dni,
                "Ana",
                "Gomez",
                correoElectronico,
                "1111-2222",
                "Calle 123"
        ));
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

    private Map<String, String> cuentaClienteRequest(String identificadorAcceso, String emailRecuperacion) {
        return Map.of(
                "identificadorAcceso", identificadorAcceso,
                "emailRecuperacion", emailRecuperacion
        );
    }

    private boolean existeTabla(String tabla) {
        Integer cantidad = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = ?
                """, Integer.class, tabla);

        return cantidad != null && cantidad > 0;
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
    static class AuditoriaFailureConfiguration {

        private static final AtomicBoolean fallarAuditoria = new AtomicBoolean(false);

        @Bean
        @Primary
        AuditoriaService auditoriaService(
                RegistroAuditoriaRepository registroAuditoriaRepository,
                UsuarioRepository usuarioRepository,
                Clock clock
        ) {
            return new AuditoriaService(registroAuditoriaRepository, usuarioRepository, clock) {
                @Override
                public void registrarOperacionUsuario(
                        String operacion,
                        String entidadAfectada,
                        String identificadorRegistroAfectado,
                        String estadoAnterior,
                        String estadoNuevo,
                        String detalleCambio,
                        String motivo
                ) {
                    if (fallarAuditoria.get()) {
                        throw new IllegalStateException("Fallo auditoria");
                    }
                    super.registrarOperacionUsuario(
                            operacion,
                            entidadAfectada,
                            identificadorRegistroAfectado,
                            estadoAnterior,
                            estadoNuevo,
                            detalleCambio,
                            motivo
                    );
                }
            };
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
