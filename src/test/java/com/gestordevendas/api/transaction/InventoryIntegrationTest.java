package com.gestordevendas.api.transaction;

import com.gestordevendas.api.product.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InventoryIntegrationTest extends BusinessIntegrationTestSupport {

    @Autowired JdbcTemplate jdbc;

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

        adjust(adminToken, product.getId(), "-3", "Perda");

        mvc.perform(delete("/api/purchases/{id}", purchaseId).header("Authorization", bearer(adminToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PURCHASE_LOT_ALREADY_CONSUMED"));
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

    @Test
    void manualEntryCreatesLotAndTraceableMovement() throws Exception {
        Product product = createProduct("Entrada rastreavel", new BigDecimal("12.50"), new BigDecimal("20.00"), true);
        adjust(adminToken, product.getId(), "7", "Inventario inicial");

        UUID movementId = lastManualMovement(product.getId());
        assertNotNull(movementId);
        assertQuantity("7", productStock(product.getId()));
        assertQuantity("7", fifoStock(product.getId()));
        assertEquals(1, adjustmentCount(movementId));
        assertQuantity("7", adjustmentTotal(movementId));
        assertEquals(1, jdbc.queryForObject("""
            SELECT count(*) FROM api_internal.estoque_lote_ajustes a
            JOIN api_internal.estoque_lotes l ON l.id = a.lote_id
            WHERE a.movimentacao_id = ? AND a.empresa_id = ? AND a.produto_id = ?
              AND a.origem = 'AJUSTE_MANUAL' AND a.motivo = 'Inventario inicial'
              AND l.quantidade_inicial = 7 AND l.quantidade_restante = 7
            """, Integer.class, movementId, company.getId(), product.getId()));
    }

    @Test
    void manualExitConsumesOldestLotsAndRecordsEachLot() throws Exception {
        Product product = createProduct("Saida FIFO", new BigDecimal("10.00"), new BigDecimal("20.00"), true);
        createPurchase(adminToken, product, "3", "10.00");
        createPurchase(adminToken, product, "5", "20.00");

        adjust(adminToken, product.getId(), "-6", "Quebra de estoque");
        UUID movementId = lastManualMovement(product.getId());

        assertQuantity("2", productStock(product.getId()));
        assertQuantity("2", fifoStock(product.getId()));
        assertEquals(2, adjustmentCount(movementId));
        assertQuantity("-6", adjustmentTotal(movementId));
        assertEquals(1, jdbc.queryForObject("""
            SELECT count(*) FROM api_internal.estoque_lote_ajustes a
            JOIN api_internal.estoque_lotes l ON l.id = a.lote_id
            WHERE a.movimentacao_id = ? AND a.quantidade_delta = -3
              AND l.custo_unitario = 10 AND l.quantidade_restante = 0
            """, Integer.class, movementId));
        assertEquals(1, jdbc.queryForObject("""
            SELECT count(*) FROM api_internal.estoque_lote_ajustes a
            JOIN api_internal.estoque_lotes l ON l.id = a.lote_id
            WHERE a.movimentacao_id = ? AND a.quantidade_delta = -3
              AND l.custo_unitario = 20 AND l.quantidade_restante = 2
            """, Integer.class, movementId));
    }

    @Test
    void rejectedAdjustmentDoesNotPersistMovementOrLotAdjustment() throws Exception {
        Product product = createProduct("Sem saldo", BigDecimal.ONE, BigDecimal.TEN, true);
        createPurchase(adminToken, product, "2", "1.00");
        int movementsBefore = manualMovementCount(product.getId());
        int adjustmentsBefore = productAdjustmentCount(product.getId());

        mvc.perform(post("/api/stock/adjustments")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + product.getId() + "\",\"quantityDelta\":-3,\"reason\":\"Excesso\"}"))
            .andExpect(status().isConflict());

        assertQuantity("2", productStock(product.getId()));
        assertQuantity("2", fifoStock(product.getId()));
        assertEquals(movementsBefore, manualMovementCount(product.getId()));
        assertEquals(adjustmentsBefore, productAdjustmentCount(product.getId()));
    }

    @Test
    void otherTenantCannotAdjustProductOrItsLots() throws Exception {
        Product product = createProduct("Produto isolado", BigDecimal.ONE, BigDecimal.TEN, true);
        createPurchase(adminToken, product, "4", "1.00");

        mvc.perform(post("/api/stock/adjustments")
                .header("Authorization", bearer(otherOwnerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + product.getId() + "\",\"quantityDelta\":-2,\"reason\":\"Invasao\"}"))
            .andExpect(status().isNotFound());

        assertQuantity("4", productStock(product.getId()));
        assertQuantity("4", fifoStock(product.getId()));
        assertEquals(0, productAdjustmentCount(product.getId()));
    }

    private void adjust(String token, UUID productId, String delta, String reason) throws Exception {
        mvc.perform(post("/api/stock/adjustments")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + productId + "\",\"quantityDelta\":" + delta +
                    ",\"reason\":\"" + reason + "\"}"))
            .andExpect(status().isOk());
    }

    private UUID lastManualMovement(UUID productId) {
        return jdbc.queryForObject("""
            SELECT id FROM api_internal.movimentacoes_estoque
            WHERE empresa_id = ? AND produto_id = ? AND tipo IN ('AJUSTE_ENTRADA', 'AJUSTE_SAIDA')
            ORDER BY created_at DESC, id DESC LIMIT 1
            """, UUID.class, company.getId(), productId);
    }

    private int manualMovementCount(UUID productId) {
        return jdbc.queryForObject("""
            SELECT count(*) FROM api_internal.movimentacoes_estoque
            WHERE empresa_id = ? AND produto_id = ? AND tipo IN ('AJUSTE_ENTRADA', 'AJUSTE_SAIDA')
            """, Integer.class, company.getId(), productId);
    }

    private int productAdjustmentCount(UUID productId) {
        return jdbc.queryForObject("""
            SELECT count(*) FROM api_internal.estoque_lote_ajustes
            WHERE empresa_id = ? AND produto_id = ?
            """, Integer.class, company.getId(), productId);
    }

    private int adjustmentCount(UUID movementId) {
        return jdbc.queryForObject("""
            SELECT count(*) FROM api_internal.estoque_lote_ajustes WHERE movimentacao_id = ?
            """, Integer.class, movementId);
    }

    private BigDecimal adjustmentTotal(UUID movementId) {
        return jdbc.queryForObject("""
            SELECT coalesce(sum(quantidade_delta), 0)
            FROM api_internal.estoque_lote_ajustes WHERE movimentacao_id = ?
            """, BigDecimal.class, movementId);
    }

    private BigDecimal fifoStock(UUID productId) {
        return jdbc.queryForObject("""
            SELECT coalesce(sum(quantidade_restante), 0)
            FROM api_internal.estoque_lotes WHERE empresa_id = ? AND produto_id = ?
            """, BigDecimal.class, company.getId(), productId);
    }

    private BigDecimal productStock(UUID productId) throws Exception {
        String body = mvc.perform(get("/api/products/{id}", productId)
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return json(body).get("currentStock").decimalValue();
    }

    private void assertQuantity(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
            () -> "Quantidade esperada: " + expected + "; obtida: " + actual);
    }
}
