package com.gestordevendas.api.order;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ClientHiddenProductId implements Serializable {
    private UUID client;
    private UUID product;

    public ClientHiddenProductId() {}

    public ClientHiddenProductId(UUID client, UUID product) {
        this.client = client;
        this.product = product;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClientHiddenProductId that)) return false;
        return Objects.equals(client, that.client) && Objects.equals(product, that.product);
    }

    @Override
    public int hashCode() { return Objects.hash(client, product); }
}
