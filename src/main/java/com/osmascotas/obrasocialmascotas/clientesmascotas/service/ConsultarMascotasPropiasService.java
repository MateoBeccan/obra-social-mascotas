package com.osmascotas.obrasocialmascotas.clientesmascotas.service;

import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Mascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.MascotaResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.ClienteRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.TitularidadMascotaRepository;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.UsuarioAutenticadoNoEncontradoException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ConsultarMascotasPropiasService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final TitularidadMascotaRepository titularidadMascotaRepository;

    public ConsultarMascotasPropiasService(
            UsuarioRepository usuarioRepository,
            ClienteRepository clienteRepository,
            TitularidadMascotaRepository titularidadMascotaRepository
    ) {
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
        this.titularidadMascotaRepository = titularidadMascotaRepository;
    }

    @PreAuthorize("hasRole('CLIENTE')")
    public List<MascotaResponse> consultar() {
        Usuario usuario = obtenerUsuarioAutenticado();
        Cliente cliente = clienteRepository.buscarPorUsuarioId(usuario.getId())
                .orElseThrow(ClienteAsociadoNoEncontradoException::new);

        return titularidadMascotaRepository.buscarVigentesPorClienteId(cliente.getId())
                .stream()
                .map(this::toResponse)
                .toList();
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

    private MascotaResponse toResponse(TitularidadMascota titularidad) {
        Mascota mascota = titularidad.getMascota();

        return new MascotaResponse(
                mascota.getId(),
                mascota.getNombre(),
                mascota.getEspecie(),
                mascota.getRaza(),
                mascota.getSexo(),
                mascota.getFechaNacimiento(),
                titularidad.getCliente().getId(),
                titularidad.getId(),
                titularidad.getFechaDesde()
        );
    }
}
