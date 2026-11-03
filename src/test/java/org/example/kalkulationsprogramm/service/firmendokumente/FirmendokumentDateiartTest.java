package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FirmendokumentDateiartTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "pdf;PDF;application/pdf",
            "png;BILD;image/png",
            "jpg;BILD;image/jpeg",
            "jpeg;BILD;image/jpeg",
            "gif;BILD;image/gif",
            "webp;BILD;image/webp",
            "bmp;BILD;image/bmp",
            "svg;SONSTIGE;application/octet-stream",
            "html;SONSTIGE;application/octet-stream",
            "docx;SONSTIGE;application/octet-stream",
            "'';SONSTIGE;application/octet-stream"
    })
    @DisplayName("Dateiart und Content-Type aus der Endung; SVG und HTML zählen nicht als Bild (Skript-Risiko)")
    void ausEndung(String endung, FirmendokumentDateiart art, String mediaType) {
        assertThat(FirmendokumentDateiart.aus(endung)).isEqualTo(art);
        assertThat(FirmendokumentDateiart.mediaType(endung).toString()).isEqualTo(mediaType);
    }

    @Test
    @DisplayName("Endung null: SONSTIGE als Download")
    void ohneEndung() {
        assertThat(FirmendokumentDateiart.aus(null)).isEqualTo(FirmendokumentDateiart.SONSTIGE);
        assertThat(FirmendokumentDateiart.mediaType(null).toString()).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("Nur SONSTIGE kommt immer als Anhang, PDF und Bild dürfen im Browser angezeigt werden")
    void immerAlsAnhang() {
        assertThat(FirmendokumentDateiart.SONSTIGE.immerAlsAnhang()).isTrue();
        assertThat(FirmendokumentDateiart.PDF.immerAlsAnhang()).isFalse();
        assertThat(FirmendokumentDateiart.BILD.immerAlsAnhang()).isFalse();
    }
}
