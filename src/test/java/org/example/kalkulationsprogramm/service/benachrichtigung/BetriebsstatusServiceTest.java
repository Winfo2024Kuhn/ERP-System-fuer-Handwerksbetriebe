package org.example.kalkulationsprogramm.service.benachrichtigung;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import tools.jackson.databind.ObjectMapper;

/**
 * Statusdateien von nächtlicher Sicherung und Nachtupdate (geschrieben von
 * deployment/docker-server/gemeinsam.sh) → Meldungen für die Benachrichtigungen.
 */
class BetriebsstatusServiceTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    /** "Jetzt" in allen Tests: Sonntag, 11.10.2026, 10:00 Uhr deutscher Zeit (Sommerzeit, +02:00). */
    private static final Clock UHR = Clock.fixed(ZonedDateTime.of(2026, 10, 11, 10, 0, 0, 0, BERLIN).toInstant(), BERLIN);

    @TempDir
    Path ordner;

    private BetriebsstatusService service;

    @BeforeEach
    void setUp() {
        service = new BetriebsstatusService(ordner.toString(), JsonMapper.builderWithJackson2Defaults().build(), UHR);
    }

    // ── Sicherung ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ohne eingestellten Ordner gibt es keine Meldungen")
    void ohneOrdnerKeineMeldung() throws IOException {
        schreiben("sicherung.json", status("FEHLER", "2026-10-11T02:00:17+02:00", "Sicherung fehlgeschlagen", "x", null));
        BetriebsstatusService aus = new BetriebsstatusService("  ", JsonMapper.builderWithJackson2Defaults().build(), UHR);
        assertThat(aus.sicherung()).isEmpty();
        assertThat(aus.nachtupdate()).isEmpty();
    }

    @Test
    @DisplayName("Fehlt die Datei (Installation ohne Updater), gibt es keine Meldung")
    void ohneDateiKeineMeldung() {
        assertThat(service.sicherung()).isEmpty();
        assertThat(service.nachtupdate()).isEmpty();
    }

    @Test
    @DisplayName("Erfolgreiche Sicherung von heute Nacht: keine Meldung")
    void erfolgKeineMeldung() throws IOException {
        schreiben("sicherung.json", status("OK", "2026-10-11T02:00:17+02:00", "Sicherung erfolgreich", "",
                "2026-10-11T02:00:17+02:00"));
        assertThat(service.sicherung()).isEmpty();
    }

    @Test
    @DisplayName("Teilweise gesichert: Titel und Text aus der Datei, dazu die letzte vollständige Sicherung")
    void teilweise() throws IOException {
        schreiben("sicherung.json", status("TEILWEISE", "2026-10-11T02:00:17+02:00", "Sicherung nur teilweise",
                "Externe Festplatte nicht gefunden.", "2026-10-10T02:00:05+02:00"));

        var meldung = service.sicherung().orElseThrow();

        assertThat(meldung.schluessel()).isEqualTo("SICHERUNG:20261011-020017");
        assertThat(meldung.titel()).isEqualTo("Sicherung nur teilweise");
        assertThat(meldung.text()).isEqualTo(
                "Externe Festplatte nicht gefunden.\nLetzte vollständige Sicherung: 10.10.2026 um 02:00 Uhr.");
        assertThat(meldung.zeitpunkt()).isEqualTo(LocalDateTime.of(2026, 10, 11, 2, 0, 17));
    }

    @Test
    @DisplayName("Fehlgeschlagen ohne Titel und ohne jemals vollständige Sicherung")
    void fehlerOhneTitel() throws IOException {
        schreiben("sicherung.json", status("FEHLER", "2026-10-11T02:00:17+02:00", "", "Datenbank nicht erreichbar.", null));

        var meldung = service.sicherung().orElseThrow();

        assertThat(meldung.titel()).isEqualTo("Sicherung fehlgeschlagen");
        assertThat(meldung.text()).endsWith("Es gibt noch keine vollständige Sicherung.");
    }

    @Test
    @DisplayName("Zeitpunkt in UTC wird in deutsche Zeit umgerechnet (Schlüssel und Anzeige)")
    void zeitzone() throws IOException {
        schreiben("sicherung.json", status("FEHLER", "2026-10-11T00:00:17Z", "Sicherung fehlgeschlagen", "x", null));
        var meldung = service.sicherung().orElseThrow();
        assertThat(meldung.zeitpunkt()).isEqualTo(LocalDateTime.of(2026, 10, 11, 2, 0, 17));
        assertThat(meldung.schluessel()).isEqualTo("SICHERUNG:20261011-020017");
    }

    @Test
    @DisplayName("Letzter Lauf über 26 Stunden her: Meldung, auch wenn er erfolgreich war")
    void ueberfaellig() throws IOException {
        schreiben("sicherung.json", status("OK", "2026-10-09T02:00:00+02:00", "Sicherung erfolgreich", "",
                "2026-10-09T02:00:00+02:00"));

        var meldung = service.sicherung().orElseThrow();

        assertThat(meldung.titel()).isEqualTo("Seit über einem Tag keine Sicherung");
        assertThat(meldung.schluessel()).isEqualTo("SICHERUNG:ALT-20261009-020000");
        assertThat(meldung.text()).contains("09.10.2026 um 02:00 Uhr").contains("Rechner nachts aus");
    }

    @Test
    @DisplayName("Gerade eingerichtet (NEU): erst nach 26 Stunden ohne Lauf eine Meldung")
    void neuEingerichtet() throws IOException {
        schreiben("sicherung.json", status("NEU", "2026-10-11T09:00:00+02:00", "", "", null));
        assertThat(service.sicherung()).isEmpty();

        schreiben("sicherung.json", status("NEU", "2026-10-09T09:00:00+02:00", "", "", null));
        assertThat(service.sicherung().orElseThrow().text()).startsWith("Die tägliche Sicherung ist noch nie gelaufen");
    }

    @Test
    @DisplayName("Unbekanntes Ergebnis gilt als Fehler – lieber einmal zu viel melden")
    void unbekanntesErgebnis() throws IOException {
        schreiben("sicherung.json", status("KAPUTT", "2026-10-11T02:00:00+02:00", "", "x", null));
        assertThat(service.sicherung().orElseThrow().titel()).isEqualTo("Sicherung fehlgeschlagen");
    }

    // ── Nachtupdate ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Update erfolgreich oder keine neue Version: keine Meldung")
    void updateOk() throws IOException {
        schreiben("nachtupdate.json", status("OK", "2026-10-11T03:01:19+02:00", "Keine neue Version", "", null));
        assertThat(service.nachtupdate()).isEmpty();
    }

    @Test
    @DisplayName("Update gescheitert: Meldung mit Text aus dem Skript")
    void updateFehler() throws IOException {
        schreiben("nachtupdate.json", status("FEHLER", "2026-10-11T03:01:19+02:00",
                "ERP-Update fehlgeschlagen", "Version 6f4efa2 -> 48d63f6\nGrund: nicht hochgefahren", null));

        var meldung = service.nachtupdate().orElseThrow();

        assertThat(meldung.schluessel()).isEqualTo("UPDATE:20261011-030119");
        assertThat(meldung.titel()).isEqualTo("ERP-Update fehlgeschlagen");
        assertThat(meldung.text()).isEqualTo("Version 6f4efa2 -> 48d63f6\nGrund: nicht hochgefahren");
    }

    @Test
    @DisplayName("Notfall ohne Titel bekommt einen eindeutigen Titel")
    void updateNotfall() throws IOException {
        schreiben("nachtupdate.json", status("NOTFALL", "2026-10-11T03:00:00+02:00", null, "Rollback gescheitert", null));
        assertThat(service.nachtupdate().orElseThrow().titel()).isEqualTo("NOTFALL beim Update");
    }

    @Test
    @DisplayName("Liest die Datei genau so, wie gemeinsam.sh sie schreibt (leerer letzterErfolg, Umlaute)")
    void dateiAusDemSkript() throws IOException {
        schreiben("sicherung.json", "{\"ergebnis\": \"TEILWEISE\", \"zeitpunkt\": \"2026-10-11T02:00:54+02:00\", "
                + "\"titel\": \"ERP-Sicherung nur teilweise\", \"text\": \"Externe Festplatte nicht gefunden. Bitte "
                + "anschließen und den Rechner neu starten.\\nGesichert auf diesem Rechner und im Cloud-Ordner.\", "
                + "\"letzterErfolg\": \"\"}\n");

        var meldung = service.sicherung().orElseThrow();

        assertThat(meldung.titel()).isEqualTo("ERP-Sicherung nur teilweise");
        assertThat(meldung.text()).isEqualTo("""
                Externe Festplatte nicht gefunden. Bitte anschließen und den Rechner \
                neu starten.
                Gesichert auf diesem Rechner und im Cloud-Ordner.
                Es gibt noch keine vollständige Sicherung.""");
        assertThat(meldung.schluessel()).isEqualTo("SICHERUNG:20261011-020054");
    }

    // ── Robustheit ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Halb geschriebene Datei: keine Meldung statt Fehler")
    void kaputtesJson() throws IOException {
        schreiben("sicherung.json", "{\"ergebnis\":\"FEHLER\",\"zeit");
        assertThat(service.sicherung()).isEmpty();
    }

    @Test
    @DisplayName("Ungültiger Zeitpunkt: keine Meldung")
    void ungueltigerZeitpunkt() throws IOException {
        schreiben("sicherung.json", status("FEHLER", "gestern Nacht", "x", "x", null));
        assertThat(service.sicherung()).isEmpty();
    }

    @Test
    @DisplayName("Riesige Datei wird gar nicht erst gelesen")
    void zuGross() throws IOException {
        String text = "x".repeat((int) BetriebsstatusService.MAX_DATEIGROESSE);
        schreiben("sicherung.json", status("FEHLER", "2026-10-11T02:00:00+02:00", "x", text, null));
        assertThat(service.sicherung()).isEmpty();
    }

    @Test
    @DisplayName("Lange Texte werden gekürzt, Steuerzeichen entfernt, HTML bleibt reiner Text")
    void textBereinigt() throws IOException {
        String lang = "<script>alert(1)</script>\u0007" + "a".repeat(5000);
        schreiben("nachtupdate.json", status("FEHLER", "2026-10-11T03:00:00+02:00", "'; DROP TABLE x; --", lang, null));

        var meldung = service.nachtupdate().orElseThrow();

        assertThat(meldung.text()).hasSize(BetriebsstatusService.MAX_TEXT)
                .startsWith("<script>alert(1)</script>a").endsWith("…")
                .doesNotContain("\u0007");
        assertThat(meldung.titel()).isEqualTo("'; DROP TABLE x; --");
    }

    // ── Wer darf es sehen ──────────────────────────────────────────────────

    @Test
    void nurAdminsSehenBetriebsmeldungen() {
        var admin = UsernamePasswordAuthenticationToken.authenticated("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var mitarbeiter = UsernamePasswordAuthenticationToken.authenticated("max", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var anonym = new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        assertThat(service.darfSehen(admin)).isTrue();
        assertThat(service.darfSehen(mitarbeiter)).isFalse();
        assertThat(service.darfSehen(anonym)).isFalse();
        assertThat(service.darfSehen(null)).isFalse();
    }

    // ── Hilfen ─────────────────────────────────────────────────────────────

    private void schreiben(String name, String inhalt) throws IOException {
        Files.writeString(ordner.resolve(name), inhalt);
    }

    private static String status(String ergebnis, String zeitpunkt, String titel, String text, String letzterErfolg)
            throws IOException {
        var werte = new java.util.LinkedHashMap<String, String>();
        werte.put("ergebnis", ergebnis);
        werte.put("zeitpunkt", zeitpunkt);
        werte.put("titel", titel);
        werte.put("text", text);
        werte.put("letzterErfolg", letzterErfolg);
        return JsonMapper.builderWithJackson2Defaults().build().writeValueAsString(werte);
    }
}
