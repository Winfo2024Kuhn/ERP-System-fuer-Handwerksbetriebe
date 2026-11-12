package org.example.kalkulationsprogramm.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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

import org.example.kalkulationsprogramm.service.vorschau.BildDrehung;
import org.example.kalkulationsprogramm.service.vorschau.ExifOrientierung;
import org.example.kalkulationsprogramm.service.vorschau.UmrechnungAusgelastetException;
import org.example.kalkulationsprogramm.service.vorschau.Umrechnungsplaetze;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Erzeugt verkleinerte Vorschaubilder (Thumbnails) und hält sie im Speicher vor.
 *
 * <p>Wird von allen Stellen genutzt, die Bilder in einer Galerie oder Liste
 * anzeigen — Dokumente ({@code DateiController}) genauso wie Reklamationsbilder
 * von Lieferanten. Ein gemeinsamer Cache heißt: Ein Bild wird höchstens einmal
 * verkleinert, egal von welcher Seite aus es zuerst angefordert wird.</p>
 *
 * <p>Die Bausteine liegen in {@code service/vorschau}: EXIF-Lage lesen ({@link ExifOrientierung}),
 * drehen ({@link BildDrehung}) und gleichzeitige Umrechnungen begrenzen ({@link Umrechnungsplaetze}).</p>
 */
@Service
public class BildVorschauService {

    private static final Logger log = LoggerFactory.getLogger(BildVorschauService.class);

    /** Maximale Kantenlänge des Vorschaubildes in Pixeln. */
    public static final int THUMBNAIL_MAX_SIZE = 300;

    /**
     * Maximale Kantenlänge der Anzeigegröße für die Vollbildansicht am Handy.
     * Ein Handyfoto schrumpft damit von 3–5 MB auf rund 300 KB, bleibt auf dem
     * Display aber scharf. Wer zoomt, lädt bei Bedarf das Original nach.
     */
    public static final int ANZEIGE_MAX_SIZE = 1600;

    /**
     * Anzeigegrößen sind rund 20-mal so groß wie Vorschaubilder. 40 Einträge halten
     * den Speicherbedarf bei ~12 MB und reichen für die zuletzt geöffneten Galerien.
     */
    private static final int ANZEIGE_CACHE_MAX_ENTRIES = 40;

    /**
     * Für die Anzeigegröße wird ein typisches 12-MP-Handyfoto kaum ausgedünnt und landet
     * fast vollständig im Speicher (~50 MB, bei extremen Seitenverhältnissen bis zur
     * Grenze {@link #MAX_DEKODIERTE_PIXEL}, also ~160 MB). Öffnet jemand eine Galerie,
     * lädt die App das Bild und seine Nachbarn gleichzeitig. Mehr als zwei Umrechnungen
     * auf einmal lässt die Sperre deshalb nicht zu, damit der Heap nicht überläuft.
     */
    private static final int ANZEIGE_PARALLEL = 2;

    /**
     * So lange wartet ein Aufruf höchstens auf eine freie Umrechnung. Danach gibt er mit
     * {@link UmrechnungAusgelastetException} auf – bei Andrang sollen keine Server-Threads
     * unbegrenzt an der Sperre hängen.
     */
    private static final long ANZEIGE_WARTEZEIT_MS = 5_000;

    private final Umrechnungsplaetze anzeigeUmrechnungen;

    @Autowired
    public BildVorschauService() {
        this(ANZEIGE_PARALLEL, ANZEIGE_WARTEZEIT_MS);
    }

    /** Anzahl paralleler Umrechnungen und Wartezeit frei wählbar, z. B. für Überlast-Tests. */
    public BildVorschauService(int parallel, long wartezeitMs) {
        this.anzeigeUmrechnungen = new Umrechnungsplaetze(parallel, wartezeitMs);
    }

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
    private final Map<String, byte[]> cache = lruCache(CACHE_MAX_ENTRIES);

    private final Map<String, byte[]> anzeigeCache = lruCache(ANZEIGE_CACHE_MAX_ENTRIES);

