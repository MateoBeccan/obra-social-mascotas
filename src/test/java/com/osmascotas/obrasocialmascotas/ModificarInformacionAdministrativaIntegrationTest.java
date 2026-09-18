package com.osmascotas.obrasocialmascotas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmascotas.obrasocialmascotas.auditoria.domain.RegistroAuditoria;
import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Mascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ActualizarClienteAdministrativoRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ActualizarMascotaAdministrativaRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.ClienteRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.MascotaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.TitularidadMascotaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ModificarClienteAdministradorService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ModificarMascotaAdministradorService;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.ActivacionCuentaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.RecuperacionContrasenaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.JwtService;
import com.osmascotas.obrasocialmascotas.storage.ObjectStorageService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({
        TestcontainersConfiguration.class,
        ModificarInformacionAdministrativaIntegrationTest.AuditoriaFailureConfiguration.class,
        ModificarInformacionAdministrativaIntegrationTest.ClockTestConfiguration.class
})
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.password-recovery.expiration=PT15M",
        "app.security.account-activation.expiration=PT24H"
})
class ModificarInformacionAdministrativaIntegrationTest {

    private static final String CONTRASENA = "Password123!";
    private static final String ADMIN_IDENTIFICADOR = "admin-rf0208@osmascotas.com";
    private static final Instant FECHA_HORA = Instant.parse("2026-09-17T12:00:00Z");

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
    private ModificarClienteAdministradorService modificarClienteAdministradorService;

    @Autowired
    private ModificarMascotaAdministradorService modificarMascotaAdministradorService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ObjectStorageService objectStorageService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        AuditoriaFailureConfiguration.fallarAuditoria.set(false);
        reset(objectStorageService);
        titularidadMascotaRepository.deleteAll();
        mascotaRepository.deleteAll();
        clienteRepository.deleteAll();
        registroAuditoriaRepository.deleteAll();
        activacionCuentaTokenRepository.deleteAll();
        recuperacionContrasenaTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void administradorModificaClienteNormalizaAuditaYConservaUsuarioRelaciones() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Usuario cuentaCliente = guardarUsuario("cuenta-cliente@test.local", RolUsuario.CLIENTE, EstadoUsuario.BLOQUEADO);
        Cliente cliente = guardarCliente(cuentaCliente, "123456789", "Ana", "Gomez", "ana@test.local", "1111", "Calle A");
        Mascota mascota = guardarMascota("Luna", "Canino", null, null, null, "mascotas/1/fotografias/original.jpg");
        TitularidadMascota titularidad = guardarTitularidad(mascota, cliente, LocalDate.of(2025, 1, 1), null);

        mockMvc.perform(put("/api/admin/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteRequest(
                                " 987654321 ",
                                " Beatriz ",
                                " Lopez ",
                                " bea@test.local ",
                                " 2222 ",
                                " Calle B "
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cliente.getId()))
                .andExpect(jsonPath("$.dni").value("987654321"))
                .andExpect(jsonPath("$.nombre").value("Beatriz"))
                .andExpect(jsonPath("$.apellido").value("Lopez"))
                .andExpect(jsonPath("$.correoElectronico").value("bea@test.local"))
                .andExpect(jsonPath("$.telefono").value("2222"))
                .andExpect(jsonPath("$.domicilio").value("Calle B"))
                .andExpect(jsonPath("$.usuarioId").value(cuentaCliente.getId()));

        Cliente actualizado = clienteRepository.findById(cliente.getId()).orElseThrow();
        assertCliente(actualizado, "987654321", "Beatriz", "Lopez", "bea@test.local", "2222", "Calle B");
        Usuario usuarioSinCambios = usuarioRepository.findById(cuentaCliente.getId()).orElseThrow();
        assertThat(usuarioSinCambios.getIdentificadorAcceso()).isEqualTo("cuenta-cliente@test.local");
        assertThat(usuarioSinCambios.getEmailRecuperacion()).isEqualTo("cuenta-cliente@test.local");
        assertThat(usuarioSinCambios.getRolUsuario()).isEqualTo(RolUsuario.CLIENTE);
        assertThat(usuarioSinCambios.getEstadoUsuario()).isEqualTo(EstadoUsuario.BLOQUEADO);

