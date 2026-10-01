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
import org.example.kalkulationsprogramm.dto.OpenExternalResponse;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.service.BildVorschauService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
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

    private static final Logger log = LoggerFactory.getLogger(DateiController.class);

    /**
     * Gespeicherte Bilder tragen einen eindeutigen UUID-Namen und werden nie
     * überschrieben – unter derselben Adresse liegt also immer dasselbe Bild. Das Handy
     * darf es deshalb lange behalten und muss Baustellenfotos nicht bei jedem Öffnen
     * neu laden. {@code private}: Fotos sind personenbezogen und gehören in den Cache
     * des Geräts, nicht in einen geteilten Zwischenspeicher (Proxy, CDN).
     *
     * <p>Bewusst in Kauf genommen: Wird ein Foto im ERP gelöscht, bleibt die Kopie im
     * Gerät bis zum Abmelden (die App leert dann ihren Bilder-Cache) bzw. höchstens
     * 30 Tage bestehen. Der Server selbst liefert es ab sofort nicht mehr aus.</p>
     */
    static final CacheControl BILD_CACHE = CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate();

    /** Erzeugt eine verkleinerte Fassung; {@code null}, wenn das Format nicht lesbar ist. */
    @FunctionalInterface
    private interface Verkleinerung {
        byte[] erzeuge(String dateiname, Resource resource) throws IOException;
    }

    /**
     * Dieser Endpunkt liefert gespeicherte Bilder aus.
     * Das ":.+" ist wichtig, damit auch Dateiendungen im Dateinamen erlaubt sind.
     * @param dateiname Der einzigartige Name des Bildes.
     * @return Die Bilddatei mit dem korrekten Content-Type.
     */
    @GetMapping("/api/images/{dateiname:.+}")
    public ResponseEntity<Resource> liefereBild(@PathVariable String dateiname) {
        Resource resource = dateiSpeicherService.ladeBildAlsResource(dateiname);

        String contentType = bestimmeContentType(resource, dateiname);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(BILD_CACHE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/api/dokumente/{dateiname:.+}")
    public ResponseEntity<?> liefereDokument(@PathVariable String dateiname,
                                             @RequestParam(required = false) String token,
                                             @RequestParam(required = false, defaultValue = "false") boolean download,
                                             Principal principal) {
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

        boolean inline = contentType != null && (MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(contentType)
                || contentType.toLowerCase().startsWith("image/"));
        String disposition = inline ? "inline" : "attachment";

        return mitBildCache(ResponseEntity.ok(), contentType)
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"")
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
        Resource resource;
        try {
            resource = dateiSpeicherService.ladeDokumentAlsResource(dateiname);
        } catch (RuntimeException ex) {
            // Fallback: versuche als Bild zu laden
            try {
                resource = dateiSpeicherService.ladeBildAlsResource(dateiname);
            } catch (RuntimeException ex2) {
                bildVorschauService.vergiss(dateiname);
                throw new NotFoundException("Dokument nicht gefunden: " + dateiname);
            }
        }

        byte[] cached = ausCache.apply(dateiname);
        if (cached != null) {
            return verkleinertesBild(cached, antwortName);
        }

        // Prüfen ob es ein Bild ist
        String contentType = bestimmeContentType(resource, dateiname);
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            // Kein Bild – Original zurückgeben als Fallback
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + dateiname + "\"")
                    .body(resourceToBytes(resource));
        }

        try {
            byte[] jpegBytes;
            try {
                jpegBytes = verkleinerung.erzeuge(dateiname, resource);
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
                .cacheControl(BILD_CACHE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(jpegName).build().toString())
                .body(jpegBytes);
    }

    private ResponseEntity<byte[]> fallbackOriginal(Resource resource, String dateiname, String contentType) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .cacheControl(BILD_CACHE)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + dateiname + "\"")
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
        String contentType = bestimmeContentType(resource, dateiname);
        boolean inline = contentType != null && (MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(contentType)
                || contentType.toLowerCase().startsWith("image/"));
        String disposition = inline ? "inline" : "attachment";

        return mitBildCache(ResponseEntity.ok(), contentType)
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + dateiname + "\"")
                .body(resource);
    }

    /**
     * Nur Bilder bekommen den langen Cache. PDFs wie Rechnungen werden unter demselben
     * Namen neu erzeugt und müssen deshalb immer frisch vom Server kommen.
     */
    private ResponseEntity.BodyBuilder mitBildCache(ResponseEntity.BodyBuilder builder, String contentType) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            builder.cacheControl(BILD_CACHE);
        }
        return builder;
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
