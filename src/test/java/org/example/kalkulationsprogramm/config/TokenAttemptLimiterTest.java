package org.example.kalkulationsprogramm.config;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;

class TokenAttemptLimiterTest {
    @Test void wechselndeTokensUndFrueheWiederholungenUmgehenSperreNicht() {
        AtomicLong time = new AtomicLong();
        TokenAttemptLimiter limiter = new TokenAttemptLimiter(time::get, 10);
        AtomicInteger checks = new AtomicInteger();
        java.util.function.Supplier<Optional<String>> invalid = () -> { checks.incrementAndGet(); return Optional.empty(); };
        assertThat(limiter.attempt("ip", invalid).retryAfterSeconds()).isZero();
        assertThat(limiter.attempt("ip", invalid).retryAfterSeconds()).isZero();
        assertThat(limiter.attempt("ip", invalid).retryAfterSeconds()).isEqualTo(2);
        assertThat(limiter.attempt("ip", invalid).retryAfterSeconds()).isEqualTo(2);
        assertThat(checks).hasValue(3);
        time.set(2_000);
        assertThat(limiter.attempt("ip", invalid).retryAfterSeconds()).isEqualTo(4);
        time.set(6_000);
        assertThat(limiter.attempt("ip", () -> Optional.of("valid")).value()).contains("valid");
        assertThat(limiter.attempt("ip", invalid).retryAfterSeconds()).isEqualTo(8);
        assertThat(limiter.attempt("other-ip", () -> Optional.of("valid")).value()).contains("valid");
    }
    @Test void wartezeitIstBegrenztUndVerfaelltNachRuhe() {
        AtomicLong time = new AtomicLong();
        TokenAttemptLimiter limiter = new TokenAttemptLimiter(time::get, 10);
        for (long expected : new long[]{0, 0, 2, 4, 8, 16, 32, 64, 128, 256, 300, 300}) {
            assertThat(limiter.attempt("ip", Optional::empty).retryAfterSeconds()).isEqualTo(expected);
            time.addAndGet(expected * 1_000);
        }
        time.addAndGet(30 * 60_000);
        assertThat(limiter.attempt("ip", Optional::empty).retryAfterSeconds()).isZero();
    }
    @Test void paralleleVersucheWerdenProIpSerialisiert() throws Exception {
        TokenAttemptLimiter limiter = new TokenAttemptLimiter(() -> 0, 10);
        AtomicInteger checks = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = java.util.stream.IntStream.range(0, 50).<java.util.concurrent.Callable<Void>>mapToObj(i -> () -> {
                limiter.attempt("same-ip", () -> { checks.incrementAndGet(); return Optional.empty(); }); return null;
            }).toList();
            for (var result : pool.invokeAll(tasks)) result.get();
        }
        assertThat(checks).hasValue(3);
    }
    @Test void volleTabelleVerwirftKeineAktiveSperre() {
        AtomicLong time = new AtomicLong();
        TokenAttemptLimiter limiter = new TokenAttemptLimiter(time::get, 1);
        for (int i = 0; i < 3; i++) limiter.attempt("ip", Optional::empty);
        AtomicInteger checks = new AtomicInteger();
        assertThat(limiter.attempt("new-ip", () -> { checks.incrementAndGet(); return Optional.empty(); }).retryAfterSeconds()).isPositive();
        assertThat(checks).hasValue(0);
        assertThat(limiter.attempt("ip", Optional::empty).retryAfterSeconds()).isEqualTo(2);
        time.set(31 * 60_000);
        assertThat(limiter.attempt("new-ip", () -> Optional.of("ok")).value()).contains("ok");
    }
    @Test void erfolgreicheAnmeldungenBelegenKeinenPlatz() {
        TokenAttemptLimiter limiter = new TokenAttemptLimiter(() -> 0, 2);
        for (int i = 0; i < 50; i++) {
            assertThat(limiter.attempt("198.51.100." + i, () -> Optional.of("ok")).value()).contains("ok");
        }
        assertThat(limiter.eintraege()).isZero();
    }
    @Test void nichtGesperrteEintraegeWeichenNeuenAdressen() {
        AtomicLong time = new AtomicLong();
        TokenAttemptLimiter limiter = new TokenAttemptLimiter(time::get, 1);
        limiter.attempt("198.51.100.1", Optional::empty); // ein Fehlversuch, keine Sperre
        assertThat(limiter.attempt("198.51.100.2", () -> Optional.of("ok")).value()).contains("ok");
    }
    @Test void ipv6ZaehltJeNetz() {
        TokenAttemptLimiter limiter = new TokenAttemptLimiter(() -> 0, 100);
        for (int i = 1; i <= 3; i++) limiter.attempt("2001:db8:1:2::" + Integer.toHexString(i), Optional::empty);
        AtomicInteger checks = new AtomicInteger();
        assertThat(limiter.attempt("2001:db8:1:2::ffff", () -> { checks.incrementAndGet(); return Optional.of("ok"); })
                .retryAfterSeconds()).isEqualTo(2);
        assertThat(checks).hasValue(0);
        assertThat(limiter.attempt("2001:db8:1:3::1", () -> Optional.of("ok")).value()).contains("ok");
        assertThat(TokenAttemptLimiter.schluessel("203.0.113.8")).isEqualTo("203.0.113.8");
        assertThat(TokenAttemptLimiter.schluessel("2001:db8:1:2::1")).isEqualTo("20010db800010002::/64");
        assertThat(TokenAttemptLimiter.schluessel("kein:gueltiger:wert")).isEqualTo("kein:gueltiger:wert");
        assertThat(TokenAttemptLimiter.schluessel(null)).isNull();
    }
}
