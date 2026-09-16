package com.gestordevendas.api.invite;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invites")
public class InviteController {
    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping("/user/accept")
    ResponseEntity<Void> acceptUser(@Valid @RequestBody AcceptInviteRequest request) {
        inviteService.acceptUserInvite(request.token(), request.name(), request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/company/accept")
    ResponseEntity<Void> acceptCompany(@Valid @RequestBody AcceptInviteRequest request) {
        inviteService.acceptCompanyInvite(request.token(), request.name(), request.password());
        return ResponseEntity.noContent().build();
    }

    public record AcceptInviteRequest(@NotBlank String token, @NotBlank String name, @NotBlank String password) {}
}
