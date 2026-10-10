package org.example.kalkulationsprogramm.controller;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


import org.example.kalkulationsprogramm.domain.Dokument;
import org.example.kalkulationsprogramm.config.MobilePrincipal;
import org.example.kalkulationsprogramm.dto.OpenExternalResponse;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.service.BildVorschauService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.MobileObjectAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;

@RestController
@AllArgsConstructor
public class DateiController {

    private final DateiSpeicherService dateiSpeicherService;
    private final BildVorschauService bildVorschauService;
    private final MobileObjectAccessService mobileObjectAccessService;

    private static final Logger log = LoggerFactory.getLogger(DateiController.class);

    private static final String NOSNIFF = "X-Content-Type-Options";

    /** Nur Fotos und PDFs zeigt der Browser direkt an. SVG und HTML könnten Skript im Ursprung des ERP ausführen. */
    private static final java.util.Set<String> INLINE_SICHERE_TYPEN = java.util.Set.of(
            MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_GIF_VALUE, "image/webp",
            "image/heic", "image/heif", "image/bmp", MediaType.APPLICATION_PDF_VALUE);

    /** Wie eine Datei ausgeliefert wird: sichere Typen inline, alles andere als Download. */
    record Auslieferung(String typ, boolean inline) {
        String disposition(String dateiname) {
            String name = dateiname != null && !dateiname.isBlank() ? dateiname : "datei";
            ContentDisposition.Builder builder = inline ? ContentDisposition.inline() : ContentDisposition.attachment();
            // Reine ASCII-Namen bleiben lesbar; alles andere kodiert ContentDisposition nach RFC 5987,
            // damit Anführungszeichen oder Zeilenumbrüche im Namen den Header nicht aufbrechen.
            boolean einfach = name.chars().allMatch(c -> c >= 0x20 && c < 0x7f && c != '"' && c != '\\');
            return (einfach ? builder.filename(name) : builder.filename(name, StandardCharsets.UTF_8)).build().toString();
        }
    }

    static Auslieferung auslieferung(String contentType) {
        String typ = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (INLINE_SICHERE_TYPEN.contains(typ)) {
            return new Auslieferung(typ, true);
        }
        // Downloads behalten ihren Typ (Excel, Word …). Was der Browser als Seite oder Skript
        // ausführen würde (HTML, SVG, XML, JavaScript), bekommt einen neutralen Typ.
        return new Auslieferung(istAktiverInhalt(typ) ? MediaType.APPLICATION_OCTET_STREAM_VALUE : typ, false);
    }

    private static boolean istAktiverInhalt(String typ) {
        int strich = typ.indexOf('/');
        if (strich <= 0) return true;
        String haupt = typ.substring(0, strich);
        String unter = typ.substring(strich + 1);
        if (haupt.equals("text")) return !unter.equals("plain") && !unter.equals("csv");
        if (haupt.equals("multipart")) return true;
        return unter.equals("xml") || unter.endsWith("+xml") || unter.contains("javascript")
                || unter.contains("ecmascript") || unter.equals("xsl") || unter.contains("html");
    }

    /**
     * Gespeicherte Bilder tragen einen eindeutigen UUID-Namen und werden nie
     * überschrieben – unter derselben Adresse liegt also immer dasselbe Bild.
     * Desktop-Sitzungen behalten deshalb den bisherigen privaten Browser-Cache.
     * Mobile Antworten verwenden {@code no-store}, damit Änderungen an der
     * Objektfreigabe bei jeder Anfrage geprüft werden.
     *
     * <p>Bewusst in Kauf genommen: Wird ein Foto im ERP gelöscht, bleibt die Kopie im
     * Desktop-Browser höchstens 30 Tage bestehen. Der Server selbst liefert es
     * ab sofort nicht mehr aus.</p>
     */
    static final CacheControl BILD_CACHE = CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate();

    /** Erzeugt eine verkleinerte Fassung; {@code null}, wenn das Format nicht lesbar ist. */
    @FunctionalInterface
    private interface Verkleinerung {
        byte[] erzeuge(String dateiname, Resource resource) throws IOException;
    }

    private record MobileDatei(Resource resource, String cacheKey) {}

