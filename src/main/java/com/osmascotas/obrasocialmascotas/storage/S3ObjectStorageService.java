package com.osmascotas.obrasocialmascotas.storage;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Objects;

@Service
public class S3ObjectStorageService implements ObjectStorageService {

    private final S3Client s3Client;
    private final S3StorageProperties properties;

    public S3ObjectStorageService(S3Client s3Client, S3StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public void guardar(String objectKey, byte[] contenido, String contentType) {
        Objects.requireNonNull(objectKey, "El object key es obligatorio.");
        Objects.requireNonNull(contenido, "El contenido es obligatorio.");
        Objects.requireNonNull(contentType, "El content type es obligatorio.");

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength((long) contenido.length)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(contenido));
        } catch (S3Exception | SdkClientException ex) {
            throw new AlmacenamientoNoDisponibleException("No se pudo guardar el objeto en el almacenamiento.", ex);
        }
    }

    @Override
    public void eliminar(String objectKey) {
        Objects.requireNonNull(objectKey, "El object key es obligatorio.");

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(request);
        } catch (S3Exception | SdkClientException ex) {
            throw new AlmacenamientoNoDisponibleException("No se pudo eliminar el objeto del almacenamiento.", ex);
        }
    }
}
