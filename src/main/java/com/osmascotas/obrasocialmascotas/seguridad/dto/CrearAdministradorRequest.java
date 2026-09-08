package com.osmascotas.obrasocialmascotas.seguridad.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class CrearAdministradorRequest {

    @NotBlank
    private final String identificadorAcceso;

    @NotBlank
    @Email
    private final String emailRecuperacion;

    @JsonCreator
    public CrearAdministradorRequest(
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
