package com.osmascotas.obrasocialmascotas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmascotas.obrasocialmascotas.auditoria.domain.OrigenOperacion;
import com.osmascotas.obrasocialmascotas.auditoria.domain.RegistroAuditoria;
import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.CrearClienteRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.ClienteRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.MascotaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.TitularidadMascotaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.RegistrarClienteService;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.ActivacionCuentaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.RecuperacionContrasenaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({
        TestcontainersConfiguration.class,
        RegistrarClienteIntegrationTest.AuditoriaFailureConfiguration.class,
        RegistrarClienteIntegrationTest.ClockTestConfiguration.class
})
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.password-recovery.expiration=PT15M",
        "app.security.account-activation.expiration=PT24H"
})
class RegistrarClienteIntegrationTest {

    private static final String CONTRASENA = "Password123!";
    private static final String ADMIN_IDENTIFICADOR = "admin-clientes@osmascotas.com";
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
    private RegistroAuditoriaRepository registroAuditoriaRepository;

    @Autowired
    private RecuperacionContrasenaTokenRepository recuperacionContrasenaTokenRepository;

    @Autowired
    private ActivacionCuentaTokenRepository activacionCuentaTokenRepository;

    @Autowired
    private RegistrarClienteService registrarClienteService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        AuditoriaFailureConfiguration.fallarAuditoria.set(false);
        titularidadMascotaRepository.deleteAll();
        mascotaRepository.deleteAll();
        clienteRepository.deleteAll();
        registroAuditoriaRepository.deleteAll();
        activacionCuentaTokenRepository.deleteAll();
        recuperacionContrasenaTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void registrarClienteConAdministradorDevuelveCreatedPersisteClienteSinUsuarioYAudita() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        long usuariosAntes = usuarioRepository.count();
        long clientesAntes = clienteRepository.count();
        long mascotasAntes = mascotaRepository.count();
        long titularidadesAntes = titularidadMascotaRepository.count();

