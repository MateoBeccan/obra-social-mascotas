package com.osmascotas.obrasocialmascotas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Mascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.ClienteRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.MascotaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.TitularidadMascotaRepository;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.ActivacionCuentaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.RecuperacionContrasenaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.password-recovery.expiration=PT15M",
        "app.security.account-activation.expiration=PT24H"
})
class ConsultarMascotasPropiasIntegrationTest {

    private static final String CONTRASENA = "Password123!";

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        titularidadMascotaRepository.deleteAll();
        mascotaRepository.deleteAll();
        clienteRepository.deleteAll();
        registroAuditoriaRepository.deleteAll();
        activacionCuentaTokenRepository.deleteAll();
        recuperacionContrasenaTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void consultarMascotasPropiasConUnaMascotaActualDevuelveDatosDeTitularidadVigente() throws Exception {
        Usuario usuario = guardarUsuario("cliente-una-mascota@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "10101010", "Ana");
        Mascota mascota = guardarMascota("Luna", "PERRO", "Labrador", "HEMBRA", LocalDate.of(2022, 4, 15));
        TitularidadMascota titularidad = guardarTitularidad(mascota, cliente, LocalDate.of(2026, 9, 1), null);

        String response = mockMvc.perform(get("/api/clientes/me/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(jsonPath("$[0].id").value(mascota.getId()))
                .andExpect(jsonPath("$[0].nombre").value("Luna"))
                .andExpect(jsonPath("$[0].especie").value("PERRO"))
                .andExpect(jsonPath("$[0].raza").value("Labrador"))
                .andExpect(jsonPath("$[0].sexo").value("HEMBRA"))
                .andExpect(jsonPath("$[0].fechaNacimiento").value("2022-04-15"))
                .andExpect(jsonPath("$[0].clienteId").value(cliente.getId()))
                .andExpect(jsonPath("$[0].titularidadMascotaId").value(titularidad.getId()))
                .andExpect(jsonPath("$[0].fechaDesde").value("2026-09-01"))
                .andExpect(jsonPath("$[0].fotografiaObjetoKey").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json).hasSize(1);
        assertCamposExactos(json.get(0));
        assertConteos(1, 1, 1, 1, 0);
    }

    @Test
    void consultarMascotasPropiasConVariasMascotasActualesDevuelveTodasAunqueRepitanNombre() throws Exception {
        Usuario usuario = guardarUsuario("cliente-varias-mascotas@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "20202020", "Bruno");
        Mascota lunaPerro = guardarMascota("Luna", "PERRO", "Mestiza", "HEMBRA", LocalDate.of(2020, 1, 10));
        Mascota lunaGato = guardarMascota("Luna", "GATO", "Siames", "HEMBRA", LocalDate.of(2021, 2, 20));
        TitularidadMascota titularidadPerro = guardarTitularidad(lunaPerro, cliente, LocalDate.of(2026, 1, 1), null);
        TitularidadMascota titularidadGato = guardarTitularidad(lunaGato, cliente, LocalDate.of(2026, 2, 1), null);

        String response = mockMvc.perform(get("/api/clientes/me/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(lunaPerro.getId()))
                .andExpect(jsonPath("$[0].titularidadMascotaId").value(titularidadPerro.getId()))
                .andExpect(jsonPath("$[1].id").value(lunaGato.getId()))
                .andExpect(jsonPath("$[1].titularidadMascotaId").value(titularidadGato.getId()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json).hasSize(2);
        assertThat(json.findValuesAsText("nombre")).containsExactly("Luna", "Luna");
        assertThat(json.findValuesAsText("especie")).containsExactly("PERRO", "GATO");
        assertConteos(1, 1, 2, 2, 0);
    }

    @Test
    void consultarMascotasPropiasNoDevuelveMascotasDeOtroCliente() throws Exception {
        Usuario usuarioA = guardarUsuario("cliente-a-mascotas@test.local", RolUsuario.CLIENTE);
        Usuario usuarioB = guardarUsuario("cliente-b-mascotas@test.local", RolUsuario.CLIENTE);
        Cliente clienteA = guardarCliente(usuarioA, "30303030", "Clara");
        Cliente clienteB = guardarCliente(usuarioB, "40404040", "Diego");
        Mascota mascotaA = guardarMascota("Nina", "PERRO", null, null, null);
        Mascota mascotaB = guardarMascota("Toto", "GATO", null, null, null);
        guardarTitularidad(mascotaA, clienteA, LocalDate.of(2026, 3, 1), null);
        guardarTitularidad(mascotaB, clienteB, LocalDate.of(2026, 3, 1), null);

        String response = mockMvc.perform(get("/api/clientes/me/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(usuarioA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(mascotaA.getId()))
                .andExpect(jsonPath("$[0].clienteId").value(clienteA.getId()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json).hasSize(1);
        assertThat(json.get(0).get("id").asLong()).isNotEqualTo(mascotaB.getId());
        assertThat(json.get(0).get("clienteId").asLong()).isNotEqualTo(clienteB.getId());
        assertThat(response).doesNotContain("Toto");
        assertConteos(2, 2, 2, 2, 0);
    }

    @Test
    void consultarMascotasPropiasNoIncluyeTitularidadesHistoricas() throws Exception {
        Usuario usuario = guardarUsuario("cliente-historico@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "50505050", "Elena");
        Mascota historica = guardarMascota("Olivia", "PERRO", null, null, null);
        guardarTitularidad(historica, cliente, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1));

        String response = mockMvc.perform(get("/api/clientes/me/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(response)).isEmpty();
        assertConteos(1, 1, 1, 1, 0);
    }

    @Test
    void consultarMascotasPropiasSinMascotasActualesDevuelveColeccionVacia() throws Exception {
        Usuario usuario = guardarUsuario("cliente-sin-mascotas@test.local", RolUsuario.CLIENTE);
        guardarCliente(usuario, "60606060", "Fabiana");

        String response = mockMvc.perform(get("/api/clientes/me/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).isEqualTo("[]");
        assertConteos(1, 1, 0, 0, 0);
    }

    @Test
    void consultarMascotasPropiasConCuentaClienteSinClienteAsociadoDevuelveNotFound() throws Exception {
        Usuario usuarioSinCliente = guardarUsuario("cliente-sin-perfil-mascotas@test.local", RolUsuario.CLIENTE);
        guardarCliente(null, "70707070", "Gabriel");

        mockMvc.perform(get("/api/clientes/me/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(usuarioSinCliente)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No se encontro el perfil de Cliente asociado a la cuenta"))
                .andExpect(jsonPath("$.id").doesNotExist());

        assertConteos(1, 1, 0, 0, 0);
    }

    @Test
    void consultarMascotasPropiasSinJwtDevuelveUnauthorized() throws Exception {
        mockMvc.perform(get("/api/clientes/me/mascotas"))
                .andExpect(status().isUnauthorized());

        assertConteos(0, 0, 0, 0, 0);
    }

    @Test
    void consultarMascotasPropiasConAdministradorDevuelveForbidden() throws Exception {
        Usuario administrador = guardarUsuario("admin-mascotas-propias@test.local", RolUsuario.ADMINISTRADOR);

        mockMvc.perform(get("/api/clientes/me/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isForbidden());

        assertConteos(1, 0, 0, 0, 0);
    }

    @Test
    void consultarMascotasPropiasNoCreaAuditoriaNiModificaDatos() throws Exception {
        Usuario usuario = guardarUsuario("cliente-sin-efectos-mascotas@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "80808080", "Helena");
        Mascota mascota = guardarMascota("Mora", "PERRO", "Cruza", "HEMBRA", LocalDate.of(2023, 8, 8));
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 8, 1), null);

        long usuariosAntes = usuarioRepository.count();
        long clientesAntes = clienteRepository.count();
        long mascotasAntes = mascotaRepository.count();
        long titularidadesAntes = titularidadMascotaRepository.count();
        long auditoriasAntes = registroAuditoriaRepository.count();

        mockMvc.perform(get("/api/clientes/me/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(mascota.getId()));

        assertThat(usuarioRepository.count()).isEqualTo(usuariosAntes);
        assertThat(clienteRepository.count()).isEqualTo(clientesAntes);
        assertThat(mascotaRepository.count()).isEqualTo(mascotasAntes);
        assertThat(titularidadMascotaRepository.count()).isEqualTo(titularidadesAntes);
        assertThat(registroAuditoriaRepository.count()).isEqualTo(auditoriasAntes);
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(recuperacionContrasenaTokenRepository.count()).isZero();
    }

    @Test
    void consultarMascotasPropiasUsaTitularidadActualEnCambioDeTitular() throws Exception {
        Usuario usuarioA = guardarUsuario("cliente-historico-a@test.local", RolUsuario.CLIENTE);
        Usuario usuarioB = guardarUsuario("cliente-actual-b@test.local", RolUsuario.CLIENTE);
        Cliente clienteA = guardarCliente(usuarioA, "90909090", "Ivana");
        Cliente clienteB = guardarCliente(usuarioB, "91919191", "Julian");
        Mascota mascota = guardarMascota("Simba", "GATO", "Comun", "MACHO", LocalDate.of(2021, 9, 9));
        guardarTitularidad(mascota, clienteA, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        TitularidadMascota titularidadActual = guardarTitularidad(mascota, clienteB, LocalDate.of(2026, 6, 1), null);

        mockMvc.perform(get("/api/clientes/me/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(usuarioA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/clientes/me/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(usuarioB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(mascota.getId()))
                .andExpect(jsonPath("$[0].clienteId").value(clienteB.getId()))
                .andExpect(jsonPath("$[0].titularidadMascotaId").value(titularidadActual.getId()))
                .andExpect(jsonPath("$[0].fechaDesde").value("2026-06-01"));

        assertConteos(2, 2, 1, 2, 0);
    }

    private Usuario guardarUsuario(String identificadorAcceso, RolUsuario rolUsuario) {
        Usuario usuario = new Usuario(
                identificadorAcceso,
                passwordEncoder.encode(CONTRASENA),
                rolUsuario,
                EstadoUsuario.ACTIVO,
                identificadorAcceso
        );

        return usuarioRepository.saveAndFlush(usuario);
    }

    private Cliente guardarCliente(Usuario usuario, String dni, String nombre) {
        return clienteRepository.saveAndFlush(new Cliente(
                usuario,
                dni,
                nombre,
                "Apellido",
                nombre.toLowerCase() + "@test.local",
                "3410000000",
                "Calle Test 123"
        ));
    }

    private Mascota guardarMascota(
            String nombre,
            String especie,
            String raza,
            String sexo,
            LocalDate fechaNacimiento
    ) {
        return mascotaRepository.saveAndFlush(new Mascota(
                nombre,
                especie,
                raza,
                sexo,
                fechaNacimiento,
                null
        ));
    }

    private TitularidadMascota guardarTitularidad(
            Mascota mascota,
            Cliente cliente,
            LocalDate fechaDesde,
            LocalDate fechaHasta
    ) {
        return titularidadMascotaRepository.saveAndFlush(new TitularidadMascota(
                mascota,
                cliente,
                fechaDesde,
                fechaHasta,
                null,
                null
        ));
    }

    private String tokenValido(Usuario usuario) {
        return jwtService.generarAccessToken(UsernamePasswordAuthenticationToken.authenticated(
                usuario.getIdentificadorAcceso(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRolUsuario().name()))
        ));
    }

    private void assertCamposExactos(JsonNode json) {
        assertThat(json).hasSize(9);

        Iterator<String> fieldNames = json.fieldNames();
        assertThat(fieldNames)
                .toIterable()
                .containsExactlyInAnyOrder(
                        "id",
                        "nombre",
                        "especie",
                        "raza",
                        "sexo",
                        "fechaNacimiento",
                        "clienteId",
                        "titularidadMascotaId",
                        "fechaDesde"
                );
    }

    private void assertConteos(
            long usuariosEsperados,
            long clientesEsperados,
            long mascotasEsperadas,
            long titularidadesEsperadas,
            long auditoriasEsperadas
    ) {
        assertThat(usuarioRepository.count()).isEqualTo(usuariosEsperados);
        assertThat(clienteRepository.count()).isEqualTo(clientesEsperados);
        assertThat(mascotaRepository.count()).isEqualTo(mascotasEsperadas);
        assertThat(titularidadMascotaRepository.count()).isEqualTo(titularidadesEsperadas);
        assertThat(registroAuditoriaRepository.count()).isEqualTo(auditoriasEsperadas);
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(recuperacionContrasenaTokenRepository.count()).isZero();
    }
}
