package com.osmascotas.obrasocialmascotas.clientesmascotas.domain;

import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
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

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "titularidad_mascota")
public class TitularidadMascota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "titularidad_mascota_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "mascota_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_titularidad_mascota_mascota")
    )
    private Mascota mascota;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "cliente_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_titularidad_mascota_cliente")
    )
    private Cliente cliente;

    @Column(
            name = "fecha_desde",
            nullable = false
    )
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta")
    private LocalDate fechaHasta;

    @Column(
            name = "motivo_cambio",
            columnDefinition = "TEXT"
    )
    private String motivoCambio;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(
            name = "usuario_responsable_id",
            foreignKey = @ForeignKey(name = "fk_titularidad_mascota_usuario_responsable")
    )
    private Usuario usuarioResponsable;

    protected TitularidadMascota() {
        // Requerido por JPA.
    }

    public TitularidadMascota(
            Mascota mascota,
            Cliente cliente,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String motivoCambio,
            Usuario usuarioResponsable
    ) {
        this.mascota = Objects.requireNonNull(mascota, "La mascota es obligatoria.");
        this.cliente = Objects.requireNonNull(cliente, "El cliente es obligatorio.");
        this.fechaDesde = Objects.requireNonNull(fechaDesde, "La fecha desde es obligatoria.");
        validarFechaHasta(fechaDesde, fechaHasta);
        this.fechaHasta = fechaHasta;
        this.motivoCambio = validarOpcional(motivoCambio, "El motivo de cambio no puede estar vacio.");
        this.usuarioResponsable = usuarioResponsable;
    }

    public Long getId() {
        return id;
    }

    public Mascota getMascota() {
        return mascota;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public LocalDate getFechaDesde() {
        return fechaDesde;
    }

    public LocalDate getFechaHasta() {
        return fechaHasta;
    }

    public String getMotivoCambio() {
        return motivoCambio;
    }

    public Usuario getUsuarioResponsable() {
        return usuarioResponsable;
    }

    private static void validarFechaHasta(LocalDate fechaDesde, LocalDate fechaHasta) {
        if (fechaHasta != null && !fechaHasta.isAfter(fechaDesde)) {
            throw new IllegalArgumentException("La fecha hasta debe ser posterior a la fecha desde.");
        }
    }

    private static String validarOpcional(String valor, String mensaje) {
        if (valor != null && valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor == null ? null : valor.trim();
    }
}
