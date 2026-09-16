package com.gestordevendas.api.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class R2ObjectStorage implements ObjectStorage {
    private final S3Client s3;
    private final R2Properties properties;

    public R2ObjectStorage(S3Client s3, R2Properties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    @Override
    public String put(String key, String contentType, byte[] content) {
        s3.putObject(PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .contentType(contentType)
            .build(), RequestBody.fromBytes(content));
        return publicUrl(key);
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }

    @Override
    public String publicUrl(String key) {
        String base = properties.publicBaseUrl();
        if (base == null || base.isBlank()) return key;
        return base.replaceAll("/$", "") + "/" + key;
    }
}
