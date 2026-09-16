package com.gestordevendas.api.transaction;

import com.gestordevendas.api.client.Client;
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
class ReceivablesIntegrationTest extends BusinessIntegrationTestSupport {

    @Test
    void creditSaleSupportsPartialPaymentAndRejectsOverpayment() throws Exception {
        Product product = createProduct("Prazo", new BigDecimal("20.00"), new BigDecimal("50.00"), false);
        String saleId = createSale(sellerToken, product, "2", "50.00", "PRAZO");

        mvc.perform(get("/api/receivables").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type").value("SALE"))
            .andExpect(jsonPath("$[0].outstanding").value(100.00));

        mvc.perform(post("/api/sales/{id}/payments", saleId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":40.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outstanding").value(60.00));

        mvc.perform(post("/api/sales/{id}/payments", saleId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":70.00}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_BALANCE"));

        mvc.perform(get("/api/receivables").header("Authorization", bearer(sellerToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    void paidCreditSaleCannotBecomeCashOrShrinkBelowAmountAlreadyPaid() throws Exception {
        Product product = createProduct("Mudança", new BigDecimal("10.00"), new BigDecimal("100.00"), false);
        String saleId = createSale(ownerToken, product, "1", "100.00", "PRAZO");
        mvc.perform(post("/api/sales/{id}/payments", saleId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":30.00}"))
            .andExpect(status().isOk());

        mvc.perform(put("/api/sales/{id}", saleId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentType\":\"AVISTA\",\"items\":[{\"productId\":\"" + product.getId() +
                    "\",\"quantity\":1,\"unitPrice\":100}]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PAID_SALE_CANNOT_BECOME_CASH"));

        mvc.perform(put("/api/sales/{id}", saleId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentType\":\"PRAZO\",\"items\":[{\"productId\":\"" + product.getId() +
                    "\",\"quantity\":1,\"unitPrice\":20}]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SALE_TOTAL_BELOW_PAID"));
    }

    @Test
    void cancellingCreditSaleRemovesOpenBalance() throws Exception {
        Product product = createProduct("Cancelar prazo", BigDecimal.ONE, new BigDecimal("25.00"), false);
        String saleId = createSale(ownerToken, product, "2", "25.00", "PRAZO");

        mvc.perform(post("/api/sales/{id}/cancel", saleId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Cancelado\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outstanding").value(0));

        mvc.perform(get("/api/receivables").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }


    @Test
    void saleCanMoveBetweenCashAndCreditWhileNoPaymentsExist() throws Exception {
        Product product = createProduct("Troca pagamento", BigDecimal.ONE, new BigDecimal("80.00"), false);
        String saleId = createSale(ownerToken, product, "1", "80.00", "AVISTA");

        mvc.perform(put("/api/sales/{id}", saleId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentType\":\"PRAZO\",\"items\":[{\"productId\":\"" + product.getId() +
                    "\",\"quantity\":1,\"unitPrice\":80}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outstanding").value(80.00));

        mvc.perform(put("/api/sales/{id}", saleId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentType\":\"AVISTA\",\"items\":[{\"productId\":\"" + product.getId() +
                    "\",\"quantity\":1,\"unitPrice\":80}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outstanding").value(0));
    }

    @Test
    void manualReceivableSupportsPartialPaymentAndSettlement() throws Exception {
        Client client = createClient("Cliente Prazo");
        String body = mvc.perform(post("/api/manual-receivables")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + client.getId() + "\",\"description\":\"Saldo anterior\",\"totalAmount\":150.00}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.outstanding").value(150.00))
            .andReturn().getResponse().getContentAsString();
        String receivableId = json(body).get("id").asText();

        mvc.perform(post("/api/manual-receivables/{id}/payments", receivableId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":50.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outstanding").value(100.00));

        mvc.perform(post("/api/manual-receivables/{id}/settle", receivableId)
                .header("Authorization", bearer(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outstanding").value(0))
            .andExpect(jsonPath("$.status").value("QUITADO"));
    }
}
