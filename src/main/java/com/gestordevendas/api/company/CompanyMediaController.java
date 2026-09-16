package com.gestordevendas.api.company;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/company/logo")
public class CompanyMediaController {
    private final CompanyMediaService service;
    public CompanyMediaController(CompanyMediaService service) { this.service = service; }

    @GetMapping
    public CompanyMediaResponse get() { return service.getLogo(); }

    @PostMapping
    public CompanyMediaResponse upload(@RequestParam("file") MultipartFile file) { return service.uploadLogo(file); }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete() { service.deleteLogo(); }
}
