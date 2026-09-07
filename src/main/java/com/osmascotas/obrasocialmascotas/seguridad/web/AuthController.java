package com.osmascotas.obrasocialmascotas.seguridad.web;

import com.osmascotas.obrasocialmascotas.seguridad.dto.CambiarContrasenaRequest;
import com.osmascotas.obrasocialmascotas.seguridad.dto.ForgotPasswordRequest;
import com.osmascotas.obrasocialmascotas.seguridad.dto.ForgotPasswordResponse;
import com.osmascotas.obrasocialmascotas.seguridad.dto.LoginErrorResponse;
import com.osmascotas.obrasocialmascotas.seguridad.dto.LoginRequest;
import com.osmascotas.obrasocialmascotas.seguridad.dto.LoginResponse;
import com.osmascotas.obrasocialmascotas.seguridad.service.AuthService;
import com.osmascotas.obrasocialmascotas.seguridad.service.ContrasenaActualInvalidaException;
import com.osmascotas.obrasocialmascotas.seguridad.service.CredencialesInvalidasException;
import com.osmascotas.obrasocialmascotas.seguridad.service.RecuperacionContrasenaService;
import com.osmascotas.obrasocialmascotas.seguridad.service.UsuarioAutenticadoNoEncontradoException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String MENSAJE_RECUPERACION =
            "Si la cuenta existe, se enviaron instrucciones de recuperacion.";

    private final AuthService authService;
    private final RecuperacionContrasenaService recuperacionContrasenaService;

    public AuthController(
            AuthService authService,
            RecuperacionContrasenaService recuperacionContrasenaService
    ) {
        this.authService = authService;
        this.recuperacionContrasenaService = recuperacionContrasenaService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest loginRequest) {
        return authService.login(loginRequest);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> solicitarRecuperacion(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        recuperacionContrasenaService.solicitarRecuperacion(request.identificadorAcceso());
        return ResponseEntity
                .accepted()
                .body(new ForgotPasswordResponse(MENSAJE_RECUPERACION));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> cambiarContrasena(
            Authentication authentication,
            @Valid @RequestBody CambiarContrasenaRequest request
    ) {
        authService.cambiarContrasena(authentication, request);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<LoginErrorResponse> manejarCredencialesInvalidas() {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new LoginErrorResponse("Credenciales invalidas"));
    }

    @ExceptionHandler(ContrasenaActualInvalidaException.class)
    public ResponseEntity<LoginErrorResponse> manejarContrasenaActualInvalida() {
        return ResponseEntity
                .badRequest()
                .body(new LoginErrorResponse("Contrasena actual invalida"));
    }

    @ExceptionHandler(UsuarioAutenticadoNoEncontradoException.class)
    public ResponseEntity<LoginErrorResponse> manejarUsuarioAutenticadoNoEncontrado() {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new LoginErrorResponse("Usuario autenticado no encontrado"));
    }
}
