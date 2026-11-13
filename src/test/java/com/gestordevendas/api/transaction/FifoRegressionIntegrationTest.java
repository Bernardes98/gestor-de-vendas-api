package com.gestordevendas.api.transaction;

import com.gestordevendas.api.inventory.InventoryLotRepository;
import com.gestordevendas.api.product.Product;
import com.gestordevendas.api.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FifoRegressionIntegrationTest extends BusinessIntegrationTestSupport {
    @Autowired private InventoryLotRepository lots;
    @Autowired private ProductRepository products;

    private void assertBalances(Product product, String expected) {
        Product saved = products.findById(product.getId()).orElseThrow();
        BigDecimal fifo = lots.allForCompany(company.getId()).stream()
            .filter(l -> l.getProduct().getId().equals(product.getId()))
            .map(l -> l.getRemainingQuantity())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, saved.getCurrentStock().compareTo(new BigDecimal(expected)), "Estoque principal");
        assertEquals(0, fifo.compareTo(new BigDecimal(expected)), "Saldo dos lotes FIFO");
    }

    private void editSale(String saleId, Product product, String quantity, String price) throws Exception {
        mvc.perform(put("/api/sales/{id}", saleId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentType\":\"AVISTA\",\"items\":[{\"productId\":\"" + product.getId() +
                    "\",\"quantity\":" + quantity + ",\"unitPrice\":" + price + "}]}"))
            .andExpect(status().isOk());
    }

    @Test
    void saleAcrossTwoLotsUsesWeightedFifoCostAndCancelRestoresBothBalances() throws Exception {
        Product product = createProduct("FIFO custo " + UUID.randomUUID(), new BigDecimal("99"), new BigDecimal("30"), true);
        createPurchase(adminToken, product, "2", "5.00");
        createPurchase(adminToken, product, "3", "9.00");
        assertBalances(product, "5");

        String saleId = createSale(sellerToken, product, "4", "30.00", "AVISTA");
        assertBalances(product, "1");
        mvc.perform(get("/api/sales/{id}", saleId).header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.costTotal").value(28.00));

        mvc.perform(post("/api/sales/{id}/cancel", saleId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Teste de devolucao FIFO\"}"))
            .andExpect(status().isOk());
        assertBalances(product, "5");
    }

    @Test
    void changingOnlySalePricePreservesOriginalFifoCostAndStock() throws Exception {
        Product product = createProduct("FIFO comercial " + UUID.randomUUID(), new BigDecimal("99"), new BigDecimal("30"), true);
        createPurchase(adminToken, product, "2", "5.00");
        createPurchase(adminToken, product, "3", "9.00");
        String saleId = createSale(sellerToken, product, "4", "30.00", "AVISTA");
        assertBalances(product, "1");

        editSale(saleId, product, "4", "40.00");
        assertBalances(product, "1");
        mvc.perform(get("/api/sales/{id}", saleId).header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(160.00))
            .andExpect(jsonPath("$.costTotal").value(28.00));
    }

    @Test
    void failedSaleDoesNotConsumePartOfFifoOrPrincipalStock() throws Exception {
        Product product = createProduct("FIFO rollback " + UUID.randomUUID(), BigDecimal.ONE, BigDecimal.TEN, true);
        createPurchase(adminToken, product, "2", "5.00");
        createPurchase(adminToken, product, "3", "9.00");

        mvc.perform(post("/api/sales")
                .header("Authorization", bearer(sellerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentType\":\"AVISTA\",\"items\":[{\"productId\":\"" + product.getId() +
                    "\",\"quantity\":6,\"unitPrice\":10}]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertBalances(product, "5");
    }
}
