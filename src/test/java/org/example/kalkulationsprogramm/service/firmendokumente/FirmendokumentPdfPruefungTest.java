package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class FirmendokumentPdfPruefungTest {

    private static MockMultipartFile datei(String name, byte[] inhalt) {
        return new MockMultipartFile("datei", name, "application/pdf", inhalt);
    }

    @Test
    @DisplayName("Echte PDF (auch mit Vorspann vor %PDF-) geht durch")
    void echtePdf() {
        assertThatCode(() -> FirmendokumentPdfPruefung.pruefe(datei("a.pdf", "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII))))
                .doesNotThrowAnyException();
        assertThatCode(() -> FirmendokumentPdfPruefung.pruefe(datei("a.pdf", "\r\n  %PDF-1.4".getBytes(StandardCharsets.US_ASCII))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Fehlende, leere, falsche oder zu große Dateien → 400 mit verständlicher Meldung")
    void abgelehnt() {
        assertThatThrownBy(() -> FirmendokumentPdfPruefung.pruefe(null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Bitte eine Datei auswählen.");
        assertThatThrownBy(() -> FirmendokumentPdfPruefung.pruefe(datei("leer.pdf", new byte[0])))
                .hasMessageContaining("Bitte eine Datei auswählen.");
        assertThatThrownBy(() -> FirmendokumentPdfPruefung.pruefe(datei("virus.pdf", "MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1))))
                .hasMessageContaining("Es können nur PDF-Dateien abgelegt werden.");
        assertThatThrownBy(() -> FirmendokumentPdfPruefung.pruefe(datei("x.pdf", "%PD".getBytes(StandardCharsets.US_ASCII))))
                .hasMessageContaining("Es können nur PDF-Dateien abgelegt werden.");
        MockMultipartFile riesig = new MockMultipartFile("datei", "gross.pdf", "application/pdf", new byte[1]) {
            @Override
            public long getSize() {
                return FirmendokumentPdfPruefung.MAX_BYTES + 1;
            }
        };
        assertThatThrownBy(() -> FirmendokumentPdfPruefung.pruefe(riesig)).hasMessageContaining("Die Datei ist größer als 50 MB. Bitte eine kleinere PDF wählen oder die Datei verkleinern.");
    }

    @Test
    @DisplayName("PDF-Kennung erst hinter den ersten 1024 Bytes zählt nicht")
    void kennungZuSpaet() {
        byte[] inhalt = new byte[1100];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, inhalt, 1050, 5);
        assertThatThrownBy(() -> FirmendokumentPdfPruefung.pruefe(datei("spaet.pdf", inhalt)))
                .hasMessageContaining("Es können nur PDF-Dateien abgelegt werden.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', nullValues = "NULL", value = {
            "Torprüfung.pdf;Torprüfung.pdf",
            "../../etc/passwd;passwd",
            "C:\\Users\\Max\\scan.pdf;scan.pdf",
            "a<b>c|d.pdf;a_b_c_d.pdf",
            "NULL;dokument.pdf",
            "'   ';dokument.pdf",
            "ordner/;dokument.pdf"
    })
    @DisplayName("Originalname wird nur bereinigt angezeigt – ohne Pfad und Sonderzeichen")
    void anzeigeName(String roh, String erwartet) {
        assertThat(FirmendokumentPdfPruefung.anzeigeName(roh)).isEqualTo(erwartet);
    }

    @Test
    @DisplayName("Überlanger Originalname wird auf 255 Zeichen gekürzt")
    void anzeigeNameLang() {
        assertThat(FirmendokumentPdfPruefung.anzeigeName("x".repeat(10_001))).hasSize(255);
    }
}
