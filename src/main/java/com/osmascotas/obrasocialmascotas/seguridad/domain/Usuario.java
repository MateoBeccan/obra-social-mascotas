package com.osmascotas.obrasocialmascotas.seguridad.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usuario_id")
    private Long id;

    @Column(
            name = "identificador_acceso",
            nullable = false,
            length = 150
    )
    private String identificadorAcceso;

    @Column(
            name = "contrasena_hash",
            nullable = false,
            length = 255
    )
    private String contrasenaHash;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "rol_usuario",
            nullable = false,
            length = 20
    )
    private RolUsuario rolUsuario;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "estado_usuario",
            nullable = false,
            length = 20
    )
    private EstadoUsuario estadoUsuario;

    @Column(
            name = "email_recuperacion",
            nullable = false,
            length = 254
    )
    private String emailRecuperacion;

    protected Usuario() {
        // Requerido por JPA.
    }

    public Usuario(
            String identificadorAcceso,
            String contrasenaHash,
            RolUsuario rolUsuario,
            EstadoUsuario estadoUsuario,
            String emailRecuperacion
    ) {
        this.identificadorAcceso = identificadorAcceso;
        this.contrasenaHash = contrasenaHash;
        this.rolUsuario = rolUsuario;
        this.estadoUsuario = estadoUsuario;
        this.emailRecuperacion = emailRecuperacion;
    }

    public Long getId() {
        return id;
    }

    public String getIdentificadorAcceso() {
        return identificadorAcceso;
    }

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public RolUsuario getRolUsuario() {
        return rolUsuario;
    }

    public EstadoUsuario getEstadoUsuario() {
        return estadoUsuario;
    }

    public String getEmailRecuperacion() {
        return emailRecuperacion;
    }

    public void cambiarContrasenaHash(String nuevaContrasenaHash) {
        this.contrasenaHash = nuevaContrasenaHash;
    }

    public void aplicarOperacionEstado(OperacionEstadoUsuario operacion) {
        if (estadoUsuario == EstadoUsuario.INACTIVO && operacion == OperacionEstadoUsuario.HABILITAR) {
            estadoUsuario = EstadoUsuario.ACTIVO;
            return;
        }

        if (estadoUsuario == EstadoUsuario.ACTIVO && operacion == OperacionEstadoUsuario.BLOQUEAR) {
            estadoUsuario = EstadoUsuario.BLOQUEADO;
            return;
        }

        if (estadoUsuario == EstadoUsuario.BLOQUEADO && operacion == OperacionEstadoUsuario.DESBLOQUEAR) {
            estadoUsuario = EstadoUsuario.ACTIVO;
            return;
        }

        if ((estadoUsuario == EstadoUsuario.ACTIVO || estadoUsuario == EstadoUsuario.BLOQUEADO)
                && operacion == OperacionEstadoUsuario.INACTIVAR) {
            estadoUsuario = EstadoUsuario.INACTIVO;
            return;
        }

        throw new TransicionEstadoUsuarioInvalidaException(estadoUsuario, operacion);
    }
}
