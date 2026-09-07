package com.osmascotas.obrasocialmascotas.seguridad.service;

import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.dto.CambiarContrasenaRequest;
import com.osmascotas.obrasocialmascotas.seguridad.dto.LoginRequest;
import com.osmascotas.obrasocialmascotas.seguridad.dto.LoginResponse;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            loginRequest.identificadorAcceso(),
                            loginRequest.contrasena()
                    )
            );

            return new LoginResponse(
                    jwtService.generarAccessToken(authentication),
                    TOKEN_TYPE,
                    jwtService.getExpiresIn()
            );
        } catch (AuthenticationException ex) {
            throw new CredencialesInvalidasException();
        }
    }

    @Transactional
    public void cambiarContrasena(Authentication authentication, CambiarContrasenaRequest request) {
        Usuario usuario = usuarioRepository.buscarPorIdentificadorAcceso(authentication.getName())
                .orElseThrow(UsuarioAutenticadoNoEncontradoException::new);

        if (!passwordEncoder.matches(request.contrasenaActual(), usuario.getContrasenaHash())) {
            throw new ContrasenaActualInvalidaException();
        }

        usuario.cambiarContrasenaHash(passwordEncoder.encode(request.contrasenaNueva()));
    }
}
