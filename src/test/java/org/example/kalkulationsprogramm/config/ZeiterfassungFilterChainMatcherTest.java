package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

/**
 * Charakterisiert ausschließlich die Auswahl der Security-Kette. Zugehörigkeit
 * zur mobilen Kette gewährt keine Rechte: Token-Prüfung und MobileApiPolicy
 * entscheiden dort zusätzlich über Authentifizierung und Methode/Route.
 */
class ZeiterfassungFilterChainMatcherTest {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static boolean nutztMobileFilterkette(String pfad) {
        return Arrays.stream(SecurityConfig.ZEITERFASSUNG_PATHS)
                .anyMatch(pattern -> MATCHER.match(pattern, pfad));
    }

    @Test
    @DisplayName("PWA-Kernpfade nutzen die Kette mit Token-Authentifizierung")
    void mobileKernpfadeNutzenMobileFilterkette() {
        assertThat(nutztMobileFilterkette("/api/zeiterfassung/projekte")).isTrue();
        assertThat(nutztMobileFilterkette("/api/zeiterfassung/start")).isTrue();
        assertThat(nutztMobileFilterkette("/api/mitarbeiter/by-token/dummy-token")).isTrue();
        assertThat(nutztMobileFilterkette("/api/urlaub/antraege")).isTrue();
        assertThat(nutztMobileFilterkette("/api/kalender/mobile/tag")).isTrue();
        assertThat(nutztMobileFilterkette("/api/abwesenheit/team")).isTrue();
        assertThat(nutztMobileFilterkette("/api/push/subscribe")).isTrue();
        assertThat(nutztMobileFilterkette("/api/buchhaltung/mobile/belege")).isTrue();
        assertThat(nutztMobileFilterkette("/api/spracheingabe/transkribieren")).isTrue();
    }

    @Test
    @DisplayName("Bilder und Dokumente werden in der mobilen Kette authentifiziert")
    void dateiAuslieferungNutztMobileFilterkette() {
        assertThat(nutztMobileFilterkette("/api/images/beispiel.jpg")).isTrue();
        assertThat(nutztMobileFilterkette("/api/dokumente/beispiel.pdf")).isTrue();
        assertThat(nutztMobileFilterkette("/api/dokumente/beispiel.pdf/thumbnail")).isTrue();
        // Anzeigegröße für die Vollbildansicht der Tagebuch-Fotos in der Handy-App
        assertThat(nutztMobileFilterkette("/api/dokumente/beispiel.jpg/anzeige")).isTrue();
    }

    @Test
    @DisplayName("Reklamationen und Feiertags-Lookup nutzen die mobile Kette")
    void reklamationenUndFeiertageNutzenMobileFilterkette() {
        assertThat(nutztMobileFilterkette("/api/reklamationen/lieferant/42")).isTrue();
        assertThat(nutztMobileFilterkette("/api/reklamationen/7")).isTrue();
        assertThat(nutztMobileFilterkette("/api/reklamationen/7/bilder")).isTrue();
        assertThat(nutztMobileFilterkette("/api/zeitverwaltung/feiertage/zwischen")).isTrue();
    }

    @Test
    @DisplayName("Reklamations-Verwaltung und Feiertags-Regeneration bleiben in der Desktop-Kette")
    void reklamationsVerwaltungNutztDesktopKette() {
        assertThat(nutztMobileFilterkette("/api/reklamationen/7/status")).isFalse();
        assertThat(nutztMobileFilterkette("/api/reklamationen/lieferscheine/search")).isFalse();
        assertThat(nutztMobileFilterkette("/api/zeitverwaltung/feiertage/regenerieren")).isFalse();
        assertThat(nutztMobileFilterkette("/api/zeitverwaltung/feiertage")).isFalse();
        assertThat(MobileApiPolicy.allows("POST", "/api/zeitverwaltung/feiertage/regenerieren")).isFalse();
        assertThat(MobileApiPolicy.allows("PATCH", "/api/reklamationen/7/status")).isFalse();
    }

    @Test
    @DisplayName("Gemeinsame Kette erlaubt mobil keine Verwaltungsaktionen")
    void gemeinsameKetteErlaubtKeineMobileVerwaltung() {
        assertThat(nutztMobileFilterkette("/api/projekte/preise-nachtragen")).isTrue();
        assertThat(nutztMobileFilterkette("/api/projekte/1")).isTrue();
        assertThat(nutztMobileFilterkette("/api/kunden/1")).isTrue();
        assertThat(nutztMobileFilterkette("/api/anfragen/1")).isTrue();
        assertThat(nutztMobileFilterkette("/api/lieferanten/1")).isTrue();
        assertThat(nutztMobileFilterkette("/api/arbeitsgaenge/1")).isTrue();
        // Auch diese gemeinsam geroutete Verwaltungsaktion bleibt mobil gesperrt.
        assertThat(nutztMobileFilterkette("/api/produktkategorien/1")).isTrue();
        assertThat(MobileApiPolicy.allows("POST", "/api/projekte/preise-nachtragen")).isFalse();
        assertThat(MobileApiPolicy.allows("DELETE", "/api/projekte/1")).isFalse();
        assertThat(MobileApiPolicy.allows("DELETE", "/api/kunden/1")).isFalse();
        assertThat(MobileApiPolicy.allows("DELETE", "/api/anfragen/1")).isFalse();
        assertThat(MobileApiPolicy.allows("DELETE", "/api/lieferanten/1")).isFalse();
        assertThat(MobileApiPolicy.allows("PUT", "/api/arbeitsgaenge/1")).isFalse();
        assertThat(MobileApiPolicy.allows("DELETE", "/api/produktkategorien/1")).isFalse();
        assertThat(nutztMobileFilterkette("/api/reklamationen/7")).isTrue();
        assertThat(MobileApiPolicy.allows("DELETE", "/api/reklamationen/7")).isFalse();
    }

    @Test
    @DisplayName("Reine Desktop-Bereiche nutzen die Kette mit Session-Login und CSRF")
    void verwaltungsPfadeNutzenDesktopKette() {
        assertThat(nutztMobileFilterkette("/api/firma/stammdaten")).isFalse();
        assertThat(nutztMobileFilterkette("/api/settings/allgemein")).isFalse();
        assertThat(nutztMobileFilterkette("/api/frontend-users/1")).isFalse();
        assertThat(nutztMobileFilterkette("/api/admin/projekte/wartung")).isFalse();
        assertThat(nutztMobileFilterkette("/api/admin/lieferanten/1/reprocess-attachments")).isFalse();
        assertThat(nutztMobileFilterkette("/api/auth/me")).isFalse();
        assertThat(nutztMobileFilterkette("/api/mahnwesen/lauf")).isFalse();
        assertThat(nutztMobileFilterkette("/api/emails/admin/backfill")).isFalse();
        assertThat(nutztMobileFilterkette("/api/verrechnungslohn/uebernehmen")).isFalse();
        // Buchhaltung: Nur der /mobile-Subpath nutzt die mobile Token-Prüfung.
        assertThat(nutztMobileFilterkette("/api/buchhaltung/belege")).isFalse();
    }
}
