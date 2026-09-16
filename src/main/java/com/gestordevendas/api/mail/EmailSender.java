package com.gestordevendas.api.mail;

public interface EmailSender {
    void sendPasswordReset(String email, String resetUrl);
    void sendCompanyInvite(String email, String inviteUrl);
    void sendUserInvite(String email, String inviteUrl);
}
