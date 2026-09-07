package com.osmascotas.obrasocialmascotas.seguridad.service;

import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorIdentificadorAcceso(String identificadorAcceso) {
        Objects.requireNonNull(identificadorAcceso, "El identificador de acceso es obligatorio.");
        return usuarioRepository.buscarPorIdentificadorAcceso(identificadorAcceso);
    }

    @Transactional(readOnly = true)
    public boolean existeIdentificadorAcceso(String identificadorAcceso) {
        Objects.requireNonNull(identificadorAcceso, "El identificador de acceso es obligatorio.");
        return usuarioRepository.existeIdentificadorAcceso(identificadorAcceso);
    }

    @Transactional
    public Usuario cambiarContrasenaHash(Long usuarioId, String nuevaContrasenaHash) {
        Objects.requireNonNull(usuarioId, "El id de usuario es obligatorio.");
        Objects.requireNonNull(nuevaContrasenaHash, "El hash de contrasena es obligatorio.");

        Usuario usuario = obtenerUsuario(usuarioId);
        usuario.cambiarContrasenaHash(nuevaContrasenaHash);
        return usuario;
    }

    private Usuario obtenerUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontradoException(usuarioId));
    }
}
