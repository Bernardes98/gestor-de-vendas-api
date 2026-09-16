package com.gestordevendas.api.order;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class CustomerOrderController {
    private final CustomerOrderService service;
    public CustomerOrderController(CustomerOrderService service) { this.service = service; }

    @GetMapping public List<CustomerOrderResponse> list() { return service.list(); }
    @GetMapping("/{id}") public CustomerOrderResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping("/{id}/viewed") public CustomerOrderResponse viewed(@PathVariable UUID id) { return service.viewed(id); }
    @PostMapping("/{id}/conversion/claim") public OrderClaimResponse claim(@PathVariable UUID id) { return service.claim(id); }
    @PostMapping("/{id}/conversion/release") public ResponseEntity<Void> release(@PathVariable UUID id) { service.release(id); return ResponseEntity.noContent().build(); }
    @PostMapping("/{id}/reject") public CustomerOrderResponse reject(@PathVariable UUID id) { return service.reject(id); }
    @PostMapping("/{id}/converted") public CustomerOrderResponse converted(@PathVariable UUID id, @Valid @RequestBody OrderConversionRequest request) { return service.converted(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id) { service.deleteRejected(id); return ResponseEntity.noContent().build(); }
}
