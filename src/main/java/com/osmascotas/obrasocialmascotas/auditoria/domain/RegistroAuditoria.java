package com.osmascotas.obrasocialmascotas.auditoria.domain;

import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Objects;

@Entity
@Immutable
@Table(name = "registro_auditoria")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "registro_auditoria_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "usuario_responsable_id")
    private Usuario usuarioResponsable;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "origen_operacion",
            nullable = false,
            length = 20
    )
    private OrigenOperacion origenOperacion;

    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;

    @Column(
            name = "operacion",
            nullable = false,
            length = 100
    )
    private String operacion;

    @Column(
            name = "entidad_afectada",
            nullable = false,
            length = 100
    )
    private String entidadAfectada;

    @Column(
            name = "identificador_registro_afectado",
            nullable = false,
            length = 100
    )
    private String identificadorRegistroAfectado;

    @Column(name = "estado_anterior", length = 50)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", length = 50)
    private String estadoNuevo;

    @Column(name = "detalle_cambio", columnDefinition = "TEXT")
    private String detalleCambio;

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    protected RegistroAuditoria() {
        // Requerido por JPA.
    }

    private RegistroAuditoria(
            Usuario usuarioResponsable,
            OrigenOperacion origenOperacion,
            Instant fechaHora,
            String operacion,
            String entidadAfectada,
            String identificadorRegistroAfectado,
            String estadoAnterior,
            String estadoNuevo,
            String detalleCambio,
            String motivo
    ) {
        this.usuarioResponsable = usuarioResponsable;
        this.origenOperacion = Objects.requireNonNull(origenOperacion, "El origen de operacion es obligatorio.");
        this.fechaHora = Objects.requireNonNull(fechaHora, "La fecha y hora es obligatoria.");
        this.operacion = validarObligatorio(operacion, "La operacion es obligatoria.");
        this.entidadAfectada = validarObligatorio(entidadAfectada, "La entidad afectada es obligatoria.");
        this.identificadorRegistroAfectado = validarObligatorio(
                identificadorRegistroAfectado,
                "El identificador del registro afectado es obligatorio."
        );
        this.estadoAnterior = validarOpcional(estadoAnterior, "El estado anterior no puede estar vacio.");
        this.estadoNuevo = validarOpcional(estadoNuevo, "El estado nuevo no puede estar vacio.");
        this.detalleCambio = validarOpcional(detalleCambio, "El detalle de cambio no puede estar vacio.");
        this.motivo = validarOpcional(motivo, "El motivo no puede estar vacio.");
    }

    public static RegistroAuditoria registrarUsuario(
            Usuario usuarioResponsable,
            Instant fechaHora,
            String operacion,
            String entidadAfectada,
            String identificadorRegistroAfectado,
            String estadoAnterior,
            String estadoNuevo,
            String detalleCambio,
            String motivo
    ) {
        Objects.requireNonNull(usuarioResponsable, "El usuario responsable es obligatorio.");
        return new RegistroAuditoria(
                usuarioResponsable,
                OrigenOperacion.USUARIO,
                fechaHora,
                operacion,
                entidadAfectada,
                identificadorRegistroAfectado,
                estadoAnterior,
                estadoNuevo,
                detalleCambio,
                motivo
        );
    }

    public static RegistroAuditoria registrarSistema(
            Instant fechaHora,
            String operacion,
            String entidadAfectada,
            String identificadorRegistroAfectado,
            String estadoAnterior,
            String estadoNuevo,
            String detalleCambio,
            String motivo
    ) {
        return new RegistroAuditoria(
                null,
                OrigenOperacion.SISTEMA,
                fechaHora,
                operacion,
                entidadAfectada,
                identificadorRegistroAfectado,
                estadoAnterior,
                estadoNuevo,
                detalleCambio,
                motivo
        );
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuarioResponsable() {
        return usuarioResponsable;
    }

    public OrigenOperacion getOrigenOperacion() {
        return origenOperacion;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public String getOperacion() {
        return operacion;
    }

    public String getEntidadAfectada() {
        return entidadAfectada;
    }

    public String getIdentificadorRegistroAfectado() {
        return identificadorRegistroAfectado;
    }

    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    public String getEstadoNuevo() {
        return estadoNuevo;
    }

    public String getDetalleCambio() {
        return detalleCambio;
    }

    public String getMotivo() {
        return motivo;
    }

    private static String validarObligatorio(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor;
    }

    private static String validarOpcional(String valor, String mensaje) {
        if (valor != null && valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor;
    }
}
