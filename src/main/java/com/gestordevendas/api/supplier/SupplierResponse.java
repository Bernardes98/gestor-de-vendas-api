package com.gestordevendas.api.supplier;
import java.util.UUID;
public record SupplierResponse(UUID id,String name,int order){ static SupplierResponse from(Supplier s){return new SupplierResponse(s.getId(),s.getName(),s.getOrderIndex());} }
