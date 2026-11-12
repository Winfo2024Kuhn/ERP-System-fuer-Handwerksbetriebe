package org.example.kalkulationsprogramm.service.vorschau;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Liest die Lage-Angabe der Kamera (EXIF „Orientation“, Tag 0x0112) aus dem Kopf eines JPEGs. Gedreht wird mit
 * {@link BildDrehung}. Reine Leseklasse ohne Spring; kaputte oder fremde Daten ergeben immer 1, nie eine Ausnahme.
 */
public final class ExifOrientierung {

    private ExifOrientierung() {
    }

    /**
     * Liest den EXIF-Wert "Orientation" (Tag 0x0112) aus einem JPEG.
     *
     * <p>Bewusst von Hand geparst statt über eine zusätzliche Bibliothek: Gebraucht wird
     * genau ein Zahlenwert aus dem ersten IFD. Bei allem, was nicht passt — kein JPEG,
     * kein EXIF-Block, abgeschnittene Datei — liefert die Methode 1 ("normale Lage"),
     * das Bild bleibt dann unverändert.</p>
     *
     * <p>Liest höchstens bis zum Beginn der Bilddaten; der Datenstrom wird nicht geschlossen.</p>
     *
     * @return EXIF-Orientierung 1–8, im Zweifel 1
     */
    public static int aus(InputStream eingabe) {
        final int normaleLage = 1;
        try {
            InputStream is = new BufferedInputStream(eingabe);
            if (lese16(is) != 0xFFD8) {
                return normaleLage; // kein JPEG – nur JPEGs tragen die Drehung im Header
            }
            while (true) {
                int marker = lese16(is);
                // Jeder Abschnitt beginnt mit 0xFF; sonst sind wir aus dem Tritt geraten
                if ((marker & 0xFF00) != 0xFF00) {
                    return normaleLage;
                }
                // Ab hier folgen die Bilddaten, EXIF kann nicht mehr kommen
                if (marker == 0xFFDA || marker == 0xFFD9) {
                    return normaleLage;
                }
                int laenge = lese16(is);
                if (laenge < 2) {
                    return normaleLage;
                }
                if (marker == 0xFFE1) {
                    byte[] segment = is.readNBytes(laenge - 2);
                    Integer gefunden = leseOrientierungAusExifSegment(segment);
                    if (gefunden != null) {
                        return gefunden;
                    }
                } else {
                    is.skipNBytes(laenge - 2);
                }
            }
        } catch (IOException | RuntimeException e) {
            return normaleLage;
        }
    }

    /** @return die Orientierung aus dem APP1-Segment oder {@code null}, wenn dort keine steht */
    private static Integer leseOrientierungAusExifSegment(byte[] segment) {
        // "Exif\0\0" + TIFF-Header (8 Bytes) + mindestens ein IFD-Eintrag
        if (segment.length < 6 + 8 + 2
                || segment[0] != 'E' || segment[1] != 'x' || segment[2] != 'i' || segment[3] != 'f'
                || segment[4] != 0) {
            return null;
        }

        // Alle Offsets im EXIF-Block zählen ab dem Beginn des TIFF-Headers
        int tiff = 6;
        boolean bigEndian;
        if (segment[tiff] == 0x49 && segment[tiff + 1] == 0x49) {
            bigEndian = false; // "II" – Intel
        } else if (segment[tiff] == 0x4D && segment[tiff + 1] == 0x4D) {
            bigEndian = true;  // "MM" – Motorola
        } else {
            return null;
        }
        if (lese16(segment, tiff + 2, bigEndian) != 42) {
            return null; // TIFF-Kennung fehlt
        }

        int ifdOffset = lese32(segment, tiff + 4, bigEndian);
        // Ohne Addition vergleichen: Bei einem manipulierten Offset nahe Integer.MAX_VALUE
        // liefe "tiff + ifdOffset" ins Negative und rutschte an der Prüfung vorbei.
        if (ifdOffset < 8 || ifdOffset > segment.length - tiff - 2) {
            return null;
        }
        int ifdStart = tiff + ifdOffset;

        int anzahlEintraege = lese16(segment, ifdStart, bigEndian);
        for (int i = 0; i < anzahlEintraege; i++) {
            int eintrag = ifdStart + 2 + i * 12;
            if (eintrag + 12 > segment.length) {
                return null; // Datei abgeschnitten
            }
            if (lese16(segment, eintrag, bigEndian) == 0x0112) {
                int wert = lese16(segment, eintrag + 8, bigEndian);
                return (wert >= 1 && wert <= 8) ? wert : null;
            }
        }
        return null;
    }

    /** Liest zwei Bytes aus dem Stream als vorzeichenlose Zahl; -1 am Dateiende. */
    private static int lese16(InputStream is) throws IOException {
        int hoch = is.read();
        int niedrig = is.read();
        if (hoch < 0 || niedrig < 0) {
            return -1;
        }
        return (hoch << 8) | niedrig;
    }

    private static int lese16(byte[] daten, int offset, boolean bigEndian) {
        int a = daten[offset] & 0xFF;
        int b = daten[offset + 1] & 0xFF;
        return bigEndian ? (a << 8) | b : (b << 8) | a;
    }

    private static int lese32(byte[] daten, int offset, boolean bigEndian) {
        int a = daten[offset] & 0xFF;
        int b = daten[offset + 1] & 0xFF;
        int c = daten[offset + 2] & 0xFF;
        int d = daten[offset + 3] & 0xFF;
        return bigEndian
                ? (a << 24) | (b << 16) | (c << 8) | d
                : (d << 24) | (c << 16) | (b << 8) | a;
    }
}
