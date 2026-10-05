package com.gestordevendas.api.pricing;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clients/{clientId}/product-prices")
public class ClientProductPriceController {
    private final ClientProductPriceService service;
    public ClientProductPriceController(ClientProductPriceService service) { this.service = service; }

    @GetMapping
    public List<ProductPriceResponse> list(@PathVariable UUID clientId) {
        return service.list(clientId);
    }

    @GetMapping("/{productId}")
    public ProductPriceResponse get(@PathVariable UUID clientId, @PathVariable UUID productId) {
        return service.get(clientId, productId);
    }

    @PutMapping("/{productId}")
    public ProductPriceResponse put(@PathVariable UUID clientId, @PathVariable UUID productId,
                                    @Valid @RequestBody ProductPriceRequest request) {
        return service.put(clientId, productId, request);
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clientId, @PathVariable UUID productId) {
        service.delete(clientId, productId);
    }
}
