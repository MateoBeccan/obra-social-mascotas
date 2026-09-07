package com.osmascotas.obrasocialmascotas.auditoria.domain;

import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistroAuditoriaTest {

    private static final Instant FECHA_HORA = Instant.parse("2026-09-07T12:00:00Z");

    @Test
    void factoryUsuarioDefineOrigenUsuarioYUsuarioResponsable() {
        Usuario usuario = usuario();

        RegistroAuditoria registro = RegistroAuditoria.registrarUsuario(
                usuario,
                FECHA_HORA,
                "CAMBIO_ESTADO",
                "Usuario",
                "123",
                "ACTIVO",
                "BLOQUEADO",
                "Cambio solicitado",
                "Motivo administrativo"
        );

        assertThat(registro.getOrigenOperacion()).isEqualTo(OrigenOperacion.USUARIO);
        assertThat(registro.getUsuarioResponsable()).isSameAs(usuario);
    }

    @Test
    void factorySistemaDefineOrigenSistemaYUsuarioResponsableNull() {
        RegistroAuditoria registro = RegistroAuditoria.registrarSistema(
                FECHA_HORA,
                "JOB_NOCTURNO",
                "Afiliacion",
                "456",
                null,
                null,
                null,
                null
        );

        assertThat(registro.getOrigenOperacion()).isEqualTo(OrigenOperacion.SISTEMA);
        assertThat(registro.getUsuarioResponsable()).isNull();
    }

    @Test
    void factoryUsuarioRechazaUsuarioNull() {
        assertThatThrownBy(() -> RegistroAuditoria.registrarUsuario(
                null,
                FECHA_HORA,
                "CAMBIO_ESTADO",
                "Usuario",
                "123",
                null,
                null,
                null,
                null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void camposObligatoriosBlankSonRechazados() {
        Usuario usuario = usuario();

        assertThatThrownBy(() -> RegistroAuditoria.registrarUsuario(
                usuario,
                FECHA_HORA,
                " ",
                "Usuario",
                "123",
                null,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> RegistroAuditoria.registrarUsuario(
                usuario,
                FECHA_HORA,
                "CAMBIO_ESTADO",
                " ",
                "123",
                null,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> RegistroAuditoria.registrarUsuario(
                usuario,
                FECHA_HORA,
                "CAMBIO_ESTADO",
                "Usuario",
                " ",
                null,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void camposOpcionalesNullSonValidos() {
        RegistroAuditoria registro = RegistroAuditoria.registrarSistema(
                FECHA_HORA,
                "JOB_NOCTURNO",
                "Afiliacion",
                "456",
                null,
                null,
                null,
                null
        );

        assertThat(registro.getEstadoAnterior()).isNull();
        assertThat(registro.getEstadoNuevo()).isNull();
        assertThat(registro.getDetalleCambio()).isNull();
        assertThat(registro.getMotivo()).isNull();
    }

    @Test
    void camposOpcionalesBlankSonRechazados() {
        assertThatThrownBy(() -> RegistroAuditoria.registrarSistema(
                FECHA_HORA,
                "JOB_NOCTURNO",
                "Afiliacion",
                "456",
                " ",
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> RegistroAuditoria.registrarSistema(
                FECHA_HORA,
                "JOB_NOCTURNO",
                "Afiliacion",
                "456",
                null,
                " ",
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> RegistroAuditoria.registrarSistema(
                FECHA_HORA,
                "JOB_NOCTURNO",
                "Afiliacion",
                "456",
                null,
                null,
                " ",
                null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> RegistroAuditoria.registrarSistema(
                FECHA_HORA,
                "JOB_NOCTURNO",
                "Afiliacion",
                "456",
                null,
                null,
                null,
                " "
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noExponeMetodosPublicosDeMutacionFuncional() {
        assertThat(Arrays.stream(RegistroAuditoria.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(RegistroAuditoria.class))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName))
                .noneMatch(nombre -> nombre.startsWith("set")
                        || nombre.startsWith("cambiar")
                        || nombre.startsWith("actualizar")
                        || nombre.startsWith("eliminar"));
    }

    private Usuario usuario() {
        return new Usuario(
                "admin@osmascotas.com",
                "$2a$10$hash",
                RolUsuario.ADMINISTRADOR,
                EstadoUsuario.ACTIVO,
                "admin@test.local"
        );
    }
}
