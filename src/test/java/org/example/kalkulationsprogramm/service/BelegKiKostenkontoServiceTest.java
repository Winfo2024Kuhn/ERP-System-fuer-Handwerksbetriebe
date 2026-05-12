package org.example.kalkulationsprogramm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruef-Tests fuer die Retry-Klassifizierung des KI-Kostenkonto-Agenten.
 * Echte HTTP-Calls werden hier bewusst nicht gemockt — die Verkabelung mit
 * Spring-HttpClient deckt der Smoke-Test in BelegKiAnalyseServiceTest ab.
 */
class BelegKiKostenkontoServiceTest {

    @ParameterizedTest(name = "HTTP {0} ist transient und sollte retried werden")
    @ValueSource(ints = { 429, 500, 502, 503, 504, 599 })
    void retryableStatusCodes(int status) {
        assertThat(BelegKiKostenkontoService.isRetryableStatus(status)).isTrue();
    }

    @ParameterizedTest(name = "HTTP {0} ist deterministisch und sollte NICHT retried werden")
    @ValueSource(ints = { 200, 201, 301, 400, 401, 403, 404, 422, 600, 0 })
    void nonRetryableStatusCodes(int status) {
        assertThat(BelegKiKostenkontoService.isRetryableStatus(status)).isFalse();
    }

    @Test
    void maxAttemptsIstDrei() {
        // Vertraglich festgeschrieben (User-Anforderung: max 3 Versuche).
        // Schuetzt vor versehentlichem Hochdrehen der Retry-Zahl, die jedem
        // async-Worker bis zu 3*45s Wartezeit kosten wuerde.
        assertThat(BelegKiKostenkontoService.MAX_GEMINI_ATTEMPTS).isEqualTo(3);
    }
}