        var result = mockMvc.perform(post("/api/admin/clientes")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteRequest(
                                " 12345678 ",
                                " Ana ",
                                " Gomez ",
                                " ana@test.local ",
                                " 1111-2222 ",
                                " Calle 123 "
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(nullValue())))
                .andExpect(jsonPath("$.dni").value("12345678"))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.apellido").value("Gomez"))
                .andExpect(jsonPath("$.correoElectronico").value("ana@test.local"))
                .andExpect(jsonPath("$.telefono").value("1111-2222"))
                .andExpect(jsonPath("$.domicilio").value("Calle 123"))
                .andExpect(jsonPath("$.usuarioId").value(nullValue()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        Long clienteId = response.get("id").asLong();

        Cliente cliente = clienteRepository.findById(clienteId).orElseThrow();
        assertThat(clienteRepository.count()).isEqualTo(clientesAntes + 1);
        assertThat(cliente.getDni()).isEqualTo("12345678");
        assertThat(cliente.getNombre()).isEqualTo("Ana");
        assertThat(cliente.getApellido()).isEqualTo("Gomez");
        assertThat(cliente.getCorreoElectronico()).isEqualTo("ana@test.local");
        assertThat(cliente.getTelefono()).isEqualTo("1111-2222");
        assertThat(cliente.getDomicilio()).isEqualTo("Calle 123");
        assertThat(cliente.getUsuario()).isNull();

        assertThat(usuarioRepository.count()).isEqualTo(usuariosAntes);
        assertThat(mascotaRepository.count()).isEqualTo(mascotasAntes);
        assertThat(titularidadMascotaRepository.count()).isEqualTo(titularidadesAntes);
        assertThat(existeTabla("afiliacion")).isFalse();
        assertThat(existeTabla("solicitud_afiliacion")).isFalse();

        List<RegistroAuditoria> registros = registroAuditoriaRepository.findAll();
        assertThat(registros).hasSize(1);
        RegistroAuditoria registro = registros.getFirst();
        assertThat(registro.getOrigenOperacion()).isEqualTo(OrigenOperacion.USUARIO);
        assertThat(registro.getUsuarioResponsable().getId()).isEqualTo(administrador.getId());
        assertThat(registro.getOperacion()).isEqualTo("REGISTRAR_CLIENTE");
        assertThat(registro.getEntidadAfectada()).isEqualTo("CLIENTE");
        assertThat(registro.getIdentificadorRegistroAfectado()).isEqualTo(clienteId.toString());
        assertThat(registro.getEstadoAnterior()).isNull();
        assertThat(registro.getEstadoNuevo()).isNull();
        assertThat(registro.getDetalleCambio()).isNull();
        assertThat(registro.getMotivo()).isNull();
        assertThat(registro.toString())
                .doesNotContain("12345678")
                .doesNotContain("Ana")
                .doesNotContain("Gomez")
                .doesNotContain("ana@test.local")
                .doesNotContain("1111-2222")
                .doesNotContain("Calle 123");
    }

    @ParameterizedTest
    @CsvSource({"CLIENTE", "VETERINARIO"})
    void registrarClienteConRolNoAdministradorDevuelveForbiddenYNoCreaDatos(RolUsuario rolUsuario) throws Exception {
        Usuario usuario = guardarUsuario(rolUsuario.name().toLowerCase() + "-clientes@test.local", rolUsuario, EstadoUsuario.ACTIVO);

        mockMvc.perform(post("/api/admin/clientes")
                        .header("Authorization", "Bearer " + tokenValido(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteRequestValido())))
                .andExpect(status().isForbidden());

        assertThat(clienteRepository.count()).isZero();
        assertThat(mascotaRepository.count()).isZero();
        assertThat(titularidadMascotaRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void registrarClienteSinAutenticacionDevuelveUnauthorizedYNoCreaDatos() throws Exception {
        mockMvc.perform(post("/api/admin/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteRequestValido())))
                .andExpect(status().isUnauthorized());

        assertThat(clienteRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void registrarClienteConDniDuplicadoNormalizadoDevuelveConflictYNoAuditaRechazo() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);

        mockMvc.perform(post("/api/admin/clientes")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteRequestValido())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/clientes")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteRequest(
                                " 12345678 ",
                                "Otra",
                                "Persona",
                                null,
                                null,
                                null
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya existe un Cliente con el mismo DNI"));

        assertThat(clienteRepository.count()).isEqualTo(1);
        assertThat(registroAuditoriaRepository.findAll())
                .filteredOn(registro -> "REGISTRAR_CLIENTE".equals(registro.getOperacion()))
                .hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({
            "dni, ' ', 400",
            "nombre, ' ', 400",
            "apellido, ' ', 400",
            "correoElectronico, correo-invalido, 400",
            "correoElectronico, ' ', 400",
            "telefono, ' ', 400",
            "domicilio, ' ', 400"
    })
    void registrarClienteConRequestInvalidoDevuelveBadRequest(String campo, String valor, int statusEsperado) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Map<String, String> request = clienteRequestValido();
        request.put(campo, valor);

        mockMvc.perform(post("/api/admin/clientes")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(statusEsperado));

        assertThat(clienteRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void registrarClienteConCampoExcedidoDevuelveBadRequest() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Map<String, String> request = clienteRequestValido();
        request.put("dni", "1".repeat(51));

        mockMvc.perform(post("/api/admin/clientes")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(clienteRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"usuarioId", "usuario", "rol", "estado", "password", "mascotaId", "afiliacionId"})
    void registrarClienteConCampoJsonNoPermitidoDevuelveBadRequest(String campoNoPermitido) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);

        mockMvc.perform(post("/api/admin/clientes")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dni": "12345678",
                                  "nombre": "Ana",
                                  "apellido": "Gomez",
                                  "%s": "valor-no-permitido"
                                }
                                """.formatted(campoNoPermitido)))
                .andExpect(status().isBadRequest());

        assertThat(clienteRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void siFallaAuditoriaRollbackRevierteCliente() {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        autenticar(administrador);
        AuditoriaFailureConfiguration.fallarAuditoria.set(true);

        assertThatThrownBy(() -> registrarClienteService.registrar(new CrearClienteRequest(
                "12345678",
                "Ana",
                "Gomez",
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Fallo auditoria");

        assertThat(clienteRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    private Usuario guardarUsuario(
            String identificadorAcceso,
            RolUsuario rolUsuario,
            EstadoUsuario estadoUsuario
    ) {
        Usuario usuario = new Usuario(
                identificadorAcceso,
                passwordEncoder.encode(CONTRASENA),
                rolUsuario,
                estadoUsuario,
                identificadorAcceso
        );

        return usuarioRepository.saveAndFlush(usuario);
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

    private Map<String, String> clienteRequestValido() {
        return clienteRequest("12345678", "Ana", "Gomez", "ana@test.local", "1111-2222", "Calle 123");
    }

    private Map<String, String> clienteRequest(
            String dni,
            String nombre,
            String apellido,
            String correoElectronico,
            String telefono,
            String domicilio
    ) {
        Map<String, String> request = new java.util.HashMap<>();
        request.put("dni", dni);
        request.put("nombre", nombre);
        request.put("apellido", apellido);
        request.put("correoElectronico", correoElectronico);
        request.put("telefono", telefono);
        request.put("domicilio", domicilio);
        return request;
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
