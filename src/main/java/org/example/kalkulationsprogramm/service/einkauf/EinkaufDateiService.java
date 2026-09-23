package org.example.kalkulationsprogramm.service.einkauf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufAnlageVersion;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufDatei;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufDateiDto.AnlageDto;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.EinkaufAnlageVersionRepository;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.repository.EinkaufDateiRepository;
import org.example.kalkulationsprogramm.repository.EmailAttachmentRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EinkaufDateiService {
    public static final long MAX_DATEIGROESSE = 10L * 1024 * 1024;
    private static final long MAX_MAIL_GROESSE = 20L * 1024 * 1024;
    private final EinkaufDateiRepository dateien;
    private final EinkaufAnlageVersionRepository versionen;
    private final EinkaufBedarfRepository bedarfe;
    private final EmailAttachmentRepository emailAttachments;
    private final LieferantDokumentRepository lieferantDokumente;
    private final Path uploadRoot;
    private final Path emailAttachmentRoot;

    public EinkaufDateiService(EinkaufDateiRepository dateien, EinkaufAnlageVersionRepository versionen,
            EinkaufBedarfRepository bedarfe, @Value("${upload.path:uploads}") String uploadRoot) {
        this(dateien, versionen, bedarfe, null, null, uploadRoot, "uploads/email");
    }

    @Autowired
    public EinkaufDateiService(EinkaufDateiRepository dateien, EinkaufAnlageVersionRepository versionen,
            EinkaufBedarfRepository bedarfe, EmailAttachmentRepository emailAttachments,
            LieferantDokumentRepository lieferantDokumente, @Value("${upload.path:uploads}") String uploadRoot,
            @Value("${file.mail-attachment-dir:uploads/email}") String emailAttachmentRoot) {
        this.dateien = dateien;
        this.versionen = versionen;
        this.bedarfe = bedarfe;
        this.emailAttachments = emailAttachments;
        this.lieferantDokumente = lieferantDokumente;
        this.uploadRoot = Path.of(uploadRoot).toAbsolutePath().normalize().resolve("einkauf").normalize();
        this.emailAttachmentRoot = Path.of(emailAttachmentRoot).toAbsolutePath().normalize();
    }

    @Transactional
    public AnlageDto hochladen(Long bedarfId, MultipartFile datei, String revision, Long akteurId) {
        if (bedarfId == null || bedarfId <= 0 || akteurId == null || akteurId <= 0)
            throw new IllegalArgumentException("Bedarf und handelnder Benutzer müssen gültig sein.");
        if (datei == null || datei.isEmpty() || datei.getSize() > MAX_DATEIGROESSE)
            throw new IllegalArgumentException("Die Datei fehlt oder überschreitet das Limit von 10 MiB.");
        if (revision == null || revision.isBlank() || revision.length() > 80)
            throw new IllegalArgumentException("Bitte eine gültige Revision angeben.");
        EinkaufBedarf bedarf = bedarfe.findByIdForUpdate(bedarfId)
                .orElseThrow(() -> new NotFoundException("Der Einkaufsbedarf wurde nicht gefunden."));
        if (versionen.findByBedarfIdAndRevision(bedarfId, revision.trim()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Diese Revision ist bereits vorhanden.");

        String originalName = safeFilename(datei.getOriginalFilename());
        byte[] bytes;
        try {
            try (var input = datei.getInputStream()) { bytes = input.readNBytes((int) MAX_DATEIGROESSE + 1); }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Die Datei konnte nicht gelesen werden.", exception);
        }
        if (bytes.length == 0 || bytes.length > MAX_DATEIGROESSE)
            throw new IllegalArgumentException("Die Datei fehlt oder überschreitet das Limit von 10 MiB.");
        String extension = extension(originalName);
        String mime = validateFormat(extension, datei.getContentType(), bytes);
        String hash = sha256(bytes);
        EinkaufDatei dateiEntity = dateien.findBySha256(hash)
                .map(existing -> reuseOrRepair(existing, bytes, originalName, mime))
                .orElseGet(() -> writeNewFile(bytes, originalName, mime));
        EinkaufAnlageVersion version = versionen.save(new EinkaufAnlageVersion(bedarf, dateiEntity, revision.trim()));
        return toDto(version);
    }

    @Transactional
    public AnlageDto nutzeEmailAnlage(Long bedarfId, Long emailAttachmentId, String revision, Long akteurId) {
        if (emailAttachments == null) throw new IllegalStateException("Der Mailanhang-Zugriff ist nicht verfügbar.");
        validateSourceRequest(bedarfId, emailAttachmentId, revision, akteurId);
        EinkaufBedarf bedarf = bedarfe.findByIdForUpdate(bedarfId)
                .orElseThrow(() -> new NotFoundException("Der Einkaufsbedarf wurde nicht gefunden."));
        EmailAttachment attachment = emailAttachments.findById(emailAttachmentId)
                .orElseThrow(() -> new NotFoundException("Der Mailanhang wurde nicht gefunden."));
        String name = safeFilename(attachment.getOriginalFilename());
        byte[] bytes = readBounded(resolveEmailAttachment(attachment));
        if (attachment.getSizeBytes() != null && attachment.getSizeBytes() != bytes.length)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen");
        String mime = validateFormat(extension(name), attachment.getMimeType(), bytes);
        EinkaufDatei stored = reuseOrReference(bytes, name, mime, emailAttachmentId, null);
        return saveVersion(bedarf, stored, revision);
    }

    @Transactional
    public AnlageDto nutzeLieferantDokument(Long bedarfId, Long dokumentId, String revision, Long akteurId) {
        if (lieferantDokumente == null) throw new IllegalStateException("Der Lieferantendokument-Zugriff ist nicht verfügbar.");
        validateSourceRequest(bedarfId, dokumentId, revision, akteurId);
        EinkaufBedarf bedarf = bedarfe.findByIdForUpdate(bedarfId)
                .orElseThrow(() -> new NotFoundException("Der Einkaufsbedarf wurde nicht gefunden."));
        LieferantDokument dokument = lieferantDokumente.findById(dokumentId)
                .orElseThrow(() -> new NotFoundException("Das Lieferantendokument wurde nicht gefunden."));
        if (dokument.getAttachment() != null)
            return nutzeEmailAnlage(bedarfId, dokument.getAttachment().getId(), revision, akteurId);
        String name = safeFilename(dokument.getOriginalDateiname());
        Path directory = uploadRoot.getParent().resolve("lieferanten").resolve(String.valueOf(dokument.getLieferant().getId())).normalize();
        Path file = secureStoredPath(directory, dokument.getGespeicherterDateiname());
        byte[] bytes = readBounded(file);
        String mime = validateFormat(extension(name), null, bytes);
        EinkaufDatei stored = reuseOrReference(bytes, name, mime, null, dokumentId);
        return saveVersion(bedarf, stored, revision);
    }

    @Transactional
    public ImportBildDto speichereImportBild(String filename, String declaredMime, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_DATEIGROESSE)
            throw new IllegalArgumentException("Das eingebettete Bild fehlt oder überschreitet 10 MiB.");
        String name = safeFilename(filename);
        String extension = extension(name);
        if (!List.of("png", "jpg", "jpeg").contains(extension))
            throw new IllegalArgumentException("Als HiCAD-Bild sind nur PNG und JPEG erlaubt.");
        String mime = validateFormat(extension, declaredMime, bytes);
        EinkaufDatei stored = dateien.findBySha256(sha256(bytes)).orElseGet(() -> writeNewFile(bytes, name, mime));
        return new ImportBildDto(stored.getId(), stored.getOriginalName(), stored.getMimeTyp(), stored.getByteAnzahl());
    }

    @Transactional
    public AnlageDto anhaengenImportBild(Long bedarfId, Long dateiId, String revision, Long akteurId) {
        validateSourceRequest(bedarfId, dateiId, revision, akteurId);
        EinkaufBedarf bedarf = bedarfe.findByIdForUpdate(bedarfId)
                .orElseThrow(() -> new NotFoundException("Der Einkaufsbedarf wurde nicht gefunden."));
        EinkaufDatei stored = dateien.findById(dateiId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen"));
        try { readStoredBytes(stored); }
        catch (IOException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen", e); }
        return saveVersion(bedarf, stored, revision);
    }

    @Transactional(readOnly = true)
    public Resource ladeImportBild(Long dateiId, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        EinkaufDatei datei = dateien.findById(dateiId).orElseThrow(() -> new NotFoundException("Das importierte Bild wurde nicht gefunden."));
        try { return new org.springframework.core.io.ByteArrayResource(readStoredBytes(datei)); }
        catch (IOException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen", e); }
    }

    @Transactional(readOnly = true)
    public ImportBildDto findImportBild(Long dateiId) {
        EinkaufDatei datei = dateien.findById(dateiId)
                .orElseThrow(() -> new NotFoundException("Das importierte Bild wurde nicht gefunden."));
        return new ImportBildDto(datei.getId(), datei.getOriginalName(), datei.getMimeTyp(), datei.getByteAnzahl());
    }

    @Transactional
    public org.example.kalkulationsprogramm.dto.Einkauf.EinkaufDateiDto.PdfSnapshotDto speicherePdfSnapshot(byte[] bytes, String filename) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_DATEIGROESSE || !starts(bytes, "%PDF-"))
            throw new IllegalArgumentException("Der PDF-Snapshot ist ungültig oder zu groß.");
        String name = safeFilename(filename);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) throw new IllegalArgumentException("Der PDF-Dateiname ist ungültig.");
        String hash = sha256(bytes);
        EinkaufDatei stored = dateien.findBySha256(hash).map(existing -> {
            try {
                if (!java.security.MessageDigest.isEqual(readStoredBytes(existing), bytes))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Der PDF-Snapshot ist nicht unveränderlich.");
                return existing;
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Der PDF-Snapshot fehlt.", exception);
            }
        }).orElseGet(() -> writeNewFile(bytes.clone(), name, "application/pdf"));
        if (!"application/pdf".equalsIgnoreCase(stored.getMimeTyp()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Der PDF-Snapshot ist nicht als PDF gespeichert.");
        return new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufDateiDto.PdfSnapshotDto(
                stored.getId(), hash, bytes.length);
    }

    @Transactional(readOnly = true)
    public byte[] ladePdfSnapshotBytes(Long dateiId) {
        if (dateiId == null || dateiId <= 0) throw new IllegalArgumentException("Der PDF-Snapshot ist ungültig.");
        EinkaufDatei file = dateien.findById(dateiId)
                .orElseThrow(() -> new NotFoundException("Der PDF-Snapshot wurde nicht gefunden."));
        if (!"application/pdf".equalsIgnoreCase(file.getMimeTyp()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Der PDF-Snapshot wurde nicht gefunden.");
        try { return readStoredBytes(file); }
        catch (IOException exception) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Der PDF-Snapshot fehlt.", exception); }
    }

    @Transactional(readOnly = true)
    public Resource ladePdfSnapshot(Long dateiId, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return new org.springframework.core.io.ByteArrayResource(ladePdfSnapshotBytes(dateiId));
    }

    @Transactional(readOnly = true)
    public Resource laden(Long dateiId, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        EinkaufAnlageVersion version = versionen.findById(dateiId)
                .orElseThrow(() -> new NotFoundException("Die Anlage wurde nicht gefunden."));
        try { return new org.springframework.core.io.ByteArrayResource(readStoredBytes(version.getDatei())); }
        catch (IOException exception) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen", exception); }
    }

    @Transactional(readOnly = true)
    public List<EmailService.Attachment> ladeVersandanlagen(List<Long> versionIds) {
        List<EinkaufAnlageVersion> found = loadVersions(versionIds);
        return found.stream().map(version -> {
            if (!version.isFreigegeben())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage ist nicht freigegeben.");
            try {
                byte[] bytes = readStoredBytes(version.getDatei());
                if (bytes.length != version.getDatei().getByteAnzahl() || !sha256(bytes).equals(version.getDatei().getSha256()))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen");
                return new EmailService.Attachment(bytes, version.getDatei().getOriginalName(), version.getDatei().getMimeTyp());
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen", exception);
            }
        }).toList();
    }

    @Transactional(readOnly = true)
    public void pruefePaketgroesse(List<Long> ids, long pdfBytes) {
        if (pdfBytes < 0 || pdfBytes > MAX_DATEIGROESSE || ids != null && ids.size() > 50)
            throw new IllegalArgumentException("Die PDF-Größe oder Anzahl der Anlagen ist ungültig.");
        long encoded = 0;
        for (EinkaufAnlageVersion version : loadVersions(ids)) {
            long bytes = version.getDatei().getByteAnzahl();
            encoded = Math.addExact(encoded, 4L * ((bytes + 2L) / 3L) + 512L);
        }
        encoded = Math.addExact(encoded, 4L * ((pdfBytes + 2L) / 3L) + 2048L);
        if (encoded > MAX_MAIL_GROESSE) throw new IllegalArgumentException("Die Anhänge überschreiten gemeinsam das Mail-Limit von 20 MiB.");
    }

    @Transactional
    public AnlageDto freigeben(Long versionId, Long akteurId) {
        if (akteurId == null || akteurId <= 0) throw new IllegalArgumentException("Der handelnde Benutzer fehlt.");
        EinkaufAnlageVersion version = versionen.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Die Anlage wurde nicht gefunden."));
        version.setFreigegeben(true);
        return toDto(version);
    }

    @Transactional(readOnly = true)
    public List<AnlageDto> freigegebeneAnlagen(Long bedarfId) {
        return versionen.findByBedarfIdAndFreigegebenTrue(bedarfId).stream().map(this::toDto).toList();
    }

    /** Task13 uses this as a hard send gate: a positive ID is insufficient. */
    @Transactional(readOnly = true)
    public void pruefeFreigegebeneBedarfsanlagen(Long bedarfId, List<Long> ids) {
        if (bedarfId == null || bedarfId <= 0 || ids == null || ids.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Für diesen Bedarf fehlt eine freigegebene technische Anlage.");
        List<EinkaufAnlageVersion> found = loadVersions(ids);
        for (EinkaufAnlageVersion version : found) {
            if (!bedarfId.equals(version.getBedarf().getId()) || !version.isFreigegeben())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Die Anlage gehört nicht zu diesem Bedarf oder ist nicht freigegeben.");
            try { readStoredBytes(version.getDatei()); }
            catch (IOException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen", e); }
        }
        if (found.size() != ids.stream().distinct().count())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Die vollständige Anlagenzuordnung konnte nicht bestätigt werden.");
    }

    @Transactional
    public void markiereVersendet(List<Long> versionIds) {
        for (EinkaufAnlageVersion version : loadVersions(versionIds)) version.setVersendet(true);
    }

    private List<EinkaufAnlageVersion> loadVersions(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) throw new IllegalArgumentException("Eine Anlagen-ID ist ungültig.");
        List<EinkaufAnlageVersion> found = versionen.findAllByIdIn(ids);
        if (found.size() != ids.stream().distinct().count())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen");
        return found;
    }

    private AnlageDto toDto(EinkaufAnlageVersion version) {
        EinkaufDatei file = version.getDatei();
        return new AnlageDto(version.getId(), file.getId(), version.getBedarf().getId(), version.getRevision(), file.getOriginalName(),
                file.getMimeTyp(), file.getByteAnzahl(), file.getSha256(), version.isFreigegeben(), version.isVersendet(), null);
    }

    private EinkaufDatei reuseOrReference(byte[] bytes, String filename, String mime, Long emailId, Long lieferantDocId) {
        String hash = sha256(bytes);
        return dateien.findBySha256(hash).orElseGet(() -> dateien.save(new EinkaufDatei(hash, null, filename,
                mime, bytes.length, emailId, lieferantDocId)));
    }

    private EinkaufDatei writeNewFile(byte[] bytes, String filename, String mime) {
        String stored = UUID.randomUUID().toString();
        Path destination = uploadRoot.resolve(stored).normalize();
        if (!destination.startsWith(uploadRoot)) throw new IllegalArgumentException("Ungültiger Dateipfad.");
        try {
            Files.createDirectories(uploadRoot);
            Files.write(destination, bytes);
            try {
                return dateien.save(new EinkaufDatei(sha256(bytes), stored, filename, mime, bytes.length));
            } catch (RuntimeException exception) {
                try { Files.deleteIfExists(destination); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
                throw exception;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Die Datei konnte nicht gespeichert werden.", exception);
        }
    }

    private EinkaufDatei reuseOrRepair(EinkaufDatei existing, byte[] uploadedBytes, String filename, String mime) {
        try {
            if (java.security.MessageDigest.isEqual(readStoredBytes(existing), uploadedBytes)) return existing;
        } catch (IOException | ResponseStatusException missingSource) {
            // An explicit upload may restore a broken legacy reference; reads alone still return 409.
        }
        String stored = UUID.randomUUID().toString();
        Path destination = uploadRoot.resolve(stored).normalize();
        if (!destination.startsWith(uploadRoot)) throw new IllegalArgumentException("Ungültiger Dateipfad.");
        try {
            Files.createDirectories(uploadRoot);
            Files.write(destination, uploadedBytes);
            existing.setGespeicherterName(stored);
            existing.setOriginalName(filename);
            existing.setMimeTyp(mime);
            existing.setByteAnzahl(uploadedBytes.length);
            existing.setEmailAttachmentId(null);
            existing.setLieferantDokumentId(null);
            try {
                return dateien.save(existing);
            } catch (RuntimeException exception) {
                try { Files.deleteIfExists(destination); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
                throw exception;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Die Datei konnte nicht gespeichert werden.", exception);
        }
    }

    private byte[] readStoredBytes(EinkaufDatei file) throws IOException {
        byte[] bytes;
        if (file.getGespeicherterName() != null) {
            Path path = uploadRoot.resolve(file.getGespeicherterName()).normalize();
            if (!path.startsWith(uploadRoot) || !Files.isRegularFile(path))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen");
            bytes = readBounded(path);
        } else if (file.getEmailAttachmentId() != null && emailAttachments != null) {
            EmailAttachment source = emailAttachments.findById(file.getEmailAttachmentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen"));
            bytes = readBounded(resolveEmailAttachment(source));
        } else if (file.getLieferantDokumentId() != null && lieferantDokumente != null) {
            LieferantDokument source = lieferantDokumente.findById(file.getLieferantDokumentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen"));
            Path directory = uploadRoot.getParent().resolve("lieferanten").resolve(String.valueOf(source.getLieferant().getId())).normalize();
            bytes = readBounded(secureStoredPath(directory, source.getGespeicherterDateiname()));
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen");
        }
        if (bytes.length != file.getByteAnzahl() || !sha256(bytes).equals(file.getSha256()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen");
        return bytes;
    }

    private Path resolveEmailAttachment(EmailAttachment attachment) {
        String stored = attachment.getStoredFilename();
        if (stored == null || stored.isBlank() || !Path.of(stored).getFileName().toString().equals(stored))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen");
        List<Path> candidates = new java.util.ArrayList<>();
        candidates.add(emailAttachmentRoot.resolve(stored).normalize());
        if (attachment.getEmail() != null && attachment.getEmail().getId() != null)
            candidates.add(emailAttachmentRoot.resolve(attachment.getEmail().getId().toString()).resolve(stored).normalize());
        if (attachment.getEmail() != null && attachment.getEmail().getLieferant() != null)
            candidates.add(emailAttachmentRoot.resolve(attachment.getEmail().getLieferant().getId().toString()).resolve(stored).normalize());
        return candidates.stream().filter(p -> p.startsWith(emailAttachmentRoot) && Files.isRegularFile(p)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen"));
    }

    private static Path secureStoredPath(Path directory, String stored) {
        if (stored == null || stored.isBlank() || !Path.of(stored).getFileName().toString().equals(stored))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen");
        Path base = directory.toAbsolutePath().normalize();
        Path path = base.resolve(stored).normalize();
        if (!path.startsWith(base)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen");
        return path;
    }

    private static byte[] readBounded(Path path) {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes((int) MAX_DATEIGROESSE + 1);
            if (bytes.length == 0 || bytes.length > MAX_DATEIGROESSE)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen");
            return bytes;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Anlage fehlt, bitte neu hochladen", exception);
        }
    }

    private AnlageDto saveVersion(EinkaufBedarf bedarf, EinkaufDatei file, String revision) {
        if (versionen.findByBedarfIdAndRevision(bedarf.getId(), revision.trim()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Diese Revision ist bereits vorhanden.");
        return toDto(versionen.save(new EinkaufAnlageVersion(bedarf, file, revision.trim())));
    }

    private static void validateSourceRequest(Long bedarfId, Long sourceId, String revision, Long actorId) {
        if (bedarfId == null || bedarfId <= 0 || sourceId == null || sourceId <= 0 || actorId == null || actorId <= 0
                || revision == null || revision.isBlank() || revision.length() > 80)
            throw new IllegalArgumentException("Bedarf, Quelle, Revision und handelnder Benutzer müssen gültig sein.");
    }

    public record ImportBildDto(Long id, String dateiname, String mimeTyp, long byteAnzahl) {}

    private static String safeFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > 255 || filename.contains("/") || filename.contains("\\")
                || filename.contains("..") || filename.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Der Dateiname ist ungültig.");
        return filename;
    }
    private static String extension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String blocked : List.of(".exe.", ".bat.", ".cmd.", ".js.", ".jar.", ".sh."))
            if (lower.contains(blocked)) throw new IllegalArgumentException("Doppelte oder gefährliche Dateiendung.");
        int dot = lower.lastIndexOf('.');
        if (dot < 0) throw new IllegalArgumentException("Dateiformat nicht erlaubt.");
        String extension = lower.substring(dot + 1);
        if (!List.of("pdf", "dxf", "step", "stp", "png", "jpg", "jpeg").contains(extension))
            throw new IllegalArgumentException("Dateiformat nicht erlaubt.");
        return extension;
    }
    private static String validateFormat(String extension, String declaredMime, byte[] bytes) {
        String mime = switch (extension) {
            case "pdf" -> "application/pdf";
            case "dxf" -> "application/dxf";
            case "step", "stp" -> "model/step";
            case "png" -> "image/png";
            default -> "image/jpeg";
        };
        boolean valid = switch (extension) {
            case "pdf" -> starts(bytes, "%PDF-");
            case "dxf" -> new String(bytes, 0, Math.min(bytes.length, 4096), StandardCharsets.US_ASCII).matches("(?s)^\\s*(?:0\\s*\\R\\s*SECTION|999\\s*\\R).* ".trim());
            case "step", "stp" -> starts(bytes, "ISO-10303-21;");
            case "png" -> bytes.length > 8 && (bytes[0] & 255) == 137 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
            case "jpg", "jpeg" -> bytes.length > 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
            default -> false;
        };
        if (!valid || declaredMime != null && !declaredMime.equalsIgnoreCase(mime)
                && !declaredMime.equalsIgnoreCase("application/octet-stream"))
            throw new IllegalArgumentException("Dateiendung, MIME-Typ und Dateiinhalte passen nicht zusammen.");
        if (extension.equals("png") || extension.equals("jpg") || extension.equals("jpeg")) {
            try (var input = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException("Das Schnittbild ist ungültig.");
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || (long) width * height > 16_000_000L)
                        throw new IllegalArgumentException("Das Schnittbild ist ungültig oder zu groß.");
                    reader.read(0);
                } finally {
                    reader.dispose();
                }
            } catch (IOException exception) { throw new IllegalArgumentException("Das Schnittbild ist ungültig.", exception); }
        }
        return mime;
    }
    private static boolean starts(byte[] bytes, String prefix) {
        byte[] expected = prefix.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (bytes[i] != expected[i]) return false;
        return true;
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
