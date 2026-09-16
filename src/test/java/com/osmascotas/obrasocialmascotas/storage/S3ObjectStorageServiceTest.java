package com.osmascotas.obrasocialmascotas.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ObjectStorageServiceTest {

    private static final S3StorageProperties PROPERTIES = new S3StorageProperties(
            "http://localhost:9000",
            "us-east-1",
            "access-test",
            "secret-test",
            "os-mascotas-test"
    );

    @Test
    void guardarEnviaBucketKeyContentTypeYBytes() throws IOException {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        S3ObjectStorageService service = new S3ObjectStorageService(s3Client, PROPERTIES);
        byte[] contenido = "imagen".getBytes(StandardCharsets.UTF_8);

        service.guardar("mascotas/1/fotografias/foto.jpg", contenido, "image/jpeg");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("os-mascotas-test");
        assertThat(request.key()).isEqualTo("mascotas/1/fotografias/foto.jpg");
        assertThat(request.contentType()).isEqualTo("image/jpeg");
        assertThat(request.contentLength()).isEqualTo(contenido.length);
        assertThat(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes())
                .isEqualTo(contenido);
    }

    @Test
    void eliminarEnviaBucketYKey() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());
        S3ObjectStorageService service = new S3ObjectStorageService(s3Client, PROPERTIES);

        service.eliminar("mascotas/1/fotografias/foto.jpg");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());

        DeleteObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("os-mascotas-test");
        assertThat(request.key()).isEqualTo("mascotas/1/fotografias/foto.jpg");
    }

    @Test
    void guardarCuandoS3NoEstaDisponibleLanzaExcepcionSegura() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.builder().message("conexion rechazada").build());
        S3ObjectStorageService service = new S3ObjectStorageService(s3Client, PROPERTIES);

        assertThatThrownBy(() -> service.guardar(
                "mascotas/1/fotografias/foto.jpg",
                new byte[]{1, 2, 3},
                "image/jpeg"
        ))
                .isInstanceOf(AlmacenamientoNoDisponibleException.class)
                .hasMessage("No se pudo guardar el objeto en el almacenamiento.");
    }

    @Test
    void eliminarCuandoS3NoEstaDisponibleLanzaExcepcionSegura() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.builder().message("conexion rechazada").build());
        S3ObjectStorageService service = new S3ObjectStorageService(s3Client, PROPERTIES);

        assertThatThrownBy(() -> service.eliminar("mascotas/1/fotografias/foto.jpg"))
                .isInstanceOf(AlmacenamientoNoDisponibleException.class)
                .hasMessage("No se pudo eliminar el objeto del almacenamiento.");
    }
}
