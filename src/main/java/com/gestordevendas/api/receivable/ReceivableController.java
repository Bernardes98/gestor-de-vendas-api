package com.gestordevendas.api.receivable;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/receivables")
public class ReceivableController {
    private final ReceivableService service;
    public ReceivableController(ReceivableService service) { this.service = service; }
    @GetMapping public List<ReceivableResponse> list() { return service.list(); }
}
