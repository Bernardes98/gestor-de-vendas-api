package com.gestordevendas.api.mail;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class BrevoEmailSender implements EmailSender {
    private final RestClient restClient;
    private final MailProperties properties;

    public BrevoEmailSender(RestClient.Builder builder, MailProperties properties) {
        this.properties = properties;
        this.restClient = builder.baseUrl("https://api.brevo.com/v3").build();
    }

    @Override
    public void sendPasswordReset(String email, String resetUrl) {
        send(email, "Redefina sua senha - Gestor de Vendas",
            "<p>Recebemos uma solicitação para redefinir sua senha.</p><p><a href=\"" + htmlEscape(resetUrl) + "\">Criar nova senha</a></p><p>Este link expira em 30 minutos.</p>");
    }

    @Override
    public void sendCompanyInvite(String email, String inviteUrl) {
        send(email, "Convite para o Gestor de Vendas",
            "<p>Você foi convidado para cadastrar sua empresa no Gestor de Vendas.</p><p><a href=\"" + htmlEscape(inviteUrl) + "\">Aceitar convite</a></p>");
    }

    @Override
    public void sendUserInvite(String email, String inviteUrl) {
        send(email, "Seu acesso ao Gestor de Vendas",
            "<p>Você recebeu um convite para acessar o Gestor de Vendas.</p><p><a href=\"" + htmlEscape(inviteUrl) + "\">Criar acesso</a></p>");
    }

    private void send(String email, String subject, String htmlContent) {
        if (properties.brevoApiKey() == null || properties.brevoApiKey().isBlank()) {
            throw new IllegalStateException("BREVO_API_KEY não configurada.");
        }
        if (properties.fromEmail() == null || properties.fromEmail().isBlank()) {
            throw new IllegalStateException("MAIL_FROM_EMAIL não configurado.");
        }
        Map<String, Object> body = Map.of(
            "sender", Map.of("name", properties.fromName(), "email", properties.fromEmail()),
            "to", List.of(Map.of("email", email)),
            "subject", subject,
            "htmlContent", htmlContent
        );
        restClient.post()
            .uri("/smtp/email")
            .contentType(MediaType.APPLICATION_JSON)
            .header("api-key", properties.brevoApiKey())
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    private String htmlEscape(String value) {
        return value.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
