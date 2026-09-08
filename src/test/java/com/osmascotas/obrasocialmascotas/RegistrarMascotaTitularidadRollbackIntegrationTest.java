package com.osmascotas.obrasocialmascotas;

import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.password-recovery.expiration=PT15M",
        "app.security.account-activation.expiration=PT24H"
})
class RegistrarMascotaTitularidadRollbackIntegrationTest {

    private static final String CONTRASENA = "Password123!";
    private static final String ADMIN_IDENTIFICADOR = "admin-rollback-mascotas@osmascotas.com";

    @Autowired
    private RegistrarMascotaService registrarMascotaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @MockitoBean
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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        mascotaRepository.deleteAll();
        clienteRepository.deleteAll();
        registroAuditoriaRepository.deleteAll();
        activacionCuentaTokenRepository.deleteAll();
        recuperacionContrasenaTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void siFallaTitularidadRollbackRevierteMascotaYNoAudita() {
        Usuario administrador = guardarUsuario();
        Cliente cliente = clienteRepository.saveAndFlush(new Cliente("92345678", "Ana", "Gomez", null, null, null));
        when(titularidadMascotaRepository.saveAndFlush(any(TitularidadMascota.class)))
                .thenThrow(new DataIntegrityViolationException("Fallo titularidad"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                administrador.getIdentificadorAcceso(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))
        ));

        assertThatThrownBy(() -> registrarMascotaService.registrar(new CrearMascotaRequest(
                cliente.getId(),
                "Luna",
                "Canino",
                null,
                null,
                null
        )))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(contarFilas("mascota")).isZero();
        assertThat(contarFilas("titularidad_mascota")).isZero();
        assertThat(contarAuditoriasRegistrarMascota()).isZero();
    }

    private Usuario guardarUsuario() {
        Usuario usuario = new Usuario(
                ADMIN_IDENTIFICADOR,
                passwordEncoder.encode(CONTRASENA),
                RolUsuario.ADMINISTRADOR,
                EstadoUsuario.ACTIVO,
                ADMIN_IDENTIFICADOR
        );

        return usuarioRepository.saveAndFlush(usuario);
    }

    private long contarFilas(String tabla) {
        Long cantidad = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class);
        return cantidad == null ? 0 : cantidad;
    }

    private long contarAuditoriasRegistrarMascota() {
        Long cantidad = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM registro_auditoria
                WHERE operacion = 'REGISTRAR_MASCOTA'
                """, Long.class);
        return cantidad == null ? 0 : cantidad;
    }
}
