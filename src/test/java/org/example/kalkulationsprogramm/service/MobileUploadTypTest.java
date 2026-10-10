package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

/** Die App darf nur Fotos (bzw. Fotos und PDFs als Lieferschein) hochladen – nie SVG oder HTML. */
class MobileUploadTypTest {

    private static MockMultipartFile datei(String typ) {
        return new MockMultipartFile("datei", "foto", typ, new byte[] { 1, 2, 3 });
    }

    @ParameterizedTest
    @ValueSource(strings = { "image/jpeg", "image/png", "IMAGE/JPEG", "image/webp", "image/heic", "image/heif",
            "image/jpeg; charset=binary" })
    void fotosSindErlaubt(String typ) {
        assertThat(MobileObjectAccessService.istErlaubtesMobilBild(datei(typ))).isTrue();
        assertThat(MobileObjectAccessService.istErlaubterMobilScan(datei(typ))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "image/svg+xml", "text/html", "application/javascript", "application/octet-stream", "" })
    void aktiveInhalteSindVerboten(String typ) {
        assertThat(MobileObjectAccessService.istErlaubtesMobilBild(datei(typ))).isFalse();
        assertThat(MobileObjectAccessService.istErlaubterMobilScan(datei(typ))).isFalse();
    }

    @Test
    @DisplayName("PDF nur als Lieferschein-Scan, nicht als Foto; leere Dateien und null nie")
    void pdfNurAlsScan() {
        assertThat(MobileObjectAccessService.istErlaubterMobilScan(datei("application/pdf"))).isTrue();
        assertThat(MobileObjectAccessService.istErlaubtesMobilBild(datei("application/pdf"))).isFalse();
        assertThat(MobileObjectAccessService.istErlaubtesMobilBild(
                new MockMultipartFile("datei", "leer.jpg", "image/jpeg", new byte[0]))).isFalse();
        assertThat(MobileObjectAccessService.istErlaubtesMobilBild(null)).isFalse();
        assertThat(MobileObjectAccessService.istErlaubtesMobilBild(datei(null))).isFalse();
    }

    @Test
    @DisplayName("Die Endung kommt aus dem geprüften Typ")
    void endungAusTyp() {
        assertThat(MobileObjectAccessService.endungFuerBild("image/jpeg")).isEqualTo(".jpg");
        assertThat(MobileObjectAccessService.endungFuerBild("image/png")).isEqualTo(".png");
        assertThat(MobileObjectAccessService.endungFuerBild("image/gif")).isEqualTo(".gif");
        assertThat(MobileObjectAccessService.endungFuerBild("image/webp")).isEqualTo(".webp");
        assertThat(MobileObjectAccessService.endungFuerBild("image/heic")).isEqualTo(".heic");
        assertThat(MobileObjectAccessService.endungFuerBild("IMAGE/HEIF")).isEqualTo(".heif");
        assertThatThrownBy(() -> MobileObjectAccessService.endungFuerBild("image/svg+xml"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