        Mascota mascotaSinCambios = mascotaRepository.findById(mascota.getId()).orElseThrow();
        assertThat(mascotaSinCambios.getNombre()).isEqualTo("Luna");
        assertThat(mascotaSinCambios.getFotografiaObjetoKey()).isEqualTo("mascotas/1/fotografias/original.jpg");
        TitularidadMascota titularidadSinCambios = titularidadMascotaRepository.findById(titularidad.getId()).orElseThrow();
        assertThat(titularidadSinCambios.getCliente().getId()).isEqualTo(cliente.getId());
        assertThat(titularidadSinCambios.getMascota().getId()).isEqualTo(mascota.getId());
        assertThat(titularidadSinCambios.getFechaDesde()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(titularidadSinCambios.getFechaHasta()).isNull();

        RegistroAuditoria auditoria = unicoRegistro();
        assertThat(auditoria.getOperacion()).isEqualTo("MODIFICAR_CLIENTE_ADMINISTRATIVO");
        assertThat(auditoria.getEntidadAfectada()).isEqualTo("CLIENTE");
        assertThat(auditoria.getIdentificadorRegistroAfectado()).isEqualTo(cliente.getId().toString());
        assertThat(auditoria.getDetalleCambio())
                .isEqualTo("camposModificados=dni,nombre,apellido,correoElectronico,telefono,domicilio")
                .doesNotContain("987654321")
                .doesNotContain("Beatriz")
                .doesNotContain("bea@test.local");
    }

    @Test
    void administradorPuedeDejarOpcionalesNullMantenerDniPropioYUsarDniNoOchoDigitos() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", "ana@test.local", "1111", "Calle A");

        mockMvc.perform(put("/api/admin/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteRequest(
                                "123456789",
                                "Ana",
                                "Gomez",
                                null,
                                null,
                                null
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dni").value("123456789"))
                .andExpect(jsonPath("$.correoElectronico").value(nullValue()))
                .andExpect(jsonPath("$.telefono").value(nullValue()))
                .andExpect(jsonPath("$.domicilio").value(nullValue()));

        Cliente actualizado = clienteRepository.findById(cliente.getId()).orElseThrow();
        assertCliente(actualizado, "123456789", "Ana", "Gomez", null, null, null);
        assertThat(unicoRegistro().getDetalleCambio())
                .isEqualTo("camposModificados=correoElectronico,telefono,domicilio");
    }

    @Test
    void modificarClienteInexistenteDevuelveNotFound() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);

        mockMvc.perform(put("/api/admin/clientes/{clienteId}", 999_999L)
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteRequestValido())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe el Cliente indicado"));

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void modificarClienteConDniDeOtroClienteDevuelveConflictYConservaDatosSinAuditoria() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", "ana@test.local", "1111", "Calle A");
        guardarCliente(null, "987654321", "Bruno", "Diaz", null, null, null);

        mockMvc.perform(put("/api/admin/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteRequest(
                                "987654321",
                                "Cambio",
                                "Cambio",
                                "cambio@test.local",
                                "9999",
                                "Calle X"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya existe un Cliente con el mismo DNI"));

        Cliente sinCambios = clienteRepository.findById(cliente.getId()).orElseThrow();
        assertCliente(sinCambios, "123456789", "Ana", "Gomez", "ana@test.local", "1111", "Calle A");
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "dni, ' '",
            "nombre, ' '",
            "apellido, ' '",
            "correoElectronico, correo-invalido",
            "correoElectronico, ' '",
            "telefono, ' '",
            "domicilio, ' '"
    })
    void modificarClienteConRequestInvalidoDevuelveBadRequest(String campo, String valor) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", "ana@test.local", "1111", "Calle A");
        Map<String, String> request = clienteRequestValido();
        request.put(campo, valor);

