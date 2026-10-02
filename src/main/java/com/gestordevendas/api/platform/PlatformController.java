package com.gestordevendas.api.platform;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.feature.CompanyFeature;
import com.gestordevendas.api.feature.CompanyFeatureService;
import com.gestordevendas.api.audit.AuditQueryService;
import com.gestordevendas.api.audit.AuditResponse;
import com.gestordevendas.api.invite.CompanyInvite;
import com.gestordevendas.api.user.CompanyRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {
    private final PlatformService platformService;
    private final AuditQueryService auditQueryService;
    private final CompanyFeatureService companyFeatureService;

    public PlatformController(PlatformService platformService, AuditQueryService auditQueryService, CompanyFeatureService companyFeatureService) {
        this.platformService = platformService;
        this.auditQueryService = auditQueryService;
        this.companyFeatureService = companyFeatureService;
    }

    @PostMapping("/companies/invites")
    ResponseEntity<CompanyInviteResponse> createInvite(@Valid @RequestBody CreateCompanyInviteRequest request) {
        CompanyInvite invite = platformService.createCompanyInvite(new PlatformService.CreateCompanyInviteCommand(
            request.name(), request.legalName(), request.document(), request.ownerEmail(),
            request.primaryColor(), request.secondaryColor()));
        companyFeatureService.update(invite.getCompany().getId(), request.features() == null ? Map.of() : request.features());
        platformService.saveMobileNavigation(invite.getCompany().getId(), request.mobileNavigation());
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


    @GetMapping("/companies/{id}/mobile-navigation")
    List<String> mobileNavigation(@PathVariable UUID id) {
        return platformService.getMobileNavigation(id);
    }

    @PutMapping("/companies/{id}/mobile-navigation")
    List<String> updateMobileNavigation(@PathVariable UUID id, @RequestBody List<String> items) {
        platformService.saveMobileNavigation(id, items);
        return platformService.getMobileNavigation(id);
    }

    @GetMapping("/companies/{id}/features")
    Map<CompanyFeature, Boolean> features(@PathVariable UUID id) {
        platformService.requirePlatformCompany(id);
        return companyFeatureService.get(id);
    }

    @PatchMapping("/companies/{id}/features")
    Map<CompanyFeature, Boolean> updateFeatures(@PathVariable UUID id, @RequestBody Map<CompanyFeature, Boolean> features) {
        platformService.requirePlatformCompany(id);
        return companyFeatureService.update(id, features);
    }

    @GetMapping("/companies/{id}/users")
    List<PlatformService.CompanyUserView> users(@PathVariable UUID id) {
        return platformService.listCompanyUsers(id);
    }

    @PostMapping("/companies/{id}/users/invites")
    ResponseEntity<UserInviteResponse> inviteUser(@PathVariable UUID id,
                                                   @Valid @RequestBody CompanyUserInviteRequest request) {
        var invite = platformService.inviteCompanyUser(id, request.email(), request.role());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{inviteId}")
            .buildAndExpand(invite.getId()).toUri();
        return ResponseEntity.created(location).body(new UserInviteResponse(
            invite.getId(), invite.getEmail(), invite.getRole(), invite.getExpiresAt()));
    }

    @PostMapping("/companies/{id}/users/link")
    PlatformService.CompanyUserView linkExistingUser(@PathVariable UUID id,
                                                       @Valid @RequestBody CompanyUserInviteRequest request) {
        return platformService.linkExistingUser(id, request.email(), request.role());
    }

    @PatchMapping("/companies/{id}/users/{membershipId}")
    PlatformService.CompanyUserView updateUser(@PathVariable UUID id,
                                                @PathVariable UUID membershipId,
                                                @Valid @RequestBody UpdateCompanyUserRequest request) {
        return platformService.updateCompanyUser(id, membershipId, request.role(), request.active());
    }

    @DeleteMapping("/companies/{id}/users/{membershipId}")
    ResponseEntity<Void> removeUser(@PathVariable UUID id, @PathVariable UUID membershipId) {
        platformService.removeCompanyUser(id, membershipId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/companies/{id}")
    ResponseEntity<Void> deleteCompany(@PathVariable UUID id) {
        platformService.deleteCompany(id);
        return ResponseEntity.noContent().build();
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
        String secondaryColor,
        Map<CompanyFeature, Boolean> features,
        List<String> mobileNavigation
    ) {}

    public record CompanyResponse(UUID id, String name, String slug, boolean active) {}
    public record CompanySummaryResponse(UUID id, String name, String slug, boolean active, String ownerEmail,
                                         long userCount, long clientCount, long productCount, long saleCount) {}
    public record CompanyInviteResponse(UUID id, CompanyResponse company, String ownerEmail, Instant expiresAt) {}
    public record CompanyUserInviteRequest(@NotBlank @Email String email, CompanyRole role) {}
    public record UpdateCompanyUserRequest(
        @jakarta.validation.constraints.NotNull CompanyRole role,
        boolean active
    ) {}
    public record UserInviteResponse(UUID id, String email, CompanyRole role, Instant expiresAt) {}
}
