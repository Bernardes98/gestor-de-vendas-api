package com.gestordevendas.api.fixedexpense;
import jakarta.validation.Valid; import org.springframework.http.ResponseEntity; import org.springframework.web.bind.annotation.*; import java.net.URI; import java.util.*;
@RestController @RequestMapping("/api/fixed-expenses")
public class FixedExpenseController {
    private final FixedExpenseService service; public FixedExpenseController(FixedExpenseService s){service=s;}
    @GetMapping public List<FixedExpenseResponse> list(){return service.list();}
    @PostMapping public ResponseEntity<FixedExpenseResponse> create(@Valid @RequestBody FixedExpenseRequest req){var r=service.create(req);return ResponseEntity.created(URI.create("/api/fixed-expenses/"+r.id())).body(r);}
    @PutMapping("/{id}") public FixedExpenseResponse update(@PathVariable UUID id,@Valid @RequestBody FixedExpenseRequest req){return service.update(id,req);}
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id){service.delete(id);return ResponseEntity.noContent().build();}
}
