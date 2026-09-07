package com.osmascotas.obrasocialmascotas.auditoria.service;

import com.osmascotas.obrasocialmascotas.auditoria.domain.RegistroAuditoria;
import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.UsuarioAutenticadoNoEncontradoException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AuditoriaService {

    private final RegistroAuditoriaRepository registroAuditoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public AuditoriaService(
            RegistroAuditoriaRepository registroAuditoriaRepository,
            UsuarioRepository usuarioRepository,
            Clock clock
    ) {
        this.registroAuditoriaRepository = registroAuditoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarOperacionUsuario(
            String operacion,
            String entidadAfectada,
            String identificadorRegistroAfectado,
            String estadoAnterior,
            String estadoNuevo,
            String detalleCambio,
            String motivo
    ) {
        Usuario usuarioResponsable = obtenerUsuarioAutenticado();
        RegistroAuditoria registroAuditoria = RegistroAuditoria.registrarUsuario(
                usuarioResponsable,
                clock.instant(),
                operacion,
                entidadAfectada,
                identificadorRegistroAfectado,
                estadoAnterior,
                estadoNuevo,
                detalleCambio,
                motivo
        );

        registroAuditoriaRepository.save(registroAuditoria);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarOperacionSistema(
            String operacion,
            String entidadAfectada,
            String identificadorRegistroAfectado,
            String estadoAnterior,
            String estadoNuevo,
            String detalleCambio,
            String motivo
    ) {
        RegistroAuditoria registroAuditoria = RegistroAuditoria.registrarSistema(
                clock.instant(),
                operacion,
                entidadAfectada,
                identificadorRegistroAfectado,
                estadoAnterior,
                estadoNuevo,
                detalleCambio,
                motivo
        );

        registroAuditoriaRepository.save(registroAuditoria);
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
}
