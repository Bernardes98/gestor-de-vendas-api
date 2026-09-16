package com.gestordevendas.api.client;

import java.util.UUID;

public record ClientResponse(
    UUID id,
    String name,
    String document,
    String phone,
    String email,
    String address,
    String city,
    String notes,
    String orderToken
) {
    static ClientResponse from(Client client) {
        return new ClientResponse(client.getId(), client.getName(), client.getDocument(), client.getPhone(),
            client.getEmail(), client.getAddress(), client.getCity(), client.getNotes(), client.getOrderToken());
    }
}
