package com.gestordevendas.api.receivable;

import com.gestordevendas.api.sale.PaymentRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/manual-receivables")
public class ManualReceivableController {
    private final ManualReceivableService service;
    public ManualReceivableController(ManualReceivableService service) { this.service = service; }
    @PostMapping public ResponseEntity<ManualReceivableResponse> create(@Valid @RequestBody ManualReceivableRequest request) {
        ManualReceivableResponse response = service.create(request); return ResponseEntity.created(URI.create("/api/manual-receivables/" + response.id())).body(response);
    }
    @PostMapping("/{id}/payments") public ManualReceivableResponse payment(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request) { return service.addPayment(id, request); }
    @PostMapping("/{id}/settle") public ManualReceivableResponse settle(@PathVariable UUID id) { return service.settle(id); }
}
