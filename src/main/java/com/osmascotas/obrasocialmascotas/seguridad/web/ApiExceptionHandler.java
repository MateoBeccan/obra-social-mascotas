package com.osmascotas.obrasocialmascotas.seguridad.web;

import com.osmascotas.obrasocialmascotas.clientesmascotas.service.CriteriosBusquedaMascotaInvalidosException;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.MascotaNoEncontradaException;
import com.osmascotas.obrasocialmascotas.storage.AlmacenamientoNoDisponibleException;
import com.osmascotas.obrasocialmascotas.seguridad.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarJsonInvalido(HttpMessageNotReadableException exception) {
        LOGGER.warn("Solicitud JSON invalida: {}", exception.getMostSpecificCause().getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Solicitud JSON invalida"));
    }

    @ExceptionHandler(AlmacenamientoNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> manejarAlmacenamientoNoDisponible(
            AlmacenamientoNoDisponibleException exception
    ) {
        LOGGER.warn("Almacenamiento no disponible durante la operacion solicitada.", exception);

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("Almacenamiento no disponible"));
    }

    @ExceptionHandler(CriteriosBusquedaMascotaInvalidosException.class)
    public ResponseEntity<ErrorResponse> manejarCriteriosBusquedaMascotaInvalidos(
            CriteriosBusquedaMascotaInvalidosException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MascotaNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> manejarMascotaNoEncontrada() {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No se encontro la Mascota"));
    }
}
