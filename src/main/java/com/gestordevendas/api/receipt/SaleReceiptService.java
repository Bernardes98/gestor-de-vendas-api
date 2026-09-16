package com.gestordevendas.api.receipt;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.sale.*;
import com.gestordevendas.api.storage.ObjectStorage;
import com.gestordevendas.api.tenant.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.util.List;

@Service
public class SaleReceiptService {
    private final SaleService saleService;
    private final SaleItemRepository itemRepository;
    private final SalePaymentRepository paymentRepository;
    private final ObjectStorage objectStorage;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;

    public SaleReceiptService(SaleService saleService, SaleItemRepository itemRepository,
                              SalePaymentRepository paymentRepository, ObjectStorage objectStorage,
                              CurrentUserService currentUserService, TenantContextService tenantContextService) {
        this.saleService = saleService; this.itemRepository = itemRepository; this.paymentRepository = paymentRepository;
        this.objectStorage = objectStorage; this.currentUserService = currentUserService; this.tenantContextService = tenantContextService;
    }

    @Transactional(readOnly = true)
    public SaleReceiptResponse get(java.util.UUID saleId) {
        TenantContext context = tenantContextService.requireForUser(currentUserService.requireUserId());
        Sale sale = saleService.require(saleId, context.companyId());
        Company company = sale.getCompany(); Client client = sale.getClient();
        BigDecimal paid = paymentRepository.sumPaid(context.companyId(), saleId).setScale(2, RoundingMode.HALF_UP);
        BigDecimal outstanding = sale.getPaymentType() == SalePaymentType.PRAZO && sale.getStatus() == SaleStatus.ATIVA
            ? sale.getTotal().subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2);
        String logo = company.getLogoKey() == null ? null : objectStorage.publicUrl(company.getLogoKey());
        SaleReceiptResponse.CompanyInfo companyInfo = new SaleReceiptResponse.CompanyInfo(company.getName(), company.getLegalName(),
            company.getDocument(), company.getPhone(), company.getEmail(), company.getAddress(), company.getCity(), logo);
        SaleReceiptResponse.ClientInfo clientInfo = client == null ? null : new SaleReceiptResponse.ClientInfo(client.getName(), client.getDocument(),
            client.getPhone(), client.getAddress(), client.getCity());
        List<SaleReceiptResponse.Item> items = itemRepository.findAllByCompanyIdAndSaleId(context.companyId(), saleId).stream()
            .map(item -> new SaleReceiptResponse.Item(item.getProductName(), item.getQuantity(), item.getUnitPrice(), item.getLineTotal())).toList();
        return new SaleReceiptResponse(companyInfo, clientInfo, sale.getNumber(), sale.getSoldAt(), sale.getPaymentType(),
            sale.getTotal(), paid, outstanding, items);
    }
}
