package com.osmascotas.obrasocialmascotas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmascotas.obrasocialmascotas.auditoria.domain.OrigenOperacion;
import com.osmascotas.obrasocialmascotas.auditoria.domain.RegistroAuditoria;
import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.seguridad.domain.ActivacionCuentaToken;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RecuperacionContrasenaToken;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.ActivacionCuentaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.RecuperacionContrasenaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.ActivacionCuentaService;
import com.osmascotas.obrasocialmascotas.seguridad.service.JwtService;
import com.osmascotas.obrasocialmascotas.seguridad.service.RecuperacionContrasenaNotifier;
import jakarta.persistence.LockModeType;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

@Import({
        TestcontainersConfiguration.class,
        AuthControllerIntegrationTest.NotifierTestConfiguration.class,
        AuthControllerIntegrationTest.ClockTestConfiguration.class
})
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
    private static final String CONTRASENA_RESTABLECIDA = "Restablecida123!";
    private static final String EMAIL_RECUPERACION = "cliente@test.local";
    private static final String TOKEN_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String TOKEN_ORIGINAL_PRUEBA = "token-original-para-prueba";
    private static final String TOKEN_ACTIVACION_ORIGINAL = "token-original-activacion";
    private static final Instant FECHA_HORA = Instant.parse("2026-09-07T12:00:00Z");
    private static final String MENSAJE_RECUPERACION =
            "Si la cuenta existe, se enviaron instrucciones de recuperacion.";
    private static final String MENSAJE_TOKEN_INVALIDO = "Token de recuperacion invalido o expirado";
    private static final String MENSAJE_TOKEN_ACTIVACION_INVALIDO = "Token de activacion invalido o expirado";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RecuperacionContrasenaTokenRepository recuperacionContrasenaTokenRepository;

    @Autowired
    private ActivacionCuentaTokenRepository activacionCuentaTokenRepository;

    @Autowired
    private RegistroAuditoriaRepository registroAuditoriaRepository;

    @Autowired
    private ActivacionCuentaService activacionCuentaService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecuperacionContrasenaNotifier recuperacionContrasenaNotifier;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        reset(recuperacionContrasenaNotifier);
        registroAuditoriaRepository.deleteAll();
        activacionCuentaTokenRepository.deleteAll();
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
    void loginGeneraTokenConSubjectYAuthorityDelRolParaCadaTipoDeUsuario() throws Exception {
        for (RolUsuario rolUsuario : RolUsuario.values()) {
            String identificadorAcceso = rolUsuario.name().toLowerCase() + "@osmascotas.com";
            guardarUsuario(
                    identificadorAcceso,
                    CONTRASENA,
                    rolUsuario,
                    EstadoUsuario.ACTIVO,
                    rolUsuario.name().toLowerCase() + "@test.local"
            );

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest(identificadorAcceso, CONTRASENA))))
                    .andExpect(status().isOk())
                    .andReturn();

            String accessToken = objectMapper
                    .readTree(result.getResponse().getContentAsString())
                    .get("accessToken")
                    .asText();

            assertThat(jwtService.extraerSubject(accessToken)).isEqualTo(identificadorAcceso);
            assertThat(jwtService.extraerAuthorities(accessToken)).containsExactly("ROLE_" + rolUsuario.name());
        }
    }

    @Test
    void endpointProtegidoRechazaRequestSinBearerToken() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegidoPermiteRequestConBearerTokenValido() throws Exception {
        String token = jwtService.generarAccessToken(UsernamePasswordAuthenticationToken.authenticated(
                IDENTIFICADOR_ACCESO,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))
        ));

        mockMvc.perform(get("/actuator/info")
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
    void cambiarContrasenaSiguePermitidoParaCualquierRolAutenticado() throws Exception {
        for (RolUsuario rolUsuario : RolUsuario.values()) {
            String identificadorAcceso = rolUsuario.name().toLowerCase() + "@osmascotas.com";
            guardarUsuario(
                    identificadorAcceso,
                    CONTRASENA,
                    rolUsuario,
                    EstadoUsuario.ACTIVO,
                    rolUsuario.name().toLowerCase() + "@test.local"
            );
            String token = tokenValido(identificadorAcceso, rolUsuario);

            mockMvc.perform(post("/api/auth/change-password")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cambiarContrasenaRequest(
                                    CONTRASENA,
                                    CONTRASENA_NUEVA
                            ))))
                    .andExpect(status().isNoContent());

            assertThat(passwordEncoder.matches(CONTRASENA_NUEVA, buscarUsuario(identificadorAcceso).getContrasenaHash()))
                    .isTrue();
        }
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
    void resetPasswordSigueSiendoPublicoSinBearerToken() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetPasswordRequest(
                                "token-inexistente",
                                CONTRASENA_RESTABLECIDA
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_INVALIDO));
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
    void resetPasswordEsPublicoYConTokenValidoDevuelveNoContent() throws Exception {
        guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        String tokenOriginal = solicitarRecuperacionYCapturarToken(IDENTIFICADOR_ACCESO);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetPasswordRequest(
                                tokenOriginal,
                                CONTRASENA_RESTABLECIDA
                        ))))
                .andExpect(status().isNoContent())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(content().string(""));
    }

    @Test
    void resetPasswordConTokenValidoCambiaHashYMarcaTokenUsado() throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        String hashOriginal = usuario.getContrasenaHash();
        String tokenOriginal = solicitarRecuperacionYCapturarToken(IDENTIFICADOR_ACCESO);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetPasswordRequest(
                                tokenOriginal,
                                CONTRASENA_RESTABLECIDA
                        ))))
                .andExpect(status().isNoContent());

        Usuario usuarioActualizado = buscarUsuario(IDENTIFICADOR_ACCESO);
        RecuperacionContrasenaToken tokenUsado = recuperacionContrasenaTokenRepository.findAll().getFirst();

        assertThat(usuarioActualizado.getId()).isEqualTo(usuario.getId());
        assertThat(usuarioActualizado.getContrasenaHash()).isNotEqualTo(hashOriginal);
        assertThat(passwordEncoder.matches(CONTRASENA_RESTABLECIDA, usuarioActualizado.getContrasenaHash())).isTrue();
        assertThat(passwordEncoder.matches(CONTRASENA, usuarioActualizado.getContrasenaHash())).isFalse();
        assertThat(usuarioActualizado.getEstadoUsuario()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(usuarioActualizado.getRolUsuario()).isEqualTo(RolUsuario.CLIENTE);
        assertThat(usuarioActualizado.getEmailRecuperacion()).isEqualTo(EMAIL_RECUPERACION);
        assertThat(tokenUsado.getFechaUso()).isNotNull();
        assertThat(tokenUsado.getFechaInvalidacion()).isNull();
    }

    @Test
    void resetPasswordConMismoTokenPorSegundaVezDevuelveBadRequestYNoVuelveAModificarContrasena() throws Exception {
        guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        String tokenOriginal = solicitarRecuperacionYCapturarToken(IDENTIFICADOR_ACCESO);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetPasswordRequest(
                                tokenOriginal,
                                CONTRASENA_RESTABLECIDA
                        ))))
                .andExpect(status().isNoContent());

        String hashLuegoDelPrimerUso = buscarUsuario(IDENTIFICADOR_ACCESO).getContrasenaHash();

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetPasswordRequest(
                                tokenOriginal,
                                "OtraContrasena123!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_INVALIDO));

        String hashLuegoDelSegundoUso = buscarUsuario(IDENTIFICADOR_ACCESO).getContrasenaHash();
        assertThat(hashLuegoDelSegundoUso).isEqualTo(hashLuegoDelPrimerUso);
        assertThat(passwordEncoder.matches("OtraContrasena123!", hashLuegoDelSegundoUso)).isFalse();
    }

    @Test
    void resetPasswordConTokenInexistenteDevuelveErrorGenericoSinExponerToken() throws Exception {
        String tokenOriginal = "token-inexistente";
        String tokenHash = sha256Hex(tokenOriginal);

        MvcResult result = mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetPasswordRequest(
                                tokenOriginal,
                                CONTRASENA_RESTABLECIDA
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_INVALIDO))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(tokenOriginal)
                .doesNotContain(tokenHash);
    }

    @Test
    void resetPasswordConTokenExpiradoDevuelveErrorGenericoYNoModificaContrasenaNiToken() throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        String hashOriginal = usuario.getContrasenaHash();
        RecuperacionContrasenaToken token = guardarToken(
                usuario,
                TOKEN_ORIGINAL_PRUEBA,
                Instant.parse("2026-09-05T12:00:00Z"),
                Instant.parse("2026-09-05T12:15:00Z")
        );

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetPasswordRequest(
                                TOKEN_ORIGINAL_PRUEBA,
                                CONTRASENA_RESTABLECIDA
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_INVALIDO));

        RecuperacionContrasenaToken tokenLuegoDelIntento = recuperacionContrasenaTokenRepository
                .findById(token.getId())
                .orElseThrow();
        assertThat(buscarUsuario(IDENTIFICADOR_ACCESO).getContrasenaHash()).isEqualTo(hashOriginal);
        assertThat(tokenLuegoDelIntento.getFechaUso()).isNull();
        assertThat(tokenLuegoDelIntento.getFechaInvalidacion()).isNull();
    }

    @Test
    void resetPasswordConTokenInvalidadoDevuelveMismoErrorGenerico() throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        RecuperacionContrasenaToken token = guardarTokenVigente(usuario, TOKEN_ORIGINAL_PRUEBA);
        token.invalidar(Instant.now());
        recuperacionContrasenaTokenRepository.saveAndFlush(token);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetPasswordRequest(
                                TOKEN_ORIGINAL_PRUEBA,
                                CONTRASENA_RESTABLECIDA
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_INVALIDO));

        assertThat(buscarUsuario(IDENTIFICADOR_ACCESO).getContrasenaHash()).isEqualTo(usuario.getContrasenaHash());
    }

    @Test
    void forgotPasswordSeguidoDeResetPasswordPermiteLoginConNuevaContrasenaYRechazaAnterior() throws Exception {
        guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
        String tokenOriginal = solicitarRecuperacionYCapturarToken(IDENTIFICADOR_ACCESO);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetPasswordRequest(
                                tokenOriginal,
                                CONTRASENA_RESTABLECIDA
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(
                                IDENTIFICADOR_ACCESO,
                                CONTRASENA_RESTABLECIDA
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(nullValue())));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(IDENTIFICADOR_ACCESO, CONTRASENA))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Credenciales invalidas"));
    }

    @Test
    void buscarPorTokenHashParaActualizarUsaBloqueoPesimista() throws Exception {
        Method method = RecuperacionContrasenaTokenRepository.class
                .getMethod("buscarPorTokenHashParaActualizar", String.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void buscarTokenActivacionPorTokenHashParaActualizarUsaBloqueoPesimista() throws Exception {
        Method method = ActivacionCuentaTokenRepository.class
                .getMethod("buscarPorTokenHashParaActualizar", String.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void flywayTieneAplicadaLaMigracionV004() {
        assertThat(flyway.info().applied())
                .anySatisfy(migration -> assertThat(migration.getVersion().getVersion()).isEqualTo("004"));
    }

    @Test
    void migracionV003CreaRegistroAuditoriaConConstraintsEIndicesEsperados() {
        assertThat(existeTabla("registro_auditoria")).isTrue();
        assertThat(constraintsDeTabla("registro_auditoria"))
                .contains(
                        "pk_registro_auditoria",
                        "fk_registro_auditoria_usuario_responsable",
                        "ck_registro_auditoria_origen",
                        "ck_registro_auditoria_origen_usuario",
                        "ck_registro_auditoria_operacion_no_vacia",
                        "ck_registro_auditoria_entidad_afectada_no_vacia",
                        "ck_registro_auditoria_identificador_afectado_no_vacio",
                        "ck_registro_auditoria_estado_anterior_no_vacio",
                        "ck_registro_auditoria_estado_nuevo_no_vacio",
                        "ck_registro_auditoria_detalle_cambio_no_vacio",
                        "ck_registro_auditoria_motivo_no_vacio"
                );
        assertThat(indicesDeTabla("registro_auditoria"))
                .contains(
                        "ix_registro_auditoria_usuario_responsable",
                        "ix_registro_auditoria_fecha_hora",
                        "ix_registro_auditoria_entidad_registro"
                );
    }

    @Test
    void migracionV004CreaActivacionCuentaTokenConConstraintsEIndicesEsperados() {
        assertThat(existeTabla("activacion_cuenta_token")).isTrue();
        assertThat(constraintsDeTabla("activacion_cuenta_token"))
                .contains(
                        "pk_activacion_cuenta_token",
                        "fk_activacion_cuenta_token_usuario",
                        "uq_activacion_cuenta_token_hash",
                        "ck_activacion_cuenta_token_hash_no_vacio",
                        "ck_activacion_cuenta_token_expiracion",
                        "ck_activacion_cuenta_token_fecha_uso",
                        "ck_activacion_cuenta_token_fecha_invalidacion"
                );
        assertThat(indicesDeTabla("activacion_cuenta_token"))
                .contains(
                        "ix_activacion_cuenta_token_usuario",
                        "ux_activacion_cuenta_token_activo_usuario"
                );
    }

    @Test
    void persisteYRecuperaTokenDeActivacionCuenta() {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.INACTIVO);
        Instant fechaCreacion = Instant.parse("2026-09-05T12:00:00Z");
        Instant fechaExpiracion = Instant.parse("2026-09-05T12:15:00Z");
        ActivacionCuentaToken token = new ActivacionCuentaToken(
                usuario,
                TOKEN_HASH,
                fechaCreacion,
                fechaExpiracion
        );

        ActivacionCuentaToken tokenGuardado = activacionCuentaTokenRepository.saveAndFlush(token);

        ActivacionCuentaToken tokenRecuperado = activacionCuentaTokenRepository
                .findById(tokenGuardado.getId())
                .orElseThrow();

        assertThat(tokenRecuperado.getUsuario().getId()).isEqualTo(usuario.getId());
        assertThat(tokenRecuperado.getTokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(tokenRecuperado.getFechaCreacion()).isEqualTo(fechaCreacion);
        assertThat(tokenRecuperado.getFechaExpiracion()).isEqualTo(fechaExpiracion);
        assertThat(tokenRecuperado.getFechaUso()).isNull();
        assertThat(tokenRecuperado.getFechaInvalidacion()).isNull();
    }

    @Test
    void activateAccountEsPublicoYConTokenValidoActivaCuentaSinDevolverJwt() throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.INACTIVO);
        String hashOriginal = usuario.getContrasenaHash();
        ActivacionCuentaToken token = guardarTokenActivacionVigente(usuario, TOKEN_ACTIVACION_ORIGINAL);

        mockMvc.perform(post("/api/auth/activate-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activarCuentaRequest(
                                TOKEN_ACTIVACION_ORIGINAL,
                                CONTRASENA_NUEVA
                        ))))
                .andExpect(status().isNoContent())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(content().string(""));

        Usuario usuarioActualizado = buscarUsuario(IDENTIFICADOR_ACCESO);
        ActivacionCuentaToken tokenUsado = activacionCuentaTokenRepository.findById(token.getId()).orElseThrow();
        RegistroAuditoria registro = registroAuditoriaRepository.findAll().getFirst();

        assertThat(usuarioActualizado.getEstadoUsuario()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(usuarioActualizado.getContrasenaHash()).isNotEqualTo(hashOriginal);
        assertThat(passwordEncoder.matches(CONTRASENA_NUEVA, usuarioActualizado.getContrasenaHash())).isTrue();
        assertThat(passwordEncoder.matches(CONTRASENA, usuarioActualizado.getContrasenaHash())).isFalse();
        assertThat(usuarioActualizado.getIdentificadorAcceso()).isEqualTo(IDENTIFICADOR_ACCESO);
        assertThat(usuarioActualizado.getRolUsuario()).isEqualTo(RolUsuario.CLIENTE);
        assertThat(usuarioActualizado.getEmailRecuperacion()).isEqualTo(EMAIL_RECUPERACION);
        assertThat(tokenUsado.getFechaUso()).isEqualTo(FECHA_HORA);
        assertThat(tokenUsado.getFechaInvalidacion()).isNull();
        assertThat(registroAuditoriaRepository.findAll()).hasSize(1);
        assertThat(registro.getOrigenOperacion()).isEqualTo(OrigenOperacion.SISTEMA);
        assertThat(registro.getUsuarioResponsable()).isNull();
        assertThat(registro.getOperacion()).isEqualTo("ACTIVAR_CUENTA");
        assertThat(registro.getEntidadAfectada()).isEqualTo("USUARIO");
        assertThat(registro.getIdentificadorRegistroAfectado()).isEqualTo(usuario.getId().toString());
        assertThat(registro.getEstadoAnterior()).isEqualTo("INACTIVO");
        assertThat(registro.getEstadoNuevo()).isEqualTo("ACTIVO");
        assertThat(registro.getDetalleCambio()).isNull();
        assertThat(registro.getMotivo()).isNull();
    }

    @Test
    void activateAccountConTokenInexistenteDevuelveErrorGenericoYNoModificaNiAudita() throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.INACTIVO);
        String hashOriginal = usuario.getContrasenaHash();
        String tokenOriginal = "token-inexistente-activacion";
        String tokenHash = sha256Hex(tokenOriginal);

        MvcResult result = mockMvc.perform(post("/api/auth/activate-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activarCuentaRequest(tokenOriginal, CONTRASENA_NUEVA))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_ACTIVACION_INVALIDO))
                .andReturn();

        Usuario usuarioLuegoDelIntento = buscarUsuario(IDENTIFICADOR_ACCESO);
        assertThat(usuarioLuegoDelIntento.getEstadoUsuario()).isEqualTo(EstadoUsuario.INACTIVO);
        assertThat(usuarioLuegoDelIntento.getContrasenaHash()).isEqualTo(hashOriginal);
        assertThat(registroAuditoriaRepository.count()).isZero();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(tokenOriginal)
                .doesNotContain(tokenHash);
    }

    @Test
    void activateAccountConTokenUsadoDevuelveErrorGenericoYNoModifica() throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.INACTIVO);
        String hashOriginal = usuario.getContrasenaHash();
        ActivacionCuentaToken token = guardarTokenActivacionVigente(usuario, TOKEN_ACTIVACION_ORIGINAL);
        Instant fechaUsoOriginal = FECHA_HORA;
        token.marcarUsado(fechaUsoOriginal);
        activacionCuentaTokenRepository.saveAndFlush(token);

        mockMvc.perform(post("/api/auth/activate-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activarCuentaRequest(
                                TOKEN_ACTIVACION_ORIGINAL,
                                CONTRASENA_NUEVA
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_ACTIVACION_INVALIDO));

        ActivacionCuentaToken tokenLuegoDelIntento = activacionCuentaTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(buscarUsuario(IDENTIFICADOR_ACCESO).getContrasenaHash()).isEqualTo(hashOriginal);
        assertThat(tokenLuegoDelIntento.getFechaUso()).isEqualTo(fechaUsoOriginal);
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void activateAccountConTokenInvalidadoDevuelveErrorGenericoYNoModifica() throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.INACTIVO);
        String hashOriginal = usuario.getContrasenaHash();
        ActivacionCuentaToken token = guardarTokenActivacionVigente(usuario, TOKEN_ACTIVACION_ORIGINAL);
        token.invalidar(FECHA_HORA);
        activacionCuentaTokenRepository.saveAndFlush(token);

        mockMvc.perform(post("/api/auth/activate-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activarCuentaRequest(
                                TOKEN_ACTIVACION_ORIGINAL,
                                CONTRASENA_NUEVA
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_ACTIVACION_INVALIDO));

        assertThat(buscarUsuario(IDENTIFICADOR_ACCESO).getContrasenaHash()).isEqualTo(hashOriginal);
        assertThat(buscarUsuario(IDENTIFICADOR_ACCESO).getEstadoUsuario()).isEqualTo(EstadoUsuario.INACTIVO);
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void activateAccountConTokenExpiradoDevuelveErrorGenericoYNoMarcaUso() throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.INACTIVO);
        String hashOriginal = usuario.getContrasenaHash();
        ActivacionCuentaToken token = guardarTokenActivacion(
                usuario,
                TOKEN_ACTIVACION_ORIGINAL,
                FECHA_HORA.minus(Duration.ofMinutes(30)),
                FECHA_HORA.minus(Duration.ofSeconds(1))
        );

        mockMvc.perform(post("/api/auth/activate-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activarCuentaRequest(
                                TOKEN_ACTIVACION_ORIGINAL,
                                CONTRASENA_NUEVA
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_ACTIVACION_INVALIDO));

        ActivacionCuentaToken tokenLuegoDelIntento = activacionCuentaTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(buscarUsuario(IDENTIFICADOR_ACCESO).getContrasenaHash()).isEqualTo(hashOriginal);
        assertThat(tokenLuegoDelIntento.getFechaUso()).isNull();
        assertThat(tokenLuegoDelIntento.getFechaInvalidacion()).isNull();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void activateAccountConTokenEnLimiteExactoDeExpiracionDevuelveErrorGenerico() throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.INACTIVO);
        ActivacionCuentaToken token = guardarTokenActivacion(
                usuario,
                TOKEN_ACTIVACION_ORIGINAL,
                FECHA_HORA.minus(Duration.ofMinutes(15)),
                FECHA_HORA
        );

        mockMvc.perform(post("/api/auth/activate-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activarCuentaRequest(
                                TOKEN_ACTIVACION_ORIGINAL,
                                CONTRASENA_NUEVA
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_ACTIVACION_INVALIDO));

        assertThat(activacionCuentaTokenRepository.findById(token.getId()).orElseThrow().getFechaUso()).isNull();
        assertThat(buscarUsuario(IDENTIFICADOR_ACCESO).getEstadoUsuario()).isEqualTo(EstadoUsuario.INACTIVO);
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void activateAccountConUsuarioActivoDevuelveErrorGenericoYNoConsumeToken() throws Exception {
        assertActivacionRechazadaPorEstadoUsuario(EstadoUsuario.ACTIVO);
    }

    @Test
    void activateAccountConUsuarioBloqueadoDevuelveErrorGenericoYNoConsumeToken() throws Exception {
        assertActivacionRechazadaPorEstadoUsuario(EstadoUsuario.BLOQUEADO);
    }

    @Test
    void activateAccountConMismoTokenPorSegundaVezDevuelveBadRequestYNoVuelveAModificar() throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.INACTIVO);
        guardarTokenActivacionVigente(usuario, TOKEN_ACTIVACION_ORIGINAL);

        mockMvc.perform(post("/api/auth/activate-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activarCuentaRequest(
                                TOKEN_ACTIVACION_ORIGINAL,
                                CONTRASENA_NUEVA
                        ))))
                .andExpect(status().isNoContent());

        String hashLuegoDelPrimerUso = buscarUsuario(IDENTIFICADOR_ACCESO).getContrasenaHash();

        mockMvc.perform(post("/api/auth/activate-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activarCuentaRequest(
                                TOKEN_ACTIVACION_ORIGINAL,
                                "OtraContrasena123!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_ACTIVACION_INVALIDO));

        String hashLuegoDelSegundoUso = buscarUsuario(IDENTIFICADOR_ACCESO).getContrasenaHash();
        assertThat(hashLuegoDelSegundoUso).isEqualTo(hashLuegoDelPrimerUso);
        assertThat(passwordEncoder.matches("OtraContrasena123!", hashLuegoDelSegundoUso)).isFalse();
        assertThat(registroAuditoriaRepository.findAll())
                .filteredOn(registro -> "ACTIVAR_CUENTA".equals(registro.getOperacion()))
                .hasSize(1);
    }

    @Test
    void activateAccountPermiteLoginPosteriorConNuevaContrasena() throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.INACTIVO);
        guardarTokenActivacionVigente(usuario, TOKEN_ACTIVACION_ORIGINAL);

        mockMvc.perform(post("/api/auth/activate-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activarCuentaRequest(
                                TOKEN_ACTIVACION_ORIGINAL,
                                CONTRASENA_NUEVA
                        ))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(IDENTIFICADOR_ACCESO, CONTRASENA_NUEVA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(nullValue())));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(IDENTIFICADOR_ACCESO, CONTRASENA))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Credenciales invalidas"));
    }

    @Test
    void activateAccountConRequestInvalidoDevuelveBadRequestYNoPersisteCambios() throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.INACTIVO);
        String hashOriginal = usuario.getContrasenaHash();
        ActivacionCuentaToken token = guardarTokenActivacionVigente(usuario, TOKEN_ACTIVACION_ORIGINAL);

        mockMvc.perform(post("/api/auth/activate-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activarCuentaRequest("", ""))))
                .andExpect(status().isBadRequest());

        Usuario usuarioLuegoDelIntento = buscarUsuario(IDENTIFICADOR_ACCESO);
        ActivacionCuentaToken tokenLuegoDelIntento = activacionCuentaTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(usuarioLuegoDelIntento.getEstadoUsuario()).isEqualTo(EstadoUsuario.INACTIVO);
        assertThat(usuarioLuegoDelIntento.getContrasenaHash()).isEqualTo(hashOriginal);
        assertThat(tokenLuegoDelIntento.getFechaUso()).isNull();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void rollbackDeTransaccionExteriorRevierteActivacionTokenYAuditoria() {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, EstadoUsuario.INACTIVO);
        String hashOriginal = usuario.getContrasenaHash();
        ActivacionCuentaToken token = guardarTokenActivacionVigente(usuario, TOKEN_ACTIVACION_ORIGINAL);

        transactionTemplate.executeWithoutResult(status -> {
            activacionCuentaService.activarCuenta(TOKEN_ACTIVACION_ORIGINAL, CONTRASENA_NUEVA);
            status.setRollbackOnly();
        });

        Usuario usuarioLuegoDelRollback = buscarUsuario(IDENTIFICADOR_ACCESO);
        ActivacionCuentaToken tokenLuegoDelRollback = activacionCuentaTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(usuarioLuegoDelRollback.getEstadoUsuario()).isEqualTo(EstadoUsuario.INACTIVO);
        assertThat(usuarioLuegoDelRollback.getContrasenaHash()).isEqualTo(hashOriginal);
        assertThat(tokenLuegoDelRollback.getFechaUso()).isNull();
        assertThat(registroAuditoriaRepository.count()).isZero();
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
        return guardarUsuario(
                identificadorAcceso,
                contrasena,
                rolUsuario,
                estadoUsuario,
                EMAIL_RECUPERACION
        );
    }

    private Usuario guardarUsuario(
            String identificadorAcceso,
            String contrasena,
            RolUsuario rolUsuario,
            EstadoUsuario estadoUsuario,
            String emailRecuperacion
    ) {
        Usuario usuario = new Usuario(
                identificadorAcceso,
                passwordEncoder.encode(contrasena),
                rolUsuario,
                estadoUsuario,
                emailRecuperacion
        );

        return usuarioRepository.save(usuario);
    }

    private Usuario buscarUsuario(String identificadorAcceso) {
        return usuarioRepository.buscarPorIdentificadorAcceso(identificadorAcceso).orElseThrow();
    }

    private String tokenValido(String identificadorAcceso) {
        return tokenValido(identificadorAcceso, RolUsuario.CLIENTE);
    }

    private String tokenValido(String identificadorAcceso, RolUsuario rolUsuario) {
        return jwtService.generarAccessToken(UsernamePasswordAuthenticationToken.authenticated(
                identificadorAcceso,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rolUsuario.name()))
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

    private Map<String, String> resetPasswordRequest(String token, String nuevaContrasena) {
        return Map.of(
                "token", token,
                "nuevaContrasena", nuevaContrasena
        );
    }

    private Map<String, String> activarCuentaRequest(String token, String nuevaContrasena) {
        return Map.of(
                "token", token,
                "nuevaContrasena", nuevaContrasena
        );
    }

    private void assertActivacionRechazadaPorEstadoUsuario(EstadoUsuario estadoUsuario) throws Exception {
        Usuario usuario = guardarUsuario(IDENTIFICADOR_ACCESO, CONTRASENA, RolUsuario.CLIENTE, estadoUsuario);
        String hashOriginal = usuario.getContrasenaHash();
        ActivacionCuentaToken token = guardarTokenActivacionVigente(usuario, TOKEN_ACTIVACION_ORIGINAL);

        mockMvc.perform(post("/api/auth/activate-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activarCuentaRequest(
                                TOKEN_ACTIVACION_ORIGINAL,
                                CONTRASENA_NUEVA
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_ACTIVACION_INVALIDO));

        Usuario usuarioLuegoDelIntento = buscarUsuario(IDENTIFICADOR_ACCESO);
        ActivacionCuentaToken tokenLuegoDelIntento = activacionCuentaTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(usuarioLuegoDelIntento.getEstadoUsuario()).isEqualTo(estadoUsuario);
        assertThat(usuarioLuegoDelIntento.getContrasenaHash()).isEqualTo(hashOriginal);
        assertThat(tokenLuegoDelIntento.getFechaUso()).isNull();
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    private String solicitarRecuperacionYCapturarToken(String identificadorAcceso) throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotPasswordRequest(identificadorAcceso))))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(recuperacionContrasenaNotifier).notificar(any(), tokenCaptor.capture(), any());
        return tokenCaptor.getValue();
    }

    private RecuperacionContrasenaToken guardarTokenVigente(Usuario usuario, String tokenOriginal) {
        Instant fechaCreacion = Instant.now();
        return guardarToken(usuario, tokenOriginal, fechaCreacion, fechaCreacion.plus(Duration.ofMinutes(15)));
    }

    private ActivacionCuentaToken guardarTokenActivacionVigente(Usuario usuario, String tokenOriginal) {
        return guardarTokenActivacion(
                usuario,
                tokenOriginal,
                FECHA_HORA.minus(Duration.ofMinutes(1)),
                FECHA_HORA.plus(Duration.ofMinutes(15))
        );
    }

    private ActivacionCuentaToken guardarTokenActivacion(
            Usuario usuario,
            String tokenOriginal,
            Instant fechaCreacion,
            Instant fechaExpiracion
    ) {
        ActivacionCuentaToken token = new ActivacionCuentaToken(
                usuario,
                sha256Hex(tokenOriginal),
                fechaCreacion,
                fechaExpiracion
        );

        return activacionCuentaTokenRepository.saveAndFlush(token);
    }

    private RecuperacionContrasenaToken guardarToken(
            Usuario usuario,
            String tokenOriginal,
            Instant fechaCreacion,
            Instant fechaExpiracion
    ) {
        RecuperacionContrasenaToken token = new RecuperacionContrasenaToken(
                usuario,
                sha256Hex(tokenOriginal),
                fechaCreacion,
                fechaExpiracion
        );

        return recuperacionContrasenaTokenRepository.saveAndFlush(token);
    }

    private String sha256Hex(String tokenOriginal) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(tokenOriginal.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
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

    private Set<String> constraintsDeTabla(String tabla) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT c.conname
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = 'public'
                  AND t.relname = ?
                """, String.class, tabla));
    }

    private Set<String> indicesDeTabla(String tabla) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = ?
                """, String.class, tabla));
    }

    @TestConfiguration
    static class NotifierTestConfiguration {

        @Bean
        @Primary
        RecuperacionContrasenaNotifier recuperacionContrasenaNotifier() {
            return mock(RecuperacionContrasenaNotifier.class);
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
