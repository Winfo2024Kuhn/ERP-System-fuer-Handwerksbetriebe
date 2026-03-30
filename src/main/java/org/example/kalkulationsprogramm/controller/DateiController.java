package org.example.kalkulationsprogramm.controller;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.example.kalkulationsprogramm.domain.Dokument;
import org.example.kalkulationsprogramm.dto.OpenExternalResponse;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
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

    private static final Logger log = LoggerFactory.getLogger(DateiController.class);

    private static final int THUMBNAIL_MAX_SIZE = 300;
    private final Map<String, byte[]> thumbnailCache = new ConcurrentHashMap<>();

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
        } catch (RuntimeException ex) {
            if (!dokument.getGespeicherterDateiname().equalsIgnoreCase(dateiname)) {
                return liefereDokumentOhneMetadaten(dateiname);
            }
            throw ex;
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

        return ResponseEntity.ok()
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
        // Aus Cache liefern, falls vorhanden
        byte[] cached = thumbnailCache.get(dateiname);
        if (cached != null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"thumb_" + dateiname + "\"")
                    .body(cached);
        }

        // Original-Datei laden
        Resource resource;
        try {
            resource = dateiSpeicherService.ladeDokumentAlsResource(dateiname);
        } catch (RuntimeException ex) {
            // Fallback: versuche als Bild zu laden
            try {
                resource = dateiSpeicherService.ladeBildAlsResource(dateiname);
            } catch (RuntimeException ex2) {
                throw new org.example.kalkulationsprogramm.exception.NotFoundException(
                        "Dokument nicht gefunden: " + dateiname);
            }
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

        try (InputStream is = resource.getInputStream()) {
            BufferedImage originalImage = ImageIO.read(is);
            if (originalImage == null) {
                // ImageIO konnte das Bild nicht lesen – Original zurückgeben
                return fallbackOriginal(resource, dateiname, contentType);
            }

            int origWidth = originalImage.getWidth();
            int origHeight = originalImage.getHeight();

            // Wenn das Bild bereits klein genug ist, als JPEG zurückgeben
            if (origWidth <= THUMBNAIL_MAX_SIZE && origHeight <= THUMBNAIL_MAX_SIZE) {
                byte[] jpegBytes = convertToJpeg(originalImage);
                thumbnailCache.put(dateiname, jpegBytes);
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"thumb_" + dateiname + "\"")
                        .body(jpegBytes);
            }

            // Skalierung berechnen (Seitenverhältnis beibehalten)
            double scale = Math.min(
                    (double) THUMBNAIL_MAX_SIZE / origWidth,
                    (double) THUMBNAIL_MAX_SIZE / origHeight);
            int newWidth = (int) Math.round(origWidth * scale);
            int newHeight = (int) Math.round(origHeight * scale);

            BufferedImage thumbnail = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = thumbnail.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
            g2d.dispose();

            byte[] jpegBytes = convertToJpeg(thumbnail);
            thumbnailCache.put(dateiname, jpegBytes);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"thumb_" + dateiname + "\"")
                    .body(jpegBytes);

        } catch (IOException e) {
            log.warn("Thumbnail-Erzeugung fehlgeschlagen für {}: {}", dateiname, e.getMessage());
            return fallbackOriginal(resource, dateiname, contentType);
        }
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

    private ResponseEntity<byte[]> fallbackOriginal(Resource resource, String dateiname, String contentType) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS))
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
        Resource resource = dateiSpeicherService.ladeDokumentAlsResource(dateiname);
        if (resource == null) {
            throw new NotFoundException("Dokument nicht gefunden: " + dateiname);
        }
        String contentType = bestimmeContentType(resource, dateiname);
        boolean inline = contentType != null && (MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(contentType)
                || contentType.toLowerCase().startsWith("image/"));
        String disposition = inline ? "inline" : "attachment";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + dateiname + "\"")
                .body(resource);
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
