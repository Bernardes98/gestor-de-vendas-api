package com.gestordevendas.api.auth;

import com.gestordevendas.api.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimiter {
    private static final int LIMIT = 5;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration RESET_WINDOW = Duration.ofMinutes(60);

    private final Clock clock;
    private final ConcurrentHashMap<Key, Counter> counters = new ConcurrentHashMap<>();

    public AuthRateLimiter() {
        this(Clock.systemUTC());
    }

    AuthRateLimiter(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public void checkLogin(String ip, String normalizedEmail) {
        check(new Key("login", safe(ip), safe(normalizedEmail)), LOGIN_WINDOW);
    }

    public void checkPasswordReset(String ip, String normalizedEmail) {
        check(new Key("password-reset", safe(ip), safe(normalizedEmail)), RESET_WINDOW);
    }

    private void check(Key key, Duration window) {
        Instant now = clock.instant();
        Counter updated = counters.compute(key, (ignored, current) -> {
            if (current == null || !current.windowEndsAt().isAfter(now)) {
                return new Counter(1, now.plus(window));
            }
            return new Counter(current.count() + 1, current.windowEndsAt());
        });
        if (updated.count() > LIMIT) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                "Muitas tentativas. Tente novamente mais tarde.");
        }
        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(entry -> !entry.getValue().windowEndsAt().isAfter(now));
        }
    }

    void clear() {
        counters.clear();
    }

    private String safe(String value) {
        return value == null ? "unknown" : value.trim().toLowerCase();
    }

    private record Key(String type, String ip, String email) {}
    private record Counter(int count, Instant windowEndsAt) {}
}
