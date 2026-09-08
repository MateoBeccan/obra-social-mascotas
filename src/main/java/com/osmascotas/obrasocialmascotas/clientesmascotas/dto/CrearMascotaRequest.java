package com.osmascotas.obrasocialmascotas.clientesmascotas.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class CrearMascotaRequest {

    @NotNull
    @Positive
    private final Long clienteId;

    @NotBlank
    @Size(max = 120)
    private final String nombre;

    @NotBlank
    @Size(max = 80)
    private final String especie;

    @Size(max = 120)
    @Pattern(regexp = ".*\\S.*")
    private final String raza;

    @Size(max = 30)
    @Pattern(regexp = ".*\\S.*")
    private final String sexo;

    private final LocalDate fechaNacimiento;

    @JsonCreator
    public CrearMascotaRequest(
            @JsonProperty("clienteId") Long clienteId,
            @JsonProperty("nombre") String nombre,
            @JsonProperty("especie") String especie,
            @JsonProperty("raza") String raza,
            @JsonProperty("sexo") String sexo,
            @JsonProperty("fechaNacimiento") LocalDate fechaNacimiento
    ) {
        this.clienteId = clienteId;
        this.nombre = normalizar(nombre);
        this.especie = normalizar(especie);
        this.raza = normalizar(raza);
        this.sexo = normalizar(sexo);
        this.fechaNacimiento = fechaNacimiento;
    }

    public Long clienteId() {
        return clienteId;
    }

    public String nombre() {
        return nombre;
    }

    public String especie() {
        return especie;
    }

    public String raza() {
        return raza;
    }

    public String sexo() {
        return sexo;
    }

    public LocalDate fechaNacimiento() {
        return fechaNacimiento;
    }

    @JsonAnySetter
    void rechazarCampoNoPermitido(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo no permitido: " + nombre);
    }

    private static String normalizar(String valor) {
        return valor == null ? null : valor.trim();
    }
}
