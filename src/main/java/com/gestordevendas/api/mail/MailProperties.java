package com.gestordevendas.api.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
    String brevoApiKey,
    String fromEmail,
    String fromName,
    String appBaseUrl
) {
}
