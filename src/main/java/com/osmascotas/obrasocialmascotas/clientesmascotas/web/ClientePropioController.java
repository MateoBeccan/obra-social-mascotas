package com.osmascotas.obrasocialmascotas.clientesmascotas.web;

import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ActualizarContactoClienteRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ClienteResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.MascotaResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ActualizarContactoClienteService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ClienteAsociadoNoEncontradoException;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ConsultarClientePropioService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ConsultarMascotasPropiasService;
import com.osmascotas.obrasocialmascotas.seguridad.dto.ErrorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClientePropioController {

    private final ConsultarClientePropioService consultarClientePropioService;
    private final ActualizarContactoClienteService actualizarContactoClienteService;
    private final ConsultarMascotasPropiasService consultarMascotasPropiasService;

    public ClientePropioController(
            ConsultarClientePropioService consultarClientePropioService,
            ActualizarContactoClienteService actualizarContactoClienteService,
            ConsultarMascotasPropiasService consultarMascotasPropiasService
    ) {
        this.consultarClientePropioService = consultarClientePropioService;
        this.actualizarContactoClienteService = actualizarContactoClienteService;
        this.consultarMascotasPropiasService = consultarMascotasPropiasService;
    }

    @GetMapping("/me")
    public ResponseEntity<ClienteResponse> consultar() {
        return ResponseEntity.ok(consultarClientePropioService.consultar());
    }

    @PutMapping("/me/contacto")
    public ResponseEntity<ClienteResponse> actualizarContacto(
            @Valid @RequestBody ActualizarContactoClienteRequest request
    ) {
        return ResponseEntity.ok(actualizarContactoClienteService.actualizar(request));
    }

    @GetMapping("/me/mascotas")
    public ResponseEntity<List<MascotaResponse>> consultarMascotas() {
        return ResponseEntity.ok(consultarMascotasPropiasService.consultar());
    }

    @ExceptionHandler(ClienteAsociadoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarClienteAsociadoNoEncontrado() {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No se encontro el perfil de Cliente asociado a la cuenta"));
    }
}
