package com.gestordevendas.api.transaction;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.product.Product;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportingIntegrationTest extends BusinessIntegrationTestSupport {

    @Test
    void reportsExcludeCancelledSalesAndSellerCannotReadFinancialReports() throws Exception {
        Product product = createProduct("Relatório", new BigDecimal("40.00"), new BigDecimal("100.00"), false);
        String activeId = createSale(adminToken, product, "2", "100.00", "AVISTA");
        String cancelledId = createSale(adminToken, product, "1", "100.00", "AVISTA");
        mvc.perform(post("/api/sales/{id}/cancel", cancelledId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Teste\"}"))
            .andExpect(status().isOk());

        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        mvc.perform(get("/api/reports/sales")
                .param("from", today.minusDays(1).toString())
                .param("to", today.plusDays(1).toString())
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.saleCount").value(1))
            .andExpect(jsonPath("$.summary.revenue").value(200.00))
            .andExpect(jsonPath("$.summary.cost").value(80.00))
            .andExpect(jsonPath("$.summary.profit").value(120.00))
            .andExpect(jsonPath("$.sales[0].id").value(activeId));

        mvc.perform(get("/api/reports/sales").header("Authorization", bearer(sellerToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    void dashboardAndChartsReturnServerAggregates() throws Exception {
        Product product = createProduct("Indicador", new BigDecimal("10.00"), new BigDecimal("25.00"), false);
        createSale(adminToken, product, "2", "25.00", "AVISTA");

        mvc.perform(get("/api/dashboard/overview").header("Authorization", bearer(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.today.revenue").value(50.00))
            .andExpect(jsonPath("$.month.profit").value(30.00))
            .andExpect(jsonPath("$.itemsSoldToday[0].name").value("Indicador"));

        mvc.perform(get("/api/reports/charts")
                .param("period", "DAY")
                .param("date", LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString())
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period").value("DAY"))
            .andExpect(jsonPath("$.currentRevenue").value(50.00))
            .andExpect(jsonPath("$.points.length()").value(24));
    }

    @Test
    void sellerReceiptContainsCustomerFacingDataButNeverCostOrProfit() throws Exception {
        Client client = createClient("Cliente Recibo");
        client.update("Cliente Recibo", "123", "51999999999", null, "Rua Um, 10", "Porto Alegre", null);
        clientRepository.save(client);
        Product product = createProduct("Item Recibo", new BigDecimal("15.00"), new BigDecimal("30.00"), false);

        String saleBody = mvc.perform(post("/api/sales")
                .header("Authorization", bearer(sellerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + client.getId() + "\",\"paymentType\":\"PRAZO\",\"items\":[{\"productId\":\"" + product.getId() + "\",\"quantity\":2,\"unitPrice\":30.00}]}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String saleId = json(saleBody).get("id").asText();

        mvc.perform(get("/api/sales/{id}/receipt", saleId).header("Authorization", bearer(sellerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.company.name").value("Phase 3 A"))
            .andExpect(jsonPath("$.client.phone").value("51999999999"))
            .andExpect(jsonPath("$.items[0].unitPrice").value(30.00))
            .andExpect(jsonPath("$.total").value(60.00))
            .andExpect(jsonPath("$.costTotal").doesNotExist())
            .andExpect(jsonPath("$.profitTotal").doesNotExist())
            .andExpect(jsonPath("$.items[0].unitCost").doesNotExist());
    }
}
