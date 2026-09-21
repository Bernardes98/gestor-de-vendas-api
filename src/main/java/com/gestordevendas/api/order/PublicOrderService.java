package com.gestordevendas.api.order;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.client.ClientRepository;
import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.pricing.ClientProductPrice;
import com.gestordevendas.api.pricing.ClientProductPriceRepository;
import com.gestordevendas.api.product.*;
import com.gestordevendas.api.storage.ObjectStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PublicOrderService {
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final ProductPhotoRepository photoRepository;
    private final ClientProductPriceRepository priceRepository;
    private final ClientHiddenProductRepository hiddenRepository;
    private final CustomerOrderRepository orderRepository;
    private final CustomerOrderItemRepository itemRepository;
    private final ObjectStorage objectStorage;

    public PublicOrderService(ClientRepository clientRepository, ProductRepository productRepository,
                              ProductPhotoRepository photoRepository, ClientProductPriceRepository priceRepository,
                              ClientHiddenProductRepository hiddenRepository, CustomerOrderRepository orderRepository,
                              CustomerOrderItemRepository itemRepository, ObjectStorage objectStorage) {
        this.clientRepository = clientRepository; this.productRepository = productRepository; this.photoRepository = photoRepository;
        this.priceRepository = priceRepository; this.hiddenRepository = hiddenRepository; this.orderRepository = orderRepository;
        this.itemRepository = itemRepository; this.objectStorage = objectStorage;
    }

    @Transactional(readOnly = true)
    public PublicOrderCatalogResponse catalog(String token) {
        Client client = requirePublicClient(token); UUID companyId = client.getCompany().getId();
        Set<UUID> hidden = hiddenRepository.findAllByCompanyIdAndClientId(companyId, client.getId()).stream()
            .map(v -> v.getProduct().getId()).collect(Collectors.toSet());
        Map<UUID, BigDecimal> prices = priceRepository.findAllByCompanyIdAndClientId(companyId, client.getId()).stream()
            .collect(Collectors.toMap(ClientProductPrice::getProductReferenceId, ClientProductPrice::getPrice));
        List<PublicOrderCatalogResponse.Product> products = productRepository.findAllByCompanyIdAndActiveTrueOrderByNameAsc(companyId).stream()
            .filter(product -> !hidden.contains(product.getId()))
            .map(product -> new PublicOrderCatalogResponse.Product(product.getId(), product.getName(), product.getBrand(), product.getCode(),
                prices.getOrDefault(product.getId(), product.getSalePrice()), photoUrls(companyId, product.getId())))
            .toList();
        String logo = client.getCompany().getLogoKey() == null ? null : objectStorage.publicUrl(client.getCompany().getLogoKey());
        return new PublicOrderCatalogResponse(client.getCompany().getName(), logo, client.getCompany().getPrimaryColor(),
            client.getCompany().getSecondaryColor(), client.getName(), products);
    }

    @Transactional
    public CustomerOrderResponse create(String token, PublicOrderRequest request) {
        Client client = requirePublicClient(token); UUID companyId = client.getCompany().getId();
        if (request.items().stream().map(PublicOrderRequest.Item::productId).distinct().count() != request.items().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_PRODUCT", "Produto duplicado no pedido.");
        }
        List<PreparedItem> prepared = request.items().stream().map(item -> prepare(client, item)).toList();
        BigDecimal total = prepared.stream().map(PreparedItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        CustomerOrder order = orderRepository.save(CustomerOrder.create(client.getCompany(), client, request.notes(), total));
        List<CustomerOrderItem> items = prepared.stream().map(value -> CustomerOrderItem.create(client.getCompany(), order,
            value.product(), value.quantity(), value.unitPrice(), value.lineTotal())).toList();
        itemRepository.saveAll(items);
        return response(order, items);
    }

    @Transactional(readOnly = true)
    public CustomerOrderResponse recent(String token) {
        Client client = requirePublicClient(token);
        return orderRepository.findFirstByCompanyIdAndClientIdAndStatusInOrderByCreatedAtDesc(client.getCompany().getId(), client.getId(),
                List.of(CustomerOrderStatus.PENDENTE, CustomerOrderStatus.CONVERTIDO))
            .map(order -> response(order, itemRepository.findAllByCompanyIdAndOrderIdOrderByCreatedAtAsc(client.getCompany().getId(), order.getId())))
            .orElse(null);
    }

    private PreparedItem prepare(Client client, PublicOrderRequest.Item request) {
        UUID companyId = client.getCompany().getId();
        Product product = productRepository.findByIdAndCompanyIdAndActiveTrue(request.productId(), companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Produto não encontrado."));
        if (hiddenRepository.existsByCompanyIdAndClientIdAndProductId(companyId, client.getId(), product.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Produto não encontrado.");
        }
        BigDecimal price = priceRepository.findByCompanyIdAndClientIdAndProductId(companyId, client.getId(), product.getId())
            .map(ClientProductPrice::getPrice).orElse(product.getSalePrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = price.multiply(request.quantity()).setScale(2, RoundingMode.HALF_UP);
        return new PreparedItem(product, request.quantity(), price, total);
    }

    private Client requirePublicClient(String token) {
        if (token == null || token.isBlank()) throw invalidLink();
        UUID orderToken;
        try { orderToken = UUID.fromString(token); } catch (IllegalArgumentException ex) { throw invalidLink(); }
        Client client = clientRepository.findByOrderTokenAndActiveTrue(orderToken).orElseThrow(this::invalidLink);
        if (!client.getCompany().isActive()) throw invalidLink();
        return client;
    }

    private ApiException invalidLink() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_LINK_NOT_FOUND", "Este link de pedido não é válido ou não está mais disponível.");
    }

    private List<String> photoUrls(UUID companyId, UUID productId) {
        return photoRepository.findAllByCompanyIdAndProductIdOrderByOrderIndexAsc(companyId, productId).stream()
            .map(photo -> photo.getUrl()).toList();
    }

    static CustomerOrderResponse response(CustomerOrder order, List<CustomerOrderItem> items) {
        return new CustomerOrderResponse(order.getId(), order.getClient().getId(), order.getClient().getName(), apiStatus(order.getStatus()),
            order.getViewedAt(), order.getNotes(), order.getTotal(), order.getSale() == null ? null : order.getSale().getId(), order.getCreatedAt(),
            items.stream().map(item -> new CustomerOrderItemResponse(item.getId(), item.getProduct().getId(), item.getProductName(),
                item.getQuantity(), item.getUnitPrice(), item.getLineTotal())).toList());
    }

    static String apiStatus(CustomerOrderStatus status) {
        return switch (status) { case PENDENTE -> "PENDING"; case CONVERTIDO -> "CONVERTED"; case RECUSADO -> "REJECTED"; };
    }

    private record PreparedItem(Product product, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
}
