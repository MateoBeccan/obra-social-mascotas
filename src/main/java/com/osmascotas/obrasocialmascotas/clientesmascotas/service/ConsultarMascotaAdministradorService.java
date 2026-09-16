package com.osmascotas.obrasocialmascotas.clientesmascotas.service;

import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Mascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.MascotaDetalleAdministradorResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.TitularAdministradorResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.TitularidadActualAdministradorResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.TitularidadMascotaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsultarMascotaAdministradorService {

    private final TitularidadMascotaRepository titularidadMascotaRepository;

    public ConsultarMascotaAdministradorService(TitularidadMascotaRepository titularidadMascotaRepository) {
        this.titularidadMascotaRepository = titularidadMascotaRepository;
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public MascotaDetalleAdministradorResponse consultar(Long mascotaId) {
        TitularidadMascota titularidad = titularidadMascotaRepository.buscarTitularidadVigentePorMascotaId(mascotaId)
                .orElseThrow(MascotaNoEncontradaException::new);
        Mascota mascota = titularidad.getMascota();
        Cliente cliente = titularidad.getCliente();

        return new MascotaDetalleAdministradorResponse(
                mascota.getId(),
                mascota.getNombre(),
                mascota.getEspecie(),
                mascota.getRaza(),
                mascota.getSexo(),
                mascota.getFechaNacimiento(),
                new TitularAdministradorResponse(
                        cliente.getId(),
                        cliente.getDni(),
                        cliente.getNombre(),
                        cliente.getApellido(),
                        cliente.getCorreoElectronico(),
                        cliente.getTelefono(),
                        cliente.getDomicilio()
                ),
                new TitularidadActualAdministradorResponse(
                        titularidad.getId(),
                        titularidad.getFechaDesde()
                )
        );
    }
}
