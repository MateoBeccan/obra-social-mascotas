package com.osmascotas.obrasocialmascotas.seguridad.web;

import com.osmascotas.obrasocialmascotas.seguridad.domain.TransicionEstadoUsuarioInvalidaException;
import com.osmascotas.obrasocialmascotas.seguridad.dto.CambiarEstadoUsuarioRequest;
import com.osmascotas.obrasocialmascotas.seguridad.dto.ErrorResponse;
import com.osmascotas.obrasocialmascotas.seguridad.dto.UsuarioAdministracionResponse;
import com.osmascotas.obrasocialmascotas.seguridad.service.AdministracionUsuarioService;
import com.osmascotas.obrasocialmascotas.seguridad.service.UsuarioNoEncontradoException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/usuarios")
public class AdministracionUsuarioController {

    private final AdministracionUsuarioService administracionUsuarioService;

    public AdministracionUsuarioController(AdministracionUsuarioService administracionUsuarioService) {
        this.administracionUsuarioService = administracionUsuarioService;
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
}