    private static Map<String, byte[]> lruCache(int maxEintraege) {
        return Collections.synchronizedMap(
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                        return size() > maxEintraege;
                    }
                });
    }

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

    /** Liefert die gecachte Anzeigegröße oder {@code null}, wenn noch keine erzeugt wurde. */
    public byte[] anzeigeAusCache(String cacheKey) {
        return anzeigeCache.get(cacheKey);
    }

    /**
     * Verwirft alle verkleinerten Fassungen eines Bildes – etwa weil die Datei
     * gelöscht wurde und das Foto nicht mehr ausgeliefert werden darf.
     */
    public void vergiss(String cacheKey) {
        cache.remove(cacheKey);
        anzeigeCache.remove(cacheKey);
    }

    /**
     * Erzeugt die Anzeigegröße (max. {@link #ANZEIGE_MAX_SIZE} px) und legt sie
     * unter {@code cacheKey} ab.
     *
     * @return das JPEG oder {@code null}, wenn kein Reader das Format lesen kann
     * @throws UmrechnungAusgelastetException wenn innerhalb der Wartezeit keine Umrechnung
     *         frei wurde – ein vorübergehender Zustand, anders als ein unlesbares Format
     */
    public byte[] erzeugeAnzeigeUndCache(String cacheKey, Resource resource) throws IOException {
        try {
            return anzeigeUmrechnungen.belege(() -> {
                // Ein paralleler Aufruf kann das Bild erzeugt haben, während dieser gewartet hat
                byte[] vorhanden = anzeigeCache.get(cacheKey);
                if (vorhanden != null) {
                    return vorhanden;
                }
                byte[] jpegBytes = erzeugeVerkleinert(resource, ANZEIGE_MAX_SIZE);
                if (jpegBytes != null) {
                    anzeigeCache.put(cacheKey, jpegBytes);
                }
                return jpegBytes;
            });
        } catch (UmrechnungAusgelastetException ausgelastet) {
            log.info("Anzeigegröße für {} übersprungen: Server ausgelastet", cacheKey);
            throw ausgelastet;
        }
    }

    /** Erzeugt das Vorschaubild (max. {@link #THUMBNAIL_MAX_SIZE} px). */
    public byte[] erzeugeThumbnail(Resource resource) throws IOException {
        return erzeugeVerkleinert(resource, THUMBNAIL_MAX_SIZE);
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
     * <p>Kleinere Bilder werden nie vergrößert. Auch von außen nutzbar (z. B. Vorschaubilder der
     * Firmendokumente mit 512 px); Cache und Umrechnungsgrenze sind dann Sache des Aufrufers.</p>
     *
     * @param maxKante maximale Kantenlänge des Ergebnisses in Pixeln
     * @return das JPEG oder {@code null}, wenn kein Reader das Format lesen kann
     */
    public byte[] erzeugeVerkleinert(Resource resource, int maxKante) throws IOException {
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

                int subsampling = ermittleSubsampling(origWidth, origHeight, maxKante);
                ImageReadParam param = reader.getDefaultReadParam();
                if (subsampling > 1) {
                    param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }

                BufferedImage bild = reader.read(0, param);
                if (bild == null) {
                    return null;
                }

                // Subsampling trifft die Zielgröße nur grob – Rest sauber herunterrechnen
                if (bild.getWidth() > maxKante || bild.getHeight() > maxKante) {
                    bild = skaliereAufZielgroesse(bild, maxKante);
                }

                // Erst am Ende drehen: auf dem kleinen Bild kostet das praktisch nichts,
                // und beide Kanten bleiben dabei unter der Maximalgröße.
                bild = BildDrehung.wendeAn(bild, orientierung);

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
        return ermittleSubsampling(breite, hoehe, THUMBNAIL_MAX_SIZE);
    }

    int ermittleSubsampling(int breite, int hoehe, int maxKante) {
        int subsampling = Math.max(1, Math.min(
                breite / maxKante,
                hoehe / maxKante));

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

    /** EXIF-Lage des Bildes ({@link ExifOrientierung}); nicht lesbar → 1 („normale Lage“). */
    private static int leseExifOrientierung(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            return ExifOrientierung.aus(is);
        } catch (IOException | RuntimeException e) {
            return 1;
        }
    }

    /** Skaliert auf max. {@code maxKante} px Kantenlänge, Seitenverhältnis bleibt erhalten. */
    private BufferedImage skaliereAufZielgroesse(BufferedImage original, int maxKante) {
        double scale = Math.min(
                (double) maxKante / original.getWidth(),
                (double) maxKante / original.getHeight());
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