        mockMvc.perform(put("/api/admin/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertCliente(clienteRepository.findById(cliente.getId()).orElseThrow(), "123456789", "Ana", "Gomez", "ana@test.local", "1111", "Calle A");
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "dni, 51",
            "nombre, 121",
            "apellido, 121",
            "correoElectronico, 255",
            "telefono, 51",
            "domicilio, 501"
    })
    void modificarClienteConLongitudesExcedidasDevuelveBadRequest(String campo, int largo) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", "ana@test.local", "1111", "Calle A");
        Map<String, String> request = clienteRequestValido();
        request.put(campo, "x".repeat(largo));

        mockMvc.perform(put("/api/admin/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "id", "usuarioId", "usuario", "rol", "estado", "password", "contrasena", "mascotaId", "titularidadId", "afiliacionId"
    })
    void modificarClienteConCampoJsonProhibidoDevuelveBadRequest(String campoNoPermitido) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", "ana@test.local", "1111", "Calle A");
        Map<String, Object> request = new HashMap<>(clienteRequestValido());
        request.put(campoNoPermitido, "valor-no-permitido");

        mockMvc.perform(put("/api/admin/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"CLIENTE", "VETERINARIO"})
    void modificarClienteConRolNoAdministradorDevuelveForbidden(RolUsuario rolUsuario) throws Exception {
        Usuario usuario = guardarUsuario(rolUsuario.name().toLowerCase() + "-rf0208@test.local", rolUsuario, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", null, null, null);

        mockMvc.perform(put("/api/admin/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteRequestValido())))
                .andExpect(status().isForbidden());

        assertCliente(clienteRepository.findById(cliente.getId()).orElseThrow(), "123456789", "Ana", "Gomez", null, null, null);
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void modificarClienteSinJwtDevuelveUnauthorized() throws Exception {
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", null, null, null);

        mockMvc.perform(put("/api/admin/clientes/{clienteId}", cliente.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteRequestValido())))
                .andExpect(status().isUnauthorized());

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void modificarClienteSinCambioEfectivoDevuelveOkSinAuditoria() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", "ana@test.local", "1111", "Calle A");

        mockMvc.perform(put("/api/admin/clientes/{clienteId}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteRequest(
                                "123456789",
                                "Ana",
                                "Gomez",
                                "ana@test.local",
                                "1111",
                                "Calle A"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dni").value("123456789"));

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void siFallaAuditoriaRollbackRevierteCliente() {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", "ana@test.local", "1111", "Calle A");
        autenticar(administrador);
        AuditoriaFailureConfiguration.fallarAuditoria.set(true);

        assertThatThrownBy(() -> modificarClienteAdministradorService.modificar(
                cliente.getId(),
                new ActualizarClienteAdministrativoRequest(
                        "987654321",
                        "Beatriz",
                        "Lopez",
                        "bea@test.local",
                        "2222",
                        "Calle B"
                )
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Fallo auditoria");

        assertCliente(clienteRepository.findById(cliente.getId()).orElseThrow(), "123456789", "Ana", "Gomez", "ana@test.local", "1111", "Calle A");
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void administradorModificaMascotaConservaTitularidadFotografiaNoUsaStorageYAudita() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", "ana@test.local", "1111", "Calle A");
        Mascota mascota = guardarMascota("Luna", "Canino", "Mestiza", "Macho", LocalDate.of(2020, 1, 1), "mascotas/10/fotografias/abc.jpg");
        guardarTitularidad(mascota, cliente, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
        TitularidadMascota vigente = guardarTitularidad(mascota, cliente, LocalDate.of(2025, 1, 1), null);

        mockMvc.perform(put("/api/admin/mascotas/{mascotaId}", mascota.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mascotaRequest(
                                " Mora ",
                                " Felino ",
                                " Siames ",
                                " Hembra ",
                                "2022-02-02"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mascota.getId()))
                .andExpect(jsonPath("$.nombre").value("Mora"))
                .andExpect(jsonPath("$.especie").value("Felino"))
                .andExpect(jsonPath("$.raza").value("Siames"))
                .andExpect(jsonPath("$.sexo").value("Hembra"))
                .andExpect(jsonPath("$.fechaNacimiento").value("2022-02-02"))
                .andExpect(jsonPath("$.titular.clienteId").value(cliente.getId()))
                .andExpect(jsonPath("$.titularidadActual.titularidadMascotaId").value(vigente.getId()))
                .andExpect(jsonPath("$.titularidadActual.fechaDesde").value("2025-01-01"))
                .andExpect(jsonPath("$.fotografiaObjetoKey").doesNotExist());

        Mascota actualizada = mascotaRepository.findById(mascota.getId()).orElseThrow();
        assertMascota(actualizada, "Mora", "Felino", "Siames", "Hembra", LocalDate.of(2022, 2, 2));
        assertThat(actualizada.getFotografiaObjetoKey()).isEqualTo("mascotas/10/fotografias/abc.jpg");
        assertThat(titularidadMascotaRepository.findAll()).hasSize(2);
        TitularidadMascota titularidadSinCambios = titularidadMascotaRepository.findById(vigente.getId()).orElseThrow();
        assertThat(titularidadSinCambios.getCliente().getId()).isEqualTo(cliente.getId());
        assertThat(titularidadSinCambios.getFechaDesde()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(titularidadSinCambios.getFechaHasta()).isNull();
        assertThat(titularidadSinCambios.getMotivoCambio()).isNull();
        assertThat(titularidadSinCambios.getUsuarioResponsable()).isNull();
        verifyNoInteractions(objectStorageService);

        RegistroAuditoria auditoria = unicoRegistro();
        assertThat(auditoria.getOperacion()).isEqualTo("MODIFICAR_MASCOTA_ADMINISTRATIVA");
        assertThat(auditoria.getEntidadAfectada()).isEqualTo("MASCOTA");
        assertThat(auditoria.getIdentificadorRegistroAfectado()).isEqualTo(mascota.getId().toString());
        assertThat(auditoria.getDetalleCambio())
                .isEqualTo("camposModificados=nombre,especie,raza,sexo,fechaNacimiento")
                .doesNotContain("Mora")
                .doesNotContain("Felino")
                .doesNotContain("Siames");
    }

    @Test
    void modificarMascotaPermiteOpcionalesNullYNombreRepetido() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", null, null, null);
        guardarMascotaConTitularidad(cliente, "Luna", "Canino");
        Mascota mascota = guardarMascotaConTitularidad(cliente, "Mora", "Felino");

        mockMvc.perform(put("/api/admin/mascotas/{mascotaId}", mascota.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mascotaRequest(
                                "Luna",
                                "Felino",
                                null,
                                null,
                                null
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Luna"))
                .andExpect(jsonPath("$.raza").value(nullValue()))
                .andExpect(jsonPath("$.sexo").value(nullValue()))
                .andExpect(jsonPath("$.fechaNacimiento").value(nullValue()));

        Mascota actualizada = mascotaRepository.findById(mascota.getId()).orElseThrow();
        assertMascota(actualizada, "Luna", "Felino", null, null, null);
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void modificarMascotaInexistenteDevuelveNotFound() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);

        mockMvc.perform(put("/api/admin/mascotas/{mascotaId}", 999_999L)
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mascotaRequestValido())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No se encontro la Mascota"));

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "nombre, ' '",
            "especie, ' '",
            "raza, ' '",
            "sexo, ' '"
    })
    void modificarMascotaConRequestInvalidoDevuelveBadRequest(String campo, String valor) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", null, null, null);
        Mascota mascota = guardarMascotaConTitularidad(cliente, "Luna", "Canino");
        Map<String, Object> request = mascotaRequestValido();
        request.put(campo, valor);

        mockMvc.perform(put("/api/admin/mascotas/{mascotaId}", mascota.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertMascota(mascotaRepository.findById(mascota.getId()).orElseThrow(), "Luna", "Canino", null, null, null);
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "nombre, 121",
            "especie, 81",
            "raza, 121",
            "sexo, 31"
    })
    void modificarMascotaConLongitudesExcedidasDevuelveBadRequest(String campo, int largo) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", null, null, null);
        Mascota mascota = guardarMascotaConTitularidad(cliente, "Luna", "Canino");
        Map<String, Object> request = mascotaRequestValido();
        request.put(campo, "x".repeat(largo));

        mockMvc.perform(put("/api/admin/mascotas/{mascotaId}", mascota.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "id", "clienteId", "titularidadId", "titularActualId", "fechaDesde", "fechaHasta",
            "motivoCambio", "usuarioResponsableId", "fotografiaObjetoKey", "afiliacionId",
            "qr", "microchip", "scanner"
    })
    void modificarMascotaConCampoJsonProhibidoDevuelveBadRequest(String campoNoPermitido) throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", null, null, null);
        Mascota mascota = guardarMascotaConTitularidad(cliente, "Luna", "Canino");
        Map<String, Object> request = mascotaRequestValido();
        request.put(campoNoPermitido, "valor-no-permitido");

        mockMvc.perform(put("/api/admin/mascotas/{mascotaId}", mascota.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"CLIENTE", "VETERINARIO"})
    void modificarMascotaConRolNoAdministradorDevuelveForbidden(RolUsuario rolUsuario) throws Exception {
        Usuario usuario = guardarUsuario(rolUsuario.name().toLowerCase() + "-mascota-rf0208@test.local", rolUsuario, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", null, null, null);
        Mascota mascota = guardarMascotaConTitularidad(cliente, "Luna", "Canino");

        mockMvc.perform(put("/api/admin/mascotas/{mascotaId}", mascota.getId())
                        .header("Authorization", "Bearer " + tokenValido(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mascotaRequestValido())))
                .andExpect(status().isForbidden());

        assertMascota(mascotaRepository.findById(mascota.getId()).orElseThrow(), "Luna", "Canino", null, null, null);
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void modificarMascotaSinJwtDevuelveUnauthorized() throws Exception {
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", null, null, null);
        Mascota mascota = guardarMascotaConTitularidad(cliente, "Luna", "Canino");

        mockMvc.perform(put("/api/admin/mascotas/{mascotaId}", mascota.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mascotaRequestValido())))
                .andExpect(status().isUnauthorized());

        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void modificarMascotaSinCambioEfectivoDevuelveOkSinAuditoria() throws Exception {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", null, null, null);
        Mascota mascota = guardarMascota("Luna", "Canino", "Mestiza", "Hembra", LocalDate.of(2020, 1, 1), "mascotas/10/fotografias/abc.jpg");
        guardarTitularidad(mascota, cliente, LocalDate.of(2025, 1, 1), null);

        mockMvc.perform(put("/api/admin/mascotas/{mascotaId}", mascota.getId())
                        .header("Authorization", "Bearer " + tokenValido(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mascotaRequest(
                                "Luna",
                                "Canino",
                                "Mestiza",
                                "Hembra",
                                "2020-01-01"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Luna"));

        assertThat(registroAuditoriaRepository.count()).isZero();
        assertThat(mascotaRepository.findById(mascota.getId()).orElseThrow().getFotografiaObjetoKey())
                .isEqualTo("mascotas/10/fotografias/abc.jpg");
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void siFallaAuditoriaRollbackRevierteMascotaFotografiaYTitularidad() {
        Usuario administrador = guardarUsuario(ADMIN_IDENTIFICADOR, RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVO);
        Cliente cliente = guardarCliente(null, "123456789", "Ana", "Gomez", null, null, null);
        Mascota mascota = guardarMascota("Luna", "Canino", "Mestiza", "Hembra", LocalDate.of(2020, 1, 1), "mascotas/10/fotografias/abc.jpg");
        TitularidadMascota titularidad = guardarTitularidad(mascota, cliente, LocalDate.of(2025, 1, 1), null);
        autenticar(administrador);
        AuditoriaFailureConfiguration.fallarAuditoria.set(true);

        assertThatThrownBy(() -> modificarMascotaAdministradorService.modificar(
                mascota.getId(),
                new ActualizarMascotaAdministrativaRequest(
                        "Mora",
                        "Felino",
                        "Siames",
                        "Hembra",
                        LocalDate.of(2022, 2, 2)
                )
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Fallo auditoria");

        Mascota sinCambios = mascotaRepository.findById(mascota.getId()).orElseThrow();
        assertMascota(sinCambios, "Luna", "Canino", "Mestiza", "Hembra", LocalDate.of(2020, 1, 1));
        assertThat(sinCambios.getFotografiaObjetoKey()).isEqualTo("mascotas/10/fotografias/abc.jpg");
        TitularidadMascota titularidadSinCambios = titularidadMascotaRepository.findById(titularidad.getId()).orElseThrow();
        assertThat(titularidadSinCambios.getCliente().getId()).isEqualTo(cliente.getId());
        assertThat(titularidadSinCambios.getFechaDesde()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(titularidadSinCambios.getFechaHasta()).isNull();
        assertThat(registroAuditoriaRepository.count()).isZero();
        verifyNoInteractions(objectStorageService);
    }

    private Usuario guardarUsuario(String identificadorAcceso, RolUsuario rolUsuario, EstadoUsuario estadoUsuario) {
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

    private Mascota guardarMascotaConTitularidad(Cliente cliente, String nombre, String especie) {
        Mascota mascota = guardarMascota(nombre, especie, null, null, null, null);
        guardarTitularidad(mascota, cliente, LocalDate.of(2025, 1, 1), null);
        return mascota;
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

    private void autenticar(Usuario usuario) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                usuario.getIdentificadorAcceso(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRolUsuario().name()))
        ));
    }

    private Map<String, String> clienteRequestValido() {
        return clienteRequest("987654321", "Beatriz", "Lopez", "bea@test.local", "2222", "Calle B");
    }

    private Map<String, String> clienteRequest(
            String dni,
            String nombre,
            String apellido,
            String correoElectronico,
            String telefono,
            String domicilio
    ) {
        Map<String, String> request = new HashMap<>();
        request.put("dni", dni);
        request.put("nombre", nombre);
        request.put("apellido", apellido);
        request.put("correoElectronico", correoElectronico);
        request.put("telefono", telefono);
        request.put("domicilio", domicilio);
        return request;
    }

    private Map<String, Object> mascotaRequestValido() {
        return mascotaRequest("Mora", "Felino", "Siames", "Hembra", "2022-02-02");
    }

    private Map<String, Object> mascotaRequest(
            String nombre,
            String especie,
            String raza,
            String sexo,
            String fechaNacimiento
    ) {
        Map<String, Object> request = new HashMap<>();
        request.put("nombre", nombre);
        request.put("especie", especie);
        request.put("raza", raza);
        request.put("sexo", sexo);
        request.put("fechaNacimiento", fechaNacimiento);
        return request;
    }

    private RegistroAuditoria unicoRegistro() {
        List<RegistroAuditoria> registros = registroAuditoriaRepository.findAll();
        assertThat(registros).hasSize(1);
        return registros.getFirst();
    }

    private void assertCliente(
            Cliente cliente,
            String dni,
            String nombre,
            String apellido,
            String correoElectronico,
            String telefono,
            String domicilio
    ) {
        assertThat(cliente.getDni()).isEqualTo(dni);
        assertThat(cliente.getNombre()).isEqualTo(nombre);
        assertThat(cliente.getApellido()).isEqualTo(apellido);
        assertThat(cliente.getCorreoElectronico()).isEqualTo(correoElectronico);
        assertThat(cliente.getTelefono()).isEqualTo(telefono);
        assertThat(cliente.getDomicilio()).isEqualTo(domicilio);
    }

    private void assertMascota(
            Mascota mascota,
            String nombre,
            String especie,
            String raza,
            String sexo,
            LocalDate fechaNacimiento
    ) {
        assertThat(mascota.getNombre()).isEqualTo(nombre);
        assertThat(mascota.getEspecie()).isEqualTo(especie);
        assertThat(mascota.getRaza()).isEqualTo(raza);
        assertThat(mascota.getSexo()).isEqualTo(sexo);
        assertThat(mascota.getFechaNacimiento()).isEqualTo(fechaNacimiento);
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
