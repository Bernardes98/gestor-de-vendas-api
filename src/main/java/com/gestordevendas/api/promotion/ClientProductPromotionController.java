package com.gestordevendas.api.promotion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class ClientProductPromotionController {
 private final ClientProductPromotionService service; public ClientProductPromotionController(ClientProductPromotionService service){this.service=service;}
 @GetMapping("/api/promotions") public List<PromotionAdminResponse> listAll(){return service.listAll();}
 @GetMapping("/api/clients/{clientId}/promotions") public List<PromotionResponse> list(@PathVariable UUID clientId){return service.list(clientId);}
 @PutMapping("/api/clients/{clientId}/promotions/{productId}") public PromotionResponse put(@PathVariable UUID clientId,@PathVariable UUID productId,@Valid @RequestBody PromotionRequest request){return service.put(clientId,productId,request);}
 @DeleteMapping("/api/clients/{clientId}/promotions/{productId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID clientId,@PathVariable UUID productId){service.delete(clientId,productId);}
}
