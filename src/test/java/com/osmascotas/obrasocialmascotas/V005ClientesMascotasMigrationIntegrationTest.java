package com.osmascotas.obrasocialmascotas;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901"
})
class V005ClientesMascotasMigrationIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM titularidad_mascota");
        jdbcTemplate.update("DELETE FROM mascota");
        jdbcTemplate.update("DELETE FROM cliente");
        jdbcTemplate.update("DELETE FROM activacion_cuenta_token");
        jdbcTemplate.update("DELETE FROM recuperacion_contrasena_token");
        jdbcTemplate.update("DELETE FROM registro_auditoria");
        jdbcTemplate.update("DELETE FROM usuario");
    }

    @Test
    void flywayTieneAplicadaLaMigracionV005ComoVersionActual() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("005");
        assertThat(flyway.info().applied())
                .anySatisfy(migration -> assertThat(migration.getVersion().getVersion()).isEqualTo("005"));
    }

    @Test
    void migracionV005CreaTablasColumnasConstraintsEIndicesEsperados() {
        assertThat(existeTabla("cliente")).isTrue();
        assertThat(existeTabla("mascota")).isTrue();
        assertThat(existeTabla("titularidad_mascota")).isTrue();

        assertThat(columnasDeTabla("cliente"))
                .containsExactlyInAnyOrder(
                        "cliente_id",
                        "usuario_id",
                        "dni",
                        "nombre",
                        "apellido",
                        "correo_electronico",
                        "telefono",
                        "domicilio"
                );
        assertThat(columnasDeTabla("mascota"))
                .containsExactlyInAnyOrder(
                        "mascota_id",
                        "nombre",
                        "especie",
                        "raza",
                        "sexo",
                        "fecha_nacimiento",
                        "fotografia_objeto_key"
                )
                .doesNotContain(
                        "cliente_id",
                        "titular_id",
                        "titular_actual_id",
                        "usuario_id"
                );
        assertThat(columnasDeTabla("titularidad_mascota"))
                .containsExactlyInAnyOrder(
                        "titularidad_mascota_id",
                        "mascota_id",
                        "cliente_id",
                        "fecha_desde",
                        "fecha_hasta",
                        "motivo_cambio",
                        "usuario_responsable_id"
                );

        assertThat(constraintsDeTabla("cliente"))
                .contains(
                        "pk_cliente",
                        "fk_cliente_usuario",
                        "uq_cliente_usuario",
                        "ck_cliente_dni_no_vacio",
                        "ck_cliente_nombre_no_vacio",
                        "ck_cliente_apellido_no_vacio",
                        "ck_cliente_correo_electronico_no_vacio",
                        "ck_cliente_telefono_no_vacio",
                        "ck_cliente_domicilio_no_vacio"
                );
        assertThat(indicesDeTabla("cliente"))
                .contains("ux_cliente_dni_normalizado");

        assertThat(constraintsDeTabla("mascota"))
                .contains(
                        "pk_mascota",
                        "ck_mascota_nombre_no_vacio",
                        "ck_mascota_especie_no_vacia",
                        "ck_mascota_raza_no_vacia",
                        "ck_mascota_sexo_no_vacio",
                        "ck_mascota_fotografia_objeto_key_no_vacia"
                );

        assertThat(constraintsDeTabla("titularidad_mascota"))
                .contains(
                        "pk_titularidad_mascota",
                        "fk_titularidad_mascota_mascota",
                        "fk_titularidad_mascota_cliente",
                        "fk_titularidad_mascota_usuario_responsable",
                        "ck_titularidad_mascota_fechas",
                        "ck_titularidad_mascota_motivo_cambio_no_vacio",
                        "ex_titularidad_mascota_sin_solapamiento"
                );
        assertThat(indicesDeTabla("titularidad_mascota"))
                .contains(
                        "ix_titularidad_mascota_mascota",
                        "ix_titularidad_mascota_cliente",
                        "ix_titularidad_mascota_usuario_responsable",
                        "ix_titularidad_mascota_vigente"
                );
    }

    @Test
    void dniDeClienteEsUnicoIgnorandoEspaciosExteriores() {
        insertarCliente("12345678", null);

        assertThatThrownBy(() -> insertarCliente(" 12345678 ", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usuarioDeClienteEsOpcionalPeroUnicoCuandoExiste() {
        insertarCliente("10000001", null);
        insertarCliente("10000002", null);

        Long usuarioId = insertarUsuario("cliente-v005@test.local");
        insertarCliente("10000003", usuarioId);

        assertThatThrownBy(() -> insertarCliente("10000004", usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mascotaPermiteNombresRepetidosYRechazaCamposObligatoriosVacios() {
        insertarMascota("Luna", "Canino");
        insertarMascota("Luna", "Felino");

        assertThat(contarFilas("mascota")).isEqualTo(2);
        assertThat(existeTabla("afiliacion")).isFalse();
        assertThat(existeTabla("afiliacion_mascota")).isFalse();

        assertThatThrownBy(() -> insertarMascota(" ", "Canino"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarMascota("Luna", " "))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void titularidadInicialFunciona() {
        Long clienteId = insertarCliente("20000001", null);
        Long mascotaId = insertarMascota("Luna", "Canino");

        insertarTitularidad(mascotaId, clienteId, "2026-01-01", null, null);

        assertThat(contarFilas("titularidad_mascota")).isEqualTo(1);
    }

    @Test
    void noPermiteDosTitularesVigentesParaLaMismaMascota() {
        Long mascotaId = insertarMascota("Luna", "Canino");
        Long clienteAId = insertarCliente("30000001", null);
        Long clienteBId = insertarCliente("30000002", null);
        insertarTitularidad(mascotaId, clienteAId, "2026-01-01", null, null);

        assertThatThrownBy(() -> insertarTitularidad(mascotaId, clienteBId, "2026-06-01", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void noPermiteHistorialSuperpuestoParaLaMismaMascota() {
        Long mascotaId = insertarMascota("Luna", "Canino");
        Long clienteAId = insertarCliente("31000001", null);
        Long clienteBId = insertarCliente("31000002", null);
        insertarTitularidad(mascotaId, clienteAId, "2025-01-01", "2026-01-01", null);

        assertThatThrownBy(() -> insertarTitularidad(mascotaId, clienteBId, "2025-06-01", "2026-06-01", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void permitePeriodosContiguosParaLaMismaMascota() {
        Long mascotaId = insertarMascota("Luna", "Canino");
        Long clienteAId = insertarCliente("32000001", null);
        Long clienteBId = insertarCliente("32000002", null);

        insertarTitularidad(mascotaId, clienteAId, "2025-01-01", "2026-01-01", null);
        insertarTitularidad(mascotaId, clienteBId, "2026-01-01", null, null);

        assertThat(contarFilas("titularidad_mascota")).isEqualTo(2);
    }

    @Test
    void permitePeriodosIgualesEnMascotasDiferentes() {
        Long mascotaAId = insertarMascota("Luna", "Canino");
        Long mascotaBId = insertarMascota("Luna", "Felino");
        Long clienteAId = insertarCliente("33000001", null);
        Long clienteBId = insertarCliente("33000002", null);

        insertarTitularidad(mascotaAId, clienteAId, "2025-01-01", null, null);
        insertarTitularidad(mascotaBId, clienteBId, "2025-01-01", null, null);

        assertThat(contarFilas("titularidad_mascota")).isEqualTo(2);
    }

    @Test
    void rechazaFechasInvalidasDeTitularidad() {
        Long mascotaId = insertarMascota("Luna", "Canino");
        Long clienteId = insertarCliente("34000001", null);

        assertThatThrownBy(() -> insertarTitularidad(mascotaId, clienteId, "2026-01-01", "2026-01-01", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarTitularidad(mascotaId, clienteId, "2026-01-01", "2025-12-31", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void extensionBtreeGistEstaInstalada() {
        Integer cantidad = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_extension
                WHERE extname = 'btree_gist'
                """, Integer.class);

        assertThat(cantidad).isEqualTo(1);
    }

    @Test
    void titularidadRechazaReferenciasInexistentes() {
        Long mascotaId = insertarMascota("Luna", "Canino");
        Long clienteId = insertarCliente("35000001", null);
        Long usuarioId = insertarUsuario("responsable-v005@test.local");

        assertThatThrownBy(() -> insertarTitularidad(999_999L, clienteId, "2026-01-01", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarTitularidad(mascotaId, 999_999L, "2026-01-01", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarTitularidad(mascotaId, clienteId, "2026-01-01", null, 999_999L))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertarTitularidad(mascotaId, clienteId, "2026-01-01", null, usuarioId);
        assertThat(contarFilas("titularidad_mascota")).isEqualTo(1);
    }

    private Long insertarUsuario(String identificadorAcceso) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO usuario (
                    identificador_acceso,
                    contrasena_hash,
                    rol_usuario,
                    estado_usuario,
                    email_recuperacion
                )
                VALUES (?, ?, 'CLIENTE', 'ACTIVO', ?)
                RETURNING usuario_id
                """, Long.class, identificadorAcceso, "hash-" + identificadorAcceso, identificadorAcceso);
    }

    private Long insertarCliente(String dni, Long usuarioId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO cliente (
                    usuario_id,
                    dni,
                    nombre,
                    apellido
                )
                VALUES (?, ?, 'Nombre', 'Apellido')
                RETURNING cliente_id
                """, Long.class, usuarioId, dni);
    }

    private Long insertarMascota(String nombre, String especie) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO mascota (
                    nombre,
                    especie
                )
                VALUES (?, ?)
                RETURNING mascota_id
                """, Long.class, nombre, especie);
    }

    private void insertarTitularidad(
            Long mascotaId,
            Long clienteId,
            String fechaDesde,
            String fechaHasta,
            Long usuarioResponsableId
    ) {
        jdbcTemplate.update("""
                INSERT INTO titularidad_mascota (
                    mascota_id,
                    cliente_id,
                    fecha_desde,
                    fecha_hasta,
                    usuario_responsable_id
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                mascotaId,
                clienteId,
                Date.valueOf(LocalDate.parse(fechaDesde)),
                fechaHasta == null ? null : Date.valueOf(LocalDate.parse(fechaHasta)),
                usuarioResponsableId);
    }

    private long contarFilas(String tabla) {
        Long cantidad = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class);
        return cantidad == null ? 0 : cantidad;
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

    private Set<String> columnasDeTabla(String tabla) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                """, String.class, tabla));
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
}
