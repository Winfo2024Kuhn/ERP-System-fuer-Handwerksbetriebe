package org.example.kalkulationsprogramm.service.vorschau;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Liest die EXIF-Lage aus selbst gebauten JPEG-Köpfen (nur Marker, keine Bilddaten, keine Personendaten). Im
 * Zweifel immer 1 („normale Lage“), nie eine Ausnahme.
 */
class ExifOrientierungTest {

    private static final int SOI = 0xFFD8;

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3, 4, 5, 6, 7, 8 })
    @DisplayName("Orientierung 1–8 aus Intel- und Motorola-EXIF")
    void alleOrientierungen(int orientierung) {
        assertThat(lies(jpeg(exif(orientierung, false)))).isEqualTo(orientierung);
        assertThat(lies(jpeg(exif(orientierung, true)))).isEqualTo(orientierung);
    }

    @Test
    @DisplayName("EXIF hinter anderen Segmenten (z. B. JFIF-APP0) wird gefunden")
    void exifNachAnderemSegment() {
        byte[] app0 = segment(0xFFE0, new byte[] { 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0 });

        assertThat(lies(jpeg(app0, exif(6, false)))).isEqualTo(6);
    }

    @Test
    @DisplayName("Ein APP1 ohne Lage (z. B. XMP) wird übersprungen, das echte EXIF danach zählt")
    void app1OhneLageUeberspringen() {
        byte[] xmp = segment(0xFFE1, "http://ns.adobe.com/xap/1.0/\0".getBytes());

        assertThat(lies(jpeg(xmp, exif(8, true)))).isEqualTo(8);
    }

    @Test
    @DisplayName("Kein JPEG, leere Datei, JPEG ohne EXIF, Bilddaten vor EXIF → 1")
    void ohneLage() {
        assertThat(lies(new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10 })).isEqualTo(1);
        assertThat(lies(new byte[0])).isEqualTo(1);
        assertThat(lies(new byte[] { (byte) 0xFF })).isEqualTo(1);
        assertThat(lies(jpeg())).isEqualTo(1);
        byte[] bilddatenZuerst = jpeg(segment(0xFFDA, new byte[] { 0, 0 }), exif(6, false));
        assertThat(lies(bilddatenZuerst)).isEqualTo(1);
    }

    @Test
    @DisplayName("Kaputte Köpfe → 1: Marker ohne 0xFF, Länge < 2, abgeschnittenes Segment")
    void kaputteKoepfe() {
        assertThat(lies(new byte[] { (byte) 0xFF, (byte) 0xD8, 0x12, 0x34 })).isEqualTo(1);
        assertThat(lies(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 1 })).isEqualTo(1);
        byte[] voll = jpeg(exif(6, false));
        assertThat(lies(Arrays.copyOf(voll, 20))).isEqualTo(1);
    }

    @Test
    @DisplayName("Kaputtes EXIF-Segment → 1: falsche Kennung, falsche Byte-Reihenfolge, TIFF-Kennung ≠ 42")
    void kaputtesExif() {
        byte[] falscheKennung = exif(6, false);
        falscheKennung[4] = 'X';
        byte[] falscheReihenfolge = exif(6, false);
        falscheReihenfolge[4 + 6] = 'X';
        byte[] keine42 = exif(6, false);
        keine42[4 + 6 + 2] = 43;

        assertThat(lies(jpeg(falscheKennung))).isEqualTo(1);
        assertThat(lies(jpeg(falscheReihenfolge))).isEqualTo(1);
        assertThat(lies(jpeg(keine42))).isEqualTo(1);
    }

    @Test
    @DisplayName("Unsinniger IFD-Offset (nahe Integer.MAX_VALUE, < 8) und zu viele Einträge → 1")
    void unsinnigeOffsets() {
        byte[] riesig = exif(6, false);
        setze32LittleEndian(riesig, 4 + 6 + 4, 0x7FFF_FFFF);
        byte[] zuKlein = exif(6, false);
        setze32LittleEndian(zuKlein, 4 + 6 + 4, 4);
        byte[] zuVieleEintraege = exif(6, false);
        zuVieleEintraege[4 + 6 + 8] = 50; // Anzahl IFD-Einträge, Daten reichen nur für einen
        zuVieleEintraege[4 + 6 + 10] = 0x10; // der eine vorhandene ist nicht die Lage, gesucht wird dahinter

        assertThat(lies(jpeg(riesig))).isEqualTo(1);
        assertThat(lies(jpeg(zuKlein))).isEqualTo(1);
        assertThat(lies(jpeg(zuVieleEintraege))).isEqualTo(1);
    }

    @Test
    @DisplayName("Ungültiger Wert (0, 9) → 1; ein IFD ohne Orientation-Eintrag → 1")
    void ungueltigeWerte() {
        assertThat(lies(jpeg(exif(0, false)))).isEqualTo(1);
        assertThat(lies(jpeg(exif(9, true)))).isEqualTo(1);
        byte[] andererTag = exif(6, false);
        andererTag[4 + 6 + 10] = 0x10; // Tag 0x0110 (Kameramodell) statt 0x0112
        assertThat(lies(jpeg(andererTag))).isEqualTo(1);
    }

    @Test
    @DisplayName("Lesefehler im Datenstrom → 1 statt Ausnahme")
    void lesefehler() {
        InputStream kaputt = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Platte weg");
            }
        };

        assertThat(ExifOrientierung.aus(kaputt)).isEqualTo(1);
    }

    private static int lies(byte[] daten) {
        return ExifOrientierung.aus(new ByteArrayInputStream(daten));
    }

    /** SOI, die Segmente, dann EOI. */
    private static byte[] jpeg(byte[]... segmente) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(SOI >> 8);
        out.write(SOI & 0xFF);
        for (byte[] s : segmente) {
            out.writeBytes(s);
        }
        out.write(0xFF);
        out.write(0xD9);
        return out.toByteArray();
    }

    /** Marker + Länge (inklusive der zwei Längen-Bytes) + Nutzdaten. */
    private static byte[] segment(int marker, byte[] nutzdaten) {
        int laenge = nutzdaten.length + 2;
        byte[] s = new byte[nutzdaten.length + 4];
        s[0] = (byte) (marker >> 8);
        s[1] = (byte) marker;
        s[2] = (byte) (laenge >> 8);
        s[3] = (byte) laenge;
        System.arraycopy(nutzdaten, 0, s, 4, nutzdaten.length);
        return s;
    }

    /**
     * APP1-Segment mit „Exif\0\0“, TIFF-Kopf und genau einem IFD-Eintrag Orientation (0x0112, SHORT, 1 Wert).
     * Offsets im Segment-Feld: Marker/Länge 0–3, „Exif\0\0“ 4–9, TIFF ab 10.
     */
    private static byte[] exif(int orientierung, boolean motorola) {
        byte[] tiff = new byte[8 + 2 + 12 + 4];
        tiff[0] = tiff[1] = (byte) (motorola ? 'M' : 'I');
        schreibe16(tiff, 2, 42, motorola);
        schreibe32(tiff, 4, 8, motorola);
        schreibe16(tiff, 8, 1, motorola);
        schreibe16(tiff, 10, 0x0112, motorola);
        schreibe16(tiff, 12, 3, motorola);
        schreibe32(tiff, 14, 1, motorola);
        schreibe16(tiff, 18, orientierung, motorola);
        byte[] nutzdaten = new byte[6 + tiff.length];
        System.arraycopy(new byte[] { 'E', 'x', 'i', 'f', 0, 0 }, 0, nutzdaten, 0, 6);
        System.arraycopy(tiff, 0, nutzdaten, 6, tiff.length);
        return segment(0xFFE1, nutzdaten);
    }

    private static void schreibe16(byte[] d, int o, int wert, boolean motorola) {
        d[o + (motorola ? 0 : 1)] = (byte) (wert >> 8);
        d[o + (motorola ? 1 : 0)] = (byte) wert;
    }

    private static void schreibe32(byte[] d, int o, int wert, boolean motorola) {
        for (int i = 0; i < 4; i++) {
            int b = (wert >> (8 * i)) & 0xFF;
            d[o + (motorola ? 3 - i : i)] = (byte) b;
        }
    }

    private static void setze32LittleEndian(byte[] d, int o, int wert) {
        schreibe32(d, o, wert, false);
    }
}
