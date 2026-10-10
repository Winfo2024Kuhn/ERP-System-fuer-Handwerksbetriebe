package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Hochgeladene Dateien dürfen im Browser des Büros kein Skript ausführen: Nur Fotos und PDFs
 * werden angezeigt, aktive Inhalte (HTML, SVG, XML, JavaScript) kommen als neutraler Download.
 */
class DateiAuslieferungTest {

    @ParameterizedTest
    @ValueSource(strings = { "image/jpeg", "image/png", "IMAGE/PNG", "image/webp", "image/heic", "application/pdf",
            "application/pdf; charset=binary" })
    @DisplayName("Fotos und PDFs werden angezeigt")
    void sichereTypenInline(String typ) {
        var art = DateiController.auslieferung(typ);
        assertThat(art.inline()).isTrue();
        assertThat(art.disposition("bild.jpg")).startsWith("inline");
    }

    @ParameterizedTest
    @ValueSource(strings = { "image/svg+xml", "text/html", "application/xhtml+xml", "text/xml", "application/xml",
            "text/javascript", "application/javascript", "multipart/x-mixed-replace", "text/xsl", "", "kaputt" })
    @DisplayName("Aktive Inhalte: Download mit neutralem Typ")
    void aktiveInhalteAlsNeutralerDownload(String typ) {
        var art = DateiController.auslieferung(typ);
        assertThat(art.inline()).isFalse();
        assertThat(art.typ()).isEqualTo("application/octet-stream");
        assertThat(art.disposition("angriff.svg")).startsWith("attachment");
    }

    @Test
    @DisplayName("Office-Dateien bleiben Download mit ihrem Typ, null gilt als unbekannt")
    void officeBehaeltTyp() {
        String xlsx = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        assertThat(DateiController.auslieferung(xlsx).typ()).isEqualTo(xlsx);
        assertThat(DateiController.auslieferung(xlsx).inline()).isFalse();
        assertThat(DateiController.auslieferung("text/plain").typ()).isEqualTo("text/plain");
        assertThat(DateiController.auslieferung(null).typ()).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("Anführungszeichen und Zeilenumbrüche im Namen brechen den Header nicht auf")
    void dateinameWirdKodiert() {
        var art = DateiController.auslieferung("image/png");
        assertThat(art.disposition("a\"b\r\nX-Evil: 1.png")).doesNotContain("\r").doesNotContain("\n");
        assertThat(art.disposition("Größe.png")).contains("filename*=UTF-8''");
        assertThat(art.disposition(null)).contains("datei");
        assertThat(art.disposition("bild.jpg")).isEqualTo("inline; filename=\"bild.jpg\"");
    }

    @Test
    @DisplayName("Lieferanten-Dateien: Typ nur aus der Endung, SVG/HTML nie inline")
    void lieferantenTypNurAusEndung() {
        assertThat(LieferantenController.sichererInlineTyp(Path.of("x", "foto.JPG"))).isEqualTo("image/jpeg");
        assertThat(LieferantenController.sichererInlineTyp(Path.of("lieferschein.pdf"))).isEqualTo("application/pdf");
        assertThat(LieferantenController.sichererInlineTyp(Path.of("bild.heif"))).isEqualTo("image/heif");
        assertThat(LieferantenController.sichererInlineTyp(Path.of("angriff.svg"))).isNull();
        assertThat(LieferantenController.sichererInlineTyp(Path.of("seite.html"))).isNull();
        assertThat(LieferantenController.sichererInlineTyp(Path.of("ohneEndung"))).isNull();
    }
}
