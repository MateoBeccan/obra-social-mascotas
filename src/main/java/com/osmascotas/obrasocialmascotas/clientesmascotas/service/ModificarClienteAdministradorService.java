package com.osmascotas.obrasocialmascotas.clientesmascotas.service;

import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ActualizarClienteAdministrativoRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ClienteResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.ClienteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ModificarClienteAdministradorService {

    private static final String OPERACION_MODIFICAR_CLIENTE = "MODIFICAR_CLIENTE_ADMINISTRATIVO";
    private static final String ENTIDAD_CLIENTE = "CLIENTE";

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public ModificarClienteAdministradorService(
            ClienteRepository clienteRepository,
            AuditoriaService auditoriaService
    ) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ClienteResponse modificar(Long clienteId, ActualizarClienteAdministrativoRequest request) {
        Objects.requireNonNull(request, "La solicitud de modificacion de cliente es obligatoria.");

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(ClienteNoEncontradoException::new);

        if (clienteRepository.existeDniEnOtroCliente(cliente.getId(), request.dni())) {
            throw new ClienteDniDuplicadoException();
        }

        List<String> camposModificados = camposModificados(cliente, request);
        if (camposModificados.isEmpty()) {
            return toResponse(cliente);
        }

        cliente.actualizarDatosAdministrativos(
                request.dni(),
                request.nombre(),
                request.apellido(),
                request.correoElectronico(),
                request.telefono(),
                request.domicilio()
        );

        Cliente clienteGuardado = guardarCliente(cliente);
        auditoriaService.registrarOperacionUsuario(
                OPERACION_MODIFICAR_CLIENTE,
                ENTIDAD_CLIENTE,
                clienteGuardado.getId().toString(),
                null,
                null,
                "camposModificados=" + String.join(",", camposModificados),
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

    private List<String> camposModificados(Cliente cliente, ActualizarClienteAdministrativoRequest request) {
        List<String> campos = new ArrayList<>();
        agregarSiCambio(campos, "dni", cliente.getDni(), request.dni());
        agregarSiCambio(campos, "nombre", cliente.getNombre(), request.nombre());
        agregarSiCambio(campos, "apellido", cliente.getApellido(), request.apellido());
        agregarSiCambio(campos, "correoElectronico", cliente.getCorreoElectronico(), request.correoElectronico());
        agregarSiCambio(campos, "telefono", cliente.getTelefono(), request.telefono());
        agregarSiCambio(campos, "domicilio", cliente.getDomicilio(), request.domicilio());
        return campos;
    }

    private void agregarSiCambio(List<String> campos, String campo, Object actual, Object nuevo) {
        if (!Objects.equals(actual, nuevo)) {
            campos.add(campo);
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
