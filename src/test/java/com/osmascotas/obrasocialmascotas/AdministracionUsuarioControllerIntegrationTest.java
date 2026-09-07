package com.osmascotas.obrasocialmascotas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmascotas.obrasocialmascotas.auditoria.domain.OrigenOperacion;
import com.osmascotas.obrasocialmascotas.auditoria.domain.RegistroAuditoria;
import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.OperacionEstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.RecuperacionContrasenaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.AdministracionUsuarioService;
import com.osmascotas.obrasocialmascotas.seguridad.service.JwtService;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.password-recovery.expiration=PT15M"
})
class AdministracionUsuarioControllerIntegrationTest {

    private static final String CONTRASENA = "Password123!";
    private static final String ADMIN_IDENTIFICADOR = "admin@osmascotas.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

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
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        registroAuditoriaRepository.deleteAll();
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

    private Map<String, String> loginRequest(String identificadorAcceso, String contrasena) {
        return Map.of(
                "identificadorAcceso", identificadorAcceso,
                "contrasena", contrasena
        );
    }
}
