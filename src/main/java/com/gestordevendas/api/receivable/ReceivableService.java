package com.gestordevendas.api.receivable;

import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.sale.*;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ReceivableService {
    private final SaleRepository saleRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final ManualReceivableRepository manualRepository;
    private final ManualReceivablePaymentRepository manualPaymentRepository;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;

    public ReceivableService(SaleRepository saleRepository, SalePaymentRepository salePaymentRepository,
                             ManualReceivableRepository manualRepository, ManualReceivablePaymentRepository manualPaymentRepository,
                             CurrentUserService currentUserService, TenantContextService tenantContextService, TenantGuard tenantGuard) {
        this.saleRepository = saleRepository; this.salePaymentRepository = salePaymentRepository; this.manualRepository = manualRepository;
        this.manualPaymentRepository = manualPaymentRepository; this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService; this.tenantGuard = tenantGuard;
    }

    @Transactional(readOnly = true)
    public List<ReceivableResponse> list() {
        TenantContext context = tenantContextService.requireForUser(currentUserService.requireUserId()); tenantGuard.requireOwnerOrAdmin(context);
        List<ReceivableResponse> result = new ArrayList<>();
        for (Sale sale : saleRepository.findAllByCompanyIdAndStatusAndPaymentTypeOrderBySoldAtDesc(context.companyId(), SaleStatus.ATIVA, SalePaymentType.PRAZO)) {
            BigDecimal paid = salePaymentRepository.sumPaid(context.companyId(), sale.getId());
            BigDecimal outstanding = sale.getTotal().subtract(paid).max(BigDecimal.ZERO);
            if (outstanding.signum() > 0) result.add(new ReceivableResponse("SALE", sale.getId(), sale.getNumber(),
                sale.getClient() == null ? null : sale.getClient().getId(), sale.getClient() == null ? null : sale.getClient().getName(),
                "Venda #" + sale.getNumber(), sale.getTotal(), paid, outstanding, sale.getSoldAt()));
        }
        for (ManualReceivable r : manualRepository.findAllByCompanyIdAndStatusOrderByCreatedAtDesc(context.companyId(), ManualReceivableStatus.ABERTO)) {
            BigDecimal paid = manualPaymentRepository.sumPaid(context.companyId(), r.getId());
            BigDecimal outstanding = r.getTotalAmount().subtract(paid).max(BigDecimal.ZERO);
            if (outstanding.signum() > 0) result.add(new ReceivableResponse("MANUAL", r.getId(), null,
                r.getClient() == null ? null : r.getClient().getId(), r.getClient() == null ? null : r.getClient().getName(),
                r.getDescription(), r.getTotalAmount(), paid, outstanding, r.getCreatedAt()));
        }
        result.sort(Comparator.comparing(ReceivableResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }
}
