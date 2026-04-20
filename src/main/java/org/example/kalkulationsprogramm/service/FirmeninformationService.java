package org.example.kalkulationsprogramm.service;

import com.lowagie.text.Image;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FirmeninformationService {

    private static final Logger log = LoggerFactory.getLogger(FirmeninformationService.class);

    /** Maximal zulaessige Logo-Dateigroesse in Bytes (2 MB). */
    static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;

    /** Erlaubte MIME-Types fuer Firmenlogo (Whitelist). */
    static final List<String> ERLAUBTE_MIME_TYPES = List.of(
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            "image/webp");

    /** MIME-Type -> Standard-Dateiendung (lowercase, ohne Punkt). */
    private static final Map<String, String> MIME_ZU_ENDUNG = Map.of(
            MediaType.IMAGE_PNG_VALUE, "png",
            MediaType.IMAGE_JPEG_VALUE, "jpg",
            "image/webp", "webp");

    private final FirmeninformationRepository repository;

    /** Basis-Verzeichnis fuer Firmen-Uploads (Logo). Ueberschreibbar fuer Tests. */
    @Value("${firma.logo.upload-dir:uploads/firma/logo}")
    private String logoUploadDir;

    @Transactional(readOnly = true)
    public FirmeninformationDto getFirmeninformation() {
        Firmeninformation fi = repository.getOrCreate();
        return toDto(fi);
    }

    @Transactional
    public FirmeninformationDto speichern(FirmeninformationDto dto) {
        Firmeninformation fi = repository.getOrCreate();
        
        fi.setFirmenname(dto.getFirmenname());
        fi.setStrasse(dto.getStrasse());
        fi.setPlz(dto.getPlz());
        fi.setOrt(dto.getOrt());
        fi.setTelefon(dto.getTelefon());
        fi.setFax(dto.getFax());
        fi.setEmail(dto.getEmail());
        fi.setWebsite(dto.getWebsite());
        fi.setSteuernummer(dto.getSteuernummer());
        fi.setUstIdNr(dto.getUstIdNr());
        fi.setHandelsregister(dto.getHandelsregister());
        fi.setHandelsregisterNummer(dto.getHandelsregisterNummer());
        fi.setBankName(dto.getBankName());
        fi.setIban(dto.getIban());
        fi.setBic(dto.getBic());
        fi.setLogoDateiname(dto.getLogoDateiname());
        fi.setGeschaeftsfuehrer(dto.getGeschaeftsfuehrer());
        fi.setFusszeileText(dto.getFusszeileText());
        
        fi = repository.save(fi);
        return toDto(fi);
    }

    /**
     * Speichert eine hochgeladene Logo-Datei auf Platte und aktualisiert
     * {@link Firmeninformation#getLogoDateiname()} auf den Basenamen (ohne Pfad).
     * <p>
     * Sicherheits-Regeln:
     * <ul>
     *   <li>MIME-Whitelist: nur PNG, JPEG, WebP – sonst {@link IllegalArgumentException}</li>
     *   <li>Groessen-Limit {@value #MAX_LOGO_BYTES} Bytes</li>
     *   <li>Zielname wird <b>server-seitig</b> gesetzt ({@code logo.<ext>}),
     *       Client-Dateiname wird nie verwendet -> Pfad-Traversal ausgeschlossen</li>
     *   <li>Zielpfad wird nach Normalisierung gegen das Upload-Basis-Verzeichnis geprueft</li>
     * </ul>
     * Ein bereits existierendes Logo wird ueberschrieben (auch bei anderer Endung).
     */
    @Transactional
    public FirmeninformationDto speichereLogoDatei(MultipartFile datei) throws IOException {
        if (datei == null || datei.isEmpty()) {
            throw new IllegalArgumentException("Datei ist leer");
        }
        if (datei.getSize() > MAX_LOGO_BYTES) {
            throw new IllegalArgumentException(
                    "Datei zu groß (maximal " + (MAX_LOGO_BYTES / 1024 / 1024) + " MB)");
        }
        String contentType = datei.getContentType();
        String endung = MIME_ZU_ENDUNG.get(contentType != null ? contentType.toLowerCase(Locale.ROOT) : "");
        if (endung == null) {
            throw new IllegalArgumentException(
                    "Ungültiger Dateityp – erlaubt sind PNG, JPEG und WebP");
        }

        Path basis = Path.of(logoUploadDir).toAbsolutePath().normalize();
        Files.createDirectories(basis);

        String neuerDateiname = "logo." + endung;
        Path ziel = basis.resolve(neuerDateiname).normalize();
        if (!ziel.startsWith(basis)) {
            // kann nur bei manipuliertem logoUploadDir passieren – defensive Pruefung
            throw new IllegalStateException("Ungueltiger Zielpfad");
        }

        Firmeninformation fi = repository.getOrCreate();
        String alterDateiname = fi.getLogoDateiname();
        if (alterDateiname != null && !alterDateiname.equals(neuerDateiname)) {
            // Vorherige Datei mit abweichender Endung entfernen, damit nicht Reste liegen bleiben
            loescheLogoDateiFallsVorhanden(basis, alterDateiname);
        }

        try (var in = datei.getInputStream()) {
            Files.copy(in, ziel, StandardCopyOption.REPLACE_EXISTING);
        }

        fi.setLogoDateiname(neuerDateiname);
        fi = repository.save(fi);
        log.info("Firmenlogo gespeichert: dateiname={} groesse={}B", neuerDateiname, datei.getSize());
        return toDto(fi);
    }

    /**
     * Entfernt das Firmenlogo (Datei + Eintrag im Singleton). Idempotent:
     * ist kein Logo vorhanden, geschieht nichts.
     */
    @Transactional
    public FirmeninformationDto loescheLogoDatei() {
        Firmeninformation fi = repository.getOrCreate();
        String dateiname = fi.getLogoDateiname();
        if (dateiname != null && !dateiname.isBlank()) {
            Path basis = Path.of(logoUploadDir).toAbsolutePath().normalize();
            loescheLogoDateiFallsVorhanden(basis, dateiname);
        }
        fi.setLogoDateiname(null);
        fi = repository.save(fi);
        return toDto(fi);
    }

    /**
     * Liefert die Logo-Bytes oder {@code null}, falls kein Logo hinterlegt ist bzw.
     * die Datei auf Platte fehlt. Wird von PDF-Services benutzt, die bei
     * fehlendem Logo einfach kein Bild zeichnen.
     */
    @Transactional(readOnly = true)
    public byte[] loadLogoBytes() {
        Firmeninformation fi = repository.findFirmeninformation().orElse(null);
        if (fi == null || fi.getLogoDateiname() == null || fi.getLogoDateiname().isBlank()) {
            return null;
        }
        Path basis = Path.of(logoUploadDir).toAbsolutePath().normalize();
        Path datei = basis.resolve(fi.getLogoDateiname()).normalize();
        if (!datei.startsWith(basis) || !Files.isRegularFile(datei)) {
            return null;
        }
        try {
            return Files.readAllBytes(datei);
        } catch (IOException e) {
            log.warn("Firmenlogo konnte nicht gelesen werden: {}", datei, e);
            return null;
        }
    }

    /**
     * Liefert das Firmenlogo als iText-{@link Image} oder {@code null},
     * falls kein Logo hinterlegt ist. Aufrufer duerfen {@code null} ohne
     * Fallback akzeptieren (Design-Entscheidung: kein Software-Logo als Fallback).
     */
    public Image loadLogoImage() {
        byte[] bytes = loadLogoBytes();
        if (bytes == null) {
            return null;
        }
        try {
            return Image.getInstance(bytes);
        } catch (Exception e) {
            log.warn("Firmenlogo konnte nicht als PDF-Bild geladen werden", e);
            return null;
        }
    }

    /** Liefert die MIME-Type-Information fuer den GET-Endpoint. */
    @Transactional(readOnly = true)
    public String ermittleLogoContentType() {
        Firmeninformation fi = repository.findFirmeninformation().orElse(null);
        if (fi == null || fi.getLogoDateiname() == null) {
            return null;
        }
        String name = fi.getLogoDateiname().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return MediaType.IMAGE_JPEG_VALUE;
        if (name.endsWith(".webp")) return "image/webp";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private void loescheLogoDateiFallsVorhanden(Path basis, String dateiname) {
        if (dateiname == null || dateiname.isBlank()) {
            return;
        }
        // Dateiname kommt aus eigener DB -> trotzdem normalisiert pruefen
        String baseName = Path.of(dateiname).getFileName().toString();
        Path datei = basis.resolve(baseName).normalize();
        if (!datei.startsWith(basis)) {
            return;
        }
        try {
            Files.deleteIfExists(datei);
        } catch (IOException e) {
            log.warn("Altes Firmenlogo konnte nicht geloescht werden: {}", datei, e);
        }
    }

    private FirmeninformationDto toDto(Firmeninformation fi) {
        FirmeninformationDto dto = new FirmeninformationDto();
        dto.setId(fi.getId());
        dto.setFirmenname(fi.getFirmenname());
        dto.setStrasse(fi.getStrasse());
        dto.setPlz(fi.getPlz());
        dto.setOrt(fi.getOrt());
        dto.setTelefon(fi.getTelefon());
        dto.setFax(fi.getFax());
        dto.setEmail(fi.getEmail());
        dto.setWebsite(fi.getWebsite());
        dto.setSteuernummer(fi.getSteuernummer());
        dto.setUstIdNr(fi.getUstIdNr());
        dto.setHandelsregister(fi.getHandelsregister());
        dto.setHandelsregisterNummer(fi.getHandelsregisterNummer());
        dto.setBankName(fi.getBankName());
        dto.setIban(fi.getIban());
        dto.setBic(fi.getBic());
        dto.setLogoDateiname(fi.getLogoDateiname());
        dto.setGeschaeftsfuehrer(fi.getGeschaeftsfuehrer());
        dto.setFusszeileText(fi.getFusszeileText());
        return dto;
    }
}
