package com.gestordevendas.api.order;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.client.ClientRepository;
import com.gestordevendas.api.category.ProductCategory;
import com.gestordevendas.api.category.ProductCategoryRepository;
import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.pricing.ClientProductPrice;
import com.gestordevendas.api.pricing.ClientProductPriceRepository;
import com.gestordevendas.api.product.*;
import com.gestordevendas.api.promotion.*;
import com.gestordevendas.api.storage.ObjectStorage;
import com.gestordevendas.api.mail.MailProperties;
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
    private final ProductCategoryRepository categoryRepository;
    private final ProductPhotoRepository photoRepository;
    private final ClientProductPriceRepository priceRepository;
    private final ClientHiddenProductRepository hiddenRepository;
    private final ClientProductPromotionRepository promotionRepository;
    private final CustomerOrderRepository orderRepository;
    private final CustomerOrderItemRepository itemRepository;
    private final ObjectStorage objectStorage;
    private final MailProperties mailProperties;

    public PublicOrderService(ClientRepository clientRepository, ProductRepository productRepository,
                              ProductCategoryRepository categoryRepository, ProductPhotoRepository photoRepository, ClientProductPriceRepository priceRepository,
                              ClientHiddenProductRepository hiddenRepository, ClientProductPromotionRepository promotionRepository, CustomerOrderRepository orderRepository,
                              CustomerOrderItemRepository itemRepository, ObjectStorage objectStorage, MailProperties mailProperties) {
        this.clientRepository = clientRepository; this.productRepository = productRepository; this.categoryRepository = categoryRepository; this.photoRepository = photoRepository;
        this.priceRepository = priceRepository; this.hiddenRepository = hiddenRepository; this.promotionRepository = promotionRepository; this.orderRepository = orderRepository;
        this.itemRepository = itemRepository; this.objectStorage = objectStorage; this.mailProperties = mailProperties;
    }

    @Transactional(readOnly = true)
    public PublicOrderCatalogResponse catalog(String token) {
        Client client = requirePublicClient(token); UUID companyId = client.getCompany().getId();
        Set<UUID> hidden = hiddenRepository.findAllByCompanyIdAndClientId(companyId, client.getId()).stream()
            .map(v -> v.getProduct().getId()).collect(Collectors.toSet());
        Map<UUID, BigDecimal> prices = priceRepository.findAllByCompanyIdAndClientId(companyId, client.getId()).stream()
            .collect(Collectors.toMap(ClientProductPrice::getProductReferenceId, ClientProductPrice::getPrice));
        Map<UUID, ClientProductPromotion> promotions = promotionRepository.findAllByCompany_IdAndClient_Id(companyId, client.getId()).stream()
            .collect(Collectors.toMap(ClientProductPromotion::getProductId, Function.identity()));
        List<PublicOrderCatalogResponse.Group> groups = categoryRepository.findAllByCompanyIdOrderByOrderIndexAscNameAsc(companyId).stream()
            .map(group -> new PublicOrderCatalogResponse.Group(group.getId(), group.getName(), group.getOrderIndex()))
            .toList();
        List<PublicOrderCatalogResponse.Product> products = productRepository.findAllByCompanyIdAndActiveTrueOrderByNameAsc(companyId).stream()
            .filter(product -> !hidden.contains(product.getId()))
            .map(product -> {
                ClientProductPromotion promotion = promotions.get(product.getId());
                return new PublicOrderCatalogResponse.Product(product.getId(), product.getName(), product.getBrand(), product.getCode(),
                    prices.getOrDefault(product.getId(), product.getSalePrice()),
                    promotion == null ? null : promotion.getPromotionalPrice(),
                    promotion == null ? null : promotion.getMinimumQuantity(),
                    photoUrls(companyId, product.getId()), product.getCategory() == null ? null : product.getCategory().getId(),
                    product.isStockControlled(), product.getCurrentStock());
            })
            .toList();
        String logo = client.getCompany().getLogoKey() == null ? null : objectStorage.publicUrl(client.getCompany().getLogoKey());
        return new PublicOrderCatalogResponse(client.getCompany().getName(), logo, client.getCompany().getPrimaryColor(),
            client.getCompany().getSecondaryColor(), client.getName(), groups, products);
    }


    @Transactional(readOnly = true)
    public String sharePage(String token) {
        Client client = requirePublicClient(token);
        String companyName = client.getCompany().getName();
        String appBaseUrl = trimTrailingSlash(mailProperties.appBaseUrl());
        String targetUrl = appBaseUrl + "/pedido/" + token;
        String logoUrl = client.getCompany().getLogoKey() == null
            ? appBaseUrl + "/icons/icon-512.png"
            : objectStorage.publicUrl(client.getCompany().getLogoKey());
        String title = companyName + " • Pedido online";
        String description = "Faça seu pedido online com " + companyName + ".";
        return "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>" + html(title) + "</title>"
            + "<meta property=\"og:type\" content=\"website\">"
            + "<meta property=\"og:title\" content=\"" + html(title) + "\">"
            + "<meta property=\"og:description\" content=\"" + html(description) + "\">"
            + "<meta property=\"og:image\" content=\"" + html(logoUrl) + "\">"
            + "<meta property=\"og:image:secure_url\" content=\"" + html(logoUrl) + "\">"
            + "<meta property=\"og:image:alt\" content=\"Logo " + html(companyName) + "\">"
            + "<meta property=\"og:url\" content=\"" + html(targetUrl) + "\">"
            + "<meta name=\"twitter:card\" content=\"summary_large_image\">"
            + "<meta name=\"twitter:title\" content=\"" + html(title) + "\">"
            + "<meta name=\"twitter:description\" content=\"" + html(description) + "\">"
            + "<meta name=\"twitter:image\" content=\"" + html(logoUrl) + "\">"
            + "<meta http-equiv=\"refresh\" content=\"0;url=" + html(targetUrl) + "\">"
            + "</head><body><p>Abrindo pedido de " + html(companyName) + "...</p>"
            + "<script>location.replace(" + jsString(targetUrl) + ");</script></body></html>";
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "http://localhost:5173";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String html(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String jsString(String value) {
        String safe = value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c").replace(">", "\\u003e");
        return "\"" + safe + "\"";
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
        List<CustomerOrderItem> items = java.util.stream.IntStream.range(0, prepared.size()).mapToObj(position -> {
            PreparedItem value = prepared.get(position);
            return CustomerOrderItem.create(client.getCompany(), order, value.product(), value.quantity(),
                value.unitPrice(), value.lineTotal(), position);
        }).toList();
        itemRepository.saveAll(items);
        return response(order, items);
    }

    @Transactional
    public CustomerOrderResponse update(String token, UUID orderId, PublicOrderRequest request) {
        Client client = requirePublicClient(token);
        UUID companyId = client.getCompany().getId();
        CustomerOrder order = orderRepository.findByIdAndCompanyIdAndClientId(orderId, companyId, client.getId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Pedido não encontrado."));
        if (order.getStatus() != CustomerOrderStatus.PENDENTE) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_EDITABLE", "Este pedido não pode mais ser editado.");
        }
        if (request.items().stream().map(PublicOrderRequest.Item::productId).distinct().count() != request.items().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_PRODUCT", "Produto duplicado no pedido.");
        }
        List<PreparedItem> prepared = request.items().stream().map(item -> prepare(client, item)).toList();
        BigDecimal total = prepared.stream().map(PreparedItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        order.updatePending(request.notes(), total);
        itemRepository.deleteAll(itemRepository.findAllByCompanyIdAndOrderIdOrderByPositionAsc(companyId, order.getId()));
        itemRepository.flush();
        List<CustomerOrderItem> items = java.util.stream.IntStream.range(0, prepared.size()).mapToObj(position -> {
            PreparedItem value = prepared.get(position);
            return CustomerOrderItem.create(client.getCompany(), order, value.product(), value.quantity(),
                value.unitPrice(), value.lineTotal(), position);
        }).toList();
        itemRepository.saveAll(items);
        return response(orderRepository.save(order), items);
    }

    @Transactional(readOnly = true)
    public CustomerOrderResponse recent(String token) {
        Client client = requirePublicClient(token);
        return orderRepository.findFirstByCompanyIdAndClientIdAndStatusInOrderByCreatedAtDesc(client.getCompany().getId(), client.getId(),
                List.of(CustomerOrderStatus.PENDENTE, CustomerOrderStatus.CONVERTIDO))
            .map(order -> response(order, itemRepository.findAllByCompanyIdAndOrderIdOrderByPositionAsc(client.getCompany().getId(), order.getId())))
            .orElse(null);
    }

    private PreparedItem prepare(Client client, PublicOrderRequest.Item request) {
        UUID companyId = client.getCompany().getId();
        Product product = productRepository.findByIdAndCompanyIdAndActiveTrue(request.productId(), companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Produto não encontrado."));
        if (hiddenRepository.existsByCompanyIdAndClientIdAndProductId(companyId, client.getId(), product.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Produto não encontrado.");
        }
        if (product.isStockControlled() && product.getCurrentStock().compareTo(request.quantity()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Produto sem estoque suficiente para a quantidade solicitada.");
        }
        BigDecimal price = priceRepository.findByCompanyIdAndClientIdAndProductId(companyId, client.getId(), product.getId())
            .map(ClientProductPrice::getPrice).orElse(product.getSalePrice()).setScale(2, RoundingMode.HALF_UP);
        Optional<ClientProductPromotion> promotion = promotionRepository.findByCompany_IdAndClient_IdAndProduct_Id(companyId, client.getId(), product.getId());
        if (promotion.isPresent() && request.quantity().compareTo(promotion.get().getMinimumQuantity()) >= 0) {
            price = promotion.get().getPromotionalPrice().setScale(2, RoundingMode.HALF_UP);
        }
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
            order.getViewedAt(), order.getNotes(), order.getTotal(), order.getSaleId(), order.getCreatedAt(),
            items.stream().map(item -> new CustomerOrderItemResponse(item.getId(), item.getProduct().getId(), item.getProductName(),
                item.getQuantity(), item.getUnitPrice(), item.getLineTotal())).toList());
    }

    static String apiStatus(CustomerOrderStatus status) {
        return switch (status) { case PENDENTE -> "PENDING"; case CONVERTIDO -> "CONVERTED"; case RECUSADO -> "REJECTED"; };
    }

    private record PreparedItem(Product product, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
}
