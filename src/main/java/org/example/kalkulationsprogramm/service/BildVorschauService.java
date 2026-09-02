package org.example.kalkulationsprogramm.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Erzeugt verkleinerte Vorschaubilder (Thumbnails) und hält sie im Speicher vor.
 *
 * <p>Wird von allen Stellen genutzt, die Bilder in einer Galerie oder Liste
 * anzeigen — Dokumente ({@code DateiController}) genauso wie Reklamationsbilder
 * von Lieferanten. Ein gemeinsamer Cache heißt: Ein Bild wird höchstens einmal
 * verkleinert, egal von welcher Seite aus es zuerst angefordert wird.</p>
 */
@Service
public class BildVorschauService {

    private static final Logger log = LoggerFactory.getLogger(BildVorschauService.class);

    /** Maximale Kantenlänge des Vorschaubildes in Pixeln. */
    public static final int THUMBNAIL_MAX_SIZE = 300;

    /**
     * Obergrenze für die Bildgröße (100 Megapixel). Größere Bilder stammen in aller Regel
     * aus einem manipulierten Header und werden nicht dekodiert, damit ein einzelner
     * Aufruf nicht den Speicher des Servers sprengt.
     */
    private static final long MAX_PIXEL_ANZAHL = 100_000_000L;

    /**
     * Obergrenze für die Pixelzahl, die nach dem Ausdünnen tatsächlich dekodiert wird
     * (40 Megapixel ≈ 160 MB im Speicher). Verhindert, dass ein einzelnes extrem
     * langgezogenes Bild trotz Subsampling den Heap füllt.
     */
    private static final long MAX_DEKODIERTE_PIXEL = 40_000_000L;

    /**
     * Harte Obergrenze für die Ausdünnung. Bei erlaubten Bildgrößen wird sie nie
     * erreicht – sie sorgt nur dafür, dass die Suchschleife auch dann endet, wenn
     * die Abbruchbedingung später einmal geändert wird.
     */
    private static final int MAX_SUBSAMPLING = 64;

    /**
     * Maximale Anzahl gecachter Thumbnails. Bei ~15 KB pro JPEG bleibt der
     * Speicherbedarf damit unter ~8 MB — der Cache kann also nicht unbegrenzt wachsen.
     */
    private static final int CACHE_MAX_ENTRIES = 500;

