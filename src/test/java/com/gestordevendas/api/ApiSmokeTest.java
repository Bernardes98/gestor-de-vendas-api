package com.gestordevendas.api;

import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(ApiSmokeTest.ErrorTestConfiguration.class)
class ApiSmokeTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void contextLoadsAndHealthIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void controlledErrorsUseStandardEnvelopeAndRequestId() throws Exception {
        mvc.perform(get("/api/internal-test/error").with(jwt().jwt(token -> token.subject(java.util.UUID.randomUUID().toString())))
                .header("X-Request-Id", "test-request-123"))
            .andExpect(status().isBadRequest())
            .andExpect(header().string("X-Request-Id", "test-request-123"))
            .andExpect(jsonPath("$.code").value("TEST_ERROR"))
            .andExpect(jsonPath("$.message").value("Falha controlada"))
            .andExpect(jsonPath("$.requestId").value("test-request-123"));
    }

    @TestConfiguration
    static class ErrorTestConfiguration {
        @Bean
        ErrorTestController errorTestController() {
            return new ErrorTestController();
        }
    }

    @RestController
    static class ErrorTestController {
        @GetMapping("/api/internal-test/error")
        void fail() {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TEST_ERROR", "Falha controlada");
        }
    }
}
