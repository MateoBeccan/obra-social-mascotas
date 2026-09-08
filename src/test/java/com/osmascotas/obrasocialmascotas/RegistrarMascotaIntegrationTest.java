package com.osmascotas.obrasocialmascotas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmascotas.obrasocialmascotas.auditoria.domain.OrigenOperacion;
import com.osmascotas.obrasocialmascotas.auditoria.domain.RegistroAuditoria;
import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Mascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.CrearMascotaRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.ClienteRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.MascotaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.TitularidadMascotaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.RegistrarMascotaService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
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
        RegistrarMascotaIntegrationTest.AuditoriaFailureConfiguration.class,
        RegistrarMascotaIntegrationTest.ClockTestConfiguration.class
})
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.password-recovery.expiration=PT15M",
        "app.security.account-activation.expiration=PT24H"
})
class RegistrarMascotaIntegrationTest {

    private static final String CONTRASENA = "Password123!";
    private static final String ADMIN_IDENTIFICADOR = "admin-mascotas@osmascotas.com";
    private static final Instant FECHA_HORA = Instant.parse("2026-09-08T12:00:00Z");
    private static final LocalDate FECHA_REGISTRACION = LocalDate.of(2026, 9, 8);

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
    private RegistrarMascotaService registrarMascotaService;

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
    void registrarMascotaConAdministradorDevuelveCreatedCreaTitularidadInicialYAudita() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente("12345678");

        var result = mockMvc.perform(post("/api/admin/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mascotaRequest(
                                cliente.getId(),
                                " Luna ",
                                " Canino ",
                                " Mestiza ",
                                " Hembra ",
                                "2023-05-10"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(nullValue())))
                .andExpect(jsonPath("$.nombre").value("Luna"))
                .andExpect(jsonPath("$.especie").value("Canino"))
                .andExpect(jsonPath("$.raza").value("Mestiza"))
                .andExpect(jsonPath("$.sexo").value("Hembra"))
                .andExpect(jsonPath("$.fechaNacimiento").value("2023-05-10"))
                .andExpect(jsonPath("$.clienteId").value(cliente.getId()))
                .andExpect(jsonPath("$.titularidadMascotaId", not(nullValue())))
                .andExpect(jsonPath("$.fechaDesde").value("2026-09-08"))
                .andExpect(jsonPath("$.fotografiaObjetoKey").doesNotExist())
                .andExpect(jsonPath("$.usuarioResponsableId").doesNotExist())
                .andExpect(jsonPath("$.motivoCambio").doesNotExist())
                .andExpect(jsonPath("$.fechaHasta").doesNotExist())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        Long mascotaId = response.get("id").asLong();
        Long titularidadId = response.get("titularidadMascotaId").asLong();

        Mascota mascota = mascotaRepository.findById(mascotaId).orElseThrow();
        assertThat(mascota.getNombre()).isEqualTo("Luna");
        assertThat(mascota.getEspecie()).isEqualTo("Canino");
        assertThat(mascota.getRaza()).isEqualTo("Mestiza");
        assertThat(mascota.getSexo()).isEqualTo("Hembra");
        assertThat(mascota.getFechaNacimiento()).isEqualTo(LocalDate.of(2023, 5, 10));
        assertThat(mascota.getFotografiaObjetoKey()).isNull();

        List<TitularidadMascota> titularidades = titularidadMascotaRepository.findAll();
        assertThat(titularidades).hasSize(1);
        TitularidadMascota titularidad = titularidades.getFirst();
        assertThat(titularidad.getId()).isEqualTo(titularidadId);
        assertThat(titularidad.getMascota().getId()).isEqualTo(mascotaId);
        assertThat(titularidad.getCliente().getId()).isEqualTo(cliente.getId());
        assertThat(titularidad.getFechaDesde()).isEqualTo(FECHA_REGISTRACION);
        assertThat(titularidad.getFechaHasta()).isNull();
        assertThat(titularidad.getMotivoCambio()).isNull();
        assertThat(titularidad.getUsuarioResponsable()).isNull();

        List<RegistroAuditoria> registros = registroAuditoriaRepository.findAll();
        assertThat(registros).hasSize(1);
        RegistroAuditoria registro = registros.getFirst();
        assertThat(registro.getOrigenOperacion()).isEqualTo(OrigenOperacion.USUARIO);
        assertThat(registro.getUsuarioResponsable().getId()).isEqualTo(administrador.getId());
        assertThat(registro.getOperacion()).isEqualTo("REGISTRAR_MASCOTA");
        assertThat(registro.getEntidadAfectada()).isEqualTo("MASCOTA");
        assertThat(registro.getIdentificadorRegistroAfectado()).isEqualTo(mascotaId.toString());
        assertThat(registro.getEstadoAnterior()).isNull();
        assertThat(registro.getEstadoNuevo()).isNull();
        assertThat(registro.getDetalleCambio()).isNull();
        assertThat(registro.getMotivo()).isNull();

