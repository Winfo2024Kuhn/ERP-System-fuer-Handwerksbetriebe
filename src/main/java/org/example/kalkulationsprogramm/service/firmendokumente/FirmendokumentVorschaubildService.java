package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentEingaben.nichtGefunden;

import java.io.IOException;
import java.nio.file.Files;

import org.example.kalkulationsprogramm.service.BildVorschauService;
import org.example.kalkulationsprogramm.service.vorschau.PdfErsteSeiteAlsBild;
import org.example.kalkulationsprogramm.service.vorschau.UmrechnungAusgelastetException;
import org.example.kalkulationsprogramm.service.vorschau.Umrechnungsplaetze;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ETag;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Kleines Vorschaubild (JPEG, längste Kante höchstens {@value #MAX_KANTE} px) für die Kachelansicht der
 * Firmendokumente: bei Fotos das verkleinerte Bild (EXIF-Drehung eingerechnet, kleine Bilder bleiben klein), bei PDFs
 * die erste Seite auf weißem Grund. So lädt ein Ordner mit vielen Handyfotos nur wenige KB je Kachel statt der
 * Originale.
 *
 * <p>Ablauf je Abruf:</p>
 * <ol>
 * <li>Rechte und Pfad immer frisch aus {@link FirmendokumentService#datei} (unsichtbar oder unbekannt → dieselbe 404
 * wie beim Datei-Abruf) – auch vor einer 304-Antwort und auch, wenn das Bild schon im Cache liegt.</li>
 * <li>SONSTIGE oder als „kein Vorschaubild“ gemerkt → 404.</li>
 * <li>Passt {@code If-None-Match} zum {@linkplain #etag ETag} → „unverändert“ (304) ohne Umrechnung. Der ETag hängt
 * nur an der ID, weil die Datei eines Dokuments nie ersetzt wird.</li>
 * <li>Sonst aus dem Cache ({@link FirmendokumentVorschaubildCache}) oder neu umrechnen – höchstens
 * {@value #PARALLEL} Umrechnungen gleichzeitig; wer länger als {@value #WARTEZEIT_MS} ms auf einen Platz wartet,
 * bekommt {@link UmrechnungAusgelastetException} (→ 503).</li>
 * </ol>
 *
 * <p><b>Was gemerkt wird:</b> Nur dauerhafte Ergebnisse – das Bild oder „kein Vorschaubild“ bei Formatfehlern (Reader
 * kennt das Format nicht, kaputtes oder verschlüsseltes PDF, Laufzeitfehler oder {@link StackOverflowError} im Decoder
 * bzw. Renderer, Datei über {@value #MAX_DATEI_MB} MB). Ein vorübergehender Lesefehler (Datei gerade nicht lesbar)
 * ergibt 404 ohne Cache-Eintrag; der nächste Abruf versucht es neu. {@link OutOfMemoryError} wird bewusst nicht
 * abgefangen; ihn verhindern die Größengrenze, die Pixel-Grenze des {@link BildVorschauService} und der
 * Datei-Zwischenspeicher von {@link PdfErsteSeiteAlsBild}.</p>
 *
 * <p><b>Löschen während einer Umrechnung:</b> Läuft eine Umrechnung noch, während das Dokument gelöscht wird, kann ihr
 * Ergebnis nach dem {@code vergiss} des Löschens im Cache landen. Das ist kein Rechte-Leck: Jeder Abruf prüft zuerst
 * {@link FirmendokumentService#datei} (gelöscht → 404), und Dokument-IDs werden nie wiederverwendet. Der verwaiste
 * Eintrag belegt nur Platz, bis er aus dem Cache verdrängt wird.</p>
 *
 * <p>Die Originaldatei wird nur gelesen und nie verändert.</p>
 */
@Service
public class FirmendokumentVorschaubildService {

    /** Längste Kante des Vorschaubildes in Pixeln: scharf bis zur größten Kachel (320 CSS-px) auf normalen Bildschirmen. */
    public static final int MAX_KANTE = 512;

    static final String KEIN_VORSCHAUBILD = "Für diese Datei gibt es kein Vorschaubild.";

    /**
     * Gleichzeitige Umrechnungen. Ein Foto wird beim Dekodieren ausgedünnt (typisch unter 5 MB im Speicher, im
     * Extremfall bis zur Grenze des {@code BildVorschauService} von 40 Megapixeln), eine PDF-Seite in 512 px braucht
     * etwa 1 MB. Zwei Plätze halten den Heap auch im Extremfall ruhig.
     */
    static final int PARALLEL = 2;

    /**
     * Längste Wartezeit auf einen Platz. Ein Foto braucht etwa 0,1–0,4 s; so kommt auch ein Ordner mit rund
     * 50 Fotos beim ersten Öffnen meist ohne 503 durch, und kein Server-Thread hängt länger als 10 s.
     */
    static final long WARTEZEIT_MS = 10_000;

    /**
     * Größere Dateien werden gar nicht erst geöffnet. Hochladen ist bis 50 MB erlaubt; die doppelte Grenze lässt Luft
     * und hält trotzdem riesige oder manipulierte Dateien vom Renderer fern.
     */
    static final int MAX_DATEI_MB = 100;

    /**
     * Fassung des Bildaufbaus im ETag. Ändert sich, wie Vorschaubilder entstehen (z. B. {@link #MAX_KANTE}), wird sie
     * erhöht – dann laden die Browser alle Vorschaubilder neu.
     */
    private static final int ETAG_FASSUNG = 1;

    private static final Logger log = LoggerFactory.getLogger(FirmendokumentVorschaubildService.class);
    private static final byte[] KEINS = new byte[0];

    private final FirmendokumentService dokumente;
    private final FirmendokumentVorschaubildCache cache;
    private final BildVorschauService bilder;
    private final PdfErsteSeiteAlsBild pdfs;
    private final Umrechnungsplaetze plaetze;
    private final long maxDateiBytes;

    /**
     * Antwort für den Controller: ETag immer, {@code jpeg == null} heißt „unverändert“ (304, der Browser hat das Bild
     * schon).
     */
    public record Vorschaubild(String etag, byte[] jpeg) {

        public boolean unveraendert() {
            return jpeg == null;
        }
    }

    @Autowired
    public FirmendokumentVorschaubildService(FirmendokumentService dokumente, FirmendokumentVorschaubildCache cache,
            BildVorschauService bilder, PdfErsteSeiteAlsBild pdfs) {
        this(dokumente, cache, bilder, pdfs, new Umrechnungsplaetze(PARALLEL, WARTEZEIT_MS),
                (long) MAX_DATEI_MB * 1024 * 1024);
    }

    /** Umrechnungsplätze und Größengrenze frei wählbar, z. B. für Tests. */
    FirmendokumentVorschaubildService(FirmendokumentService dokumente, FirmendokumentVorschaubildCache cache,
            BildVorschauService bilder, PdfErsteSeiteAlsBild pdfs, Umrechnungsplaetze plaetze, long maxDateiBytes) {
        this.dokumente = dokumente;
        this.cache = cache;
        this.bilder = bilder;
        this.pdfs = pdfs;
        this.plaetze = plaetze;
        this.maxDateiBytes = maxDateiBytes;
    }

    /** ETag des Vorschaubildes; hängt nur an der ID (und der {@link #ETAG_FASSUNG}), nie an Nutzerdaten. */
    public static String etag(Long id) {
        return "\"firmendokument-vorschau-v" + ETAG_FASSUNG + "-" + id + "\"";
    }

    /**
     * Vorschaubild als JPEG oder „unverändert“, wenn {@code wennNichtGleich} (Wert von {@code If-None-Match}) passt.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 „Das Dokument gibt es nicht mehr.“ (unsichtbar,
     *         unbekannt) bzw. „Für diese Datei gibt es kein Vorschaubild.“ (sonstige Dateiart, kaputt, Passwort, zu
     *         groß, gerade nicht lesbar)
     * @throws UmrechnungAusgelastetException wenn gerade zu viele Umrechnungen laufen
     */
    public Vorschaubild vorschaubild(Long id, String wennNichtGleich, Authentication authentication) {
        FirmendokumentService.Datei datei = dokumente.datei(id, authentication);
        if (datei.dateiart() == FirmendokumentDateiart.SONSTIGE) {
            throw nichtGefunden(KEIN_VORSCHAUBILD);
        }
        byte[] jpeg = cache.hole(id);
        if (jpeg != null && jpeg.length == 0) {
            throw nichtGefunden(KEIN_VORSCHAUBILD);
        }
        String etag = etag(id);
        if (passt(wennNichtGleich, etag)) {
            return new Vorschaubild(etag, null);
        }
        if (jpeg == null) {
            jpeg = umrechnen(id, datei);
        }
        if (jpeg == null || jpeg.length == 0) {
            throw nichtGefunden(KEIN_VORSCHAUBILD);
        }
        return new Vorschaubild(etag, jpeg);
    }

    /** Passt einer der ETags aus {@code If-None-Match} (schwacher Vergleich, {@code *} passt immer)? */
    private static boolean passt(String wennNichtGleich, String etag) {
        if (wennNichtGleich == null || wennNichtGleich.isBlank()) {
            return false;
        }
        ETag eigenes = ETag.create(etag);
        try {
            return ETag.parse(wennNichtGleich).stream().anyMatch(e -> e.isWildcard() || e.compare(eigenes, false));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Ergebnis der Umrechnung; {@code null} = vorübergehend nicht lesbar (wird nicht gemerkt). */
    private byte[] umrechnen(Long id, FirmendokumentService.Datei datei) {
        return plaetze.belege(() -> {
            // Ein paralleler Abruf kann das Bild erzeugt haben, während dieser gewartet hat
            byte[] vorhanden = cache.hole(id);
            if (vorhanden != null) {
                return vorhanden;
            }
            byte[] neu = erzeuge(id, datei);
            if (neu != null) {
                cache.lege(id, neu);
            }
            return neu;
        });
    }

    /** JPEG, {@link #KEINS} (dauerhaft nicht darstellbar) oder {@code null} (gerade nicht lesbar). */
    private byte[] erzeuge(Long id, FirmendokumentService.Datei datei) {
        long groesse;
        try {
            groesse = Files.size(datei.pfad());
        } catch (IOException e) {
            log.warn("Datei zu Firmendokument {} gerade nicht lesbar: {}", id, e.getClass().getSimpleName());
            return null;
        }
        if (groesse > maxDateiBytes) {
            log.info("Für Firmendokument {} gibt es kein Vorschaubild (Datei größer als {} Bytes)", id, maxDateiBytes);
            return KEINS;
        }
        try {
            byte[] jpeg = datei.dateiart() == FirmendokumentDateiart.PDF
                    ? pdfs.alsJpeg(datei.pfad(), MAX_KANTE)
                    : bilder.erzeugeVerkleinert(new FileSystemResource(datei.pfad()), MAX_KANTE);
            if (jpeg == null) {
                log.info("Für Firmendokument {} gibt es kein Vorschaubild (Datei nicht darstellbar)", id);
                return KEINS;
            }
            return jpeg;
        } catch (IOException e) {
            log.warn("Datei zu Firmendokument {} gerade nicht lesbar: {}", id, e.getClass().getSimpleName());
            return null;
        } catch (RuntimeException | StackOverflowError e) {
            // Kaputte oder bösartig verschachtelte Datei: dauerhaft, deshalb merken
            log.info("Für Firmendokument {} gibt es kein Vorschaubild: {}", id, e.getClass().getSimpleName());
            return KEINS;
        }
    }
}
