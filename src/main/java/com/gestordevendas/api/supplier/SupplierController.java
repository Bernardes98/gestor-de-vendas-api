package com.gestordevendas.api.supplier;
import jakarta.validation.Valid; import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/suppliers")
public class SupplierController { private final SupplierService service; public SupplierController(SupplierService s){service=s;}
 @GetMapping public List<SupplierResponse> list(){return service.list();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public SupplierResponse create(@Valid @RequestBody SupplierRequest r){return service.create(r);}
 @PutMapping("/{id}") public SupplierResponse update(@PathVariable UUID id,@Valid @RequestBody SupplierRequest r){return service.update(id,r);}
 @PatchMapping("/reorder") public List<SupplierResponse> reorder(@Valid @RequestBody SupplierReorderRequest r){return service.reorder(r);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id){service.delete(id);}
}
