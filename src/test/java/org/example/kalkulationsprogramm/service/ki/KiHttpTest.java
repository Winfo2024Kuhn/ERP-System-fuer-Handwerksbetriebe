package org.example.kalkulationsprogramm.service.ki;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Von Fehlerantworten kommt nur die Fehlerart ins Log, nie der Inhalt (DSGVO). Nur Dummy-Daten. */
class KiHttpTest {

    @Test
    void mistral422WiederholtDieEingabeDieNichtInsLogDarf() {
        String antwort = """
                {"detail":[{"type":"string_too_long","loc":["body","messages",1,"content"],
                  "msg":"zu lang","input":"Rechnung an Max Mustermann, Musterweg 5"}]}""";

        String log = KiHttp.fehlerart(antwort);

        assertThat(log).contains("string_too_long").doesNotContain("Mustermann").doesNotContain("Musterweg");
    }

    @Test
    void geminiFehlerNurMitCodeUndStatus() {
        String antwort = """
                {"error":{"code":400,"status":"INVALID_ARGUMENT","message":"Inhalt: Max Mustermann"}}""";

        assertThat(KiHttp.fehlerart(antwort)).isEqualTo("code=400, status=INVALID_ARGUMENT");
    }

    @Test
    void unlesbarOderLeerNurMitLaenge() {
        assertThat(KiHttp.fehlerart("Max Mustermann")).isEqualTo("14 Zeichen, kein JSON");
        assertThat(KiHttp.fehlerart("")).isEqualTo("ohne Inhalt");
        assertThat(KiHttp.fehlerart("{\"message\":\"Max Mustermann\"}")).isEqualTo("28 Zeichen");
    }

    @Test
    void meldungenJeStatus() {
        assertThat(KiHttp.meldungFuerStatus("Mistral", 400)).isEqualTo("Mistral hat die Anfrage abgelehnt (HTTP 400).");
        assertThat(KiHttp.meldungFuerStatus("Mistral", 413))
                .isEqualTo("Die Datei ist für Mistral zu groß (HTTP 413). Bitte eine kleinere Datei nehmen.");
        assertThat(KiHttp.istVoruebergehend(500)).isTrue();
        assertThat(KiHttp.istVoruebergehend(404)).isFalse();
    }
}