        assertThat(existeTabla("afiliacion")).isFalse();
        assertThat(existeTabla("solicitud_afiliacion")).isFalse();
    }

    @Test
    void registrarMascotaConClienteInexistenteDevuelveNotFoundYNoCreaDatos() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);

        mockMvc.perform(post("/api/admin/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mascotaRequestValido(999_999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe el Cliente indicado"));

        assertThat(mascotaRepository.count()).isZero();
        assertThat(titularidadMascotaRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"CLIENTE", "VETERINARIO"})
    void registrarMascotaConRolNoAdministradorDevuelveForbiddenYNoCreaDatos(RolUsuario rolUsuario) throws Exception {
        Usuario usuario = guardarUsuario(rolUsuario.name().toLowerCase() + "-mascotas@test.local", rolUsuario, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente("22345678");

        mockMvc.perform(post("/api/admin/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mascotaRequestValido(cliente.getId()))))
                .andExpect(status().isForbidden());

        assertThat(mascotaRepository.count()).isZero();
        assertThat(titularidadMascotaRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void registrarMascotaSinAutenticacionDevuelveUnauthorizedYNoCreaDatos() throws Exception {
        Cliente cliente = guardarCliente("32345678");

        mockMvc.perform(post("/api/admin/mascotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mascotaRequestValido(cliente.getId()))))
                .andExpect(status().isUnauthorized());

        assertThat(mascotaRepository.count()).isZero();
        assertThat(titularidadMascotaRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "clienteId, null",
            "clienteId, 0",
            "clienteId, -1",
            "nombre, ' '",
            "especie, ' '",
            "raza, ' '",
            "sexo, ' '"
    })
    void registrarMascotaConRequestInvalidoDevuelveBadRequest(String campo, String valor) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente("42345678");
        Map<String, Object> request = mascotaRequestValido(cliente.getId());
        request.put(campo, parseValor(valor));

        mockMvc.perform(post("/api/admin/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(mascotaRepository.count()).isZero();
        assertThat(titularidadMascotaRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "nombre, 121",
            "especie, 81",
            "raza, 121",
            "sexo, 31"
    })
    void registrarMascotaConCampoExcedidoDevuelveBadRequest(String campo, int largo) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente("52345678");
        Map<String, Object> request = mascotaRequestValido(cliente.getId());
        request.put(campo, "x".repeat(largo));

        mockMvc.perform(post("/api/admin/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(mascotaRepository.count()).isZero();
        assertThat(titularidadMascotaRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "usuarioId",
            "titularidadId",
            "titularActualId",
            "afiliacionId",
            "fotografiaObjetoKey",
            "motivoCambio",
            "fechaDesde",
            "fechaHasta"
    })
    void registrarMascotaConCampoJsonNoPermitidoDevuelveBadRequest(String campoNoPermitido) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente("62345678");
        Map<String, Object> request = mascotaRequestValido(cliente.getId());
        request.put(campoNoPermitido, "valor-no-permitido");

        mockMvc.perform(post("/api/admin/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(mascotaRepository.count()).isZero();
        assertThat(titularidadMascotaRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void registrarMascotaPermiteNombreRepetidoYCreaTitularidadParaCadaMascota() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente("72345678");

        mockMvc.perform(post("/api/admin/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mascotaRequestValido(cliente.getId()))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mascotaRequestValido(cliente.getId()))))
                .andExpect(status().isCreated());

        assertThat(mascotaRepository.count()).isEqualTo(2);
        assertThat(titularidadMascotaRepository.count()).isEqualTo(2);
        assertThat(mascotaRepository.findAll())
                .extracting(Mascota::getNombre)
                .containsExactly("Luna", "Luna");
    }

    @Test
    void siFallaAuditoriaRollbackRevierteMascotaYTitularidad() {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente("82345678");
        autenticar(administrador);
        AuditoriaFailureConfiguration.fallarAuditoria.set(true);

        assertThatThrownBy(() -> registrarMascotaService.registrar(new CrearMascotaRequest(
                cliente.getId(),
                "Luna",
                "Canino",
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Fallo auditoria");

        assertThat(mascotaRepository.count()).isZero();
        assertThat(titularidadMascotaRepository.count()).isZero();
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

    private Cliente guardarCliente(String dni) {
        return clienteRepository.saveAndFlush(new Cliente(dni, "Ana", "Gomez", null, null, null));
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

    private Map<String, Object> mascotaRequestValido(Long clienteId) {
        return mascotaRequest(clienteId, "Luna", "Canino", "Mestiza", "Hembra", "2023-05-10");
    }

    private Map<String, Object> mascotaRequest(
            Long clienteId,
            String nombre,
            String especie,
            String raza,
            String sexo,
            String fechaNacimiento
    ) {
        Map<String, Object> request = new HashMap<>();
        request.put("clienteId", clienteId);
        request.put("nombre", nombre);
        request.put("especie", especie);
        request.put("raza", raza);
        request.put("sexo", sexo);
        request.put("fechaNacimiento", fechaNacimiento);
        return request;
    }

    private Object parseValor(String valor) {
        if ("null".equals(valor)) {
            return null;
        }
        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException ex) {
            return valor;
        }
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
