package com.osmascotas.obrasocialmascotas.seguridad.service;

import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.OperacionEstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.dto.UsuarioAdministracionResponse;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class AdministracionUsuarioService {

    private static final String ENTIDAD_AFECTADA = "USUARIO";

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    public AdministracionUsuarioService(
            UsuarioRepository usuarioRepository,
            AuditoriaService auditoriaService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<UsuarioAdministracionResponse> listarUsuarios() {
        return usuarioRepository.findAll(Sort.by(Sort.Direction.ASC, "identificadorAcceso"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public UsuarioAdministracionResponse cambiarEstado(
            Long usuarioId,
            OperacionEstadoUsuario operacion
    ) {
        Objects.requireNonNull(usuarioId, "El id de usuario es obligatorio.");
        Objects.requireNonNull(operacion, "La operacion de estado es obligatoria.");

        Usuario usuario = usuarioRepository.buscarPorIdParaActualizar(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontradoException(usuarioId));

        EstadoUsuario estadoAnterior = usuario.getEstadoUsuario();
        usuario.aplicarOperacionEstado(operacion);
        EstadoUsuario estadoNuevo = usuario.getEstadoUsuario();

        auditoriaService.registrarOperacionUsuario(
                codigoAuditoria(operacion),
                ENTIDAD_AFECTADA,
                usuario.getId().toString(),
                estadoAnterior.name(),
                estadoNuevo.name(),
                null,
                null
        );

        return toResponse(usuario);
    }

    private UsuarioAdministracionResponse toResponse(Usuario usuario) {
        return new UsuarioAdministracionResponse(
                usuario.getId(),
                usuario.getIdentificadorAcceso(),
                usuario.getRolUsuario(),
                usuario.getEstadoUsuario()
        );
    }

    private String codigoAuditoria(OperacionEstadoUsuario operacion) {
        return switch (operacion) {
            case HABILITAR -> "HABILITAR_USUARIO";
            case BLOQUEAR -> "BLOQUEAR_USUARIO";
            case DESBLOQUEAR -> "DESBLOQUEAR_USUARIO";
            case INACTIVAR -> "INACTIVAR_USUARIO";
        };
    }
}
