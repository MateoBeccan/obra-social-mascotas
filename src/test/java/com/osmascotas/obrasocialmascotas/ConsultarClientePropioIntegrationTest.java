package com.osmascotas.obrasocialmascotas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

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
class ConsultarClientePropioIntegrationTest {

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
    void consultarClientePropioDevuelveSoloClienteAsociadoAlUsuarioAutenticado() throws Exception {
        Usuario usuarioA = guardarUsuario("cliente-a@test.local", RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        Usuario usuarioB = guardarUsuario("cliente-b@test.local", RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        Cliente clienteA = guardarCliente(
                usuarioA,
                "11111111",
                "Ana",
                "Gomez",
                "ana@test.local",
                "1111-1111",
                "Calle A 123"
        );
        Cliente clienteB = guardarCliente(
                usuarioB,
                "22222222",
                "Bruno",
                "Perez",
                "bruno@test.local",
                "2222-2222",
                "Calle B 456"
        );

        String responseA = mockMvc.perform(get("/api/clientes/me")
                        .header("Authorization", "Bearer " + tokenValido(usuarioA)))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(jsonPath("$.id").value(clienteA.getId()))
                .andExpect(jsonPath("$.dni").value("11111111"))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.apellido").value("Gomez"))
                .andExpect(jsonPath("$.correoElectronico").value("ana@test.local"))
                .andExpect(jsonPath("$.telefono").value("1111-1111"))
                .andExpect(jsonPath("$.domicilio").value("Calle A 123"))
                .andExpect(jsonPath("$.usuarioId").value(usuarioA.getId()))
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.emailRecuperacion").doesNotExist())
                .andExpect(jsonPath("$.rolUsuario").doesNotExist())
                .andExpect(jsonPath("$.estadoUsuario").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.mascotas").doesNotExist())
                .andExpect(jsonPath("$.titularidades").doesNotExist())
                .andExpect(jsonPath("$.afiliaciones").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertCamposExactos(responseA);
        assertThat(objectMapper.readTree(responseA).get("id").asLong()).isNotEqualTo(clienteB.getId());
        assertThat(responseA)
                .doesNotContain("22222222")
                .doesNotContain("Bruno")
                .doesNotContain("Perez")
                .doesNotContain("bruno@test.local")
                .doesNotContain("2222-2222")
                .doesNotContain("Calle B 456");

        String responseB = mockMvc.perform(get("/api/clientes/me")
                        .header("Authorization", "Bearer " + tokenValido(usuarioB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clienteB.getId()))
                .andExpect(jsonPath("$.dni").value("22222222"))
                .andExpect(jsonPath("$.nombre").value("Bruno"))
                .andExpect(jsonPath("$.apellido").value("Perez"))
                .andExpect(jsonPath("$.correoElectronico").value("bruno@test.local"))
                .andExpect(jsonPath("$.telefono").value("2222-2222"))
                .andExpect(jsonPath("$.domicilio").value("Calle B 456"))
                .andExpect(jsonPath("$.usuarioId").value(usuarioB.getId()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertCamposExactos(responseB);
        assertThat(objectMapper.readTree(responseB).get("id").asLong()).isNotEqualTo(clienteA.getId());
        assertThat(responseB)
                .doesNotContain("11111111")
                .doesNotContain("Ana")
                .doesNotContain("Gomez")
                .doesNotContain("ana@test.local")
                .doesNotContain("1111-1111")
                .doesNotContain("Calle A 123");

        assertLecturaSinEfectos();
    }

    @Test
    void consultarClientePropioIgnoraClienteIdEnQueryParam() throws Exception {
        Usuario usuarioA = guardarUsuario("cliente-query-a@test.local", RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        Usuario usuarioB = guardarUsuario("cliente-query-b@test.local", RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        Cliente clienteA = guardarCliente(usuarioA, "33333333", "Clara", "Lopez", "clara@test.local", "3333-3333", "Calle C 789");
        Cliente clienteB = guardarCliente(usuarioB, "44444444", "Diego", "Suarez", "diego@test.local", "4444-4444", "Calle D 012");

        String response = mockMvc.perform(get("/api/clientes/me")
                        .queryParam("clienteId", clienteB.getId().toString())
                        .queryParam("usuarioId", usuarioB.getId().toString())
                        .header("Authorization", "Bearer " + tokenValido(usuarioA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clienteA.getId()))
                .andExpect(jsonPath("$.dni").value("33333333"))
                .andExpect(jsonPath("$.usuarioId").value(usuarioA.getId()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertCamposExactos(response);
        assertThat(objectMapper.readTree(response).get("id").asLong()).isNotEqualTo(clienteB.getId());
        assertThat(response)
                .doesNotContain("44444444")
                .doesNotContain("Diego")
                .doesNotContain("Suarez")
                .doesNotContain("diego@test.local")
                .doesNotContain("4444-4444")
                .doesNotContain("Calle D 012");
        assertLecturaSinEfectos();
    }

    @Test
    void consultarClientePropioConCuentaClienteSinClienteAsociadoDevuelveNotFound() throws Exception {
        Usuario usuarioSinCliente = guardarUsuario("cliente-sin-perfil@test.local", RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        guardarCliente(null, "55555555", "Elena", "Martinez", "elena@test.local", "5555-5555", "Calle E 345");

        String response = mockMvc.perform(get("/api/clientes/me")
                        .header("Authorization", "Bearer " + tokenValido(usuarioSinCliente)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No se encontro el perfil de Cliente asociado a la cuenta"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.dni").doesNotExist())
                .andExpect(jsonPath("$.nombre").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response)
                .doesNotContain("55555555")
                .doesNotContain("Elena")
                .doesNotContain("Martinez")
                .doesNotContain("elena@test.local")
                .doesNotContain("5555-5555")
                .doesNotContain("Calle E 345");
        assertLecturaSinEfectos();
    }

    @ParameterizedTest
    @CsvSource({"ADMINISTRADOR", "VETERINARIO"})
    void consultarClientePropioConRolNoClienteDevuelveForbidden(RolUsuario rolUsuario) throws Exception {
        Usuario usuario = guardarUsuario(rolUsuario.name().toLowerCase() + "-me@test.local", rolUsuario, EstadoUsuario.ACTIVO);

        mockMvc.perform(get("/api/clientes/me")
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isForbidden());

        assertLecturaSinEfectos();
    }

    @Test
    void consultarClientePropioSinJwtDevuelveUnauthorized() throws Exception {
        mockMvc.perform(get("/api/clientes/me"))
                .andExpect(status().isUnauthorized());

        assertLecturaSinEfectos();
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

    private Cliente guardarCliente(
            Usuario usuario,
            String dni,
            String nombre,
            String apellido,
            String correoElectronico,
            String telefono,
            String domicilio
    ) {
        return clienteRepository.saveAndFlush(new Cliente(
                usuario,
                dni,
                nombre,
                apellido,
                correoElectronico,
                telefono,
                domicilio
        ));
    }

    private String tokenValido(Usuario usuario) {
        return jwtService.generarAccessToken(UsernamePasswordAuthenticationToken.authenticated(
                usuario.getIdentificadorAcceso(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRolUsuario().name()))
        ));
    }

    private void assertCamposExactos(String response) throws Exception {
        JsonNode json = objectMapper.readTree(response);
        assertThat(json).hasSize(8);

        Iterator<String> fieldNames = json.fieldNames();
        assertThat(fieldNames)
                .toIterable()
                .containsExactlyInAnyOrder(
                        "id",
                        "dni",
                        "nombre",
                        "apellido",
                        "correoElectronico",
                        "telefono",
                        "domicilio",
                        "usuarioId"
                );
    }

    private void assertLecturaSinEfectos() {
        assertThat(mascotaRepository.count()).isZero();
        assertThat(titularidadMascotaRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(recuperacionContrasenaTokenRepository.count()).isZero();
    }
}
