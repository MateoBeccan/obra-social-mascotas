package com.osmascotas.obrasocialmascotas.clientesmascotas.service;

import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Mascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.MascotaBusquedaResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.TitularIdentificacionResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.TitularidadMascotaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BuscarMascotasService {

    private final TitularidadMascotaRepository titularidadMascotaRepository;

    public BuscarMascotasService(TitularidadMascotaRepository titularidadMascotaRepository) {
        this.titularidadMascotaRepository = titularidadMascotaRepository;
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'VETERINARIO')")
    public List<MascotaBusquedaResponse> buscar(Long mascotaId, String nombre, String dniTitular) {
        CriteriosNormalizados criterios = normalizarYValidar(mascotaId, nombre, dniTitular);

        return titularidadMascotaRepository
                .buscarVigentesPorCriterios(criterios.mascotaId(), criterios.nombre(), criterios.dniTitular())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private CriteriosNormalizados normalizarYValidar(Long mascotaId, String nombre, String dniTitular) {
        if (mascotaId != null && mascotaId <= 0) {
            throw new CriteriosBusquedaMascotaInvalidosException("El id de mascota debe ser mayor a cero");
        }

        String nombreNormalizado = normalizar(nombre);
        String dniTitularNormalizado = normalizar(dniTitular);

        if (mascotaId == null && nombreNormalizado == null && dniTitularNormalizado == null) {
            throw new CriteriosBusquedaMascotaInvalidosException("Debe indicar al menos un criterio de busqueda");
        }

        return new CriteriosNormalizados(mascotaId, nombreNormalizado, dniTitularNormalizado);
    }

    private String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private MascotaBusquedaResponse toResponse(TitularidadMascota titularidad) {
        Mascota mascota = titularidad.getMascota();
        Cliente cliente = titularidad.getCliente();

        return new MascotaBusquedaResponse(
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

    private record CriteriosNormalizados(Long mascotaId, String nombre, String dniTitular) {
    }
}
