package com.gestordevendas.api.receipt;

import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/sales")
public class SaleReceiptController {
    private final SaleReceiptService service;
    public SaleReceiptController(SaleReceiptService service) { this.service = service; }
    @GetMapping("/{id}/receipt") public SaleReceiptResponse get(@PathVariable UUID id) { return service.get(id); }
}
