package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Pruef-Tests fuer die Retry-Klassifizierung des KI-Kostenkonto-Agenten.
 * Echte HTTP-Calls werden hier bewusst nicht gemockt; die Verkabelung mit
 * Spring-HttpClient deckt der Smoke-Test in BelegKiAnalyseServiceTest ab.
 */
class BelegKiKostenkontoServiceTest {

    @ParameterizedTest(name = "HTTP {0} ist transient und sollte retried werden")
    @ValueSource(ints = [429, 500, 502, 503, 504, 599])
    fun retryableStatusCodes(status: Int) {
        assertThat(BelegKiKostenkontoService.isRetryableStatus(status)).isTrue()
    }

    @ParameterizedTest(name = "HTTP {0} ist deterministisch und sollte NICHT retried werden")
    @ValueSource(ints = [200, 201, 301, 400, 401, 403, 404, 422, 600, 0])
    fun nonRetryableStatusCodes(status: Int) {
        assertThat(BelegKiKostenkontoService.isRetryableStatus(status)).isFalse()
    }

    @Test
    fun maxAttemptsIstDrei() {
        assertThat(BelegKiKostenkontoService.MAX_GEMINI_ATTEMPTS).isEqualTo(3)
    }
}
