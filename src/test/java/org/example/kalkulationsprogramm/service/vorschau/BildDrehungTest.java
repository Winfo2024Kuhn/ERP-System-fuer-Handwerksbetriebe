package org.example.kalkulationsprogramm.service.vorschau;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Dreht/spiegelt ein 4 × 2 Pixel großes Bild mit einem roten Markierungspunkt bei (x=1, y=0) und prüft Maße und Lage
 * des Punktes nach der EXIF-Regel (Wert = wie das gespeicherte Bild für die richtige Anzeige umgebaut wird).
 */
class BildDrehungTest {

    private static final int BREITE = 4;
    private static final int HOEHE = 2;

    @ParameterizedTest(name = "Orientierung {0} → {1}×{2}, Punkt bei ({3},{4})")
    @CsvSource({
            "1, 4, 2, 1, 0", // normale Lage
            "2, 4, 2, 2, 0", // waagerecht gespiegelt
            "3, 4, 2, 2, 1", // 180° gedreht
            "4, 4, 2, 1, 1", // senkrecht gespiegelt
            "5, 2, 4, 0, 1", // gespiegelt an der Hauptdiagonale
            "6, 2, 4, 1, 1", // 90° im Uhrzeigersinn
            "7, 2, 4, 1, 2", // gespiegelt an der Nebendiagonale
            "8, 2, 4, 0, 2", // 90° gegen den Uhrzeigersinn
    })
    @DisplayName("EXIF 1–8: Maße und Lage des Markierungspunkts")
    void orientierungen(int orientierung, int breite, int hoehe, int punktX, int punktY) {
        BufferedImage ergebnis = BildDrehung.wendeAn(markiertesBild(), orientierung);

        assertThat(ergebnis.getWidth()).isEqualTo(breite);
        assertThat(ergebnis.getHeight()).isEqualTo(hoehe);
        for (int x = 0; x < breite; x++) {
            for (int y = 0; y < hoehe; y++) {
                assertThat(rot(ergebnis.getRGB(x, y))).as("Pixel (%d,%d)", x, y).isEqualTo(x == punktX && y == punktY);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, -1, 0, 1, 9, 42 })
    @DisplayName("1 und ungültige Werte lassen das Bild unverändert (dasselbe Objekt)")
    void unveraendert(int orientierung) {
        BufferedImage bild = markiertesBild();

        assertThat(BildDrehung.wendeAn(bild, orientierung)).isSameAs(bild);
    }

    @ParameterizedTest
    @ValueSource(ints = { 2, 3, 4, 5, 6, 7, 8 })
    @DisplayName("Ergebnis ist immer ein neues RGB-Bild ohne Transparenz")
    void neuesRgbBild(int orientierung) {
        BufferedImage bild = markiertesBild();

        BufferedImage ergebnis = BildDrehung.wendeAn(bild, orientierung);

        assertThat(ergebnis).isNotSameAs(bild);
        assertThat(ergebnis.getType()).isEqualTo(BufferedImage.TYPE_INT_RGB);
    }

    private static BufferedImage markiertesBild() {
        BufferedImage bild = new BufferedImage(BREITE, HOEHE, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < BREITE; x++) {
            for (int y = 0; y < HOEHE; y++) {
                bild.setRGB(x, y, Color.WHITE.getRGB());
            }
        }
        bild.setRGB(1, 0, Color.RED.getRGB());
        return bild;
    }

    private static boolean rot(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 200 && c.getGreen() < 60 && c.getBlue() < 60;
    }
}
