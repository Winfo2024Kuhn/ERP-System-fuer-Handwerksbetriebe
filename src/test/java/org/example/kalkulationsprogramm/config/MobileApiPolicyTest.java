package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Vertrag der PWA: aus Seiten, OfflineService, Service Worker und DTO-Bildlinks abgeleitet. */
class MobileApiPolicyTest {
    private record Endpoint(String path, String methods) {
        List<String> allowedMethods() { return Arrays.asList(methods.split(",")); }
        @Override public String toString() { return path; }
    }

    static Stream<Endpoint> mobileEndpoints() {
        return Stream.of(
                new Endpoint("/api/mitarbeiter/by-token/12345678-1234-4234-8234-123456789abc", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/projekte", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/projekte/42", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/projekte/42/bilder", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/kategorien", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/kategorien/42", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/lieferanten", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/feiertage", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/arbeitsgaenge/token", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/aktiv/token", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/heute/token", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/saldo/token", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/buchungen/token", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/urlaubsverfall/token", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/buchungszeitfenster/token", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/langzeitkrankmeldung/token", "GET,HEAD"),
                new Endpoint("/api/zeiterfassung/start", "POST"),
                new Endpoint("/api/zeiterfassung/stop", "POST"),
                new Endpoint("/api/zeiterfassung/pause", "POST"),
                new Endpoint("/api/urlaub/antraege", "GET,HEAD,POST"),
                new Endpoint("/api/urlaub/resturlaub", "GET,HEAD"),
                new Endpoint("/api/urlaub/typen", "GET,HEAD"),
                new Endpoint("/api/kalender/mobile", "GET,HEAD"),
                new Endpoint("/api/kalender/mobile/tag", "GET,HEAD"),
                new Endpoint("/api/abwesenheit/team", "GET,HEAD"),
                new Endpoint("/api/push/vapid-key", "GET,HEAD"),
                new Endpoint("/api/push/subscribe", "POST"),
                new Endpoint("/api/kunden", "GET,HEAD"),
                new Endpoint("/api/arbeitsgaenge", "GET,HEAD"),
                new Endpoint("/api/anfragen", "GET,HEAD"),
                new Endpoint("/api/anfragen/42", "GET,HEAD"),
                new Endpoint("/api/lieferanten/42", "GET,HEAD"),
                new Endpoint("/api/lieferanten/42/dokumente", "GET,HEAD"),
                new Endpoint("/api/lieferanten/42/dokumente/7/download", "GET,HEAD"),
                new Endpoint("/api/lieferanten/42/dokumente/analyze", "POST"),
                new Endpoint("/api/lieferanten/42/dokumente/import", "POST"),
                new Endpoint("/api/lieferanten/bilder/file/reklamation.jpg", "GET,HEAD"),
                new Endpoint("/api/lieferanten/bilder/file/reklamation.jpg/vorschau", "GET,HEAD"),
                new Endpoint("/api/projekte/42/dokumente", "GET,HEAD,POST"),
                new Endpoint("/api/anfragen/42/dokumente", "GET,HEAD,POST"),
                new Endpoint("/api/projekte/42/notizen", "GET,HEAD,POST"),
                new Endpoint("/api/anfragen/42/notizen", "GET,HEAD,POST"),
                new Endpoint("/api/projekte/42/notizen/7", "PATCH,DELETE"),
                new Endpoint("/api/anfragen/42/notizen/7", "PATCH,DELETE"),
                new Endpoint("/api/projekte/42/notizen/7/bilder", "POST"),
                new Endpoint("/api/anfragen/42/notizen/7/bilder", "POST"),
                new Endpoint("/api/projekte/42/notizen/7/bilder/3", "DELETE"),
                new Endpoint("/api/anfragen/42/notizen/7/bilder/3", "DELETE"),
                new Endpoint("/api/reklamationen/42", "GET,HEAD"),
                new Endpoint("/api/reklamationen/lieferant/42", "GET,HEAD,POST"),
                new Endpoint("/api/reklamationen/42/bilder", "POST"),
                new Endpoint("/api/zeitverwaltung/feiertage/zwischen", "GET,HEAD"),
                new Endpoint("/api/buchhaltung/mobile/me/permissions", "GET,HEAD"),
                new Endpoint("/api/buchhaltung/mobile/belege", "GET,HEAD,POST"),
                new Endpoint("/api/buchhaltung/mobile/belege/42", "GET,HEAD"),
                new Endpoint("/api/buchhaltung/mobile/belege/42/positionen", "PUT"),
                new Endpoint("/api/buchhaltung/mobile/mwst-rechner", "POST"),
                new Endpoint("/api/spracheingabe/transkribieren", "POST"),
                new Endpoint("/api/images/notiz.jpg", "GET,HEAD"),
                new Endpoint("/api/dokumente/notiz.jpg", "GET,HEAD"),
                new Endpoint("/api/dokumente/notiz.jpg/thumbnail", "GET,HEAD"),
                new Endpoint("/api/dokumente/notiz.jpg/anzeige", "GET,HEAD"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mobileEndpoints")
    void erlaubtGenauDieMobilBenoetigtenMethoden(Endpoint endpoint) {
        for (String method : List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE")) {
            assertThat(MobileApiPolicy.allows(method, endpoint.path()))
                    .as("%s %s", method, endpoint.path())
                    .isEqualTo(endpoint.allowedMethods().contains(method));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/projekte/preise-nachtragen",
            "POST, /api/projekte", "PUT, /api/projekte/42", "DELETE, /api/projekte/42",
            "PATCH, /api/projekte/42/materialkosten", "PATCH, /api/projekte/42/abgeschlossen",
            "POST, /api/projekte/42/zeiten", "DELETE, /api/projekte/42/dokumente/7",
            "POST, /api/anfragen", "PUT, /api/anfragen/42", "DELETE, /api/anfragen/42",
            "POST, /api/anfragen/42/emails", "DELETE, /api/anfragen/42/dokumente/7",
            "POST, /api/kunden", "PUT, /api/kunden/42", "DELETE, /api/kunden/42",
            "POST, /api/lieferanten", "PUT, /api/lieferanten/42", "DELETE, /api/lieferanten/42",
            "POST, /api/arbeitsgaenge", "PUT, /api/arbeitsgaenge/42", "DELETE, /api/arbeitsgaenge/42",
            "POST, /api/produktkategorien", "PUT, /api/produktkategorien/42", "DELETE, /api/produktkategorien/42",
            "PUT, /api/urlaub/antraege/42/approve", "PUT, /api/urlaub/antraege/42/reject",
            "PUT, /api/urlaub/antraege/42/storno", "GET, /api/urlaub/antraege/hinweise",
            "DELETE, /api/reklamationen/42", "PATCH, /api/reklamationen/42/status",
            "POST, /api/zeitverwaltung/feiertage/regenerieren", "POST, /api/zeitverwaltung/feiertage",
            "POST, /api/buchhaltung/belege", "DELETE, /api/buchhaltung/mobile/belege/42",
            "PUT, /api/firma", "POST, /api/mahnwesen/lauf", "POST, /api/admin/projekte/wartung",
            "GET, /api/zeiterfassung/projekte/42/verwaltung", "GET, /api/lieferanten/bilder/file/a/b/vorschau",
            "GET, /api/projekte/42/notizen/7/geheim", "GET, /api/dokumente/geheim/unterordner/datei.pdf"
    })
    void verwaltungUndNichtFreigegebeneUnterpfadeBleibenVerboten(String method, String path) {
        assertThat(MobileApiPolicy.allows(method, path)).isFalse();
    }
}
