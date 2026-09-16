package com.gestordevendas.api.auth;

import com.gestordevendas.api.common.error.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimiterTest {

    @Test
    void blocksSixthLoginAttemptWithinWindow() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);
        AuthRateLimiter limiter = new AuthRateLimiter(clock);

        for (int i = 0; i < 5; i++) {
            limiter.checkLogin("127.0.0.1", "a@b.com");
        }

        assertThatThrownBy(() -> limiter.checkLogin("127.0.0.1", "a@b.com"))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo("RATE_LIMITED"));
    }

    @Test
    void passwordResetUsesIndependentLimit() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);
        AuthRateLimiter limiter = new AuthRateLimiter(clock);

        for (int i = 0; i < 5; i++) {
            limiter.checkPasswordReset("127.0.0.1", "a@b.com");
        }

        assertThatThrownBy(() -> limiter.checkPasswordReset("127.0.0.1", "a@b.com"))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo("RATE_LIMITED"));
    }
}
