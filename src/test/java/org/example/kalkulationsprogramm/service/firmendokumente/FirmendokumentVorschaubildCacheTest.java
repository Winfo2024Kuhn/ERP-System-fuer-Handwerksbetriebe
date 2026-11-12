package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FirmendokumentVorschaubildCacheTest {

    /** Platz für genau drei Bilder à 100 Bytes (inklusive Verwaltungsaufschlag je Eintrag). */
    private final FirmendokumentVorschaubildCache cache = new FirmendokumentVorschaubildCache(
            3 * (100 + FirmendokumentVorschaubildCache.AUFSCHLAG_JE_EINTRAG));

    @Test
    @DisplayName("Abgelegtes Vorschaubild kommt je Dokument-ID zurück, unbekannte ID → null")
    void ablegenUndHolen() {
        byte[] bild = new byte[100];
        cache.lege(7L, bild);

        assertThat(cache.hole(7L)).isSameAs(bild);
        assertThat(cache.hole(8L)).isNull();
    }

    @Test
    @DisplayName("Über dem Speicherrahmen fliegt das am längsten nicht abgerufene Bild heraus")
    void verdraengtAeltestes() {
        cache.lege(1L, new byte[100]);
        cache.lege(2L, new byte[100]);
        cache.lege(3L, new byte[100]);
        cache.hole(1L);

        cache.lege(4L, new byte[100]);

        assertThat(cache.hole(1L)).isNotNull();
        assertThat(cache.hole(2L)).isNull();
        assertThat(cache.hole(3L)).isNotNull();
        assertThat(cache.hole(4L)).isNotNull();
        assertThat(cache.belegteBytes()).isLessThanOrEqualTo(3 * (100 + FirmendokumentVorschaubildCache.AUFSCHLAG_JE_EINTRAG));
    }

    @Test
    @DisplayName("Ersetzen zählt den Speicher nur einmal; Vergessen gibt ihn frei")
    void ersetzenUndVergessen() {
        cache.lege(1L, new byte[100]);
        cache.lege(1L, new byte[50]);
        assertThat(cache.belegteBytes()).isEqualTo(50 + FirmendokumentVorschaubildCache.AUFSCHLAG_JE_EINTRAG);

        cache.vergiss(1L);
        cache.vergiss(99L);

        assertThat(cache.hole(1L)).isNull();
        assertThat(cache.belegteBytes()).isZero();
    }

    @Test
    @DisplayName("Auch „kein Vorschaubild“ (leeres Feld) kostet einen Aufschlag, damit die Zahl der Einträge begrenzt bleibt")
    void leereEintraegeBegrenzt() {
        for (long id = 1; id <= 1000; id++) {
            cache.lege(id, new byte[0]);
        }
        assertThat(cache.belegteBytes()).isLessThanOrEqualTo(3 * (100 + FirmendokumentVorschaubildCache.AUFSCHLAG_JE_EINTRAG));
        assertThat(cache.hole(1000L)).isEmpty();
        assertThat(cache.hole(1L)).isNull();
    }

    @Test
    @DisplayName("Ein Bild, das allein größer als der ganze Rahmen ist, wird nicht abgelegt")
    void zuGrossesBild() {
        cache.lege(1L, new byte[100]);
        cache.lege(2L, new byte[10_000]);

        assertThat(cache.hole(2L)).isNull();
        assertThat(cache.hole(1L)).isNotNull();
    }

    @Test
    @DisplayName("Standard-Rahmen: 32 MB")
    void standardRahmen() {
        assertThat(new FirmendokumentVorschaubildCache().maxBytes()).isEqualTo(32L * 1024 * 1024);
    }
}
