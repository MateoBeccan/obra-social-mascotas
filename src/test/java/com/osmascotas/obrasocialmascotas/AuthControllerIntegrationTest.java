package com.osmascotas.obrasocialmascotas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RecuperacionContrasenaToken;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.RecuperacionContrasenaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.JwtService;
import com.osmascotas.obrasocialmascotas.seguridad.service.RecuperacionContrasenaNotifier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfiguration.class, AuthControllerIntegrationTest.NotifierTestConfiguration.class})
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.password-recovery.expiration=PT15M"
})
class AuthControllerIntegrationTest {

    private static final String IDENTIFICADOR_ACCESO = "cliente@osmascotas.com";
    private static final String CONTRASENA = "Password123!";
    private static final String CONTRASENA_NUEVA = "NuevaPassword123!";
    private static final String EMAIL_RECUPERACION = "cliente@test.local";
    private static final String TOKEN_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String MENSAJE_RECUPERACION =
            "Si la cuenta existe, se enviaron instrucciones de recuperacion.";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RecuperacionContrasenaTokenRepository recuperacionContrasenaTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private Flyway flyway;

    @Autowired
    private RecuperacionContrasenaNotifier recuperacionContrasenaNotifier;

    @BeforeEach
    void setUp() {
        reset(recuperacionContrasenaNotifier);
        recuperacionContrasenaTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void loginConCredencialesValidasDevuelveAccessToken() throws Exception {
        guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(IDENTIFICADOR_ACCESO, CONTRASENA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(nullValue())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void loginConContrasenaIncorrectaDevuelveRechazoGenerico() throws Exception {
        guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(IDENTIFICADOR_ACCESO, "incorrecta"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Credenciales invalidas"));
    }

    @Test
    void loginConUsuarioInexistenteDevuelveRechazoGenerico() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(IDENTIFICADOR_ACCESO, CONTRASENA))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Credenciales invalidas"));
    }

    @Test
    void loginConUsuarioBloqueadoDevuelveRechazoGenerico() throws Exception {
        guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.BLOQUEADO);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(IDENTIFICADOR_ACCESO, CONTRASENA))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Credenciales invalidas"));
    }

