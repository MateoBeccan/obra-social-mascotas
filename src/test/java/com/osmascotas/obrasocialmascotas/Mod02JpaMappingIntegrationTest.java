package com.osmascotas.obrasocialmascotas;

import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Mascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.ClienteRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.MascotaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.TitularidadMascotaRepository;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901"
})
@Transactional
class Mod02JpaMappingIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Autowired
    private TitularidadMascotaRepository titularidadMascotaRepository;

    @Test
    void persisteYRecuperaClienteSinUsuario() {
        Cliente cliente = clienteRepository.save(new Cliente(
                "12345678",
                "Ana",
                "Gomez",
                "ana@test.local",
                "1111-2222",
                "Calle 123"
        ));

        flushClear();

        Cliente recuperado = clienteRepository.findById(cliente.getId()).orElseThrow();
        assertThat(recuperado.getId()).isNotNull();
        assertThat(recuperado.getDni()).isEqualTo("12345678");
        assertThat(recuperado.getNombre()).isEqualTo("Ana");
        assertThat(recuperado.getApellido()).isEqualTo("Gomez");
        assertThat(recuperado.getCorreoElectronico()).isEqualTo("ana@test.local");
        assertThat(recuperado.getTelefono()).isEqualTo("1111-2222");
        assertThat(recuperado.getDomicilio()).isEqualTo("Calle 123");
        assertThat(recuperado.getUsuario()).isNull();
    }

    @Test
    void persisteYRecuperaClienteConUsuario() {
        Usuario usuario = usuarioRepository.save(new Usuario(
                "cliente-mod02@test.local",
                "hash-cliente-mod02",
                RolUsuario.CLIENTE,
                EstadoUsuario.ACTIVO,
                "cliente-mod02@test.local"
        ));
        Cliente cliente = clienteRepository.save(new Cliente(
                usuario,
                "22345678",
                "Bruno",
                "Perez",
                null,
                null,
                null
        ));

        flushClear();

        Cliente recuperado = clienteRepository.findById(cliente.getId()).orElseThrow();
        assertThat(recuperado.getUsuario()).isNotNull();
        assertThat(recuperado.getUsuario().getId()).isEqualTo(usuario.getId());
    }

    @Test
    void persisteYRecuperaMascotaYPermiteNombreRepetido() {
        LocalDate fechaNacimiento = LocalDate.of(2021, 5, 20);
        Mascota luna = mascotaRepository.save(new Mascota(
                "Luna",
                "Canino",
                "Mestiza",
                "Hembra",
                fechaNacimiento,
                "mascotas/test/luna.jpg"
        ));
        Mascota otraLuna = mascotaRepository.save(new Mascota(
                "Luna",
                "Felino",
                null,
                null,
                null,
                null
        ));

        flushClear();

        Mascota recuperada = mascotaRepository.findById(luna.getId()).orElseThrow();
        assertThat(recuperada.getNombre()).isEqualTo("Luna");
        assertThat(recuperada.getEspecie()).isEqualTo("Canino");
        assertThat(recuperada.getRaza()).isEqualTo("Mestiza");
        assertThat(recuperada.getSexo()).isEqualTo("Hembra");
        assertThat(recuperada.getFechaNacimiento()).isEqualTo(fechaNacimiento);
        assertThat(recuperada.getFotografiaObjetoKey()).isEqualTo("mascotas/test/luna.jpg");
        assertThat(mascotaRepository.findById(otraLuna.getId())).isPresent();
    }

    @Test
    void persisteYRecuperaTitularidadVigente() {
        Cliente cliente = clienteRepository.save(new Cliente("32345678", "Carla", "Lopez", null, null, null));
        Mascota mascota = mascotaRepository.save(new Mascota("Luna", "Canino", null, null, null, null));
        Usuario usuarioAdmin = usuarioRepository.save(new Usuario(
                "admin-mod02@test.local",
                "hash-admin-mod02",
                RolUsuario.ADMINISTRADOR,
                EstadoUsuario.ACTIVO,
                "admin-mod02@test.local"
        ));
        LocalDate fechaDesde = LocalDate.of(2026, 1, 1);
        TitularidadMascota titularidad = titularidadMascotaRepository.save(new TitularidadMascota(
                mascota,
                cliente,
                fechaDesde,
                null,
                null,
                usuarioAdmin
        ));

        flushClear();

        TitularidadMascota recuperada = titularidadMascotaRepository.findById(titularidad.getId()).orElseThrow();
        assertThat(recuperada.getMascota().getId()).isEqualTo(mascota.getId());
        assertThat(recuperada.getCliente().getId()).isEqualTo(cliente.getId());
        assertThat(recuperada.getFechaDesde()).isEqualTo(fechaDesde);
        assertThat(recuperada.getFechaHasta()).isNull();
        assertThat(recuperada.getUsuarioResponsable().getId()).isEqualTo(usuarioAdmin.getId());
        assertThat(recuperada.getMotivoCambio()).isNull();
    }

    @Test
    void persisteYRecuperaTitularidadHistorica() {
        Cliente cliente = clienteRepository.save(new Cliente("42345678", "Diana", "Ruiz", null, null, null));
        Mascota mascota = mascotaRepository.save(new Mascota("Nina", "Felino", null, null, null, null));
        LocalDate fechaDesde = LocalDate.of(2025, 1, 1);
        LocalDate fechaHasta = LocalDate.of(2026, 1, 1);
        TitularidadMascota titularidad = titularidadMascotaRepository.save(new TitularidadMascota(
                mascota,
                cliente,
                fechaDesde,
                fechaHasta,
                "Cambio de titular",
                null
        ));

        flushClear();

        TitularidadMascota recuperada = titularidadMascotaRepository.findById(titularidad.getId()).orElseThrow();
        assertThat(recuperada.getMascota().getId()).isEqualTo(mascota.getId());
        assertThat(recuperada.getCliente().getId()).isEqualTo(cliente.getId());
        assertThat(recuperada.getFechaDesde()).isEqualTo(fechaDesde);
        assertThat(recuperada.getFechaHasta()).isEqualTo(fechaHasta);
        assertThat(recuperada.getMotivoCambio()).isEqualTo("Cambio de titular");
        assertThat(recuperada.getUsuarioResponsable()).isNull();
    }

    @Test
    void constructoresRechazanSoloInvariantesMapeadas() {
        Mascota mascota = new Mascota("Luna", "Canino", null, null, null, null);
        Cliente cliente = new Cliente("52345678", "Elena", "Diaz", null, null, null);
        LocalDate fechaDesde = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> new Cliente(" ", "Elena", "Diaz", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cliente("52345678", " ", "Diaz", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cliente("52345678", "Elena", " ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Mascota(" ", "Canino", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Mascota("Luna", " ", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new TitularidadMascota(null, cliente, fechaDesde, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TitularidadMascota(mascota, null, fechaDesde, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TitularidadMascota(mascota, cliente, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TitularidadMascota(mascota, cliente, fechaDesde, fechaDesde, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TitularidadMascota(
                mascota,
                cliente,
                fechaDesde,
                fechaDesde.minusDays(1),
                null,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TitularidadMascota(mascota, cliente, fechaDesde, null, " ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void flushClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
