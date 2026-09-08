package com.osmascotas.obrasocialmascotas.clientesmascotas.service;

import com.osmascotas.obrasocialmascotas.auditoria.service.AuditoriaService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Mascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.CrearMascotaRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.MascotaResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.ClienteRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.MascotaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.TitularidadMascotaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

@Service
public class RegistrarMascotaService {

    private static final String OPERACION_REGISTRAR_MASCOTA = "REGISTRAR_MASCOTA";
    private static final String ENTIDAD_MASCOTA = "MASCOTA";

    private final ClienteRepository clienteRepository;
    private final MascotaRepository mascotaRepository;
    private final TitularidadMascotaRepository titularidadMascotaRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public RegistrarMascotaService(
            ClienteRepository clienteRepository,
            MascotaRepository mascotaRepository,
            TitularidadMascotaRepository titularidadMascotaRepository,
            AuditoriaService auditoriaService,
            Clock clock
    ) {
        this.clienteRepository = clienteRepository;
        this.mascotaRepository = mascotaRepository;
        this.titularidadMascotaRepository = titularidadMascotaRepository;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public MascotaResponse registrar(CrearMascotaRequest request) {
        Objects.requireNonNull(request, "La solicitud de creacion de mascota es obligatoria.");

        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(ClienteNoEncontradoException::new);
        LocalDate fechaActual = LocalDate.now(clock);
        Mascota mascota = new Mascota(
                request.nombre(),
                request.especie(),
                request.raza(),
                request.sexo(),
                request.fechaNacimiento(),
                null
        );
        Mascota mascotaGuardada = mascotaRepository.saveAndFlush(mascota);
        TitularidadMascota titularidad = new TitularidadMascota(
                mascotaGuardada,
                cliente,
                fechaActual,
                null,
                null,
                null
        );
        TitularidadMascota titularidadGuardada = titularidadMascotaRepository.saveAndFlush(titularidad);

        auditoriaService.registrarOperacionUsuario(
                OPERACION_REGISTRAR_MASCOTA,
                ENTIDAD_MASCOTA,
                mascotaGuardada.getId().toString(),
                null,
                null,
                null,
                null
        );

        return new MascotaResponse(
                mascotaGuardada.getId(),
                mascotaGuardada.getNombre(),
                mascotaGuardada.getEspecie(),
                mascotaGuardada.getRaza(),
                mascotaGuardada.getSexo(),
                mascotaGuardada.getFechaNacimiento(),
                cliente.getId(),
                titularidadGuardada.getId(),
                titularidadGuardada.getFechaDesde()
        );
    }
}
