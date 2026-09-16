package com.gestordevendas.api.stock;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
public class StockController {
    private final StockService service;
    public StockController(StockService service) { this.service = service; }

    @GetMapping("/movements")
    public List<StockMovementResponse> movements() { return service.list(); }

    @PostMapping("/adjustments")
    public ResponseEntity<StockAdjustmentResponse> adjust(@Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(service.adjust(request));
    }
}
