package com.gestordevendas.api.order;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clients/{clientId}")
public class ClientOrderSettingsController {
    private final ClientOrderSettingsService service;
    public ClientOrderSettingsController(ClientOrderSettingsService service) { this.service = service; }

    @PostMapping("/order-link/regenerate")
    public OrderLinkResponse regenerate(@PathVariable UUID clientId) { return service.regenerate(clientId); }

    @GetMapping("/hidden-products")
    public List<UUID> hidden(@PathVariable UUID clientId) { return service.hiddenProducts(clientId); }

    @PutMapping("/hidden-products")
    public ResponseEntity<Void> save(@PathVariable UUID clientId, @Valid @RequestBody HiddenProductsRequest request) {
        service.saveHiddenProducts(clientId, request); return ResponseEntity.noContent().build();
    }
}
