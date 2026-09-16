package com.gestordevendas.api.order;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/public/orders/{token}")
public class PublicOrderController {
    private final PublicOrderService service;
    public PublicOrderController(PublicOrderService service) { this.service = service; }

    @GetMapping("/catalog") public PublicOrderCatalogResponse catalog(@PathVariable String token) { return service.catalog(token); }
    @GetMapping("/recent") public ResponseEntity<CustomerOrderResponse> recent(@PathVariable String token) {
        CustomerOrderResponse response = service.recent(token); return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
    }
    @PostMapping public ResponseEntity<CustomerOrderResponse> create(@PathVariable String token, @Valid @RequestBody PublicOrderRequest request) {
        CustomerOrderResponse response = service.create(token, request);
        return ResponseEntity.created(URI.create("/api/orders/" + response.id())).body(response);
    }
}
