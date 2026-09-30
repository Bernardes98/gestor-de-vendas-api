package com.gestordevendas.api.route;
import java.util.UUID;
public record RouteClientResponse(UUID id, UUID clientId, String clientName, String phone, String address, String city, RouteDay day, int position) {
 static RouteClientResponse from(ClientRoute r){ var c=r.getClient(); return new RouteClientResponse(r.getId(),c.getId(),c.getName(),c.getPhone(),c.getAddress(),c.getCity(),r.getDay(),r.getPosition()); }
}
