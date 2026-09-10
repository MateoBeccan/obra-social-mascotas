package com.osmascotas.obrasocialmascotas.seguridad.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CrearCuentaClienteRequest {

    @NotBlank
    @Size(max = 150)
    private final String identificadorAcceso;

    @NotBlank
    @Email
    @Size(max = 254)
    private final String emailRecuperacion;

    @JsonCreator
    public CrearCuentaClienteRequest(
            @JsonProperty("identificadorAcceso") String identificadorAcceso,
            @JsonProperty("emailRecuperacion") String emailRecuperacion
    ) {
        this.identificadorAcceso = identificadorAcceso == null ? null : identificadorAcceso.trim();
        this.emailRecuperacion = emailRecuperacion == null ? null : emailRecuperacion.trim();
    }

    public String identificadorAcceso() {
        return identificadorAcceso;
    }

    public String emailRecuperacion() {
        return emailRecuperacion;
    }

    @JsonAnySetter
    void rechazarCampoNoPermitido(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo no permitido: " + nombre);
    }
}
