package com.osmascotas.obrasocialmascotas.clientesmascotas.web;

import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.MascotaBusquedaResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.dto.MascotaDetalleVeterinarioResponse;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.BuscarMascotasService;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ConsultarMascotaVeterinarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/veterinarios/mascotas")
public class ConsultaMascotaVeterinarioController {

    private final BuscarMascotasService buscarMascotasService;
    private final ConsultarMascotaVeterinarioService consultarMascotaVeterinarioService;

    public ConsultaMascotaVeterinarioController(
            BuscarMascotasService buscarMascotasService,
            ConsultarMascotaVeterinarioService consultarMascotaVeterinarioService
    ) {
        this.buscarMascotasService = buscarMascotasService;
        this.consultarMascotaVeterinarioService = consultarMascotaVeterinarioService;
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasRole('VETERINARIO')")
    public ResponseEntity<List<MascotaBusquedaResponse>> buscar(
            @RequestParam(required = false) Long mascotaId,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String dniTitular
    ) {
        return ResponseEntity.ok(buscarMascotasService.buscar(mascotaId, nombre, dniTitular));
    }

    @GetMapping("/{mascotaId}")
    public ResponseEntity<MascotaDetalleVeterinarioResponse> consultar(@PathVariable Long mascotaId) {
        return ResponseEntity.ok(consultarMascotaVeterinarioService.consultar(mascotaId));
    }
}
