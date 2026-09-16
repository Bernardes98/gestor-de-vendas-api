package com.gestordevendas.api.sale;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sales")
public class SaleController {
    private final SaleService service;
    public SaleController(SaleService service) { this.service = service; }
    @GetMapping public List<SaleResponse> list() { return service.list(); }
    @GetMapping("/{id}") public SaleResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping public ResponseEntity<SaleResponse> create(@Valid @RequestBody SaleRequest request) {
        SaleResponse response = service.create(request); return ResponseEntity.created(URI.create("/api/sales/" + response.id())).body(response);
    }
    @PutMapping("/{id}") public SaleResponse update(@PathVariable UUID id, @Valid @RequestBody SaleRequest request) { return service.update(id, request); }
    @PostMapping("/{id}/cancel") public SaleResponse cancel(@PathVariable UUID id, @Valid @RequestBody SaleCancelRequest request) { return service.cancel(id, request); }
    @PostMapping("/{id}/payments") public SaleResponse payment(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request) { return service.addPayment(id, request); }
    @PostMapping("/{id}/settle") public SaleResponse settle(@PathVariable UUID id) { return service.settle(id); }
}
