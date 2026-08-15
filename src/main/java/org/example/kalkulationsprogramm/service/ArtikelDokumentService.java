package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelDokument;
import org.example.kalkulationsprogramm.domain.ArtikelDokumentTyp;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelDokumentDto;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.ArtikelDokumentRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kapselt die gesamte Datei-Logik fuer Artikel-Dokumente: Ablage auf der
 * Platte, Sicherheitspruefungen beim Upload, Auslieferung und Loeschen.
 *
 * <p>Ablage unter {@code uploads/artikel/{artikelId}/}, Dateiname auf der
 * Platte {@code UUID_bereinigterOriginalname} - Muster wie
 * {@code LieferantenController.uploadBild} bzw. {@code LieferantDokumentService}.
 *
 * <p>Es gibt bewusst kein Datenbank-Constraint, das "hoechstens ein
 * Vorschaubild je Artikel" erzwingt - diese Regel setzt ausschliesslich dieser
 * Service durch: Ein neu hochgeladenes {@code VORSCHAUBILD} ersetzt ein
 * vorhandenes, alter Datenbankeintrag und alte Datei werden entfernt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtikelDokumentService {

    /** Groessenlimit je Datei. */
    private static final long MAX_DATEIGROESSE_BYTES = 10L * 1024 * 1024;

    /** Erlaubte Endungen - alles andere wird abgelehnt, auch exe/bat/js/sh. */
    private static final Set<String> ERLAUBTE_ENDUNGEN = Set.of("pdf", "png", "jpg", "jpeg", "webp", "gif");

    private final ArtikelDokumentRepository dokumentRepository;
    private final ArtikelRepository artikelRepository;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    /**
     * Alle Dokumente eines Artikels, sortiert wie zur Anzeige vorgesehen.
     *
     * @throws NotFoundException bei unbekannter oder ungueltiger Artikel-ID
     */
    @Transactional(readOnly = true)
    public List<ArtikelDokumentDto> listeDokumente(Long artikelId) {
        pruefeArtikelExistiert(artikelId);
        return dokumentRepository.findByArtikelIdOrderBySortierungAscIdAsc(artikelId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Speichert eine hochgeladene Datei als neues Artikel-Dokument.
     *
     * <p>Wird ein {@code VORSCHAUBILD} hochgeladen und existiert bereits eines,
     * ersetzt das neue das alte - alter Datenbankeintrag und alte Datei werden
     * entfernt, nachdem die neue Datei sicher geschrieben ist.
     *
     * @throws NotFoundException wenn die Artikel-ID unbekannt ist
     * @throws IllegalArgumentException bei fehlender Datei, verbotener Endung,
     *         Path-Traversal-Versuch oder Ueberschreitung des Groessenlimits
     */
    @Transactional
    public ArtikelDokumentDto ladeHoch(Long artikelId, MultipartFile datei, ArtikelDokumentTyp typ,
            String beschreibung) throws IOException {
        Artikel artikel = artikelRepository.findById(artikelId)
                .orElseThrow(() -> new NotFoundException("Diesen Artikel gibt es nicht."));

        if (typ == null) {
            throw new IllegalArgumentException("Bitte einen Dokumenttyp waehlen.");
        }
        if (datei == null || datei.isEmpty()) {
            throw new IllegalArgumentException("Es wurde keine Datei ausgewaehlt.");
        }
        if (datei.getSize() > MAX_DATEIGROESSE_BYTES) {
            throw new IllegalArgumentException("Die Datei ist zu gross. Erlaubt sind hoechstens 10 MB.");
        }

        String originalDateiname = bereinigeUndPruefeDateiname(datei.getOriginalFilename());
        String storedFilename = UUID.randomUUID() + "_" + originalDateiname;

        Path artikelDir = artikelVerzeichnis(artikelId);
        Files.createDirectories(artikelDir);
        Path targetPath = artikelDir.resolve(storedFilename).normalize();
        if (!targetPath.startsWith(artikelDir)) {
            throw new IllegalArgumentException("Der Dateiname ist nicht zulaessig.");
        }

        // Vorhandenes Vorschaubild merken, aber erst nach dem erfolgreichen
        // Schreiben der neuen Datei entfernen - sonst waere bei einem
        // Schreibfehler weder das alte noch das neue Bild mehr vorhanden.
        Optional<ArtikelDokument> vorhandenesVorschaubild = typ == ArtikelDokumentTyp.VORSCHAUBILD
                ? dokumentRepository.findFirstByArtikelIdAndTyp(artikelId, ArtikelDokumentTyp.VORSCHAUBILD)
                : Optional.empty();

        try (var inputStream = datei.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        ArtikelDokument dokument = new ArtikelDokument();
        dokument.setArtikel(artikel);
        dokument.setOriginalDateiname(originalDateiname);
        dokument.setGespeicherterDateiname(storedFilename);
        dokument.setTyp(typ);
        dokument.setBeschreibung(beschreibung);
        dokument.setErstelltAm(LocalDateTime.now());
        dokument.setDateigroesseBytes(datei.getSize());
        // hochgeladenVon bleibt null: der ArtikelController hat kein Verfahren,
        // um den angemeldeten Mitarbeiter zu ermitteln (kein Token/Principal an
        // seinen bestehenden Endpoints). Das Feld ist ausdruecklich optional.
        dokument = dokumentRepository.save(dokument);

        vorhandenesVorschaubild.ifPresent(this::loescheDatenbankUndDatei);

        return toDto(dokument);
    }

    /**
     * Laedt die physische Datei zu einem Dokument fuer die Auslieferung.
     *
     * @throws NotFoundException wenn das Dokument oder die Datei nicht existiert
     */
    @Transactional(readOnly = true)
    public ArtikelDokumentDatei ladeDatei(Long dokumentId) {
        ArtikelDokument dokument = dokumentRepository.findById(dokumentId)
                .orElseThrow(() -> new NotFoundException("Dieses Dokument gibt es nicht."));

        Path artikelDir = artikelVerzeichnis(dokument.getArtikel().getId());
        Path filePath = artikelDir.resolve(dokument.getGespeicherterDateiname()).normalize();
        if (!filePath.startsWith(artikelDir) || !Files.exists(filePath)) {
            throw new NotFoundException("Diese Datei gibt es nicht mehr.");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            String contentType = ermittleContentType(filePath, dokument.getOriginalDateiname());
            return new ArtikelDokumentDatei(resource, dokument.getOriginalDateiname(), contentType);
        } catch (java.net.MalformedURLException e) {
            throw new NotFoundException("Diese Datei gibt es nicht mehr.");
        }
    }

    /**
     * Loescht ein Dokument endgueltig - Datenbankeintrag und physische Datei.
     *
     * @throws NotFoundException wenn das Dokument nicht existiert
     */
    @Transactional
    public void loescheDokument(Long dokumentId) {
        ArtikelDokument dokument = dokumentRepository.findById(dokumentId)
                .orElseThrow(() -> new NotFoundException("Dieses Dokument gibt es nicht."));
        loescheDatenbankUndDatei(dokument);
    }

    /**
     * Vorschaubild-URLs mehrerer Artikel in einem Rutsch - fuer die
     * Trefferliste, die sonst je Zeile eine eigene Abfrage bräuchte (N+1).
     *
     * @return Map von Artikel-ID auf die URL seines Vorschaubilds; Artikel ohne
     *         Vorschaubild fehlen in der Map (kein Eintrag statt {@code null}-Wert)
     */
    @Transactional(readOnly = true)
    public Map<Long, String> ladeVorschaubildUrls(List<Long> artikelIds) {
        if (artikelIds == null || artikelIds.isEmpty()) {
            return Map.of();
        }
        return dokumentRepository.findByArtikelIdInAndTyp(artikelIds, ArtikelDokumentTyp.VORSCHAUBILD).stream()
                .collect(Collectors.toMap(d -> d.getArtikel().getId(), this::baueDateiUrl));
    }

    /** Vorschaubild-URL eines einzelnen Artikels, {@code null} wenn keins hinterlegt. */
    @Transactional(readOnly = true)
    public String ladeVorschaubildUrl(Long artikelId) {
        return dokumentRepository.findFirstByArtikelIdAndTyp(artikelId, ArtikelDokumentTyp.VORSCHAUBILD)
                .map(this::baueDateiUrl)
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // Interna
    // ------------------------------------------------------------------

    private void pruefeArtikelExistiert(Long artikelId) {
        if (artikelId == null || !artikelRepository.existsById(artikelId)) {
            throw new NotFoundException("Diesen Artikel gibt es nicht.");
        }
    }

    private void loescheDatenbankUndDatei(ArtikelDokument dokument) {
        Path artikelDir = artikelVerzeichnis(dokument.getArtikel().getId());
        Path filePath = artikelDir.resolve(dokument.getGespeicherterDateiname()).normalize();

        dokumentRepository.delete(dokument);

        if (filePath.startsWith(artikelDir)) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Datei konnte nicht geloescht werden: {}", filePath, e);
            }
        }
    }

    private Path artikelVerzeichnis(Long artikelId) {
        return Path.of(uploadPath, "artikel", artikelId.toString()).toAbsolutePath().normalize();
    }

    private String baueDateiUrl(ArtikelDokument dokument) {
        return "/api/artikel/dokumente/" + dokument.getId() + "/datei";
    }

    private ArtikelDokumentDto toDto(ArtikelDokument dokument) {
        ArtikelDokumentDto dto = new ArtikelDokumentDto();
        dto.setId(dokument.getId());
        dto.setOriginalDateiname(dokument.getOriginalDateiname());
        dto.setTyp(dokument.getTyp());
        dto.setBeschreibung(dokument.getBeschreibung());
        dto.setErstelltAm(dokument.getErstelltAm());
        dto.setDateigroesseBytes(dokument.getDateigroesseBytes());
        dto.setUrl(baueDateiUrl(dokument));
        return dto;
    }

    /**
     * Bereinigt den Originaldateinamen und prueft ihn gegen Path-Traversal und
     * die Endungs-Whitelist.
     *
     * <p>{@link StringUtils#cleanPath} loest {@code ..}-Segmente auf, soweit ein
     * vorangehendes Segment existiert, das sie aufheben kann ({@code a/../b} ->
     * {@code b}). Bleibt danach trotzdem ein {@code ..} oder ein Pfadtrenner
     * uebrig (z.B. bei {@code ../../etc/passwd}), ist der Name boesartig und
     * wird abgelehnt.
     */
    private String bereinigeUndPruefeDateiname(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("Der Datei fehlt ein Name.");
        }
        String bereinigt = StringUtils.cleanPath(originalFilename.trim());
        if (bereinigt.contains("..") || bereinigt.contains("/") || bereinigt.contains("\\")) {
            throw new IllegalArgumentException("Dieser Dateiname ist nicht zulaessig.");
        }

        String endung = ermittleEndung(bereinigt);
        if (!ERLAUBTE_ENDUNGEN.contains(endung)) {
            throw new IllegalArgumentException(
                    "Dieser Dateityp wird nicht unterstuetzt. Erlaubt sind PDF, PNG, JPG, JPEG, WEBP und GIF.");
        }
        return bereinigt;
    }

    private String ermittleEndung(String dateiname) {
        int punkt = dateiname.lastIndexOf('.');
        if (punkt < 0 || punkt == dateiname.length() - 1) {
            return "";
        }
        return dateiname.substring(punkt + 1).toLowerCase(Locale.ROOT);
    }

    /** Content-Type anhand des Dateisystems, sonst anhand der (bereits geprueften) Endung. */
    private String ermittleContentType(Path filePath, String originalDateiname) {
        try {
            String probed = Files.probeContentType(filePath);
            if (probed != null) {
                return probed;
            }
        } catch (IOException ignored) {
            // Fallback ueber die Endung unten
        }
        return switch (ermittleEndung(originalDateiname)) {
            case "pdf" -> MediaType.APPLICATION_PDF_VALUE;
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            case "gif" -> MediaType.IMAGE_GIF_VALUE;
            case "webp" -> "image/webp";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    /** Transportiert Datei-Ressource, Originalname und Content-Type an den Controller. */
    public record ArtikelDokumentDatei(Resource resource, String originalDateiname, String contentType) {
    }
}
