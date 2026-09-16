package com.gestordevendas.api.auth;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.company.CompanyRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyMembershipRepository membershipRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuthRateLimiter rateLimiter;

    private User user;
    private Company company;

    @BeforeEach
    void setUp() {
        rateLimiter.clear();
        refreshTokenRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        company = Company.create("empresa-auth", "Empresa Auth", null, null, null, null);
        companyRepository.save(company);
        user = User.create("admin@example.com", "Admin", passwordEncoder.encode("Senha123"));
        userRepository.save(user);
        membershipRepository.save(CompanyMembership.create(company, user, CompanyRole.ADMIN));
    }

    @Test
    void loginReturnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@example.com\",\"password\":\"Senha123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(cookie().httpOnly("refresh_token", true));
    }

    @Test
    void wrongPasswordDoesNotRevealWhetherEmailExists() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@example.com\",\"password\":\"errada\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void migratedUserMustResetPassword() throws Exception {
        user.setMustResetPassword(true);
        userRepository.save(user);

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@example.com\",\"password\":\"Senha123\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PASSWORD_RESET_REQUIRED"));
    }

    @Test
    void blockedUserCannotLogin() throws Exception {
        user.setActive(false);
        userRepository.save(user);

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@example.com\",\"password\":\"Senha123\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
    }

    @Test
    void blockedCompanyCannotLogin() throws Exception {
        company.setActive(false);
        companyRepository.save(company);

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@example.com\",\"password\":\"Senha123\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMPANY_BLOCKED"));
    }

    @Test
    void meResolvesCompanyFromDatabaseRatherThanJwtClaims() throws Exception {
        String accessToken = loginAndExtractAccessToken();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value("admin@example.com"))
            .andExpect(jsonPath("$.company.id").value(company.getId().toString()))
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    private String loginAndExtractAccessToken() throws Exception {
        String response = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@example.com\",\"password\":\"Senha123\"}"))
            .andReturn().getResponse().getContentAsString();
        int start = response.indexOf("\"accessToken\":\"") + 15;
        int end = response.indexOf('"', start);
        return response.substring(start, end);
    }
}
