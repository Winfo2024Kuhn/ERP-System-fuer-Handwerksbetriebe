package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

/**
 * Schreibt fest, welche Pfade die {@code zeiterfassungFilterChain} abdeckt und
 * damit ohne Login erreichbar sind.
 *
 * <p>Characterization-Test: er dokumentiert den Ist-Zustand, nicht den
 * Wunschzustand. {@link #bekannteLueckeGanzeControllerSindOffen()} wird beim
 * Eingrenzen der Wildcards bewusst rot — dann die Assertions umdrehen, nicht
 * den Test loeschen.
 *
 * <p>Geprueft wird mit {@link AntPathMatcher}, weil Spring Security 6.2 die
 * Patterns aus {@code securityMatcher(String...)} mit Ant-Semantik aufloest.
 */
class ZeiterfassungFilterChainMatcherTest {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static boolean ohneLoginErreichbar(String pfad) {
        return Arrays.stream(SecurityConfig.ZEITERFASSUNG_PATHS)
                .anyMatch(pattern -> MATCHER.match(pattern, pfad));
    }

    @Test
    @DisplayName("Pfade, die die Mobile-App wirklich braucht, sind erreichbar")
    void mobileKernpfadeSindErreichbar() {
        assertThat(ohneLoginErreichbar("/api/zeiterfassung/projekte")).isTrue();
        assertThat(ohneLoginErreichbar("/api/zeiterfassung/start")).isTrue();
        assertThat(ohneLoginErreichbar("/api/mitarbeiter/by-token/dummy-token")).isTrue();
        assertThat(ohneLoginErreichbar("/api/urlaub/antraege")).isTrue();
        assertThat(ohneLoginErreichbar("/api/kalender/mobile/tag")).isTrue();
        assertThat(ohneLoginErreichbar("/api/abwesenheit/team")).isTrue();
        assertThat(ohneLoginErreichbar("/api/push/subscribe")).isTrue();
        assertThat(ohneLoginErreichbar("/api/buchhaltung/mobile/belege")).isTrue();
    }

    @Test
    @DisplayName("Bilder und Dokumente kommen als <img src> aus DTO-URLs, brauchen daher offenen Zugriff")
    void dateiAuslieferungIstErreichbar() {
        assertThat(ohneLoginErreichbar("/api/images/beispiel.jpg")).isTrue();
        assertThat(ohneLoginErreichbar("/api/dokumente/beispiel.pdf")).isTrue();
        assertThat(ohneLoginErreichbar("/api/dokumente/beispiel.pdf/thumbnail")).isTrue();
    }

    @Test
    @DisplayName("Reklamationen und Feiertags-Lookup: liefen fuer Mobile vorher in 401")
    void reklamationenUndFeiertageSindErreichbar() {
        assertThat(ohneLoginErreichbar("/api/reklamationen/lieferant/42")).isTrue();
        assertThat(ohneLoginErreichbar("/api/reklamationen/7")).isTrue();
        assertThat(ohneLoginErreichbar("/api/reklamationen/7/bilder")).isTrue();
        assertThat(ohneLoginErreichbar("/api/zeitverwaltung/feiertage/zwischen")).isTrue();
    }

    @Test
    @DisplayName("Die eng gefassten Reklamations-Pfade ziehen keine Verwaltungsaktionen mit auf")
    void reklamationsVerwaltungBleibtHinterDemLogin() {
        assertThat(ohneLoginErreichbar("/api/reklamationen/7/status")).isFalse();
        assertThat(ohneLoginErreichbar("/api/reklamationen/lieferscheine/search")).isFalse();
        assertThat(ohneLoginErreichbar("/api/zeitverwaltung/feiertage/regenerieren")).isFalse();
        assertThat(ohneLoginErreichbar("/api/zeitverwaltung/feiertage")).isFalse();
    }

    @Test
    @DisplayName("Bekannte Luecke: die Wildcards oeffnen ganze Fach-Controller (siehe Issue #72)")
    void bekannteLueckeGanzeControllerSindOffen() {
        assertThat(ohneLoginErreichbar("/api/projekte/preise-nachtragen")).isTrue();
        assertThat(ohneLoginErreichbar("/api/projekte/1")).isTrue();
        assertThat(ohneLoginErreichbar("/api/kunden/1")).isTrue();
        assertThat(ohneLoginErreichbar("/api/anfragen/1")).isTrue();
        assertThat(ohneLoginErreichbar("/api/lieferanten/1")).isTrue();
        assertThat(ohneLoginErreichbar("/api/arbeitsgaenge/1")).isTrue();
        // Wird von der PWA nicht einmal aufgerufen, ist aber trotzdem offen.
        assertThat(ohneLoginErreichbar("/api/produktkategorien/1")).isTrue();
    }

    @Test
    @DisplayName("Gegenprobe: Verwaltung bleibt hinter Session-Login und CSRF")
    void verwaltungsPfadeSindNichtOffen() {
        assertThat(ohneLoginErreichbar("/api/firma/stammdaten")).isFalse();
        assertThat(ohneLoginErreichbar("/api/settings/allgemein")).isFalse();
        assertThat(ohneLoginErreichbar("/api/frontend-users/1")).isFalse();
        assertThat(ohneLoginErreichbar("/api/admin/projekte/wartung")).isFalse();
        assertThat(ohneLoginErreichbar("/api/auth/me")).isFalse();
        assertThat(ohneLoginErreichbar("/api/mahnwesen/lauf")).isFalse();
        assertThat(ohneLoginErreichbar("/api/emails/admin/backfill")).isFalse();
        assertThat(ohneLoginErreichbar("/api/verrechnungslohn/uebernehmen")).isFalse();
        // Buchhaltung: nur der /mobile-Subpath ist offen, die PC-Endpoints nicht.
        assertThat(ohneLoginErreichbar("/api/buchhaltung/belege")).isFalse();
    }
}
