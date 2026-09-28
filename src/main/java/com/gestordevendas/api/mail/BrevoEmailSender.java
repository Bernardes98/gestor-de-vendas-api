package com.gestordevendas.api.mail;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BrevoEmailSender implements EmailSender {
    private static final String RESET_HTML_TEMPLATE = """
        <!doctype html>
        <html lang="pt-BR">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <meta name="color-scheme" content="light">
          <title>Redefinição de senha</title>
        </head>
        <body style="margin:0;padding:0;background-color:#f3f5f8;font-family:Arial,Helvetica,sans-serif;color:#172033;">
          <div style="display:none;font-size:1px;color:#f3f5f8;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">
            Use o link seguro para criar uma nova senha. Ele expira em 30 minutos.
          </div>
          <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f3f5f8;">
            <tr>
              <td align="center" style="padding:32px 16px;">
                <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="width:100%;max-width:600px;background-color:#ffffff;border:1px solid #e2e7ef;border-radius:12px;overflow:hidden;">
                  <tr>
                    <td style="padding:24px 32px;background-color:#111827;border-bottom:4px solid #f5a623;">
                      <p style="margin:0;color:#ffffff;font-size:18px;font-weight:700;letter-spacing:0.4px;">GESTOR DE VENDAS</p>
                      <p style="margin:6px 0 0;color:#cbd5e1;font-size:13px;">Gestão comercial da sua empresa</p>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:32px;">
                      <h1 style="margin:0 0 16px;color:#172033;font-size:24px;line-height:1.3;">Redefina sua senha</h1>
                      <p style="margin:0 0 16px;color:#475569;font-size:16px;line-height:1.6;">
                        Recebemos uma solicitação para criar uma nova senha para sua conta do Gestor de Vendas.
                      </p>
                      <p style="margin:0 0 24px;color:#475569;font-size:16px;line-height:1.6;">
                        Para continuar, use o botão abaixo. Por segurança, o link expira em <strong>30 minutos</strong> e só pode ser usado uma vez.
                      </p>
                      <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin:0 auto 24px;">
                        <tr>
                          <td align="center" bgcolor="#f5a623" style="border-radius:8px;">
                            <a href="{{RESET_URL}}" target="_blank" style="display:inline-block;padding:14px 24px;border:1px solid #f5a623;border-radius:8px;color:#111827;font-size:16px;font-weight:700;line-height:1.2;text-decoration:none;">
                              Criar nova senha
                            </a>
                          </td>
                        </tr>
                      </table>
                      <p style="margin:0 0 8px;color:#64748b;font-size:13px;line-height:1.5;">
                        Se o botão não funcionar, copie e cole este endereço no navegador:
                      </p>
                      <p style="margin:0 0 24px;padding:12px;background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:6px;color:#334155;font-size:12px;line-height:1.5;overflow-wrap:anywhere;word-break:break-word;">
                        <a href="{{RESET_URL}}" target="_blank" style="color:#1d4ed8;overflow-wrap:anywhere;word-break:break-word;">{{RESET_URL}}</a>
                      </p>
                      <hr style="height:1px;border:0;background-color:#e2e8f0;margin:0 0 20px;">
                      <p style="margin:0;color:#64748b;font-size:14px;line-height:1.6;">
                        Se você não pediu a redefinição, ignore este e-mail. Sua senha atual continuará válida.
                      </p>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:18px 32px;background-color:#f8fafc;border-top:1px solid #e2e8f0;">
                      <p style="margin:0;color:#94a3b8;font-size:12px;line-height:1.5;text-align:center;">
                        Mensagem automática do Gestor de Vendas. Não responda a este e-mail.
                      </p>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """;

    private final RestClient restClient;
    private final MailProperties properties;

    public BrevoEmailSender(RestClient.Builder builder, MailProperties properties) {
        this.properties = properties;
        this.restClient = builder.baseUrl("https://api.brevo.com/v3").build();
    }

    @Override
    public void sendPasswordReset(String email, String resetUrl) {
        String escapedUrl = htmlEscape(resetUrl);
        String htmlContent = RESET_HTML_TEMPLATE.replace("{{RESET_URL}}", escapedUrl);
        String textContent = """
            Redefina sua senha - Gestor de Vendas

            Recebemos uma solicitação para criar uma nova senha para sua conta.

            Para continuar, acesse este link:
            %s

            Por segurança, o link expira em 30 minutos e só pode ser usado uma vez.
            Se você não pediu a redefinição, ignore este e-mail. Sua senha atual continuará válida.
            """.formatted(resetUrl);

        send(email, "Redefina sua senha - Gestor de Vendas", htmlContent, textContent);
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
        send(email, subject, htmlContent, null);
    }

    private void send(String email, String subject, String htmlContent, String textContent) {
        if (properties.brevoApiKey() == null || properties.brevoApiKey().isBlank()) {
            throw new IllegalStateException("BREVO_API_KEY não configurada.");
        }
        if (properties.fromEmail() == null || properties.fromEmail().isBlank()) {
            throw new IllegalStateException("MAIL_FROM_EMAIL não configurado.");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("sender", Map.of("name", properties.fromName(), "email", properties.fromEmail()));
        body.put("to", List.of(Map.of("email", email)));
        body.put("subject", subject);
        body.put("htmlContent", htmlContent);
        if (textContent != null && !textContent.isBlank()) {
            body.put("textContent", textContent);
        }

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