    /**
     * Dieser Endpunkt liefert gespeicherte Bilder aus.
     * Das ":.+" ist wichtig, damit auch Dateiendungen im Dateinamen erlaubt sind.
     * @param dateiname Der einzigartige Name des Bildes.
     * @return Die Bilddatei mit dem korrekten Content-Type.
     */
    @GetMapping("/api/images/{dateiname:.+}")
    public ResponseEntity<Resource> liefereBild(@PathVariable String dateiname) {
        MobileDatei mobileDatei = ladeMobileDatei(dateiname);
        Resource resource = mobileDatei != null ? mobileDatei.resource()
                : dateiSpeicherService.ladeBildAlsResource(dateiname);

        Auslieferung art = auslieferung(bestimmeContentType(resource, dateiname));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(art.typ()))
                .cacheControl(bildCache())
                .header(HttpHeaders.CONTENT_DISPOSITION, art.disposition(resource.getFilename()))
                .header(NOSNIFF, "nosniff")
                .body(resource);
    }

    @GetMapping("/api/dokumente/{dateiname:.+}")
    public ResponseEntity<?> liefereDokument(@PathVariable String dateiname,
                                             @RequestParam(required = false) String token,
                                             @RequestParam(required = false, defaultValue = "false") boolean download,
                                             Principal principal) {
        MobileDatei mobileDatei = ladeMobileDatei(dateiname);
        if (mobileDatei != null) {
            // Mobile Dateien wurden bereits am konkreten Objekt freigegeben.
            // Der Desktop-Metadatenlookup darf daraus keinen Originalnamen-Alias machen.
            Auslieferung art = auslieferung(bestimmeContentType(mobileDatei.resource(), dateiname));
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType(art.typ()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, art.disposition(dateiname))
                    .header(NOSNIFF, "nosniff")
                    .body(mobileDatei.resource());
        }
        Dokument dokument;
        try {
            dokument = dateiSpeicherService.ladeDokumentMetadaten(dateiname);
        } catch (NotFoundException ex) {
            return liefereDokumentOhneMetadaten(dateiname);
        }
        log.info("Dokument {} von Benutzer {} am {}", dokument.getId(),
                principal != null ? principal.getName() : "unbekannt", LocalDateTime.now());

        String lower = dateiname.toLowerCase(Locale.ROOT);
        boolean isHiCAD = lower.endsWith(".sza") || lower.endsWith(".tcd");
        boolean isExcel = lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".xlsm")
                || lower.endsWith(".csv") || lower.endsWith(".ods") || lower.endsWith(".xlsb");
        boolean canOpenExtern = !download && (isHiCAD || (isExcel && dateiSpeicherService.liegtInHicadSpeicher(dokument.getGespeicherterDateiname())));
        if (canOpenExtern) {
            String pfad = ensureUncPrefix(dateiSpeicherService.holeNetzwerkPfad(dokument.getGespeicherterDateiname()));
            String encPath = encodePathForProtocol(pfad);
            String cleanTok = token == null ? null : token.strip();
            String protocolUrl = "openfile://open?path=" + encPath
                    + (cleanTok != null ? "&token=" + URLEncoder.encode(cleanTok, StandardCharsets.UTF_8) : "");
            OpenExternalResponse resp = new OpenExternalResponse("openExternal", protocolUrl, cleanTok);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=f.txt")
                    .body(resp);
        }

        Resource resource;
        try {
            resource = dateiSpeicherService.ladeDokumentAlsResource(dokument.getGespeicherterDateiname());
        } catch (SecurityException ex) {
            // Verzeichnistraversal: muss als solcher sichtbar bleiben und darf nicht
            // unter den harmlosen 404ern verschwinden.
            throw ex;
        } catch (RuntimeException ex) {
            if (!dokument.getGespeicherterDateiname().equalsIgnoreCase(dateiname)) {
                return liefereDokumentOhneMetadaten(dateiname);
            }
            log.warn("Dokument {} steht in der Datenbank, die Datei fehlt aber ({})",
                    dateiname, ex.getClass().getSimpleName());
            log.debug("Details zur fehlenden Datei {}", dateiname, ex);
            throw new NotFoundException("Dokument nicht gefunden: " + dateiname);
        }

        String originalerDateiname = dokument.getOriginalDateiname();
        String contentType = dokument.getDateityp();
        if (contentType == null || contentType.isBlank()
                || MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(contentType)) {
            contentType = bestimmeContentType(resource,
                    originalerDateiname != null ? originalerDateiname : dateiname);
        }

        String filename = (originalerDateiname != null && !originalerDateiname.isBlank())
                ? originalerDateiname
                : resource.getFilename();

        Auslieferung art = auslieferung(contentType);

        return mitBildCache(ResponseEntity.ok(), art.typ())
                .contentType(MediaType.parseMediaType(art.typ()))
                .header(HttpHeaders.CONTENT_DISPOSITION, art.disposition(filename))
                .header(NOSNIFF, "nosniff")
                .body(resource);
    }

    /**
     * Liefert ein verkleinertes Vorschaubild (max 300x300 px) des Dokuments.
     * Das Thumbnail wird beim ersten Aufruf erzeugt und im Speicher gecacht.
     */
    @GetMapping("/api/dokumente/{dateiname:.+}/thumbnail")
    public ResponseEntity<byte[]> liefereThumbnail(@PathVariable String dateiname) {
        return liefereVerkleinert(dateiname, "thumb_" + dateiname,
                bildVorschauService::ausCache, bildVorschauService::erzeugeUndCache);
    }

    /**
     * Liefert das Bild in Anzeigegröße (max. 1600 px Kantenlänge) für die
     * Vollbildansicht am Handy. Ein Handyfoto ist damit rund zehnmal kleiner als
     * das Original und auch über Mobilfunk sofort da.
     */
    @GetMapping("/api/dokumente/{dateiname:.+}/anzeige")
    public ResponseEntity<byte[]> liefereAnzeige(@PathVariable String dateiname) {
        return liefereVerkleinert(dateiname, dateiname,
                bildVorschauService::anzeigeAusCache, bildVorschauService::erzeugeAnzeigeUndCache);
    }

    private ResponseEntity<byte[]> liefereVerkleinert(String dateiname, String antwortName,
                                                      Function<String, byte[]> ausCache,
                                                      Verkleinerung verkleinerung) {
        // Original-Datei laden – auch wenn die verkleinerte Fassung schon im Cache liegt.
        // Fehlt die Datei, wurde das Foto gelöscht und darf nicht weiter ausgeliefert werden,
        // egal über welchen Weg (Bautagebuch, Anfrage, PC) es entfernt wurde.
        MobileDatei mobileDatei = ladeMobileDatei(dateiname);
        Resource resource = mobileDatei == null ? null : mobileDatei.resource();
        if (mobileDatei == null) {
            try {
                resource = dateiSpeicherService.ladeDokumentAlsResource(dateiname);
            } catch (RuntimeException ex) {
                // Fallback ausschließlich für Desktop-Anfragen.
                try {
                    resource = dateiSpeicherService.ladeBildAlsResource(dateiname);
                } catch (RuntimeException ex2) {
                    bildVorschauService.vergiss(dateiname);
                    throw new NotFoundException("Dokument nicht gefunden: " + dateiname);
                }
            }
        }

        // Desktop-Fallbacks und unterschiedliche Speicher dürfen keinen Cache-Eintrag teilen.
        String cacheKey = mobileDatei == null ? dateiname : mobileDatei.cacheKey();
        byte[] cached = ausCache.apply(cacheKey);
        if (cached != null) {
            return verkleinertesBild(cached, antwortName);
        }

        // Prüfen ob es ein Bild ist
        String contentType = bestimmeContentType(resource, dateiname);
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            // Kein Bild – Original zurückgeben als Fallback
            Auslieferung art = auslieferung(contentType);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(art.typ()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, art.disposition(dateiname))
                    .header(NOSNIFF, "nosniff")
                    .body(resourceToBytes(resource));
        }

        try {
            byte[] jpegBytes;
            try {
                jpegBytes = verkleinerung.erzeuge(cacheKey, resource);
            } catch (BildVorschauService.UmrechnungAusgelastetException ausgelastet) {
                // Vorübergehend: nichts speichern lassen, die App lädt dann das Original
                // und versucht es beim nächsten Öffnen wieder mit der kleinen Fassung.
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .cacheControl(CacheControl.noStore())
                        .header(HttpHeaders.RETRY_AFTER, "5")
                        .build();
            }
            if (jpegBytes == null) {
                // Format nicht lesbar – Original zurückgeben
                return fallbackOriginal(resource, dateiname, contentType);
            }
            return verkleinertesBild(jpegBytes, antwortName);
        } catch (IOException e) {
            log.warn("Verkleinerung fehlgeschlagen für {}: {}", dateiname, e.getMessage());
            return fallbackOriginal(resource, dateiname, contentType);
        }
    }

    private ResponseEntity<byte[]> verkleinertesBild(byte[] jpegBytes, String dateiname) {
        // Inhalt ist immer JPEG, also auch die Endung – und der Name sauber kodiert,
        // weil er aus dem Pfad stammt.
        String jpegName = dateiname.replaceFirst("\\.[^.]*+$", "") + ".jpg";
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(bildCache())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(jpegName).build().toString())
                .body(jpegBytes);
    }

    private ResponseEntity<byte[]> fallbackOriginal(Resource resource, String dateiname, String contentType) {
        try {
            Auslieferung art = auslieferung(contentType);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(art.typ()))
                    .cacheControl(bildCache())
                    .header(HttpHeaders.CONTENT_DISPOSITION, art.disposition(dateiname))
                    .header(NOSNIFF, "nosniff")
                    .body(resourceToBytes(resource));
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Lesen der Datei: " + dateiname, e);
        }
    }

    private byte[] resourceToBytes(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Lesen der Resource: " + resource.getFilename(), e);
        }
    }

    private ResponseEntity<Resource> liefereDokumentOhneMetadaten(String dateiname) {
        Resource resource;
        try {
            resource = dateiSpeicherService.ladeDokumentAlsResource(dateiname);
        } catch (SecurityException ex) {
            // Verzeichnistraversal bleibt ein eigener Fall – siehe oben.
            throw ex;
        } catch (RuntimeException ex) {
            // Eine Datei, die es nicht gibt, ist kein Serverfehler. Vorher schlug die
            // RuntimeException bis zum DispatcherServlet durch und der Nutzer sah
            // "Fehler 500" statt eines verwertbaren Hinweises.
            //
            // Nur Dateiname und Exception-Klasse ins WARN: Die Meldung des Service
            // zaehlt alle Speicherpfade inklusive UNC-Serveradresse auf, und die
            // gehoert nicht in ein Log, das nach aussen gereicht wird.
            log.warn("Dokument {} konnte nicht ausgeliefert werden ({})",
                    dateiname, ex.getClass().getSimpleName());
            log.debug("Details zum nicht auslieferbaren Dokument {}", dateiname, ex);
            throw new NotFoundException("Dokument nicht gefunden: " + dateiname);
        }
        if (resource == null) {
            throw new NotFoundException("Dokument nicht gefunden: " + dateiname);
        }
        Auslieferung art = auslieferung(bestimmeContentType(resource, dateiname));

        return mitBildCache(ResponseEntity.ok(), art.typ())
                .contentType(MediaType.parseMediaType(art.typ()))
                .header(HttpHeaders.CONTENT_DISPOSITION, art.disposition(dateiname))
                .header(NOSNIFF, "nosniff")
                .body(resource);
    }

    /**
     * Nur Bilder bekommen den langen Cache. PDFs wie Rechnungen werden unter demselben
     * Namen neu erzeugt und müssen deshalb immer frisch vom Server kommen.
     */
    private ResponseEntity.BodyBuilder mitBildCache(ResponseEntity.BodyBuilder builder, String contentType) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            builder.cacheControl(bildCache());
        }
        return builder;
    }

    private MobileDatei ladeMobileDatei(String dateiname) {
        MobilePrincipal principal = MobilePrincipal.current();
        if (principal == null) {
            return null;
        }
        var datei = mobileObjectAccessService.requireReadableFile(dateiname, principal.mitarbeiterId());
        return new MobileDatei(dateiSpeicherService.ladeMobileDateiAlsResource(datei),
                "mobile:" + datei.speicher().name() + ":" + datei.dateiname());
    }

    private CacheControl bildCache() {
        return MobilePrincipal.current() == null ? BILD_CACHE : CacheControl.noStore();
    }

    private String bestimmeContentType(Resource resource, String filename) {
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        try {
            String probed = Files.probeContentType(resource.getFile().toPath());
            if (probed != null) {
                contentType = probed;
            }
        } catch (IOException ignored) {
        }

        if (MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType)) {
            String name = filename.toLowerCase();
            if (name.endsWith(".png")) {
                contentType = MediaType.IMAGE_PNG_VALUE;
            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                contentType = MediaType.IMAGE_JPEG_VALUE;
            } else if (name.endsWith(".gif")) {
                contentType = MediaType.IMAGE_GIF_VALUE;
            } else if (name.endsWith(".webp")) {
                contentType = "image/webp";
            } else if (name.endsWith(".pdf.html")) {
                contentType = MediaType.APPLICATION_PDF_VALUE;
            } else if (name.endsWith(".xlsx")) {
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } else if (name.endsWith(".xls")) {
                contentType = "application/vnd.ms-excel";
            } else if (name.endsWith(".xlsm")) {
                contentType = "application/vnd.ms-excel.sheet.macroEnabled.12";
            } else if (name.endsWith(".csv")) {
                contentType = "text/csv";
            } else if (name.endsWith(".ods")) {
                contentType = "application/vnd.oasis.opendocument.spreadsheet";
            }
        }
        return contentType;
    }

    private String ensureUncPrefix(String pfad) {
        if (pfad == null || pfad.isBlank()) {
            return pfad;
        }
        if (pfad.startsWith("\\\\")) {
            return pfad;
        }
        if (pfad.startsWith("\\")) {
            return "\\" + pfad;
        }
        return pfad;
    }

    private String encodePathForProtocol(String pfad) {
        if (pfad == null) {
            return null;
        }
        String encoded = URLEncoder.encode(pfad, StandardCharsets.UTF_8);
        return encoded.replace("+", "%20");
    }

}
