package com.gestordevendas.api.auth;

import com.gestordevendas.api.audit.AuditRepository;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.mail.EmailSender;
import com.gestordevendas.api.support.PostgresIntegrationTest;
import com.gestordevendas.api.user.CompanyMembership;
import com.gestordevendas.api.user.CompanyMembershipRepository;
import com.gestordevendas.api.user.CompanyRole;
import com.gestordevendas.api.user.User;
import com.gestordevendas.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(PasswordResetIntegrationTest.MailTestConfiguration.class)
class PasswordResetIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyMembershipRepository membershipRepository;
    @Autowired PasswordResetTokenRepository resetTokenRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuditRepository auditRepository;
    @Autowired CapturingEmailSender emailSender;

    private User user;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        resetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
        emailSender.clear();

        Company company = Company.create("reset-company", "Reset Company", null, null, null, null);
        companyRepository.save(company);
        user = User.create("reset@example.com", "Reset User", passwordEncoder.encode("Senha123"));
        user.setMustResetPassword(true);
        userRepository.save(user);
        membershipRepository.save(CompanyMembership.create(company, user, CompanyRole.OWNER));
    }

    @Test
    void existingEmailCreatesHashedTokenAndSendsResetEmail() throws Exception {
        mvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset@example.com\"}"))
            .andExpect(status().isAccepted());

        assertThat(resetTokenRepository.count()).isEqualTo(1);
        PasswordResetToken token = resetTokenRepository.findAll().getFirst();
        assertThat(token.getTokenHash()).hasSize(64);
        assertThat(emailSender.lastResetUrl()).contains("/redefinir-senha?token=");
        assertThat(emailSender.lastResetUrl()).doesNotContain(token.getTokenHash());
    }

    @Test
    void unknownEmailReturnsSameAcceptedResponseWithoutSendingEmail() throws Exception {
        mvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@example.com\"}"))
            .andExpect(status().isAccepted());

        assertThat(resetTokenRepository.count()).isZero();
        assertThat(emailSender.lastResetUrl()).isNull();
    }

    @Test
    void validTokenSetsNewBcryptPasswordAndClearsMigrationFlag() throws Exception {
        mvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset@example.com\"}"))
            .andExpect(status().isAccepted());
        String rawToken = emailSender.lastResetUrl().substring(emailSender.lastResetUrl().indexOf("token=") + 6);

        mvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"NovaSenha123\"}"))
            .andExpect(status().isNoContent());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.isMustResetPassword()).isFalse();
        assertThat(passwordEncoder.matches("NovaSenha123", reloaded.getPasswordHash())).isTrue();
        assertThat(resetTokenRepository.findAll().getFirst().getUsedAt()).isNotNull();
    }

    @Test
    void usedTokenCannotBeReused() throws Exception {
        mvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset@example.com\"}"))
            .andExpect(status().isAccepted());
        String rawToken = emailSender.lastResetUrl().substring(emailSender.lastResetUrl().indexOf("token=") + 6);

        mvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"NovaSenha123\"}"))
            .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"OutraSenha123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @TestConfiguration
    static class MailTestConfiguration {
        @Bean
        @Primary
        CapturingEmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }
    }

    static class CapturingEmailSender implements EmailSender {
        private final AtomicReference<String> lastResetUrl = new AtomicReference<>();

        @Override
        public void sendPasswordReset(String email, String resetUrl) {
            lastResetUrl.set(resetUrl);
        }

        @Override public void sendCompanyInvite(String email, String inviteUrl) {}
        @Override public void sendUserInvite(String email, String inviteUrl) {}

        String lastResetUrl() { return lastResetUrl.get(); }
        void clear() { lastResetUrl.set(null); }
    }
}
