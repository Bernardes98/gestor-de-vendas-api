package com.gestordevendas.api.transaction;

import com.gestordevendas.api.product.Product;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SalesIntegrationTest extends BusinessIntegrationTestSupport {

    @Test
    void saleUsesServerCostReducesStockAndSellerResponseHidesCostAndProfit() throws Exception {
        Product product = createProduct("Venda", new BigDecimal("40.00"), new BigDecimal("60.00"), true);
        createPurchase(adminToken, product, "10", "35.00");

        String saleId = createSale(sellerToken, product, "2", "65.00", "AVISTA");

        mvc.perform(get("/api/sales/{id}", saleId).header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(130.00))
            .andExpect(jsonPath("$.costTotal").value(80.00))
            .andExpect(jsonPath("$.profitTotal").value(50.00))
            .andExpect(jsonPath("$.items[0].unitCost").value(40.00));

        mvc.perform(get("/api/sales/{id}", saleId).header("Authorization", bearer(sellerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.costTotal").doesNotExist())
            .andExpect(jsonPath("$.profitTotal").doesNotExist())
            .andExpect(jsonPath("$.items[0].unitCost").doesNotExist());

        mvc.perform(get("/api/products/{id}", product.getId()).header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStock").value(8.000));
    }

    @Test
    void insufficientStockRollsBackSaleAndUncontrolledProductCanSellWithoutBalance() throws Exception {
        Product controlled = createProduct("Pouco", BigDecimal.ONE, BigDecimal.TEN, true);
        createPurchase(adminToken, controlled, "1", "1.00");

        mvc.perform(post("/api/sales")
                .header("Authorization", bearer(sellerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentType\":\"AVISTA\",\"items\":[{\"productId\":\"" + controlled.getId() +
                    "\",\"quantity\":2,\"unitPrice\":10}]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        mvc.perform(get("/api/products/{id}", controlled.getId()).header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStock").value(1.000));

        Product uncontrolled = createProduct("Livre", BigDecimal.ONE, BigDecimal.TEN, false);
        createSale(sellerToken, uncontrolled, "100", "10.00", "AVISTA");
    }

    @Test
    void adminEditsSaleByDeltaAndCancellationRestoresStockWhileSellerCannotEdit() throws Exception {
        Product product = createProduct("Delta", new BigDecimal("5.00"), new BigDecimal("10.00"), true);
        createPurchase(adminToken, product, "10", "5.00");
        String saleId = createSale(sellerToken, product, "2", "10.00", "AVISTA");

        mvc.perform(put("/api/sales/{id}", saleId)
                .header("Authorization", bearer(sellerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentType\":\"AVISTA\",\"items\":[{\"productId\":\"" + product.getId() +
                    "\",\"quantity\":3,\"unitPrice\":10}]}"))
            .andExpect(status().isForbidden());

        mvc.perform(put("/api/sales/{id}", saleId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentType\":\"AVISTA\",\"items\":[{\"productId\":\"" + product.getId() +
                    "\",\"quantity\":5,\"unitPrice\":11}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(55.00));

        mvc.perform(get("/api/products/{id}", product.getId()).header("Authorization", bearer(adminToken)))
            .andExpect(jsonPath("$.currentStock").value(5.000));

        mvc.perform(post("/api/sales/{id}/cancel", saleId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Cliente desistiu\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELADA"));

        mvc.perform(get("/api/products/{id}", product.getId()).header("Authorization", bearer(adminToken)))
            .andExpect(jsonPath("$.currentStock").value(10.000));
    }

    @Test
    void saleIdsAreTenantScoped() throws Exception {
        Product product = createProduct("Tenant", BigDecimal.ONE, BigDecimal.TEN, false);
        String saleId = createSale(ownerToken, product, "1", "10", "AVISTA");

        mvc.perform(get("/api/sales/{id}", saleId).header("Authorization", bearer(otherOwnerToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SALE_NOT_FOUND"));
    }
}
