package com.osmascotas.obrasocialmascotas;

import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.seguridad.repository.ActivacionCuentaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.RecuperacionContrasenaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.BootstrapAdministradorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Import({
        TestcontainersConfiguration.class,
        BootstrapAdministradorAuditoriaRollbackIntegrationTest.AuditoriaFailureConfiguration.class
})
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.password-recovery.expiration=PT15M",
        "app.security.bootstrap-admin.enabled=false"
})
class BootstrapAdministradorAuditoriaRollbackIntegrationTest {

    private static final AtomicInteger PROPERTY_SOURCE_SEQUENCE = new AtomicInteger();

    @Autowired
    private BootstrapAdministradorService bootstrapAdministradorService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ActivacionCuentaTokenRepository activacionCuentaTokenRepository;

    @Autowired
    private RecuperacionContrasenaTokenRepository recuperacionContrasenaTokenRepository;

    @Autowired
    private RegistroAuditoriaRepository registroAuditoriaRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private ConfigurableEnvironment environment;

    @BeforeEach
    void setUp() {
        registroAuditoriaRepository.deleteAll();
        activacionCuentaTokenRepository.deleteAll();
        recuperacionContrasenaTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "bootstrap-admin-audit-failure-test-" + PROPERTY_SOURCE_SEQUENCE.incrementAndGet(),
                Map.of(
                        "app.security.bootstrap-admin.enabled", "true",
                        "app.security.bootstrap-admin.identificador-acceso", "bootstrap-admin@osmascotas.com",
                        "app.security.bootstrap-admin.email-recuperacion", "bootstrap-admin@test.local",
                        "app.security.bootstrap-admin.password", "BootstrapPassword123!"
                )
        ));
    }

    @Test
    void siFallaAuditoriaRollbackRevierteUsuarioBootstrap() {
        assertThatThrownBy(() -> bootstrapAdministradorService.inicializarSiCorresponde())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Fallo auditoria");

        assertThat(usuarioRepository.count()).isZero();
        assertThat(registroAuditoriaRepository.count()).isZero();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
    }

    @TestConfiguration
    static class AuditoriaFailureConfiguration {

        @Bean
        @Primary
        AuditoriaService auditoriaService() {
            return new AuditoriaService(
                    mock(RegistroAuditoriaRepository.class),
                    mock(UsuarioRepository.class),
                    Clock.systemUTC()
            ) {
                @Override
                public void registrarOperacionSistema(
                        String operacion,
                        String entidadAfectada,
                        String identificadorRegistroAfectado,
                        String estadoAnterior,
                        String estadoNuevo,
                        String detalleCambio,
                        String motivo
                ) {
                    throw new IllegalStateException("Fallo auditoria");
                }
            };
        }
    }
}
