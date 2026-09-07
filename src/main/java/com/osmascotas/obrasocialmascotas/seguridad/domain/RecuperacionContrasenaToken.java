package com.osmascotas.obrasocialmascotas.seguridad.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.Instant;

@Entity
@Table(name = "recuperacion_contrasena_token")
public class RecuperacionContrasenaToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recuperacion_contrasena_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "usuario_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_recuperacion_contrasena_token_usuario")
    )
    private Usuario usuario;

    @JdbcTypeCode(Types.CHAR)
    @Column(
            name = "token_hash",
            nullable = false,
            length = 64,
            columnDefinition = "char(64)"
    )
    private String tokenHash;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_expiracion", nullable = false)
    private Instant fechaExpiracion;

    @Column(name = "fecha_uso")
    private Instant fechaUso;

    @Column(name = "fecha_invalidacion")
    private Instant fechaInvalidacion;

    protected RecuperacionContrasenaToken() {
        // Requerido por JPA.
    }

    public RecuperacionContrasenaToken(
            Usuario usuario,
            String tokenHash,
            Instant fechaCreacion,
            Instant fechaExpiracion
    ) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.fechaCreacion = fechaCreacion;
        this.fechaExpiracion = fechaExpiracion;
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public Instant getFechaExpiracion() {
        return fechaExpiracion;
    }

    public Instant getFechaUso() {
        return fechaUso;
    }

    public Instant getFechaInvalidacion() {
        return fechaInvalidacion;
    }

    public void marcarUsado(Instant fechaUso) {
        this.fechaUso = fechaUso;
    }

    public void invalidar(Instant fechaInvalidacion) {
        this.fechaInvalidacion = fechaInvalidacion;
    }
}
