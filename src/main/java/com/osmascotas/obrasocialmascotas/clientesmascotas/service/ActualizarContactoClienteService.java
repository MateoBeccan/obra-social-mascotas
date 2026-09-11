package com.osmascotas.obrasocialmascotas.clientesmascotas.service;

import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ActualizarContactoClienteRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ClienteResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.ClienteRepository;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.UsuarioAutenticadoNoEncontradoException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class ActualizarContactoClienteService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;

    public ActualizarContactoClienteService(
            UsuarioRepository usuarioRepository,
            ClienteRepository clienteRepository
    ) {
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENTE')")
    public ClienteResponse actualizar(ActualizarContactoClienteRequest request) {
        Objects.requireNonNull(request, "La solicitud de actualizacion de contacto es obligatoria.");

        Usuario usuario = obtenerUsuarioAutenticado();
        Cliente cliente = clienteRepository.buscarPorUsuarioId(usuario.getId())
                .orElseThrow(ClienteAsociadoNoEncontradoException::new);

        cliente.actualizarDatosContacto(
                request.correoElectronico(),
                request.telefono(),
                request.domicilio()
        );

        return toResponse(cliente);
    }

    private Usuario obtenerUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new IllegalStateException("No existe una identidad autenticada valida.");
        }

        return usuarioRepository.buscarPorIdentificadorAcceso(authentication.getName())
                .orElseThrow(UsuarioAutenticadoNoEncontradoException::new);
    }

    private ClienteResponse toResponse(Cliente cliente) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getDni(),
                cliente.getNombre(),
                cliente.getApellido(),
                cliente.getCorreoElectronico(),
                cliente.getTelefono(),
                cliente.getDomicilio(),
                cliente.getUsuario().getId()
        );
    }
}
