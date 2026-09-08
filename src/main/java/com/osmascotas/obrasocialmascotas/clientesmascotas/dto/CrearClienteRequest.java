package com.osmascotas.obrasocialmascotas.clientesmascotas.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CrearClienteRequest {

    @NotBlank
    @Size(max = 50)
    private final String dni;

    @NotBlank
    @Size(max = 120)
    private final String nombre;

    @NotBlank
    @Size(max = 120)
    private final String apellido;

    @Email
    @Size(max = 254)
    @Pattern(regexp = ".*\\S.*")
    private final String correoElectronico;

    @Size(max = 50)
    @Pattern(regexp = ".*\\S.*")
    private final String telefono;

    @Size(max = 500)
    @Pattern(regexp = ".*\\S.*")
    private final String domicilio;

    @JsonCreator
    public CrearClienteRequest(
            @JsonProperty("dni") String dni,
            @JsonProperty("nombre") String nombre,
            @JsonProperty("apellido") String apellido,
            @JsonProperty("correoElectronico") String correoElectronico,
            @JsonProperty("telefono") String telefono,
            @JsonProperty("domicilio") String domicilio
    ) {
        this.dni = normalizar(dni);
        this.nombre = normalizar(nombre);
        this.apellido = normalizar(apellido);
        this.correoElectronico = normalizar(correoElectronico);
        this.telefono = normalizar(telefono);
        this.domicilio = normalizar(domicilio);
    }

    public String dni() {
        return dni;
    }

    public String nombre() {
        return nombre;
    }

    public String apellido() {
        return apellido;
    }

    public String correoElectronico() {
        return correoElectronico;
    }

    public String telefono() {
        return telefono;
    }

    public String domicilio() {
        return domicilio;
    }

    @JsonAnySetter
    void rechazarCampoNoPermitido(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo no permitido: " + nombre);
    }

    private static String normalizar(String valor) {
        return valor == null ? null : valor.trim();
    }
}
