package com.gestordevendas.api.transaction;

import com.gestordevendas.api.product.Product;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InventoryIntegrationTest extends BusinessIntegrationTestSupport {

    @Test
    void purchaseCreateEditAndCancelApplyOnlyStockDelta() throws Exception {
        Product product = createProduct("Controlado", new BigDecimal("50.00"), new BigDecimal("70.00"), true);
        String purchaseId = createPurchase(adminToken, product, "10", "48.00");

        mvc.perform(get("/api/products/{id}", product.getId()).header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStock").value(10.000));

        mvc.perform(put("/api/purchases/{id}", purchaseId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":\"" + product.getId() + "\",\"quantity\":6,\"unitCost\":49.00}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].quantity").value(6.000));

        mvc.perform(get("/api/products/{id}", product.getId()).header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStock").value(6.000));

        mvc.perform(delete("/api/purchases/{id}", purchaseId).header("Authorization", bearer(adminToken)))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/products/{id}", product.getId()).header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStock").value(0.000));
    }

    @Test
    void purchaseReversalCannotMakeControlledStockNegative() throws Exception {
        Product product = createProduct("Controlado", new BigDecimal("10.00"), new BigDecimal("15.00"), true);
        String purchaseId = createPurchase(adminToken, product, "5", "10.00");

        mvc.perform(post("/api/stock/adjustments")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + product.getId() + "\",\"quantityDelta\":-3,\"reason\":\"Perda\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStock").value(2.000));

        mvc.perform(delete("/api/purchases/{id}", purchaseId).header("Authorization", bearer(adminToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STOCK_REVERSAL_NOT_ALLOWED"));
    }

    @Test
    void sellerCannotManagePurchasesOrManualStockAndOtherTenantCannotSeePurchase() throws Exception {
        Product product = createProduct("Produto", BigDecimal.ONE, BigDecimal.TEN, true);
        String purchaseId = createPurchase(adminToken, product, "3", "1.00");

        mvc.perform(post("/api/stock/adjustments")
                .header("Authorization", bearer(sellerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + product.getId() + "\",\"quantityDelta\":1,\"reason\":\"Hack\"}"))
            .andExpect(status().isForbidden());

        mvc.perform(get("/api/purchases/{id}", purchaseId).header("Authorization", bearer(otherOwnerToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PURCHASE_NOT_FOUND"));
    }
}
