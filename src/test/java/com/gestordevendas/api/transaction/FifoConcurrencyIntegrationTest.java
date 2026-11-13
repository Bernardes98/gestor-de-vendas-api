package com.gestordevendas.api.transaction;

import com.gestordevendas.api.inventory.InventoryLotRepository;
import com.gestordevendas.api.product.Product;
import com.gestordevendas.api.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Regression tests using independent HTTP request transactions on concurrent threads. */
class FifoConcurrencyIntegrationTest extends BusinessIntegrationTestSupport {
    @Autowired private ProductRepository products;
    @Autowired private InventoryLotRepository lots;

    private int submitSale(Product product, String quantity) throws Exception {
        return mvc.perform(post("/api/sales")
                .header("Authorization", bearer(sellerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentType\":\"AVISTA\",\"items\":[{\"productId\":\"" +
                    product.getId() + "\",\"quantity\":" + quantity + ",\"unitPrice\":20}]}"))
            .andReturn().getResponse().getStatus();
    }

    private int updatePurchase(String purchaseId, Product product, String quantity) throws Exception {
        return mvc.perform(put("/api/purchases/{id}", purchaseId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":\"" + product.getId() +
                    "\",\"quantity\":" + quantity + ",\"unitCost\":5.00}]}"))
            .andReturn().getResponse().getStatus();
    }

    private int cancelPurchase(String purchaseId) throws Exception {
        return mvc.perform(delete("/api/purchases/{id}", purchaseId)
                .header("Authorization", bearer(adminToken)))
            .andReturn().getResponse().getStatus();
    }

    private void assertBalance(Product product, String expected) {
        BigDecimal principal = products.findById(product.getId()).orElseThrow().getCurrentStock();
        BigDecimal fifo = lots.allForCompany(company.getId()).stream()
            .filter(l -> l.getProduct().getId().equals(product.getId()))
            .map(l -> l.getRemainingQuantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, principal.compareTo(new BigDecimal(expected)), "Saldo principal");
        assertEquals(0, fifo.compareTo(new BigDecimal(expected)), "Saldo FIFO");
    }

    private List<Integer> runTogether(List<Callable<Integer>> actions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> action : actions) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Inicio simultaneo expirou");
                    return action.call();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Threads nao ficaram prontas");
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) statuses.add(future.get(30, TimeUnit.SECONDS));
            return statuses;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "Threads nao finalizaram");
        }
    }

    @Test
    void concurrentSalesOfSameProductCannotOversell() throws Exception {
        Product product = createProduct("FIFO concorrencia " + UUID.randomUUID(),
            new BigDecimal("5"), new BigDecimal("20"), true);
        createPurchase(adminToken, product, "5", "5.00");

        List<Integer> statuses = runTogether(List.of(
            () -> submitSale(product, "4"),
            () -> submitSale(product, "4")
        ));

        assertEquals(1, statuses.stream().filter(s -> s == 201).count(), "Apenas uma venda deve ser criada: " + statuses);
        assertEquals(1, statuses.stream().filter(s -> s == 409).count(), "Outra venda deve receber conflito de estoque: " + statuses);
        assertBalance(product, "1");
    }

    @Test
    void concurrentSalesOfDifferentProductsBothSucceed() throws Exception {
        Product first = createProduct("FIFO paralelo A " + UUID.randomUUID(),
            new BigDecimal("5"), new BigDecimal("20"), true);
        Product second = createProduct("FIFO paralelo B " + UUID.randomUUID(),
            new BigDecimal("7"), new BigDecimal("20"), true);
        createPurchase(adminToken, first, "5", "5.00");
        createPurchase(adminToken, second, "5", "7.00");

        List<Integer> statuses = runTogether(List.of(
            () -> submitSale(first, "3"),
            () -> submitSale(second, "4")
        ));

        assertEquals(List.of(201, 201), statuses, "Vendas de produtos distintos devem funcionar: " + statuses);
        assertBalance(first, "2");
        assertBalance(second, "1");
    }

    @Test
    void concurrentSaleAndPurchaseCancellationKeepFifoConsistent() throws Exception {
        Product product = createProduct("FIFO venda cancelamento " + UUID.randomUUID(),
            new BigDecimal("5"), new BigDecimal("20"), true);
        String purchaseId = createPurchase(adminToken, product, "5", "5.00");

        List<Integer> statuses = runTogether(List.of(
            () -> submitSale(product, "3"),
            () -> cancelPurchase(purchaseId)
        ));

        int sale = statuses.get(0);
        int cancellation = statuses.get(1);
        assertTrue((sale == 201 && cancellation == 409) ||
                   (sale == 409 && cancellation == 204),
            "Venda e cancelamento nao podem confirmar juntos: " + statuses);
        assertBalance(product, sale == 201 ? "2" : "0");
    }

    @Test
    void concurrentSaleAndPurchaseEditKeepFifoConsistent() throws Exception {
        Product product = createProduct("FIFO venda edicao " + UUID.randomUUID(),
            new BigDecimal("5"), new BigDecimal("20"), true);
        String purchaseId = createPurchase(adminToken, product, "5", "5.00");

        List<Integer> statuses = runTogether(List.of(
            () -> submitSale(product, "4"),
            () -> updatePurchase(purchaseId, product, "3")
        ));

        int sale = statuses.get(0);
        int edit = statuses.get(1);
        assertTrue((sale == 201 && edit == 409) ||
                   (sale == 409 && edit == 200),
            "Venda e reducao da compra nao podem confirmar juntas: " + statuses);
        assertBalance(product, sale == 201 ? "1" : "3");
    }

    @Test
    void concurrentEditAndCancellationOfSamePurchaseKeepFifoConsistent() throws Exception {
        Product product = createProduct("FIFO edicao cancelamento " + UUID.randomUUID(),
            new BigDecimal("5"), new BigDecimal("20"), true);
        String purchaseId = createPurchase(adminToken, product, "5", "5.00");

        List<Integer> statuses = runTogether(List.of(
            () -> updatePurchase(purchaseId, product, "7"),
            () -> cancelPurchase(purchaseId)
        ));

        int edit = statuses.get(0);
        int cancellation = statuses.get(1);
        assertTrue((edit == 200 && cancellation == 204) ||
                   (edit == 409 && cancellation == 204),
            "Cancelamento deve vencer ou suceder a edicao sem ressuscitar a compra: " + statuses);
        assertBalance(product, "0");
    }
}
