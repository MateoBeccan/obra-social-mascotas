package com.osmascotas.obrasocialmascotas.clientesmascotas.service;

import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Mascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.ClienteRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.MascotaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.TitularidadMascotaRepository;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.UsuarioAutenticadoNoEncontradoException;
import com.osmascotas.obrasocialmascotas.storage.AlmacenamientoNoDisponibleException;
import com.osmascotas.obrasocialmascotas.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@Service
public class ActualizarFotografiaMascotaPropiaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActualizarFotografiaMascotaPropiaService.class);
    private static final long MAX_FOTOGRAFIA_BYTES = 5L * 1024L * 1024L;
    private static final String CONTENT_TYPE_JPEG = "image/jpeg";
    private static final String CONTENT_TYPE_PNG = "image/png";

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final TitularidadMascotaRepository titularidadMascotaRepository;
    private final MascotaRepository mascotaRepository;
    private final ObjectStorageService objectStorageService;

    public ActualizarFotografiaMascotaPropiaService(
            UsuarioRepository usuarioRepository,
            ClienteRepository clienteRepository,
            TitularidadMascotaRepository titularidadMascotaRepository,
            MascotaRepository mascotaRepository,
            ObjectStorageService objectStorageService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
        this.titularidadMascotaRepository = titularidadMascotaRepository;
        this.mascotaRepository = mascotaRepository;
        this.objectStorageService = objectStorageService;
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENTE')")
    public void actualizar(Long mascotaId, MultipartFile archivo) {
        Objects.requireNonNull(mascotaId, "El id de mascota es obligatorio.");

        Usuario usuario = obtenerUsuarioAutenticado();
        Cliente cliente = clienteRepository.buscarPorUsuarioId(usuario.getId())
                .orElseThrow(ClienteAsociadoNoEncontradoException::new);
        TitularidadMascota titularidad = titularidadMascotaRepository
                .buscarVigentePorClienteIdYMascotaId(cliente.getId(), mascotaId)
                .orElseThrow(MascotaPropiaNoEncontradaException::new);

        FotografiaValidada fotografia = validarFotografia(archivo);
        Mascota mascota = titularidad.getMascota();
        String objectKeyAnterior = mascota.getFotografiaObjetoKey();
        String objectKeyNuevo = generarObjectKey(mascota.getId(), fotografia.extension());

        objectStorageService.guardar(objectKeyNuevo, fotografia.contenido(), fotografia.contentType());
        registrarCompensaciones(mascota.getId(), objectKeyNuevo, objectKeyAnterior);

        mascota.actualizarFotografiaObjetoKey(objectKeyNuevo);
        mascotaRepository.saveAndFlush(mascota);
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

    private FotografiaValidada validarFotografia(MultipartFile archivo) {
        if (archivo == null) {
            throw new FotografiaMascotaInvalidaException("La fotografia es obligatoria");
        }
        if (archivo.isEmpty() || archivo.getSize() == 0) {
            throw new FotografiaMascotaInvalidaException("La fotografia no puede estar vacia");
        }
        if (archivo.getSize() > MAX_FOTOGRAFIA_BYTES) {
            throw new FotografiaMascotaInvalidaException("La fotografia supera el tamano maximo permitido");
        }

        String contentType = archivo.getContentType();
        if (contentType == null) {
            throw new FotografiaMascotaInvalidaException("La fotografia debe ser JPEG o PNG");
        }
        String extension = switch (contentType) {
            case CONTENT_TYPE_JPEG -> "jpg";
            case CONTENT_TYPE_PNG -> "png";
            default -> throw new FotografiaMascotaInvalidaException("La fotografia debe ser JPEG o PNG");
        };

        byte[] contenido = leerContenido(archivo);
        if (contenido.length == 0) {
            throw new FotografiaMascotaInvalidaException("La fotografia no puede estar vacia");
        }
        if (contenido.length > MAX_FOTOGRAFIA_BYTES) {
            throw new FotografiaMascotaInvalidaException("La fotografia supera el tamano maximo permitido");
        }
        if (!firmaCoincide(contentType, contenido) || !imagenDecodificable(contenido)) {
            throw new FotografiaMascotaInvalidaException("El contenido de la fotografia no es una imagen valida");
        }

        return new FotografiaValidada(contenido, contentType, extension);
    }

    private byte[] leerContenido(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException exception) {
            throw new FotografiaMascotaInvalidaException("No se pudo leer la fotografia");
        }
    }

    private boolean firmaCoincide(String contentType, byte[] contenido) {
        if (CONTENT_TYPE_JPEG.equals(contentType)) {
            return contenido.length >= 3
                    && (contenido[0] & 0xFF) == 0xFF
                    && (contenido[1] & 0xFF) == 0xD8
                    && (contenido[2] & 0xFF) == 0xFF;
        }

        return contenido.length >= 8
                && (contenido[0] & 0xFF) == 0x89
                && contenido[1] == 0x50
                && contenido[2] == 0x4E
                && contenido[3] == 0x47
                && contenido[4] == 0x0D
                && contenido[5] == 0x0A
                && contenido[6] == 0x1A
                && contenido[7] == 0x0A;
    }

    private boolean imagenDecodificable(byte[] contenido) {
        try {
            return ImageIO.read(new ByteArrayInputStream(contenido)) != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private String generarObjectKey(Long mascotaId, String extension) {
        return "mascotas/%d/fotografias/%s.%s".formatted(mascotaId, UUID.randomUUID(), extension);
    }

    private void registrarCompensaciones(Long mascotaId, String objectKeyNuevo, String objectKeyAnterior) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (objectKeyAnterior != null && !objectKeyAnterior.isBlank() && !objectKeyAnterior.equals(objectKeyNuevo)) {
                    eliminarConLogSeguro(
                            objectKeyAnterior,
                            "No se pudo eliminar la fotografia anterior de la mascota {} luego del commit.",
                            mascotaId
                    );
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    eliminarConLogSeguro(
                            objectKeyNuevo,
                            "No se pudo eliminar la fotografia nueva de la mascota {} luego del rollback.",
                            mascotaId
                    );
                }
            }
        });
    }

    private void eliminarConLogSeguro(String objectKey, String mensaje, Long mascotaId) {
        try {
            objectStorageService.eliminar(objectKey);
        } catch (RuntimeException exception) {
            LOGGER.warn(mensaje, mascotaId, exception);
        }
    }

    private record FotografiaValidada(byte[] contenido, String contentType, String extension) {
    }
}
