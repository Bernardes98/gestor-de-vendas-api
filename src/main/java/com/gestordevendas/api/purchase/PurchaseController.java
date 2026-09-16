package com.gestordevendas.api.purchase;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {
    private final PurchaseService service;
    public PurchaseController(PurchaseService service) { this.service = service; }

    @GetMapping public List<PurchaseResponse> list() { return service.list(); }
    @GetMapping("/{id}") public PurchaseResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping public ResponseEntity<PurchaseResponse> create(@Valid @RequestBody PurchaseRequest request) {
        PurchaseResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/purchases/" + response.id())).body(response);
    }
    @PutMapping("/{id}") public PurchaseResponse update(@PathVariable UUID id, @Valid @RequestBody PurchaseRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> cancel(@PathVariable UUID id) { service.cancel(id); return ResponseEntity.noContent().build(); }
}
