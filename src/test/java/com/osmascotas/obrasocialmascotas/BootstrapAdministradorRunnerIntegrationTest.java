package com.osmascotas.obrasocialmascotas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.ActivacionCuentaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.password-recovery.expiration=PT15M",
        "app.security.bootstrap-admin.enabled=true",
        "app.security.bootstrap-admin.identificador-acceso=bootstrap-admin@osmascotas.com",
        "app.security.bootstrap-admin.email-recuperacion=bootstrap-admin@test.local",
        "app.security.bootstrap-admin.password=BootstrapPassword123!"
})
class BootstrapAdministradorRunnerIntegrationTest {

    private static final String IDENTIFICADOR = "bootstrap-admin@osmascotas.com";
    private static final String PASSWORD = "BootstrapPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ActivacionCuentaTokenRepository activacionCuentaTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runnerCreaAdministradorBootstrapActivoYPermiteLogin() throws Exception {
        Usuario usuario = usuarioRepository.buscarPorIdentificadorAcceso(IDENTIFICADOR).orElseThrow();
        assertThat(usuarioRepository.count()).isEqualTo(1);
        assertThat(usuario.getRolUsuario()).isEqualTo(RolUsuario.ADMINISTRADOR);
        assertThat(usuario.getEstadoUsuario()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(passwordEncoder.matches(PASSWORD, usuario.getContrasenaHash())).isTrue();
        assertThat(activacionCuentaTokenRepository.findByUsuario_IdOrderByIdAsc(usuario.getId())).isEmpty();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identificadorAcceso", IDENTIFICADOR,
                                "contrasena", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(nullValue())))
                .andReturn();

        String accessToken = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
        assertThat(jwtService.extraerAuthorities(accessToken)).containsExactly("ROLE_ADMINISTRADOR");
    }
}
