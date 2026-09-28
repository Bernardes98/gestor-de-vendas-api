package com.gestordevendas.api.promotion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/clients/{clientId}/promotions")
public class ClientProductPromotionController {
 private final ClientProductPromotionService service; public ClientProductPromotionController(ClientProductPromotionService service){this.service=service;}
 @GetMapping public List<PromotionResponse> list(@PathVariable UUID clientId){return service.list(clientId);}
 @PutMapping("/{productId}") public PromotionResponse put(@PathVariable UUID clientId,@PathVariable UUID productId,@Valid @RequestBody PromotionRequest request){return service.put(clientId,productId,request);}
 @DeleteMapping("/{productId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID clientId,@PathVariable UUID productId){service.delete(clientId,productId);}
}
