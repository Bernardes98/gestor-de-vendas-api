package com.gestordevendas.api.platform;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.audit.AuditQueryService;
import com.gestordevendas.api.audit.AuditResponse;
import com.gestordevendas.api.invite.CompanyInvite;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {
    private final PlatformService platformService;
    private final AuditQueryService auditQueryService;

    public PlatformController(PlatformService platformService, AuditQueryService auditQueryService) {
        this.platformService = platformService;
        this.auditQueryService = auditQueryService;
    }

    @PostMapping("/companies/invites")
    ResponseEntity<CompanyInviteResponse> createInvite(@Valid @RequestBody CreateCompanyInviteRequest request) {
        CompanyInvite invite = platformService.createCompanyInvite(new PlatformService.CreateCompanyInviteCommand(
            request.name(), request.legalName(), request.document(), request.ownerEmail(),
            request.primaryColor(), request.secondaryColor()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(invite.getId()).toUri();
        return ResponseEntity.created(location).body(toInviteResponse(invite));
    }

    @PostMapping("/company-invites/{id}/resend")
    ResponseEntity<Void> resend(@PathVariable UUID id) {
        platformService.resendInvite(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/company-invites/{id}")
    ResponseEntity<Void> cancel(@PathVariable UUID id) {
        platformService.cancelInvite(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/companies/{id}/block")
    ResponseEntity<Void> block(@PathVariable UUID id) {
        platformService.setCompanyActive(id, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/companies/{id}/activate")
    ResponseEntity<Void> activate(@PathVariable UUID id) {
        platformService.setCompanyActive(id, true);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/companies")
    List<CompanySummaryResponse> list() {
        return platformService.listCompanySummaries().stream()
            .map(company -> new CompanySummaryResponse(
                company.id(),
                company.name(),
                company.slug(),
                company.active(),
                company.ownerEmail(),
                company.userCount(),
                company.clientCount(),
                company.productCount(),
                company.saleCount()))
            .toList();
    }

    @GetMapping("/companies/{id}/users")
    List<PlatformService.CompanyUserView> users(@PathVariable UUID id) {
        return platformService.listCompanyUsers(id);
    }


    @GetMapping("/companies/{id}/audit")
    List<AuditResponse> audit(@PathVariable UUID id,
                              @RequestParam(required = false) String action,
                              @RequestParam(required = false) String entityType,
                              @RequestParam(required = false) Instant from,
                              @RequestParam(required = false) Instant to,
                              @RequestParam(defaultValue = "100") int limit) {
        return auditQueryService.platformCompany(id, action, entityType, from, to, limit);
    }

    private CompanyInviteResponse toInviteResponse(CompanyInvite invite) {
        Company company = invite.getCompany();
        return new CompanyInviteResponse(invite.getId(),
            new CompanyResponse(company.getId(), company.getName(), company.getSlug(), company.isActive()),
            invite.getOwnerEmail(), invite.getExpiresAt());
    }

    public record CreateCompanyInviteRequest(
        @NotBlank String name,
        String legalName,
        String document,
        @NotBlank @Email String ownerEmail,
        String primaryColor,
        String secondaryColor
    ) {}

    public record CompanyResponse(UUID id, String name, String slug, boolean active) {}
    public record CompanySummaryResponse(UUID id, String name, String slug, boolean active, String ownerEmail,
                                         long userCount, long clientCount, long productCount, long saleCount) {}
    public record CompanyInviteResponse(UUID id, CompanyResponse company, String ownerEmail, Instant expiresAt) {}
}
