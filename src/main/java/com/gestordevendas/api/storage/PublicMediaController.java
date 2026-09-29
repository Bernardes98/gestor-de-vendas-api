package com.gestordevendas.api.storage;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@RestController
@RequestMapping("/api/public/media")
public class PublicMediaController {
    private final DatabaseStoredObjectRepository repository;

    public PublicMediaController(DatabaseStoredObjectRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{encodedKey}")
    public ResponseEntity<byte[]> get(@PathVariable String encodedKey) {
        final String key;
        try {
            key = new String(Base64.getUrlDecoder().decode(encodedKey), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }

        return repository.findByObjectKey(key)
            .map(object -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(object.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(object.getContent()))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
