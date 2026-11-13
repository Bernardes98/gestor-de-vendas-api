package com.gestordevendas.api.transaction;

import com.gestordevendas.api.inventory.InventoryLotRepository;
import com.gestordevendas.api.product.Product;
import com.gestordevendas.api.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FifoConsistencyIntegrationTest extends BusinessIntegrationTestSupport {
    @Autowired private InventoryLotRepository lots;
    @Autowired private ProductRepository products;

    private BigDecimal fifoBalance(Product product) {
        return lots.allForCompany(product.getCompany().getId()).stream()
            .filter(lot -> lot.getProduct().getId().equals(product.getId()))
            .map(lot -> lot.getRemainingQuantity())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void assertReconciled(Product product, String expected) {
        Product persisted = products.findById(product.getId()).orElseThrow();
        assertEquals(0, persisted.getCurrentStock().compareTo(new BigDecimal(expected)), "Saldo do produto");
        assertEquals(0, fifoBalance(persisted).compareTo(new BigDecimal(expected)), "Saldo FIFO");
    }

    private void adjust(Product product, String delta, int expectedStatus) throws Exception {
        mvc.perform(post("/api/stock/adjustments")
            .header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":\"" + product.getId() + "\",\"quantityDelta\":" + delta + ",\"reason\":\"Teste FIFO\"}"))
            .andExpect(expectedStatus == 200 ? status().isOk() : status().isConflict());
    }

    @Test
    void adjustmentsKeepProductAndFifoBalancesEqual() throws Exception {
        Product product = createProduct("FIFO ajustes " + UUID.randomUUID(), new BigDecimal("5.00"), new BigDecimal("9.00"), true);
        createPurchase(adminToken, product, "10", "5.00");
        createPurchase(adminToken, product, "10", "6.00");
        assertReconciled(product, "20");

        adjust(product, "-5", 200);
        assertReconciled(product, "15");
        adjust(product, "3", 200);
        assertReconciled(product, "18");
    }

    @Test
    void insufficientAdjustmentDoesNotChangeEitherBalance() throws Exception {
        Product product = createProduct("FIFO rollback " + UUID.randomUUID(), new BigDecimal("5.00"), new BigDecimal("9.00"), true);
        createPurchase(adminToken, product, "4", "5.00");
        assertReconciled(product, "4");
        adjust(product, "-5", 409);
        assertReconciled(product, "4");
    }
}
