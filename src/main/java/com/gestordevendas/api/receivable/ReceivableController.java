package com.gestordevendas.api.receivable;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/receivables")
public class ReceivableController {
    private final ReceivableService service;
    public ReceivableController(ReceivableService service) { this.service = service; }
    @GetMapping public List<ReceivableResponse> list() { return service.list(); }
    @GetMapping("/{type}/{id}/payments") public List<PaymentHistoryResponse> payments(@PathVariable String type, @PathVariable UUID id) { return service.payments(type, id); }
}
