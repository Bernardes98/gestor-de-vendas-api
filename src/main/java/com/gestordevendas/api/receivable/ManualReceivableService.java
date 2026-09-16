package com.gestordevendas.api.receivable;

import com.gestordevendas.api.audit.AuditService;
import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.client.ClientRepository;
import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.sale.PaymentRequest;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import com.gestordevendas.api.user.User;
import com.gestordevendas.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Service
public class ManualReceivableService {
    private final ManualReceivableRepository repository;
    private final ManualReceivablePaymentRepository paymentRepository;
    private final ClientRepository clientRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;
    private final AuditService auditService;

    public ManualReceivableService(ManualReceivableRepository repository, ManualReceivablePaymentRepository paymentRepository,
                                   ClientRepository clientRepository, CompanyRepository companyRepository, UserRepository userRepository,
                                   CurrentUserService currentUserService, TenantContextService tenantContextService, TenantGuard tenantGuard, AuditService auditService) {
        this.repository = repository; this.paymentRepository = paymentRepository; this.clientRepository = clientRepository;
        this.companyRepository = companyRepository; this.userRepository = userRepository; this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService; this.tenantGuard = tenantGuard; this.auditService = auditService;
    }

    @Transactional
    public ManualReceivableResponse create(ManualReceivableRequest request) {
        TenantContext context = adminContext(); Company company = companyRepository.findById(context.companyId()).orElseThrow();
        User user = userRepository.findById(context.userId()).orElseThrow();
        Client client = request.clientId() == null ? null : clientRepository.findByIdAndCompanyIdAndActiveTrue(request.clientId(), context.companyId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND", "Cliente não encontrado."));
        ManualReceivable r = repository.save(ManualReceivable.create(company, client, request.description(), money(request.totalAmount()), user));
        auditService.record("MANUAL_RECEIVABLE_CREATED", context.companyId(), context.userId(), "MANUAL_RECEIVABLE", r.getId(), null, Map.of("total", r.getTotalAmount()));
        return response(r, context.companyId());
    }

    @Transactional
    public ManualReceivableResponse addPayment(UUID id, PaymentRequest request) {
        TenantContext context = adminContext(); ManualReceivable r = requireOpen(id, context.companyId());
        BigDecimal outstanding = outstanding(r, context.companyId());
        if (request.amount().compareTo(outstanding) > 0) throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_EXCEEDS_BALANCE", "Pagamento maior que o saldo em aberto.");
        Company company = companyRepository.findById(context.companyId()).orElseThrow(); User user = userRepository.findById(context.userId()).orElseThrow();
        paymentRepository.save(ManualReceivablePayment.create(company, r, money(request.amount()), user));
        if (money(request.amount()).compareTo(outstanding) == 0) r.settle();
        auditService.record("MANUAL_RECEIVABLE_PAYMENT_ADDED", context.companyId(), context.userId(), "MANUAL_RECEIVABLE", r.getId(), null, Map.of("amount", request.amount()));
        return response(r, context.companyId());
    }

    @Transactional
    public ManualReceivableResponse settle(UUID id) {
        TenantContext context = adminContext(); ManualReceivable r = requireOpen(id, context.companyId()); BigDecimal outstanding = outstanding(r, context.companyId());
        if (outstanding.signum() > 0) {
            Company company = companyRepository.findById(context.companyId()).orElseThrow(); User user = userRepository.findById(context.userId()).orElseThrow();
            paymentRepository.save(ManualReceivablePayment.create(company, r, outstanding, user));
        }
        r.settle(); auditService.record("MANUAL_RECEIVABLE_SETTLED", context.companyId(), context.userId(), "MANUAL_RECEIVABLE", r.getId(), null, Map.of()); return response(r, context.companyId());
    }

    public BigDecimal paid(UUID companyId, UUID id) { return money(paymentRepository.sumPaid(companyId, id)); }
    public BigDecimal outstanding(ManualReceivable r, UUID companyId) {
        if (r.getStatus() == ManualReceivableStatus.QUITADO) return money(BigDecimal.ZERO);
        return money(r.getTotalAmount().subtract(paid(companyId, r.getId())).max(BigDecimal.ZERO));
    }
    public ManualReceivableResponse response(ManualReceivable r, UUID companyId) {
        BigDecimal paid = paid(companyId, r.getId()); return new ManualReceivableResponse(r.getId(), r.getClient() == null ? null : r.getClient().getId(),
            r.getClient() == null ? null : r.getClient().getName(), r.getDescription(), r.getTotalAmount(), paid, outstanding(r, companyId), r.getStatus());
    }
    private ManualReceivable requireOpen(UUID id, UUID companyId) {
        ManualReceivable r = repository.findByIdAndCompanyId(id, companyId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECEIVABLE_NOT_FOUND", "Recebível não encontrado."));
        if (r.getStatus() == ManualReceivableStatus.QUITADO) throw new ApiException(HttpStatus.CONFLICT, "RECEIVABLE_SETTLED", "Recebível já quitado."); return r;
    }
    private TenantContext adminContext() { TenantContext c = tenantContextService.requireForUser(currentUserService.requireUserId()); tenantGuard.requireOwnerOrAdmin(c); return c; }
    private BigDecimal money(BigDecimal value) { return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP); }
}
