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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ActualizarContactoClienteIntegrationTest {

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
    void actualizarContactoClientePropioActualizaSoloCamposDeContacto() throws Exception {
        Usuario usuario = guardarUsuario("cliente-contacto@test.local", "seguridad@cliente.com", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(
                usuario,
                "11111111",
                "Ana",
                "Gomez",
                "contacto@cliente.com",
                "1111-1111",
                "Calle Vieja 123"
        );

        String response = mockMvc.perform(put("/api/clientes/me/contacto")
                        .header("Authorization", "Bearer " + tokenValido(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contactoRequest(
                                " nuevo-contacto@cliente.com ",
                                " 3411234567 ",
                                " Calle Nueva 123 "
                        ))))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(jsonPath("$.id").value(cliente.getId()))
                .andExpect(jsonPath("$.dni").value("11111111"))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.apellido").value("Gomez"))
                .andExpect(jsonPath("$.correoElectronico").value("nuevo-contacto@cliente.com"))
                .andExpect(jsonPath("$.telefono").value("3411234567"))
                .andExpect(jsonPath("$.domicilio").value("Calle Nueva 123"))
                .andExpect(jsonPath("$.usuarioId").value(usuario.getId()))
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.emailRecuperacion").doesNotExist())
                .andExpect(jsonPath("$.rolUsuario").doesNotExist())
                .andExpect(jsonPath("$.estadoUsuario").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertCamposExactos(response);

        Cliente clienteActualizado = clienteRepository.findById(cliente.getId()).orElseThrow();
        assertThat(clienteActualizado.getDni()).isEqualTo("11111111");
        assertThat(clienteActualizado.getNombre()).isEqualTo("Ana");
        assertThat(clienteActualizado.getApellido()).isEqualTo("Gomez");
        assertThat(clienteActualizado.getUsuario().getId()).isEqualTo(usuario.getId());
        assertThat(clienteActualizado.getCorreoElectronico()).isEqualTo("nuevo-contacto@cliente.com");
        assertThat(clienteActualizado.getTelefono()).isEqualTo("3411234567");
        assertThat(clienteActualizado.getDomicilio()).isEqualTo("Calle Nueva 123");

        Usuario usuarioActualizado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(usuarioActualizado.getIdentificadorAcceso()).isEqualTo("cliente-contacto@test.local");
        assertThat(usuarioActualizado.getEmailRecuperacion()).isEqualTo("seguridad@cliente.com");
        assertThat(usuarioActualizado.getRolUsuario()).isEqualTo(RolUsuario.CLIENTE);
        assertThat(usuarioActualizado.getEstadoUsuario()).isEqualTo(EstadoUsuario.ACTIVO);
        assertEfectosPermitidos(1, 1);
    }

    @Test
    void actualizarContactoClientePropioPermiteNulls() throws Exception {
        Usuario usuario = guardarUsuario("cliente-nulls@test.local", "seguridad-nulls@cliente.com", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "22222222", "Bruno", "Perez", "bruno@test.local", "2222-2222", "Calle B");

        mockMvc.perform(put("/api/clientes/me/contacto")
                        .header("Authorization", "Bearer " + tokenValido(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contactoRequest(null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cliente.getId()))
                .andExpect(jsonPath("$.correoElectronico").value(nullValue()))
                .andExpect(jsonPath("$.telefono").value(nullValue()))
                .andExpect(jsonPath("$.domicilio").value(nullValue()))
                .andExpect(jsonPath("$.dni").value("22222222"))
                .andExpect(jsonPath("$.nombre").value("Bruno"))
                .andExpect(jsonPath("$.apellido").value("Perez"))
                .andExpect(jsonPath("$.usuarioId").value(usuario.getId()));

        Cliente clienteActualizado = clienteRepository.findById(cliente.getId()).orElseThrow();
        assertThat(clienteActualizado.getCorreoElectronico()).isNull();
        assertThat(clienteActualizado.getTelefono()).isNull();
        assertThat(clienteActualizado.getDomicilio()).isNull();
        assertEfectosPermitidos(1, 1);
    }

    @Test
    void actualizarContactoClientePropioAislaClientesEIgnoraClienteIdEnQueryParam() throws Exception {
        Usuario usuarioA = guardarUsuario("cliente-a-contacto@test.local", "seguridad-a@test.local", RolUsuario.CLIENTE);
        Usuario usuarioB = guardarUsuario("cliente-b-contacto@test.local", "seguridad-b@test.local", RolUsuario.CLIENTE);
        Cliente clienteA = guardarCliente(usuarioA, "33333333", "Clara", "Lopez", "clara@test.local", "3333-3333", "Calle C");
        Cliente clienteB = guardarCliente(usuarioB, "44444444", "Diego", "Suarez", "diego@test.local", "4444-4444", "Calle D");

        mockMvc.perform(put("/api/clientes/me/contacto")
                        .queryParam("clienteId", clienteB.getId().toString())
                        .header("Authorization", "Bearer " + tokenValido(usuarioA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contactoRequest("clara-nuevo@test.local", "3333-0000", "Calle C Nueva"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clienteA.getId()))
                .andExpect(jsonPath("$.correoElectronico").value("clara-nuevo@test.local"))
                .andExpect(jsonPath("$.telefono").value("3333-0000"))
                .andExpect(jsonPath("$.domicilio").value("Calle C Nueva"))
                .andExpect(jsonPath("$.usuarioId").value(usuarioA.getId()));

        Cliente clienteAActualizado = clienteRepository.findById(clienteA.getId()).orElseThrow();
        Cliente clienteBSinCambios = clienteRepository.findById(clienteB.getId()).orElseThrow();
        assertThat(clienteAActualizado.getCorreoElectronico()).isEqualTo("clara-nuevo@test.local");
        assertThat(clienteAActualizado.getTelefono()).isEqualTo("3333-0000");
        assertThat(clienteAActualizado.getDomicilio()).isEqualTo("Calle C Nueva");
        assertClienteIdentico(clienteBSinCambios, "44444444", "Diego", "Suarez", "diego@test.local", "4444-4444", "Calle D", usuarioB.getId());

        mockMvc.perform(put("/api/clientes/me/contacto")
                        .header("Authorization", "Bearer " + tokenValido(usuarioB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contactoRequest("diego-nuevo@test.local", "4444-0000", "Calle D Nueva"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clienteB.getId()))
                .andExpect(jsonPath("$.correoElectronico").value("diego-nuevo@test.local"))
                .andExpect(jsonPath("$.telefono").value("4444-0000"))
                .andExpect(jsonPath("$.domicilio").value("Calle D Nueva"))
                .andExpect(jsonPath("$.usuarioId").value(usuarioB.getId()));

        Cliente clienteAFinal = clienteRepository.findById(clienteA.getId()).orElseThrow();
        Cliente clienteBActualizado = clienteRepository.findById(clienteB.getId()).orElseThrow();
        assertClienteIdentico(clienteAFinal, "33333333", "Clara", "Lopez", "clara-nuevo@test.local", "3333-0000", "Calle C Nueva", usuarioA.getId());
        assertClienteIdentico(clienteBActualizado, "44444444", "Diego", "Suarez", "diego-nuevo@test.local", "4444-0000", "Calle D Nueva", usuarioB.getId());
        assertEfectosPermitidos(2, 2);
    }

    @Test
    void actualizarContactoClienteSinClienteAsociadoDevuelveNotFoundYNoModificaOtrosClientes() throws Exception {
        Usuario usuarioSinCliente = guardarUsuario("cliente-sin-contacto@test.local", "seguridad-sin@test.local", RolUsuario.CLIENTE);
        Usuario usuarioOtro = guardarUsuario("cliente-otro-contacto@test.local", "seguridad-otro@test.local", RolUsuario.CLIENTE);
        Cliente otroCliente = guardarCliente(usuarioOtro, "55555555", "Elena", "Martinez", "elena@test.local", "5555-5555", "Calle E");

        mockMvc.perform(put("/api/clientes/me/contacto")
                        .header("Authorization", "Bearer " + tokenValido(usuarioSinCliente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contactoRequest("nuevo@test.local", "9999", "Otra calle"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No se encontro el perfil de Cliente asociado a la cuenta"))
                .andExpect(jsonPath("$.id").doesNotExist());

        Cliente otroClienteSinCambios = clienteRepository.findById(otroCliente.getId()).orElseThrow();
        assertClienteIdentico(otroClienteSinCambios, "55555555", "Elena", "Martinez", "elena@test.local", "5555-5555", "Calle E", usuarioOtro.getId());
        assertEfectosPermitidos(2, 1);
    }

    @ParameterizedTest
    @CsvSource({"ADMINISTRADOR", "VETERINARIO"})
    void actualizarContactoClienteConRolNoClienteDevuelveForbidden(RolUsuario rolUsuario) throws Exception {
        Usuario usuario = guardarUsuario(rolUsuario.name().toLowerCase() + "-contacto@test.local", "seguridad-" + rolUsuario.name().toLowerCase() + "@test.local", rolUsuario);

        mockMvc.perform(put("/api/clientes/me/contacto")
                        .header("Authorization", "Bearer " + tokenValido(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contactoRequest("nuevo@test.local", "1234", "Calle"))))
                .andExpect(status().isForbidden());

        assertEfectosPermitidos(1, 0);
    }

    @Test
    void actualizarContactoClienteSinJwtDevuelveUnauthorized() throws Exception {
        mockMvc.perform(put("/api/clientes/me/contacto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contactoRequest("nuevo@test.local", "1234", "Calle"))))
                .andExpect(status().isUnauthorized());

        assertEfectosPermitidos(0, 0);
    }

    @ParameterizedTest
    @CsvSource({
            "correoElectronico, '   '",
            "telefono, '   '",
            "domicilio, '   '",
            "correoElectronico, correo-invalido"
    })
    void actualizarContactoClienteConValoresInvalidosDevuelveBadRequest(String campo, String valor) throws Exception {
        Usuario usuario = guardarUsuario("cliente-invalidos-" + campo + "@test.local", "seguridad-invalidos-" + campo + "@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "66666666", "Fabiana", "Diaz", "fabiana@test.local", "6666-6666", "Calle F");
        Map<String, Object> request = contactoRequest("nuevo@test.local", "1234", "Calle Nueva");
        request.put(campo, valor);

        mockMvc.perform(put("/api/clientes/me/contacto")
                        .header("Authorization", "Bearer " + tokenValido(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        Cliente clienteSinCambios = clienteRepository.findById(cliente.getId()).orElseThrow();
        assertClienteIdentico(clienteSinCambios, "66666666", "Fabiana", "Diaz", "fabiana@test.local", "6666-6666", "Calle F", usuario.getId());
        assertEfectosPermitidos(1, 1);
    }

    @ParameterizedTest
    @CsvSource({
            "correoElectronico, 255",
            "telefono, 51",
            "domicilio, 501"
    })
    void actualizarContactoClienteConCamposExcedidosDevuelveBadRequest(String campo, int largo) throws Exception {
        Usuario usuario = guardarUsuario("cliente-largo-" + campo + "@test.local", "seguridad-largo-" + campo + "@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "77777777", "Gabriel", "Ruiz", "gabriel@test.local", "7777-7777", "Calle G");
        Map<String, Object> request = contactoRequest("nuevo@test.local", "1234", "Calle Nueva");
        request.put(campo, "x".repeat(largo));

        mockMvc.perform(put("/api/clientes/me/contacto")
                        .header("Authorization", "Bearer " + tokenValido(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        Cliente clienteSinCambios = clienteRepository.findById(cliente.getId()).orElseThrow();
        assertClienteIdentico(clienteSinCambios, "77777777", "Gabriel", "Ruiz", "gabriel@test.local", "7777-7777", "Calle G", usuario.getId());
        assertEfectosPermitidos(1, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "dni",
            "nombre",
            "apellido",
            "usuarioId",
            "rol",
            "estado",
            "emailRecuperacion",
            "password",
            "clienteId"
    })
    void actualizarContactoClienteConCampoJsonNoPermitidoDevuelveBadRequest(String campoNoPermitido) throws Exception {
        Usuario usuario = guardarUsuario("cliente-campo-extra-" + campoNoPermitido + "@test.local", "seguridad-extra-" + campoNoPermitido + "@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "88888888", "Helena", "Castro", "helena@test.local", "8888-8888", "Calle H");
        Map<String, Object> request = contactoRequest("nuevo@test.local", "1234", "Calle Nueva");
        request.put(campoNoPermitido, "valor-no-permitido");

        mockMvc.perform(put("/api/clientes/me/contacto")
                        .header("Authorization", "Bearer " + tokenValido(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        Cliente clienteSinCambios = clienteRepository.findById(cliente.getId()).orElseThrow();
        assertClienteIdentico(clienteSinCambios, "88888888", "Helena", "Castro", "helena@test.local", "8888-8888", "Calle H", usuario.getId());
        assertEfectosPermitidos(1, 1);
    }

    private Usuario guardarUsuario(
            String identificadorAcceso,
            String emailRecuperacion,
            RolUsuario rolUsuario
    ) {
        Usuario usuario = new Usuario(
                identificadorAcceso,
                passwordEncoder.encode(CONTRASENA),
                rolUsuario,
                EstadoUsuario.ACTIVO,
                emailRecuperacion
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

    private Map<String, Object> contactoRequest(
            String correoElectronico,
            String telefono,
            String domicilio
    ) {
        Map<String, Object> request = new HashMap<>();
        request.put("correoElectronico", correoElectronico);
        request.put("telefono", telefono);
        request.put("domicilio", domicilio);
        return request;
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

    private void assertClienteIdentico(
            Cliente cliente,
            String dni,
            String nombre,
            String apellido,
            String correoElectronico,
            String telefono,
            String domicilio,
            Long usuarioId
    ) {
        assertThat(cliente.getDni()).isEqualTo(dni);
        assertThat(cliente.getNombre()).isEqualTo(nombre);
        assertThat(cliente.getApellido()).isEqualTo(apellido);
        assertThat(cliente.getCorreoElectronico()).isEqualTo(correoElectronico);
        assertThat(cliente.getTelefono()).isEqualTo(telefono);
        assertThat(cliente.getDomicilio()).isEqualTo(domicilio);
        assertThat(cliente.getUsuario().getId()).isEqualTo(usuarioId);
    }

    private void assertEfectosPermitidos(long usuariosEsperados, long clientesEsperados) {
        assertThat(usuarioRepository.count()).isEqualTo(usuariosEsperados);
        assertThat(clienteRepository.count()).isEqualTo(clientesEsperados);
        assertThat(mascotaRepository.count()).isZero();
        assertThat(titularidadMascotaRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(recuperacionContrasenaTokenRepository.count()).isZero();
    }
}
