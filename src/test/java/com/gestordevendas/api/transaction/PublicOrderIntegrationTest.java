package com.gestordevendas.api.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.pricing.ClientProductPrice;
import com.gestordevendas.api.pricing.ClientProductPriceRepository;
import com.gestordevendas.api.product.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicOrderIntegrationTest extends BusinessIntegrationTestSupport {

    @Autowired ClientProductPriceRepository priceRepository;

    @Test
    void publicCatalogUsesClientPriceAndHiddenProductsAndRegenerationInvalidatesOldToken() throws Exception {
        Client client = createClient("Cliente Pedido");
        Product visible = createProduct("Produto Público", new BigDecimal("10.00"), new BigDecimal("20.00"), false);
        Product hidden = createProduct("Produto Oculto", new BigDecimal("4.00"), new BigDecimal("8.00"), false);
        priceRepository.save(ClientProductPrice.create(company, client, visible, new BigDecimal("17.50")));

        mvc.perform(put("/api/clients/{id}/hidden-products", client.getId())
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productIds\":[\"" + hidden.getId() + "\"]}"))
            .andExpect(status().isNoContent());

        String oldToken = client.getOrderToken();
        mvc.perform(get("/api/public/orders/{token}/catalog", oldToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientName").value("Cliente Pedido"))
            .andExpect(jsonPath("$.products.length()").value(1))
            .andExpect(jsonPath("$.products[0].id").value(visible.getId().toString()))
            .andExpect(jsonPath("$.products[0].price").value(17.50));

        String body = mvc.perform(post("/api/clients/{id}/order-link/regenerate", client.getId())
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String newToken = json(body).get("token").asText();

        mvc.perform(get("/api/public/orders/{token}/catalog", oldToken))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/public/orders/{token}/catalog", newToken))
            .andExpect(status().isOk());
    }

    @Test
    void publicOrderIsServerPricedAndSellerCanRunWorkflowWithoutCrossTenantAccess() throws Exception {
        Client client = createClient("Cliente Fluxo");
        Product product = createProduct("Produto Fluxo", new BigDecimal("20.00"), new BigDecimal("30.00"), false);
        priceRepository.save(ClientProductPrice.create(company, client, product, new BigDecimal("27.00")));

        String createBody = mvc.perform(post("/api/public/orders/{token}", client.getOrderToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"Entregar cedo\",\"items\":[{\"productId\":\"" + product.getId() + "\",\"quantity\":2}]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.total").value(54.00))
            .andExpect(jsonPath("$.items[0].unitPrice").value(27.00))
            .andReturn().getResponse().getContentAsString();
        String orderId = json(createBody).get("id").asText();

        mvc.perform(get("/api/orders").header("Authorization", bearer(sellerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(orderId))
            .andExpect(jsonPath("$[0].status").value("PENDING"));

        mvc.perform(post("/api/orders/{id}/viewed", orderId).header("Authorization", bearer(sellerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.viewedAt").isNotEmpty());

        mvc.perform(post("/api/orders/{id}/conversion/claim", orderId).header("Authorization", bearer(sellerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.claimed").value(true));
        mvc.perform(post("/api/orders/{id}/conversion/claim", orderId).header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.claimed").value(false));
        mvc.perform(post("/api/orders/{id}/conversion/release", orderId).header("Authorization", bearer(sellerToken)))
            .andExpect(status().isNoContent());

        String saleBody = mvc.perform(post("/api/sales")
                .header("Authorization", bearer(sellerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + client.getId() + "\",\"paymentType\":\"AVISTA\",\"items\":[{\"productId\":\"" + product.getId() + "\",\"quantity\":2,\"unitPrice\":27.00}]}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String saleId = json(saleBody).get("id").asText();

        mvc.perform(post("/api/orders/{id}/converted", orderId)
                .header("Authorization", bearer(sellerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"saleId\":\"" + saleId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONVERTED"))
            .andExpect(jsonPath("$.saleId").value(saleId));

        mvc.perform(get("/api/orders/{id}", orderId).header("Authorization", bearer(otherOwnerToken)))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/public/orders/{token}/recent", client.getOrderToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONVERTED"))
            .andExpect(jsonPath("$.total").value(54.00));
    }

    @Test
    void rejectedOrderCanOnlyBeDeletedByAdminOrOwner() throws Exception {
        Client client = createClient("Cliente Recusa");
        Product product = createProduct("Produto Recusa", BigDecimal.ONE, BigDecimal.TEN, false);
        String body = mvc.perform(post("/api/public/orders/{token}", client.getOrderToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":\"" + product.getId() + "\",\"quantity\":1}]}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String id = json(body).get("id").asText();

        mvc.perform(post("/api/orders/{id}/reject", id).header("Authorization", bearer(sellerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));

        mvc.perform(delete("/api/orders/{id}", id).header("Authorization", bearer(sellerToken)))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/orders/{id}", id).header("Authorization", bearer(adminToken)))
            .andExpect(status().isNoContent());
    }
}
