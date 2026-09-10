package com.osmascotas.obrasocialmascotas.seguridad.web;

import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ClienteNoEncontradoException;
import com.osmascotas.obrasocialmascotas.seguridad.dto.CrearAdministradorRequest;
import com.osmascotas.obrasocialmascotas.seguridad.domain.TransicionEstadoUsuarioInvalidaException;
import com.osmascotas.obrasocialmascotas.seguridad.dto.CambiarEstadoUsuarioRequest;
import com.osmascotas.obrasocialmascotas.seguridad.dto.CrearCuentaClienteRequest;
import com.osmascotas.obrasocialmascotas.seguridad.dto.ErrorResponse;
import com.osmascotas.obrasocialmascotas.seguridad.dto.UsuarioAdministracionResponse;
import com.osmascotas.obrasocialmascotas.seguridad.service.AdministracionUsuarioService;
import com.osmascotas.obrasocialmascotas.seguridad.service.ClienteYaTieneCuentaException;
import com.osmascotas.obrasocialmascotas.seguridad.service.CuentaUsuarioDuplicadaException;
import com.osmascotas.obrasocialmascotas.seguridad.service.ProvisionamientoCuentaService;
import com.osmascotas.obrasocialmascotas.seguridad.service.UsuarioNoEncontradoException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/usuarios")
public class AdministracionUsuarioController {

    private final AdministracionUsuarioService administracionUsuarioService;
    private final ProvisionamientoCuentaService provisionamientoCuentaService;

    public AdministracionUsuarioController(
            AdministracionUsuarioService administracionUsuarioService,
            ProvisionamientoCuentaService provisionamientoCuentaService
    ) {
        this.administracionUsuarioService = administracionUsuarioService;
        this.provisionamientoCuentaService = provisionamientoCuentaService;
    }

    @GetMapping
    public List<UsuarioAdministracionResponse> listarUsuarios() {
        return administracionUsuarioService.listarUsuarios();
    }

    @PatchMapping("/{usuarioId}/estado")
    public UsuarioAdministracionResponse cambiarEstado(
            @PathVariable Long usuarioId,
            @Valid @RequestBody CambiarEstadoUsuarioRequest request
    ) {
        return administracionUsuarioService.cambiarEstado(usuarioId, request.operacion());
    }

    @PostMapping("/administradores")
    public ResponseEntity<UsuarioAdministracionResponse> crearAdministrador(
            @Valid @RequestBody CrearAdministradorRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(provisionamientoCuentaService.crearAdministrador(request));
    }

    @PostMapping("/clientes/{clienteId}")
    public ResponseEntity<UsuarioAdministracionResponse> crearCliente(
            @PathVariable Long clienteId,
            @Valid @RequestBody CrearCuentaClienteRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(provisionamientoCuentaService.crearCliente(clienteId, request));
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarUsuarioNoEncontrado() {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("Usuario no encontrado"));
    }

    @ExceptionHandler(TransicionEstadoUsuarioInvalidaException.class)
    public ResponseEntity<ErrorResponse> manejarTransicionInvalida() {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Transicion de estado de Usuario no permitida"));
    }

    @ExceptionHandler(CuentaUsuarioDuplicadaException.class)
    public ResponseEntity<ErrorResponse> manejarCuentaUsuarioDuplicada() {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Ya existe una cuenta equivalente"));
    }

    @ExceptionHandler(ClienteNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarClienteNoEncontrado() {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No existe el Cliente indicado"));
    }

    @ExceptionHandler(ClienteYaTieneCuentaException.class)
    public ResponseEntity<ErrorResponse> manejarClienteYaTieneCuenta() {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("El Cliente ya posee una cuenta asociada"));
    }
}