    @Test
    void loginConUsuarioInactivoDevuelveRechazoGenerico() throws Exception {
        guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.INACTIVO);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(IDENTIFICADOR_ACCESO, CONTRASENA))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Credenciales invalidas"));
    }

    @Test
    void endpointProtegidoRechazaRequestSinBearerToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegidoPermiteRequestConBearerTokenValido() throws Exception {
        String token = jwtService.generarAccessToken(UsernamePasswordAuthenticationToken.authenticated(
                IDENTIFICADOR_ACCESO,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))
        ));

        mockMvc.perform(get("/actuator/health")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void cambiarContrasenaConUsuarioAutenticadoYContrasenaActualCorrectaDevuelveNoContent() throws Exception {
        guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        String token = tokenValido(IDENTIFICADOR_ACCESO);

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cambiarContrasenaRequest(CONTRASENA, CONTRASENA_NUEVA))))
                .andExpect(status().isNoContent())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(content().string(""));
    }

    @Test
    void cambiarContrasenaActualizaHashAlmacenado() throws Exception {
        guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        String hashOriginal = buscarUsuario(IDENTIFICADOR_ACCESO).getContrasenaHash();
        String token = tokenValido(IDENTIFICADOR_ACCESO);

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cambiarContrasenaRequest(CONTRASENA, CONTRASENA_NUEVA))))
                .andExpect(status().isNoContent());

        String nuevoHash = buscarUsuario(IDENTIFICADOR_ACCESO).getContrasenaHash();
        assertThat(nuevoHash).isNotEqualTo(hashOriginal);
        assertThat(passwordEncoder.matches(CONTRASENA_NUEVA, nuevoHash)).isTrue();
        assertThat(nuevoHash).isNotEqualTo(CONTRASENA_NUEVA);
    }

    @Test
    void cambiarContrasenaConContrasenaActualIncorrectaRechazaOperacion() throws Exception {
        guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        String hashOriginal = buscarUsuario(IDENTIFICADOR_ACCESO).getContrasenaHash();
        String token = tokenValido(IDENTIFICADOR_ACCESO);

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cambiarContrasenaRequest("incorrecta", CONTRASENA_NUEVA))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Contrasena actual invalida"));

        assertThat(buscarUsuario(IDENTIFICADOR_ACCESO).getContrasenaHash()).isEqualTo(hashOriginal);
    }

    @Test
    void cambiarContrasenaSinBearerTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cambiarContrasenaRequest(CONTRASENA, CONTRASENA_NUEVA))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarContrasenaConIdentidadAutenticadaInexistenteDevuelveUnauthorizedControlado() throws Exception {
        String token = tokenValido("inexistente@osmascotas.com");

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cambiarContrasenaRequest(CONTRASENA, CONTRASENA_NUEVA))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Usuario autenticado no encontrado"));
    }

    @Test
    void postLoginSigueSiendoPublicoSinBearerToken() throws Exception {
        guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(IDENTIFICADOR_ACCESO, CONTRASENA))))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPasswordEsPublicoYRespondeAcceptedParaUsuarioExistente() throws Exception {
        guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotPasswordRequest(IDENTIFICADOR_ACCESO))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_RECUPERACION))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void forgotPasswordConUsuarioInexistenteRespondeIgualYNoCreaTokenNiNotifica() throws Exception {
        MvcResult respuestaInexistente = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotPasswordRequest("inexistente@osmascotas.com"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_RECUPERACION))
                .andReturn();

        guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);

        MvcResult respuestaExistente = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotPasswordRequest(IDENTIFICADOR_ACCESO))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_RECUPERACION))
                .andReturn();

        assertThat(respuestaInexistente.getResponse().getContentAsString())
                .isEqualTo(respuestaExistente.getResponse().getContentAsString());
        assertThat(recuperacionContrasenaTokenRepository.findAll()).hasSize(1);
    }

    @Test
    void forgotPasswordConUsuarioInexistenteNoCreaTokenNiInvocaNotifier() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotPasswordRequest("inexistente@osmascotas.com"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_RECUPERACION));

        assertThat(recuperacionContrasenaTokenRepository.findAll()).isEmpty();
        verifyNoInteractions(recuperacionContrasenaNotifier);
    }

    @Test
    void forgotPasswordConUsuarioExistenteCreaTokenHasheadoYNotificaTokenOriginal() throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);

        MvcResult result = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotPasswordRequest(IDENTIFICADOR_ACCESO))))
                .andExpect(status().isAccepted())
                .andReturn();

        List<RecuperacionContrasenaToken> tokens = recuperacionContrasenaTokenRepository.findAll();
        assertThat(tokens).hasSize(1);

        RecuperacionContrasenaToken tokenPersistido = tokens.getFirst();
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenOriginalCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> fechaExpiracionCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(recuperacionContrasenaNotifier).notificar(
                emailCaptor.capture(),
                tokenOriginalCaptor.capture(),
                fechaExpiracionCaptor.capture()
        );

        String tokenOriginal = tokenOriginalCaptor.getValue();
        String tokenHash = tokenPersistido.getTokenHash();

        assertThat(tokenPersistido.getUsuario().getId()).isEqualTo(usuario.getId());
        assertThat(tokenHash).hasSize(64);
        assertThat(tokenOriginal).isNotEqualTo(tokenHash);
        assertThat(sha256Hex(tokenOriginal)).isEqualTo(tokenHash);
        assertThat(Duration.between(tokenPersistido.getFechaCreacion(), tokenPersistido.getFechaExpiracion()))
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(emailCaptor.getValue()).isEqualTo(EMAIL_RECUPERACION);
        assertThat(Duration.between(fechaExpiracionCaptor.getValue(), tokenPersistido.getFechaExpiracion()).abs())
                .isLessThan(Duration.ofMillis(1));
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(tokenOriginal)
                .doesNotContain(tokenHash)
                .doesNotContain(EMAIL_RECUPERACION);
    }

    @Test
    void forgotPasswordSegundaSolicitudInvalidaTokenAnteriorYDejaSoloUnoActivo() throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotPasswordRequest(IDENTIFICADOR_ACCESO))))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotPasswordRequest(IDENTIFICADOR_ACCESO))))
                .andExpect(status().isAccepted());

        List<RecuperacionContrasenaToken> tokens = recuperacionContrasenaTokenRepository
                .findByUsuario_IdOrderByIdAsc(usuario.getId());

        assertThat(tokens).hasSize(2);
        assertThat(tokens.get(0).getFechaInvalidacion()).isNotNull();
        assertThat(tokens.get(1).getFechaInvalidacion()).isNull();
        assertThat(tokens.get(1).getFechaUso()).isNull();
        assertThat(recuperacionContrasenaTokenRepository
                .countByUsuario_IdAndFechaUsoIsNullAndFechaInvalidacionIsNull(usuario.getId()))
                .isEqualTo(1);
        verify(recuperacionContrasenaNotifier, times(2)).notificar(any(), any(), any());
    }

    @Test
    void flywayTieneAplicadaLaMigracionV002() {
        assertThat(flyway.info().current().getVersion().getVersion()).isGreaterThanOrEqualTo("002");
        assertThat(flyway.info().applied())
                .anySatisfy(migration -> assertThat(migration.getVersion().getVersion()).isEqualTo("002"));
    }

    @Test
    void persisteYRecuperaTokenDeRecuperacionContrasena() {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        Instant fechaCreacion = Instant.parse("2026-09-05T12:00:00Z");
        Instant fechaExpiracion = Instant.parse("2026-09-05T12:15:00Z");
        RecuperacionContrasenaToken token = new RecuperacionContrasenaToken(
                usuario,
                TOKEN_HASH,
                fechaCreacion,
                fechaExpiracion
        );

        RecuperacionContrasenaToken tokenGuardado = recuperacionContrasenaTokenRepository.saveAndFlush(token);

        RecuperacionContrasenaToken tokenRecuperado = recuperacionContrasenaTokenRepository
                .findById(tokenGuardado.getId())
                .orElseThrow();

        assertThat(tokenRecuperado.getUsuario().getId()).isEqualTo(usuario.getId());
        assertThat(tokenRecuperado.getTokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(tokenRecuperado.getFechaCreacion()).isEqualTo(fechaCreacion);
        assertThat(tokenRecuperado.getFechaExpiracion()).isEqualTo(fechaExpiracion);
    }

    private Usuario guardarUsuario(
            String identificadorAcceso,
            String contrasena,
            RolUsuario rolUsuario,
            EstadoUsuario estadoUsuario
    ) {
        Usuario usuario = new Usuario(
                identificadorAcceso,
                passwordEncoder.encode(contrasena),
                rolUsuario,
                estadoUsuario,
                EMAIL_RECUPERACION
        );

        return usuarioRepository.save(usuario);
    }

    private Usuario buscarUsuario(String identificadorAcceso) {
        return usuarioRepository.buscarPorIdentificadorAcceso(identificadorAcceso).orElseThrow();
    }

    private String tokenValido(String identificadorAcceso) {
        return jwtService.generarAccessToken(UsernamePasswordAuthenticationToken.authenticated(
                identificadorAcceso,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))
        ));
    }

    private Map<String, String> loginRequest(String identificadorAcceso, String contrasena) {
        return Map.of(
                "identificadorAcceso", identificadorAcceso,
                "contrasena", contrasena
        );
    }

    private Map<String, String> cambiarContrasenaRequest(String contrasenaActual, String contrasenaNueva) {
        return Map.of(
                "contrasenaActual", contrasenaActual,
                "contrasenaNueva", contrasenaNueva
        );
    }

    private Map<String, String> forgotPasswordRequest(String identificadorAcceso) {
        return Map.of(
                "identificadorAcceso", identificadorAcceso
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
        RecuperacionContrasenaNotifier recuperacionContrasenaNotifier() {
            return mock(RecuperacionContrasenaNotifier.class);
        }
    }
}
