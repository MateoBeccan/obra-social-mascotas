package com.osmascotas.obrasocialmascotas.clientesmascotas.service;

import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Mascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.MascotaDetalleVeterinarioResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.TitularIdentificacionResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.TitularidadMascotaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsultarMascotaVeterinarioService {

    private final TitularidadMascotaRepository titularidadMascotaRepository;

    public ConsultarMascotaVeterinarioService(TitularidadMascotaRepository titularidadMascotaRepository) {
        this.titularidadMascotaRepository = titularidadMascotaRepository;
    }

    @PreAuthorize("hasRole('VETERINARIO')")
    public MascotaDetalleVeterinarioResponse consultar(Long mascotaId) {
        TitularidadMascota titularidad = titularidadMascotaRepository.buscarTitularidadVigentePorMascotaId(mascotaId)
                .orElseThrow(MascotaNoEncontradaException::new);
        Mascota mascota = titularidad.getMascota();
        Cliente cliente = titularidad.getCliente();

        return new MascotaDetalleVeterinarioResponse(
                mascota.getId(),
                mascota.getNombre(),
                mascota.getEspecie(),
                mascota.getRaza(),
                mascota.getSexo(),
                mascota.getFechaNacimiento(),
                new TitularIdentificacionResponse(
                        cliente.getDni(),
                        cliente.getNombre(),
                        cliente.getApellido()
                )
        );
    }
}
