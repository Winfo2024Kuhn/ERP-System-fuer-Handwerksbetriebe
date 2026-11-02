package org.example.kalkulationsprogramm.service.kihilfe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KiHilfeHinweistextTest {

    @Test
    @DisplayName("Hinweistext wird aus der Ressource geladen und enthält alle Platzhalter")
    void basisGeladen() {
        String basis = KiHilfeHinweistext.basis();

        assertThat(basis).startsWith("Du bist der KI-Assistent für {{BETRIEB}}.")
                .contains("{{WEB_STIL}}\n", "{{WEB_ABSCHNITT}}\n", "## Navigationsstruktur", "## Seitenlinks")
                .doesNotContain("\r");
        assertThat(KiHilfeHinweistext.nachschlagen()).contains("{{RUNDEN}}", "{{WEB_NACHSCHLAGEN}}\n");
    }

    @Test
    @DisplayName("Mit und ohne Internet gibt es je eigene Bausteine")
    void webTexte() {
        assertThat(KiHilfeHinweistext.web(true).abschnitt()).contains("Google-Suche");
        assertThat(KiHilfeHinweistext.web(false).abschnitt()).contains("KEINEN Zugriff auf das Internet");
        assertThat(KiHilfeHinweistext.web(true).stil()).isNotEqualTo(KiHilfeHinweistext.web(false).stil());
        assertThat(KiHilfeHinweistext.web(false).nachschlagen()).contains("ohne diese Werkzeuge");
    }

    @Test
    @DisplayName("Fehlt die Ressource, kommt eine klare Fehlermeldung")
    void ressourceFehlt() {
        assertThatThrownBy(() -> KiHilfeHinweistext.lade("kihilfe/gibt-es-nicht.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hinweistext der KI-Hilfe fehlt")
                .hasMessageContaining("kihilfe/gibt-es-nicht.md");
    }

    @Test
    @DisplayName("Windows-Zeilenenden (CRLF) werden zu LF, Umlaute bleiben erhalten")
    void zeilenenden() throws IOException {
        byte[] crlf = "Prüfung {{WEB_STIL}}\r\nTor\r\n".getBytes(StandardCharsets.UTF_8);

        assertThat(KiHilfeHinweistext.zuText(new ByteArrayInputStream(crlf))).isEqualTo("Prüfung {{WEB_STIL}}\nTor\n");
    }

    @Test
    @DisplayName("Lesefehler des Datenstroms wird weitergereicht")
    void lesefehler() {
        assertThatThrownBy(() -> KiHilfeHinweistext.zuText(new java.io.InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("kaputt");
            }
        })).isInstanceOf(IOException.class).hasMessage("kaputt");
    }
}
