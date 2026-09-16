package com.gestordevendas.api.audit;

import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditQueryController {
    private final AuditQueryService service;
    public AuditQueryController(AuditQueryService service) { this.service = service; }

    @GetMapping
    public List<AuditResponse> list(@RequestParam(required = false) String action,
                                    @RequestParam(required = false) String entityType,
                                    @RequestParam(required = false) Instant from,
                                    @RequestParam(required = false) Instant to,
                                    @RequestParam(defaultValue = "100") int limit) {
        return service.currentCompany(action, entityType, from, to, limit);
    }
}
