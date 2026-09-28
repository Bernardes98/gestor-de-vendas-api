package com.gestordevendas.api.company;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/company/settings")
public class CompanySettingsController {
    private final CompanySettingsService service;

    public CompanySettingsController(CompanySettingsService service) {
        this.service = service;
    }

    @GetMapping
    public CompanySettingsResponse get() {
        return service.get();
    }

    @PutMapping
    public CompanySettingsResponse update(@Valid @RequestBody CompanySettingsRequest request) {
        return service.update(request);
    }
}
