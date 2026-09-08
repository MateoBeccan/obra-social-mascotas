package com.osmascotas.obrasocialmascotas.clientesmascotas.service;

import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ClienteResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.CrearClienteRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.ClienteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class RegistrarClienteService {

    private static final String OPERACION_REGISTRAR_CLIENTE = "REGISTRAR_CLIENTE";
    private static final String ENTIDAD_CLIENTE = "CLIENTE";

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public RegistrarClienteService(
            ClienteRepository clienteRepository,
            AuditoriaService auditoriaService
    ) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ClienteResponse registrar(CrearClienteRequest request) {
        Objects.requireNonNull(request, "La solicitud de creacion de cliente es obligatoria.");

        if (clienteRepository.existeDni(request.dni())) {
            throw new ClienteDniDuplicadoException();
        }

        Cliente cliente = new Cliente(
                request.dni(),
                request.nombre(),
                request.apellido(),
                request.correoElectronico(),
                request.telefono(),
                request.domicilio()
        );
        Cliente clienteGuardado = guardarCliente(cliente);

        auditoriaService.registrarOperacionUsuario(
                OPERACION_REGISTRAR_CLIENTE,
                ENTIDAD_CLIENTE,
                clienteGuardado.getId().toString(),
                null,
                null,
                null,
                null
        );

        return toResponse(clienteGuardado);
    }

    private Cliente guardarCliente(Cliente cliente) {
        try {
            return clienteRepository.saveAndFlush(cliente);
        } catch (DataIntegrityViolationException ex) {
            throw new ClienteDniDuplicadoException();
        }
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
                cliente.getUsuario() == null ? null : cliente.getUsuario().getId()
        );
    }
}
