package com.osmascotas.obrasocialmascotas.clientesmascotas.web;

import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ClienteResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.CrearClienteRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ActualizarClienteAdministrativoRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ClienteDniDuplicadoException;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ClienteNoEncontradoException;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ModificarClienteAdministradorService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.RegistrarClienteService;
import com.osmascotas.obrasocialmascotas.seguridad.dto.ErrorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/clientes")
public class AdministracionClienteController {

    private final RegistrarClienteService registrarClienteService;
    private final ModificarClienteAdministradorService modificarClienteAdministradorService;

    public AdministracionClienteController(
            RegistrarClienteService registrarClienteService,
            ModificarClienteAdministradorService modificarClienteAdministradorService
    ) {
        this.registrarClienteService = registrarClienteService;
        this.modificarClienteAdministradorService = modificarClienteAdministradorService;
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> registrar(
            @Valid @RequestBody CrearClienteRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(registrarClienteService.registrar(request));
    }

    @PutMapping("/{clienteId}")
    public ResponseEntity<ClienteResponse> modificar(
            @PathVariable Long clienteId,
            @Valid @RequestBody ActualizarClienteAdministrativoRequest request
    ) {
        return ResponseEntity.ok(modificarClienteAdministradorService.modificar(clienteId, request));
    }

    @ExceptionHandler(ClienteDniDuplicadoException.class)
    public ResponseEntity<ErrorResponse> manejarClienteDniDuplicado() {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Ya existe un Cliente con el mismo DNI"));
    }

    @ExceptionHandler(ClienteNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarClienteNoEncontrado() {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No existe el Cliente indicado"));
    }
}
