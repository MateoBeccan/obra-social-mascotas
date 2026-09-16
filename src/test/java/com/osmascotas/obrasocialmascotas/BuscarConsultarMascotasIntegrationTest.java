package com.osmascotas.obrasocialmascotas;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
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
class BuscarConsultarMascotasIntegrationTest {

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
    void administradorBuscaPorMascotaIdDevuelveMascotaCorrecta() throws Exception {
        Usuario administrador = guardarUsuario("admin-busca-id@test.local", RolUsuario.ADMINISTRADOR);
        Cliente cliente = guardarCliente("30111111", "Ana", "Gomez");
        Mascota mascota = guardarMascota("Luna", "PERRO", "Mestiza", "HEMBRA", LocalDate.of(2021, 1, 1), null);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 1, 1), null);
        Mascota otra = guardarMascota("Sol", "GATO", null, null, null, null);
        guardarTitularidad(otra, cliente, LocalDate.of(2026, 1, 1), null);

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("mascotaId", mascota.getId().toString())
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(mascota.getId()))
                .andExpect(jsonPath("$[0].nombre").value("Luna"))
                .andExpect(jsonPath("$[0].titular.dni").value("30111111"))
                .andExpect(jsonPath("$[0].clienteId").doesNotExist())
                .andExpect(jsonPath("$[0].fotografiaObjetoKey").doesNotExist());
    }

    @Test
    void veterinarioBuscaPorMascotaIdDevuelveMascotaCorrecta() throws Exception {
        Usuario veterinario = guardarUsuario("vet-busca-id@test.local", RolUsuario.VETERINARIO);
        Cliente cliente = guardarCliente("30222222", "Bruno", "Perez");
        Mascota mascota = guardarMascota("Mora", "PERRO", null, null, null, null);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 1, 1), null);

        mockMvc.perform(get("/api/veterinarios/mascotas/buscar")
                        .queryParam("mascotaId", mascota.getId().toString())
                        .header("Authorization", "Bearer " + tokenValido(veterinario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(mascota.getId()))
                .andExpect(jsonPath("$[0].titular.nombre").value("Bruno"))
                .andExpect(jsonPath("$[0].titular.correoElectronico").doesNotExist());
    }

    @Test
    void administradorBuscaPorNombreParcialCaseInsensitive() throws Exception {
        Usuario administrador = guardarUsuario("admin-busca-nombre@test.local", RolUsuario.ADMINISTRADOR);
        Cliente cliente = guardarCliente("30333333", "Clara", "Lopez");
        Mascota luna = guardarMascota("Luna", "PERRO", null, null, null, null);
        Mascota sol = guardarMascota("Sol", "GATO", null, null, null, null);
        guardarTitularidad(luna, cliente, LocalDate.of(2026, 1, 1), null);
        guardarTitularidad(sol, cliente, LocalDate.of(2026, 1, 1), null);

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("nombre", "luN")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(luna.getId()))
                .andExpect(jsonPath("$[0].nombre").value("Luna"));
    }

    @Test
    void busquedaPorNombreNoUnicoDevuelveTodasLasMascotasCoincidentes() throws Exception {
        Usuario administrador = guardarUsuario("admin-nombre-repetido@test.local", RolUsuario.ADMINISTRADOR);
        Cliente cliente = guardarCliente("30444444", "Diego", "Suarez");
        Mascota lunaPerro = guardarMascota("Luna", "PERRO", null, null, null, null);
        Mascota lunaGato = guardarMascota("Luna", "GATO", null, null, null, null);
        guardarTitularidad(lunaPerro, cliente, LocalDate.of(2026, 1, 1), null);
        guardarTitularidad(lunaGato, cliente, LocalDate.of(2026, 1, 2), null);

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("nombre", "Luna")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        lunaPerro.getId().intValue(),
                        lunaGato.getId().intValue()
                )));
    }

    @Test
    void busquedaPorNombreConPorcentajeLoInterpretaComoTextoLiteral() throws Exception {
        Usuario administrador = guardarUsuario("admin-nombre-porcentaje@test.local", RolUsuario.ADMINISTRADOR);
        Cliente cliente = guardarCliente("30444445", "Diana", "Suarez");
        Mascota lunaEspecial = guardarMascota("Luna%Especial", "PERRO", null, null, null, null);
        Mascota lunaNormal = guardarMascota("LunaNormal", "GATO", null, null, null, null);
        guardarTitularidad(lunaEspecial, cliente, LocalDate.of(2026, 1, 1), null);
        guardarTitularidad(lunaNormal, cliente, LocalDate.of(2026, 1, 2), null);

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("nombre", "%")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(lunaEspecial.getId()))
                .andExpect(jsonPath("$[0].nombre").value("Luna%Especial"));
    }

    @Test
    void busquedaPorNombreConGuionBajoLoInterpretaComoTextoLiteral() throws Exception {
        Usuario administrador = guardarUsuario("admin-nombre-guion-bajo@test.local", RolUsuario.ADMINISTRADOR);
        Cliente cliente = guardarCliente("30444446", "Daniela", "Suarez");
        Mascota lunaEspecial = guardarMascota("Luna_Especial", "PERRO", null, null, null, null);
        Mascota lunaNormal = guardarMascota("LunaNormal", "GATO", null, null, null, null);
        guardarTitularidad(lunaEspecial, cliente, LocalDate.of(2026, 1, 1), null);
        guardarTitularidad(lunaNormal, cliente, LocalDate.of(2026, 1, 2), null);

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("nombre", "_")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(lunaEspecial.getId()))
                .andExpect(jsonPath("$[0].nombre").value("Luna_Especial"));
    }

    @Test
    void busquedaPorDniTitularActualDevuelveMascotasDelTitularActual() throws Exception {
        Usuario administrador = guardarUsuario("admin-busca-dni@test.local", RolUsuario.ADMINISTRADOR);
        Cliente clienteA = guardarCliente("30555555", "Elena", "Diaz");
        Cliente clienteB = guardarCliente("30666666", "Fabiana", "Ruiz");
        Mascota mascotaA = guardarMascota("Nina", "PERRO", null, null, null, null);
        Mascota mascotaB = guardarMascota("Toto", "GATO", null, null, null, null);
        guardarTitularidad(mascotaA, clienteA, LocalDate.of(2026, 1, 1), null);
        guardarTitularidad(mascotaB, clienteB, LocalDate.of(2026, 1, 1), null);

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("dniTitular", " 30555555 ")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(mascotaA.getId()))
                .andExpect(jsonPath("$[0].titular.dni").value("30555555"));
    }

    @Test
    void busquedaConCriteriosCombinadosUsaAnd() throws Exception {
        Usuario administrador = guardarUsuario("admin-busca-and@test.local", RolUsuario.ADMINISTRADOR);
        Cliente clienteA = guardarCliente("30777777", "Gabriel", "Castro");
        Cliente clienteB = guardarCliente("30888888", "Helena", "Molina");
        Mascota lunaA = guardarMascota("Luna", "PERRO", null, null, null, null);
        Mascota lunaB = guardarMascota("Luna", "GATO", null, null, null, null);
        guardarTitularidad(lunaA, clienteA, LocalDate.of(2026, 1, 1), null);
        guardarTitularidad(lunaB, clienteB, LocalDate.of(2026, 1, 1), null);

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("nombre", "lun")
                        .queryParam("dniTitular", "30888888")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(lunaB.getId()))
                .andExpect(jsonPath("$[0].titular.dni").value("30888888"));
    }

    @Test
    void busquedaSinCriteriosDevuelveBadRequest() throws Exception {
        Usuario administrador = guardarUsuario("admin-sin-criterios@test.local", RolUsuario.ADMINISTRADOR);

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Debe indicar al menos un criterio de busqueda"));
    }

    @Test
    void busquedaConStringsBlankDevuelveBadRequest() throws Exception {
        Usuario veterinario = guardarUsuario("vet-blank@test.local", RolUsuario.VETERINARIO);

        mockMvc.perform(get("/api/veterinarios/mascotas/buscar")
                        .queryParam("nombre", "   ")
                        .queryParam("dniTitular", "   ")
                        .header("Authorization", "Bearer " + tokenValido(veterinario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Debe indicar al menos un criterio de busqueda"));
    }

    @Test
    void busquedaConMascotaIdNoPositivoDevuelveBadRequest() throws Exception {
        Usuario administrador = guardarUsuario("admin-id-invalido@test.local", RolUsuario.ADMINISTRADOR);

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("mascotaId", "0")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El id de mascota debe ser mayor a cero"));
    }

    @Test
    void busquedaConCriterioValidoSinCoincidenciasDevuelveColeccionVacia() throws Exception {
        Usuario administrador = guardarUsuario("admin-sin-coincidencias@test.local", RolUsuario.ADMINISTRADOR);

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("nombre", "NoExiste")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void busquedaPorDniUsaTitularActualYExcluyeTitularHistorico() throws Exception {
        Usuario administrador = guardarUsuario("admin-historico@test.local", RolUsuario.ADMINISTRADOR);
        Cliente clienteHistorico = guardarCliente("30999991", "Ivana", "Sosa");
        Cliente clienteActual = guardarCliente("30999992", "Julia", "Vega");
        Mascota mascota = guardarMascota("Chispa", "PERRO", null, null, null, null);
        guardarTitularidad(mascota, clienteHistorico, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        guardarTitularidad(mascota, clienteActual, LocalDate.of(2026, 6, 1), null);

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("dniTitular", "30999991")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("dniTitular", "30999992")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(mascota.getId()))
                .andExpect(jsonPath("$[0].titular.nombre").value("Julia"));
    }

    @Test
    void clienteIntentaBusquedaDevuelveForbidden() throws Exception {
        Usuario cliente = guardarUsuario("cliente-busca@test.local", RolUsuario.CLIENTE);

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("nombre", "Luna")
                        .header("Authorization", "Bearer " + tokenValido(cliente)))
                .andExpect(status().isForbidden());
    }

    @Test
    void veterinarioIntentaBusquedaAdminDevuelveForbidden() throws Exception {
        Usuario veterinario = guardarUsuario("vet-busqueda-admin-forbidden@test.local", RolUsuario.VETERINARIO);

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("nombre", "Luna")
                        .header("Authorization", "Bearer " + tokenValido(veterinario)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorIntentaBusquedaVeterinarioDevuelveForbidden() throws Exception {
        Usuario administrador = guardarUsuario("admin-busqueda-vet-forbidden@test.local", RolUsuario.ADMINISTRADOR);

        mockMvc.perform(get("/api/veterinarios/mascotas/buscar")
                        .queryParam("nombre", "Luna")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isForbidden());
    }

    @Test
    void busquedaSinJwtDevuelveUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("nombre", "Luna"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administradorConsultaDetalleDeMascotaExistente() throws Exception {
        Usuario administrador = guardarUsuario("admin-detalle@test.local", RolUsuario.ADMINISTRADOR);
        Cliente cliente = guardarCliente("31111111", "Karina", "Lopez", "karina@test.local", "3411111111", "Calle A");
        Mascota mascota = guardarMascota("Tina", "PERRO", "Caniche", "HEMBRA", LocalDate.of(2022, 2, 2), "interna/key.jpg");
        TitularidadMascota titularidad = guardarTitularidad(mascota, cliente, LocalDate.of(2026, 2, 1), null);

        mockMvc.perform(get("/api/admin/mascotas/{mascotaId}", mascota.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mascota.getId()))
                .andExpect(jsonPath("$.nombre").value("Tina"))
                .andExpect(jsonPath("$.especie").value("PERRO"))
                .andExpect(jsonPath("$.raza").value("Caniche"))
                .andExpect(jsonPath("$.sexo").value("HEMBRA"))
                .andExpect(jsonPath("$.fechaNacimiento").value("2022-02-02"))
                .andExpect(jsonPath("$.titular.clienteId").value(cliente.getId()))
                .andExpect(jsonPath("$.titular.dni").value("31111111"))
                .andExpect(jsonPath("$.titular.nombre").value("Karina"))
                .andExpect(jsonPath("$.titular.apellido").value("Lopez"))
                .andExpect(jsonPath("$.titular.correoElectronico").value("karina@test.local"))
                .andExpect(jsonPath("$.titular.telefono").value("3411111111"))
                .andExpect(jsonPath("$.titular.domicilio").value("Calle A"))
                .andExpect(jsonPath("$.titularidadActual.titularidadMascotaId").value(titularidad.getId()))
                .andExpect(jsonPath("$.titularidadActual.fechaDesde").value("2026-02-01"));
    }

    @Test
    void administradorConsultaMascotaInexistenteDevuelveNotFound() throws Exception {
        Usuario administrador = guardarUsuario("admin-detalle-no-existe@test.local", RolUsuario.ADMINISTRADOR);

        mockMvc.perform(get("/api/admin/mascotas/{mascotaId}", 999999L)
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No se encontro la Mascota"));
    }

    @Test
    void detalleAdministradorUsaTitularActualYNoHistorico() throws Exception {
        Usuario administrador = guardarUsuario("admin-detalle-historico@test.local", RolUsuario.ADMINISTRADOR);
        Cliente clienteHistorico = guardarCliente("31222221", "Laura", "Vieja", "historico@test.local", "111", "Vieja 1");
        Cliente clienteActual = guardarCliente("31222222", "Marcela", "Actual", "actual@test.local", "222", "Actual 2");
        Mascota mascota = guardarMascota("Pipa", "GATO", null, null, null, null);
        guardarTitularidad(mascota, clienteHistorico, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        TitularidadMascota actual = guardarTitularidad(mascota, clienteActual, LocalDate.of(2026, 6, 1), null);

        mockMvc.perform(get("/api/admin/mascotas/{mascotaId}", mascota.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titular.clienteId").value(clienteActual.getId()))
                .andExpect(jsonPath("$.titular.dni").value("31222222"))
                .andExpect(jsonPath("$.titular.nombre").value("Marcela"))
                .andExpect(jsonPath("$.titular.correoElectronico").value("actual@test.local"))
                .andExpect(jsonPath("$.titularidadActual.titularidadMascotaId").value(actual.getId()));
    }

    @Test
    void detalleAdministradorNoExponeCamposInternos() throws Exception {
        Usuario administrador = guardarUsuario("admin-no-internos@test.local", RolUsuario.ADMINISTRADOR);
        Cliente cliente = guardarCliente("31333333", "Natalia", "Rios");
        Mascota mascota = guardarMascota("Beto", "PERRO", null, null, null, "mascotas/1/fotografias/secreto.jpg");
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 1, 1), null);

        mockMvc.perform(get("/api/admin/mascotas/{mascotaId}", mascota.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotografiaObjetoKey").doesNotExist())
                .andExpect(jsonPath("$.usuarioId").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tokens").doesNotExist())
                .andExpect(jsonPath("$.titular.usuarioId").doesNotExist())
                .andExpect(jsonPath("$.titular.contrasenaHash").doesNotExist());
    }

    @Test
    void veterinarioConsultaDetalleDeMascotaExistenteConDatosPermitidos() throws Exception {
        Usuario veterinario = guardarUsuario("vet-detalle@test.local", RolUsuario.VETERINARIO);
        Cliente cliente = guardarCliente("31444444", "Olga", "Mendez", "olga@test.local", "3412222222", "Calle B");
        Mascota mascota = guardarMascota("Roco", "PERRO", "Mestizo", "MACHO", LocalDate.of(2020, 3, 3), "key/privada.png");
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 3, 1), null);

        mockMvc.perform(get("/api/veterinarios/mascotas/{mascotaId}", mascota.getId())
                        .header("Authorization", "Bearer " + tokenValido(veterinario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mascota.getId()))
                .andExpect(jsonPath("$.nombre").value("Roco"))
                .andExpect(jsonPath("$.especie").value("PERRO"))
                .andExpect(jsonPath("$.raza").value("Mestizo"))
                .andExpect(jsonPath("$.sexo").value("MACHO"))
                .andExpect(jsonPath("$.fechaNacimiento").value("2020-03-03"))
                .andExpect(jsonPath("$.titular.dni").value("31444444"))
                .andExpect(jsonPath("$.titular.nombre").value("Olga"))
                .andExpect(jsonPath("$.titular.apellido").value("Mendez"))
                .andExpect(jsonPath("$.correoElectronico").doesNotExist())
                .andExpect(jsonPath("$.telefono").doesNotExist())
                .andExpect(jsonPath("$.domicilio").doesNotExist())
                .andExpect(jsonPath("$.clienteId").doesNotExist())
                .andExpect(jsonPath("$.usuarioId").doesNotExist())
                .andExpect(jsonPath("$.titularidadMascotaId").doesNotExist())
                .andExpect(jsonPath("$.fechaDesde").doesNotExist())
                .andExpect(jsonPath("$.fotografiaObjetoKey").doesNotExist())
                .andExpect(jsonPath("$.titular.correoElectronico").doesNotExist())
                .andExpect(jsonPath("$.titular.telefono").doesNotExist())
                .andExpect(jsonPath("$.titular.domicilio").doesNotExist())
                .andExpect(jsonPath("$.titular.clienteId").doesNotExist());
    }

    @Test
    void veterinarioConsultaMascotaInexistenteDevuelveNotFound() throws Exception {
        Usuario veterinario = guardarUsuario("vet-detalle-no-existe@test.local", RolUsuario.VETERINARIO);

        mockMvc.perform(get("/api/veterinarios/mascotas/{mascotaId}", 999999L)
                        .header("Authorization", "Bearer " + tokenValido(veterinario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No se encontro la Mascota"));
    }

    @Test
    void veterinarioIntentaEndpointAdminDevuelveForbidden() throws Exception {
        Usuario veterinario = guardarUsuario("vet-admin-forbidden@test.local", RolUsuario.VETERINARIO);

        mockMvc.perform(get("/api/admin/mascotas/{mascotaId}", 1L)
                        .header("Authorization", "Bearer " + tokenValido(veterinario)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorIntentaEndpointVeterinarioDevuelveForbidden() throws Exception {
        Usuario administrador = guardarUsuario("admin-vet-forbidden@test.local", RolUsuario.ADMINISTRADOR);

        mockMvc.perform(get("/api/veterinarios/mascotas/{mascotaId}", 1L)
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteIntentaEndpointsDeDetalleDevuelveForbidden() throws Exception {
        Usuario cliente = guardarUsuario("cliente-detalle-forbidden@test.local", RolUsuario.CLIENTE);

        mockMvc.perform(get("/api/admin/mascotas/{mascotaId}", 1L)
                        .header("Authorization", "Bearer " + tokenValido(cliente)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/veterinarios/mascotas/{mascotaId}", 1L)
                        .header("Authorization", "Bearer " + tokenValido(cliente)))
                .andExpect(status().isForbidden());
    }

    @Test
    void busquedaYDetalleNoModificanDatosNiAuditoria() throws Exception {
        Usuario administrador = guardarUsuario("admin-readonly@test.local", RolUsuario.ADMINISTRADOR);
        Cliente cliente = guardarCliente("31555555", "Paula", "Nunez");
        Mascota mascota = guardarMascota("Uma", "GATO", null, null, null, null);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 1, 1), null);

        long usuariosAntes = usuarioRepository.count();
        long clientesAntes = clienteRepository.count();
        long mascotasAntes = mascotaRepository.count();
        long titularidadesAntes = titularidadMascotaRepository.count();
        long auditoriasAntes = registroAuditoriaRepository.count();
        long activacionesAntes = activacionCuentaTokenRepository.count();
        long recuperacionesAntes = recuperacionContrasenaTokenRepository.count();

        mockMvc.perform(get("/api/admin/mascotas/buscar")
                        .queryParam("nombre", "Uma")
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/admin/mascotas/{mascotaId}", mascota.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mascota.getId()));

        assertThat(usuarioRepository.count()).isEqualTo(usuariosAntes);
        assertThat(clienteRepository.count()).isEqualTo(clientesAntes);
        assertThat(mascotaRepository.count()).isEqualTo(mascotasAntes);
        assertThat(titularidadMascotaRepository.count()).isEqualTo(titularidadesAntes);
        assertThat(registroAuditoriaRepository.count()).isEqualTo(auditoriasAntes);
        assertThat(activacionCuentaTokenRepository.count()).isEqualTo(activacionesAntes);
        assertThat(recuperacionContrasenaTokenRepository.count()).isEqualTo(recuperacionesAntes);
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

    private Cliente guardarCliente(String dni, String nombre, String apellido) {
        return guardarCliente(
                dni,
                nombre,
                apellido,
                nombre.toLowerCase() + "@test.local",
                "3410000000",
                "Calle Test 123"
        );
    }

    private Cliente guardarCliente(
            String dni,
            String nombre,
            String apellido,
            String correoElectronico,
            String telefono,
            String domicilio
    ) {
        return clienteRepository.saveAndFlush(new Cliente(
                dni,
                nombre,
                apellido,
                correoElectronico,
                telefono,
                domicilio
        ));
    }

    private Mascota guardarMascota(
            String nombre,
            String especie,
            String raza,
            String sexo,
            LocalDate fechaNacimiento,
            String fotografiaObjetoKey
    ) {
        return mascotaRepository.saveAndFlush(new Mascota(
                nombre,
                especie,
                raza,
                sexo,
                fechaNacimiento,
                fotografiaObjetoKey
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
}
