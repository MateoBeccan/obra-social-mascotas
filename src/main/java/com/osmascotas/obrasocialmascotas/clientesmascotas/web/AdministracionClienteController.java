package com.osmascotas.obrasocialmascotas.clientesmascotas.web;

import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ClienteResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.CrearClienteRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ClienteDniDuplicadoException;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.RegistrarClienteService;
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
@RequestMapping("/api/admin/clientes")
public class AdministracionClienteController {

    private final RegistrarClienteService registrarClienteService;

    public AdministracionClienteController(RegistrarClienteService registrarClienteService) {
        this.registrarClienteService = registrarClienteService;
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> registrar(
            @Valid @RequestBody CrearClienteRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(registrarClienteService.registrar(request));
    }

    @ExceptionHandler(ClienteDniDuplicadoException.class)
    public ResponseEntity<ErrorResponse> manejarClienteDniDuplicado() {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Ya existe un Cliente con el mismo DNI"));
    }
}
