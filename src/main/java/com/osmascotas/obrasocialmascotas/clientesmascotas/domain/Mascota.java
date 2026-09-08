package com.osmascotas.obrasocialmascotas.clientesmascotas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "mascota")
public class Mascota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mascota_id")
    private Long id;

    @Column(
            name = "nombre",
            nullable = false,
            length = 120
    )
    private String nombre;

    @Column(
            name = "especie",
            nullable = false,
            length = 80
    )
    private String especie;

    @Column(
            name = "raza",
            length = 120
    )
    private String raza;

    @Column(
            name = "sexo",
            length = 30
    )
    private String sexo;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(
            name = "fotografia_objeto_key",
            length = 1024
    )
    private String fotografiaObjetoKey;

    protected Mascota() {
        // Requerido por JPA.
    }

    public Mascota(
            String nombre,
            String especie,
            String raza,
            String sexo,
            LocalDate fechaNacimiento,
            String fotografiaObjetoKey
    ) {
        this.nombre = validarObligatorio(nombre, "El nombre es obligatorio.");
        this.especie = validarObligatorio(especie, "La especie es obligatoria.");
        this.raza = validarOpcional(raza, "La raza no puede estar vacia.");
        this.sexo = validarOpcional(sexo, "El sexo no puede estar vacio.");
        this.fechaNacimiento = fechaNacimiento;
        this.fotografiaObjetoKey = validarOpcional(
                fotografiaObjetoKey,
                "La fotografia objeto key no puede estar vacia."
        );
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEspecie() {
        return especie;
    }

    public String getRaza() {
        return raza;
    }

    public String getSexo() {
        return sexo;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public String getFotografiaObjetoKey() {
        return fotografiaObjetoKey;
    }

    private static String validarObligatorio(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor.trim();
    }

    private static String validarOpcional(String valor, String mensaje) {
        if (valor != null && valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor == null ? null : valor.trim();
    }
}
