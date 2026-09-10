package com.osmascotas.obrasocialmascotas.clientesmascotas.web;

import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ClienteResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ClienteAsociadoNoEncontradoException;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ConsultarClientePropioService;
import com.osmascotas.obrasocialmascotas.seguridad.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clientes")
public class ClientePropioController {

    private final ConsultarClientePropioService consultarClientePropioService;

    public ClientePropioController(ConsultarClientePropioService consultarClientePropioService) {
        this.consultarClientePropioService = consultarClientePropioService;
    }

    @GetMapping("/me")
    public ResponseEntity<ClienteResponse> consultar() {
        return ResponseEntity.ok(consultarClientePropioService.consultar());
    }

    @ExceptionHandler(ClienteAsociadoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarClienteAsociadoNoEncontrado() {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No se encontro el perfil de Cliente asociado a la cuenta"));
    }
}
