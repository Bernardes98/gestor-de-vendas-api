package com.gestordevendas.api.user;

import com.gestordevendas.api.invite.UserInvite;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserManagementController {
    private final UserManagementService service;

    public UserManagementController(UserManagementService service) {
        this.service = service;
    }

    @GetMapping
    List<UserView> list() {
        return service.listUsers().stream().map(this::toView).toList();
    }

    @PostMapping("/invites")
    ResponseEntity<InviteView> invite(@Valid @RequestBody InviteRequest request) {
        UserInvite invite = service.invite(request.email(), request.role());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(invite.getId()).toUri();
        return ResponseEntity.created(location).body(new InviteView(invite.getId(), invite.getEmail(), invite.getRole(), invite.getExpiresAt()));
    }

    @PatchMapping("/{membershipId}/status")
    UserView status(@PathVariable UUID membershipId, @Valid @RequestBody StatusRequest request) {
        return toView(service.setStatus(membershipId, request.active()));
    }

    @PatchMapping("/{membershipId}/role")
    UserView role(@PathVariable UUID membershipId, @Valid @RequestBody RoleRequest request) {
        return toView(service.setRole(membershipId, request.role()));
    }

    @DeleteMapping("/{membershipId}")
    ResponseEntity<Void> remove(@PathVariable UUID membershipId) {
        service.remove(membershipId);
        return ResponseEntity.noContent().build();
    }

    private UserView toView(CompanyMembership membership) {
        User user = membership.getUser();
        return new UserView(membership.getId(), user.getId(), user.getName(), user.getEmail(), membership.getRole(), membership.isActive());
    }

    public record InviteRequest(@NotBlank @Email String email, CompanyRole role) {}
    public record StatusRequest(boolean active) {}
    public record RoleRequest(@NotNull CompanyRole role) {}
    public record UserView(UUID membershipId, UUID userId, String name, String email, CompanyRole role, boolean active) {}
    public record InviteView(UUID id, String email, CompanyRole role, java.time.Instant expiresAt) {}
}
