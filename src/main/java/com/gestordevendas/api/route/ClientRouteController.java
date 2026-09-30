package com.gestordevendas.api.route;
import jakarta.validation.Valid; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/routes")
public class ClientRouteController { private final ClientRouteService service; public ClientRouteController(ClientRouteService s){service=s;}
 @GetMapping public List<RouteClientResponse> list(){return service.list();}
 @PostMapping("/{day}/clients/{clientId}") @ResponseStatus(HttpStatus.CREATED) public RouteClientResponse add(@PathVariable RouteDay day,@PathVariable UUID clientId){return service.add(day,clientId);}
 @DeleteMapping("/{day}/clients/{clientId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@PathVariable RouteDay day,@PathVariable UUID clientId){service.remove(day,clientId);}
 @PutMapping("/{day}/order") public List<RouteClientResponse> reorder(@PathVariable RouteDay day,@Valid @RequestBody RouteReorderRequest req){return service.reorder(day,req);}
}
