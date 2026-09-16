package com.gestordevendas.api.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(R2Properties.class)
public class StorageConfig {
    @Bean
    ObjectStorage objectStorage(R2Properties properties) {
        if (!properties.enabled()) return new DisabledObjectStorage(properties);
        require(properties.endpoint(), "R2_ENDPOINT");
        require(properties.accessKeyId(), "R2_ACCESS_KEY_ID");
        require(properties.secretAccessKey(), "R2_SECRET_ACCESS_KEY");
        require(properties.bucket(), "R2_BUCKET");

        S3Client client = S3Client.builder()
            .endpointOverride(URI.create(properties.endpoint()))
            .region(Region.of("auto"))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey())))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
        return new R2ObjectStorage(client, properties);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " é obrigatório quando R2 está habilitado.");
    }
}
