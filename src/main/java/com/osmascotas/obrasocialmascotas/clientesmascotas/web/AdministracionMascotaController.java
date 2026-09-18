package com.osmascotas.obrasocialmascotas.clientesmascotas.web;

import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.CrearMascotaRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.ActualizarMascotaAdministrativaRequest;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.MascotaBusquedaResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.MascotaDetalleAdministradorResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.MascotaResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.BuscarMascotasService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ClienteNoEncontradoException;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ConsultarMascotaAdministradorService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ModificarMascotaAdministradorService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.RegistrarMascotaService;
import com.osmascotas.obrasocialmascotas.seguridad.dto.ErrorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/mascotas")
public class AdministracionMascotaController {

    private final RegistrarMascotaService registrarMascotaService;
    private final BuscarMascotasService buscarMascotasService;
    private final ConsultarMascotaAdministradorService consultarMascotaAdministradorService;
    private final ModificarMascotaAdministradorService modificarMascotaAdministradorService;

    public AdministracionMascotaController(
            RegistrarMascotaService registrarMascotaService,
            BuscarMascotasService buscarMascotasService,
            ConsultarMascotaAdministradorService consultarMascotaAdministradorService,
            ModificarMascotaAdministradorService modificarMascotaAdministradorService
    ) {
        this.registrarMascotaService = registrarMascotaService;
        this.buscarMascotasService = buscarMascotasService;
        this.consultarMascotaAdministradorService = consultarMascotaAdministradorService;
        this.modificarMascotaAdministradorService = modificarMascotaAdministradorService;
    }

    @PostMapping
    public ResponseEntity<MascotaResponse> registrar(
            @Valid @RequestBody CrearMascotaRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(registrarMascotaService.registrar(request));
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<MascotaBusquedaResponse>> buscar(
            @RequestParam(required = false) Long mascotaId,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String dniTitular
    ) {
        return ResponseEntity.ok(buscarMascotasService.buscar(mascotaId, nombre, dniTitular));
    }

    @GetMapping("/{mascotaId}")
    public ResponseEntity<MascotaDetalleAdministradorResponse> consultar(@PathVariable Long mascotaId) {
        return ResponseEntity.ok(consultarMascotaAdministradorService.consultar(mascotaId));
    }

    @PutMapping("/{mascotaId}")
    public ResponseEntity<MascotaDetalleAdministradorResponse> modificar(
            @PathVariable Long mascotaId,
            @Valid @RequestBody ActualizarMascotaAdministrativaRequest request
    ) {
        return ResponseEntity.ok(modificarMascotaAdministradorService.modificar(mascotaId, request));
    }

    @ExceptionHandler(ClienteNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarClienteNoEncontrado() {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No existe el Cliente indicado"));
    }
}
