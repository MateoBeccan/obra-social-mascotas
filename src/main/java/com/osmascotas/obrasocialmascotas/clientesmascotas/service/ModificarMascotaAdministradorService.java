package com.osmascotas.obrasocialmascotas.clientesmascotas.service;

import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Mascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ActualizarMascotaAdministrativaRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.MascotaDetalleAdministradorResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.TitularAdministradorResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.TitularidadActualAdministradorResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.MascotaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.TitularidadMascotaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ModificarMascotaAdministradorService {

    private static final String OPERACION_MODIFICAR_MASCOTA = "MODIFICAR_MASCOTA_ADMINISTRATIVA";
    private static final String ENTIDAD_MASCOTA = "MASCOTA";

    private final MascotaRepository mascotaRepository;
    private final TitularidadMascotaRepository titularidadMascotaRepository;
    private final AuditoriaService auditoriaService;

    public ModificarMascotaAdministradorService(
            MascotaRepository mascotaRepository,
            TitularidadMascotaRepository titularidadMascotaRepository,
            AuditoriaService auditoriaService
    ) {
        this.mascotaRepository = mascotaRepository;
        this.titularidadMascotaRepository = titularidadMascotaRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public MascotaDetalleAdministradorResponse modificar(Long mascotaId, ActualizarMascotaAdministrativaRequest request) {
        Objects.requireNonNull(request, "La solicitud de modificacion de mascota es obligatoria.");

        Mascota mascota = mascotaRepository.findById(mascotaId)
                .orElseThrow(MascotaNoEncontradaException::new);
        List<String> camposModificados = camposModificados(mascota, request);
        if (camposModificados.isEmpty()) {
            return toResponse(titularidadVigente(mascota.getId()));
        }

        mascota.actualizarDatosAdministrativos(
                request.nombre(),
                request.especie(),
                request.raza(),
                request.sexo(),
                request.fechaNacimiento()
        );

        Mascota mascotaGuardada = mascotaRepository.saveAndFlush(mascota);
        auditoriaService.registrarOperacionUsuario(
                OPERACION_MODIFICAR_MASCOTA,
                ENTIDAD_MASCOTA,
                mascotaGuardada.getId().toString(),
                null,
                null,
                "camposModificados=" + String.join(",", camposModificados),
                null
        );

        return toResponse(titularidadVigente(mascotaGuardada.getId()));
    }

    private TitularidadMascota titularidadVigente(Long mascotaId) {
        return titularidadMascotaRepository.buscarTitularidadVigentePorMascotaId(mascotaId)
                .orElseThrow(MascotaNoEncontradaException::new);
    }

    private List<String> camposModificados(Mascota mascota, ActualizarMascotaAdministrativaRequest request) {
        List<String> campos = new ArrayList<>();
        agregarSiCambio(campos, "nombre", mascota.getNombre(), request.nombre());
        agregarSiCambio(campos, "especie", mascota.getEspecie(), request.especie());
        agregarSiCambio(campos, "raza", mascota.getRaza(), request.raza());
        agregarSiCambio(campos, "sexo", mascota.getSexo(), request.sexo());
        agregarSiCambio(campos, "fechaNacimiento", mascota.getFechaNacimiento(), request.fechaNacimiento());
        return campos;
    }

    private void agregarSiCambio(List<String> campos, String campo, Object actual, Object nuevo) {
        if (!Objects.equals(actual, nuevo)) {
            campos.add(campo);
        }
    }

    private MascotaDetalleAdministradorResponse toResponse(TitularidadMascota titularidad) {
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