    /**
     * LRU-Cache: Wird das Limit überschritten, fliegt der am längsten nicht
     * angeforderte Eintrag raus. {@code synchronizedMap} weil {@link LinkedHashMap}
     * selbst nicht threadsicher ist und schon Lesezugriffe die Zugriffsreihenfolge ändern.
     */
    private final Map<String, byte[]> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                    return size() > CACHE_MAX_ENTRIES;
                }
            });

    /** Liefert das gecachte Thumbnail oder {@code null}, wenn noch keins erzeugt wurde. */
    public byte[] ausCache(String cacheKey) {
        return cache.get(cacheKey);
    }

    /**
     * Erzeugt das Thumbnail und legt es unter {@code cacheKey} ab.
     *
     * @return das JPEG oder {@code null}, wenn kein Reader das Format lesen kann
     */
    public byte[] erzeugeUndCache(String cacheKey, Resource resource) throws IOException {
        byte[] jpegBytes = erzeugeThumbnail(resource);
        if (jpegBytes != null) {
            cache.put(cacheKey, jpegBytes);
        }
        return jpegBytes;
    }

    /**
     * Erzeugt das verkleinerte JPEG.
     *
     * <p>Entscheidend ist das Subsampling: Statt ein 12-Megapixel-Handyfoto erst
     * vollständig in den Speicher zu dekodieren (~48 MB) und dann zu skalieren, liest
     * der Decoder direkt nur jedes n-te Pixel. Das spart bei typischen Baustellenfotos
     * grob Faktor 16 an CPU und Arbeitsspeicher und verhindert, dass ein einzelnes
     * riesiges Bild den Server aus dem Speicher wirft.</p>
     *
     * <p>Die EXIF-Drehung wird dabei fest ins Bild gerechnet. Handyfotos speichern die
     * Lage der Kamera nämlich nur als Vermerk im Datei-Header, die Pixel selbst bleiben
     * quer. Browser lesen diesen Vermerk beim Original und drehen die Anzeige — das neu
     * berechnete Vorschau-JPEG hat ihn aber nicht mehr. Ohne diesen Schritt läge die
     * Vorschau also quer, während dasselbe Bild in der Großansicht richtig steht.</p>
     *
     * @return das JPEG oder {@code null}, wenn kein Reader das Format lesen kann
     */
    public byte[] erzeugeThumbnail(Resource resource) throws IOException {
        int orientierung = leseExifOrientierung(resource);

        try (InputStream is = resource.getInputStream();
             ImageInputStream iis = ImageIO.createImageInputStream(is)) {

            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                int origWidth = reader.getWidth(0);
                int origHeight = reader.getHeight(0);

                // Absurd große Bilder gar nicht erst anfassen (manipulierter Header)
                if ((long) origWidth * origHeight >= MAX_PIXEL_ANZAHL) {
                    log.warn("Bild zu groß für Thumbnail: {}x{} Pixel", origWidth, origHeight);
                    return null;
                }

                int subsampling = ermittleSubsampling(origWidth, origHeight);
                ImageReadParam param = reader.getDefaultReadParam();
                if (subsampling > 1) {
                    param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }

                BufferedImage bild = reader.read(0, param);
                if (bild == null) {
                    return null;
                }

                // Subsampling trifft die Zielgröße nur grob – Rest sauber herunterrechnen
                if (bild.getWidth() > THUMBNAIL_MAX_SIZE || bild.getHeight() > THUMBNAIL_MAX_SIZE) {
                    bild = skaliereAufZielgroesse(bild);
                }

                // Erst am Ende drehen: auf dem kleinen Bild kostet das praktisch nichts,
                // und beide Kanten bleiben dabei unter der Maximalgröße.
                bild = wendeOrientierungAn(bild, orientierung);

                return convertToJpeg(bild);
            } catch (javax.imageio.IIOException e) {
                // Beschädigte oder abgeschnittene Datei: Der Aufrufer liefert dann das
                // Original aus, statt dem Benutzer einen Serverfehler zu zeigen.
                log.warn("Bild konnte für die Vorschau nicht dekodiert werden: {}", e.getMessage());
                return null;
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Bestimmt, wie stark beim Dekodieren ausgedünnt wird: Es wird nur jedes n-te
     * Pixel gelesen.
     *
     * <p>Maßgeblich ist die kürzere Kante, damit das Ergebnis in beiden Richtungen
     * noch groß genug für die Zielgröße bleibt. Bei extremen Seitenverhältnissen —
     * etwa 500.000 × 200 Pixel — ergibt das aber 1, und das Bild landet trotz
     * Ausdünnung in voller Größe im Speicher. Deshalb wird der Wert danach so lange
     * erhöht, bis die tatsächlich dekodierte Pixelzahl unter {@link #MAX_DEKODIERTE_PIXEL}
     * liegt.</p>
     */
    int ermittleSubsampling(int breite, int hoehe) {
        int subsampling = Math.max(1, Math.min(
                breite / THUMBNAIL_MAX_SIZE,
                hoehe / THUMBNAIL_MAX_SIZE));

        // Aufrunden, weil der Decoder das auch tut: Bei einer Kante von 1 Pixel und
        // Subsampling 2 bleibt 1 Pixel übrig, nicht 0. Mit Abrunden käme hier 0 heraus,
        // die Schleife bräche sofort ab und das Bild landete doch in voller Größe im Speicher.
        while (aufgerundet(breite, subsampling) * aufgerundet(hoehe, subsampling) > MAX_DEKODIERTE_PIXEL
                && subsampling < MAX_SUBSAMPLING) {
            subsampling++;
        }
        return subsampling;
    }

    /** Ganzzahlige Division, die aufrundet – so rechnet auch der JPEG-Decoder. */
    private long aufgerundet(int wert, int teiler) {
        return ((long) wert + teiler - 1) / teiler;
    }

    /**
     * Dreht bzw. spiegelt das Bild so, wie es die EXIF-Angabe der Kamera vorsieht.
     *
     * @param orientierung EXIF-Wert 1–8; alles andere lässt das Bild unverändert
     */
    private BufferedImage wendeOrientierungAn(BufferedImage bild, int orientierung) {
        if (orientierung <= 1 || orientierung > 8) {
            return bild;
        }

        int breite = bild.getWidth();
        int hoehe = bild.getHeight();
        // Die Werte 5–8 kippen das Bild um 90°, dadurch tauschen Breite und Höhe die Plätze
        boolean gekippt = orientierung >= 5;
        int zielBreite = gekippt ? hoehe : breite;
        int zielHoehe = gekippt ? breite : hoehe;

        AffineTransform transform = new AffineTransform();
        switch (orientierung) {
            case 2 -> { // horizontal gespiegelt
                transform.scale(-1.0, 1.0);
                transform.translate(-breite, 0);
            }
            case 3 -> { // um 180° gedreht
                transform.translate(breite, hoehe);
                transform.rotate(Math.PI);
            }
            case 4 -> { // vertikal gespiegelt
                transform.scale(1.0, -1.0);
                transform.translate(0, -hoehe);
            }
            case 5 -> { // um 90° gegen den Uhrzeigersinn und gespiegelt
                transform.rotate(-Math.PI / 2);
                transform.scale(-1.0, 1.0);
            }
            case 6 -> { // um 90° im Uhrzeigersinn
                transform.translate(hoehe, 0);
                transform.rotate(Math.PI / 2);
            }
            case 7 -> { // um 90° im Uhrzeigersinn und gespiegelt
                transform.scale(-1.0, 1.0);
                transform.translate(-hoehe, 0);
                transform.translate(0, breite);
                transform.rotate(3 * Math.PI / 2);
            }
            case 8 -> { // um 90° gegen den Uhrzeigersinn
                transform.translate(0, breite);
                transform.rotate(3 * Math.PI / 2);
            }
            default -> {
                return bild;
            }
        }

        BufferedImage gedreht = new BufferedImage(zielBreite, zielHoehe, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = gedreht.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(bild, transform, null);
        g2d.dispose();
        return gedreht;
    }

    /**
     * Liest den EXIF-Wert "Orientation" (Tag 0x0112) aus einem JPEG.
     *
     * <p>Bewusst von Hand geparst statt über eine zusätzliche Bibliothek: Gebraucht wird
     * genau ein Zahlenwert aus dem ersten IFD. Bei allem, was nicht passt — kein JPEG,
     * kein EXIF-Block, abgeschnittene Datei — liefert die Methode 1 ("normale Lage"),
     * das Bild bleibt dann unverändert.</p>
     *
     * @return EXIF-Orientierung 1–8, im Zweifel 1
     */
    private int leseExifOrientierung(Resource resource) {
        final int normaleLage = 1;
        try (InputStream is = new BufferedInputStream(resource.getInputStream())) {
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
    private Integer leseOrientierungAusExifSegment(byte[] segment) {
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
    private int lese16(InputStream is) throws IOException {
        int hoch = is.read();
        int niedrig = is.read();
        if (hoch < 0 || niedrig < 0) {
            return -1;
        }
        return (hoch << 8) | niedrig;
    }

    private int lese16(byte[] daten, int offset, boolean bigEndian) {
        int a = daten[offset] & 0xFF;
        int b = daten[offset + 1] & 0xFF;
        return bigEndian ? (a << 8) | b : (b << 8) | a;
    }

    private int lese32(byte[] daten, int offset, boolean bigEndian) {
        int a = daten[offset] & 0xFF;
        int b = daten[offset + 1] & 0xFF;
        int c = daten[offset + 2] & 0xFF;
        int d = daten[offset + 3] & 0xFF;
        return bigEndian
                ? (a << 24) | (b << 16) | (c << 8) | d
                : (d << 24) | (c << 16) | (b << 8) | a;
    }

    /** Skaliert auf max. {@link #THUMBNAIL_MAX_SIZE} px Kantenlänge, Seitenverhältnis bleibt erhalten. */
    private BufferedImage skaliereAufZielgroesse(BufferedImage original) {
        double scale = Math.min(
                (double) THUMBNAIL_MAX_SIZE / original.getWidth(),
                (double) THUMBNAIL_MAX_SIZE / original.getHeight());
        int newWidth = Math.max(1, (int) Math.round(original.getWidth() * scale));
        int newHeight = Math.max(1, (int) Math.round(original.getHeight() * scale));

        BufferedImage thumbnail = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = thumbnail.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        return thumbnail;
    }

    private byte[] convertToJpeg(BufferedImage image) throws IOException {
        // Transparenz entfernen (JPEG unterstützt kein Alpha)
        BufferedImage rgbImage = image;
        if (image.getType() == BufferedImage.TYPE_INT_ARGB || image.getColorModel().hasAlpha()) {
            rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgbImage.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(rgbImage, "jpg", baos);
        return baos.toByteArray();
    }
}
