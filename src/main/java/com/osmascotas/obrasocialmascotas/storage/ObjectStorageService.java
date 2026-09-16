package com.osmascotas.obrasocialmascotas.storage;

public interface ObjectStorageService {

    void guardar(String objectKey, byte[] contenido, String contentType);

    void eliminar(String objectKey);
}
