package com.osmascotas.obrasocialmascotas.clientesmascotas.web;

import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.CrearMascotaRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.MascotaResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ClienteNoEncontradoException;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.RegistrarMascotaService;
import com.osmascotas.obrasocialmascotas.seguridad.dto.ErrorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/mascotas")
public class AdministracionMascotaController {

    private final RegistrarMascotaService registrarMascotaService;

    public AdministracionMascotaController(RegistrarMascotaService registrarMascotaService) {
        this.registrarMascotaService = registrarMascotaService;
    }

    @PostMapping
    public ResponseEntity<MascotaResponse> registrar(
            @Valid @RequestBody CrearMascotaRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(registrarMascotaService.registrar(request));
    }

    @ExceptionHandler(ClienteNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarClienteNoEncontrado() {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No existe el Cliente indicado"));
    }
}
