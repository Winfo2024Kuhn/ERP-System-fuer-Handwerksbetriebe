package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.kalkulationsprogramm.domain.DokumentnummerCounter;
import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.FormularTemplateAssignment;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateListDto;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.DokumentnummerCounterRepository;
import org.example.kalkulationsprogramm.repository.FormularTemplateAssignmentRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FormularTemplateService {

    private static final long MAX_TEMPLATE_BYTES = 5_000_000; // 5 MB für Vorlagen mit Hintergrundbildern
    private static final List<String> SUPPORTED_PLACEHOLDERS = List.of(
            "{{DOKUMENTNUMMER}}",
            "{{PROJEKTNUMMER}}",
            "{{BAUVORHABEN}}",
            "{{KUNDENADRESSE}}",
            "{{KUNDENNAME}}",
            "{{KUNDENNUMMER}}",
            "{{DATUM}}",
            "{{SEITENZAHL}}",
            "{{DOKUMENTTYP}}");

    public record NamedTemplateData(String name, String html, List<String> assignedDokumenttypen,
            List<Long> assignedUserIds, String created, String modified) {
    }

    private final Path templateDir;
    private final Path assetDir;
    private final Path templateFile;
    private final Path templatesDir;
    private final ObjectMapper objectMapper;
    private final FormularTemplateAssignmentRepository assignmentRepository;
    private final DokumentnummerCounterRepository dokumentnummerCounterRepository;
    private final ProjektRepository projektRepository;
    private final KundeRepository kundeRepository;

    public FormularTemplateService(
            @Value("${file.form-template-dir:${user.dir}/uploads/formulare}") String templateDirProp,
            @Value("${file.form-template-filename:formular-template.html}") String fileNameProp,
            FormularTemplateAssignmentRepository assignmentRepository,
            DokumentnummerCounterRepository dokumentnummerCounterRepository, ProjektRepository projektRepository,
            KundeRepository kundeRepository) {
        this.templateDir = Path.of(templateDirProp).toAbsolutePath().normalize();
        this.assetDir = templateDir.resolve("assets");
        this.templatesDir = templateDir.resolve("templates");
        this.templateFile = templateDir.resolve(fileNameProp);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.assignmentRepository = assignmentRepository;
        this.dokumentnummerCounterRepository = dokumentnummerCounterRepository;
        this.projektRepository = projektRepository;
        this.kundeRepository = kundeRepository;
        try {
            Files.createDirectories(this.templateDir);
            Files.createDirectories(this.assetDir);
            Files.createDirectories(this.templatesDir);
        } catch (IOException e) {
            throw new IllegalStateException("Vorlagenverzeichnis konnte nicht erstellt werden.", e);
        }
    }

    public List<String> getSupportedPlaceholders() {
        return List.copyOf(SUPPORTED_PLACEHOLDERS);
    }

    private List<String> normalizeDokumenttypen(Object assignedDokumenttypen) {
        if (!(assignedDokumenttypen instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream().filter(Objects::nonNull).map(Object::toString).map(String::trim)
                .filter(s -> !s.isEmpty()).distinct().collect(Collectors.toList());
    }

    private Dokumenttyp resolveDokumenttypEntity(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dokumenttyp darf nicht leer sein.");
        }
        return Dokumenttyp.fromLabel(name.trim());
    }

    private List<String> loadAssignmentsFromDb(String templateName) {
        if (!StringUtils.hasText(templateName)) {
            return List.of();
        }
        return assignmentRepository.findByTemplateNameIgnoreCaseAndUserIsNull(templateName).stream()
                .map(FormularTemplateAssignment::getDokumenttyp)
                .filter(Objects::nonNull)
                .map(Dokumenttyp::getLabel)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> loadAssignedUserIds(String templateName) {
        if (!StringUtils.hasText(templateName)) {
            return List.of();
        }
        return assignmentRepository.findByTemplateNameIgnoreCase(templateName).stream()
                .map(FormularTemplateAssignment::getUser).filter(Objects::nonNull).map(FrontendUserProfile::getId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private List<String> resolveAssignments(String templateName, List<String> fallback) {
        List<String> fromDb = loadAssignmentsFromDb(templateName);
        if (!fromDb.isEmpty()) {
            return fromDb;
        }
        return fallback == null ? List.of() : fallback;
    }

    public Map<String, String> resolvePlaceholders(Long projektId, boolean generateDokumentnummer) {
        Map<String, String> map = new HashMap<>();
        if (generateDokumentnummer) {
            map.put("{{DOKUMENTNUMMER}}", generateDokumentnummer());
        }
        map.putAll(resolveProjektPlaceholders(projektId));
        return map;
    }

    @Transactional
    public String generateDokumentnummer() {
        String monthKey = DateTimeFormatter.ofPattern("yyyyMM").format(OffsetDateTime.now(ZoneId.systemDefault()));
        DokumentnummerCounter counter = dokumentnummerCounterRepository.findByMonthKey(monthKey).orElseGet(() -> {
            DokumentnummerCounter neu = new DokumentnummerCounter();
            neu.setMonthKey(monthKey);
            neu.setCounter(0L);
            return neu;
        });
        long next = counter.getCounter() + 1;
        counter.setCounter(next);
        dokumentnummerCounterRepository.save(counter);
        String month = monthKey.substring(4);
        return "%s/%05d".formatted(month, next);
    }

    public Map<String, String> resolveProjektPlaceholders(Long projektId) {
        if (projektId == null) {
            return Map.of();
        }
        Projekt projekt = projektRepository.findById(projektId).orElse(null);
        if (projekt == null) {
            return Map.of();
        }
        Map<String, String> map = new HashMap<>();
        map.put("{{PROJEKTNUMMER}}", Optional.ofNullable(projekt.getAuftragsnummer()).orElse(""));
        map.put("{{BAUVORHABEN}}", Optional.ofNullable(projekt.getBauvorhaben()).orElse(""));
        Kunde kunde = projekt.getKundenId();
        if (kunde == null) {
            map.put("{{KUNDENNAME}}", Optional.ofNullable(projekt.getKunde()).orElse(""));
            map.put("{{KUNDENNUMMER}}", Optional.ofNullable(projekt.getKundennummer()).orElse(""));
            String adresse = Stream.of(projekt.getKunde(), projekt.getStrasse(), projekt.getPlz(), projekt.getOrt())
                    .filter(Objects::nonNull).filter(s -> !s.isBlank()).collect(Collectors.joining("\n"));
            map.put("{{KUNDENADRESSE}}", adresse);
        } else {
            map.put("{{KUNDENNAME}}", Optional.ofNullable(kunde.getName()).orElse(""));
            map.put("{{KUNDENNUMMER}}", Optional.ofNullable(kunde.getKundennummer()).orElse(""));
            String adresse = Stream.of(kunde.getName(), kunde.getStrasse(), kunde.getPlz(), kunde.getOrt())
                    .filter(Objects::nonNull).filter(s -> !s.isBlank()).collect(Collectors.joining("\n"));
            map.put("{{KUNDENADRESSE}}", adresse);
        }
        return map;
    }

    public Optional<String> getPreferredTemplateForDokumenttyp(String dokumenttyp, Long userId) {
        if (!StringUtils.hasText(dokumenttyp)) {
            return Optional.empty();
        }
        Dokumenttyp typ = Dokumenttyp.fromLabel(dokumenttyp.trim());
        if (userId != null) {
            Optional<FormularTemplateAssignment> userHit = assignmentRepository
                    .findFirstByDokumenttypAndUser_IdOrderByIdDesc(typ, userId);
            if (userHit.isPresent()) {
                return Optional.ofNullable(userHit.get().getTemplateName());
            }
        }
        return assignmentRepository.findFirstByDokumenttypAndUserIsNullOrderByIdDesc(typ)
                .map(FormularTemplateAssignment::getTemplateName);
    }

    public void setPreferredTemplateForDokumenttyp(String dokumenttyp, String templateName, Long userId) {
        setPreferredTemplateForDokumenttyp(dokumenttyp, templateName, userId == null ? List.of() : List.of(userId));
    }

    @Transactional
    public void setPreferredTemplateForDokumenttyp(String dokumenttyp, String templateName, List<Long> userIds) {
        if (!StringUtils.hasText(dokumenttyp) || !StringUtils.hasText(templateName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Dokumenttyp und Template müssen angegeben werden.");
        }
        validateTemplateName(templateName);
        String filename = sanitizeFilename(templateName) + ".json";
        Path targetFile = templatesDir.resolve(filename).normalize();
        if (!targetFile.startsWith(templatesDir) || !Files.exists(targetFile)) {
            throw new NotFoundException("Vorlage nicht gefunden: " + templateName);
        }
        List<Long> targets = (userIds == null || userIds.isEmpty())
                ? Collections.singletonList(null)
                : userIds.stream().filter(Objects::nonNull).distinct().toList();

        List<FormularTemplateAssignment> existing = assignmentRepository.findByDokumenttyp(Dokumenttyp.fromLabel(dokumenttyp));
        Set<Long> targetSet = new HashSet<>(targets);

        // Entferne nicht mehr gewünschte Zuordnungen
        existing.stream()
                .filter(a -> {
                    Long uid = a.getUser() != null ? a.getUser().getId() : null;
                    return !targetSet.contains(uid);
                })
                .forEach(assignmentRepository::delete);

        // Füge fehlende Zuordnungen hinzu
        Set<Long> existingSet = existing.stream()
                .map(a -> a.getUser() != null ? a.getUser().getId() : null)
                .collect(Collectors.toSet());
        for (Long uid : targetSet) {
            if (existingSet.contains(uid)) {
                continue;
            }
            FormularTemplateAssignment entity = new FormularTemplateAssignment();
            entity.setDokumenttyp(resolveDokumenttypEntity(dokumenttyp));
            entity.setTemplateName(templateName.trim());
            if (uid != null) {
                FrontendUserProfile userRef = new FrontendUserProfile();
                userRef.setId(uid);
                entity.setUser(userRef);
            }
            assignmentRepository.save(entity);
        }
    }

    private void persistAssignments(String templateName, List<String> assignedDokumenttypen) {
        if (!StringUtils.hasText(templateName)) {
            return;
        }
        assignmentRepository.deleteByTemplateNameIgnoreCaseAndUserIsNull(templateName);
        if (assignedDokumenttypen == null || assignedDokumenttypen.isEmpty()) {
            return;
        }
        List<FormularTemplateAssignment> entities = assignedDokumenttypen.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .map(type -> {
                    FormularTemplateAssignment assignment = new FormularTemplateAssignment();
                    assignment.setTemplateName(templateName);
                    assignment.setDokumenttyp(resolveDokumenttypEntity(type));
                    assignment.setUser(null);
                    return assignment;
                }).toList();
        if (!entities.isEmpty()) {
            assignmentRepository.saveAll(entities);
        }
    }

    public String loadTemplate() {
        try {
            if (Files.exists(templateFile)) {
                return Files.readString(templateFile, StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // Fallback auf Standard, wenn Lesen fehlschlägt
        }
        return defaultTemplate();
    }

    public String saveTemplate(String html) {
        validateTemplate(html);
        try {
            Files.writeString(templateFile, html, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return html;
        } catch (IOException e) {
            throw new IllegalStateException("Vorlage konnte nicht gespeichert werden.", e);
        }
    }

    public String storeLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte ein Bild für das Logo auswählen.");
        }
        String contentType = Objects.requireNonNullElse(file.getContentType(), "").toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nur Bilddateien können als Logo verwendet werden.");
        }
        String original = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "logo.png"));
        String extension = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            extension = original.substring(dot);
        }
        String filename = "logo-" + UUID.randomUUID() + extension;
        Path target = assetDir.resolve(filename).normalize();
        if (!target.startsWith(assetDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Dateiname.");
        }
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Logo konnte nicht gespeichert werden.", e);
        }
        return "/api/formulare/template/assets/" + filename;
    }

    public Resource loadAsset(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset-Name fehlt.");
        }
        Path target = assetDir.resolve(filename).normalize();
        if (!target.startsWith(assetDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Pfad für Asset.");
        }
        try {
            Resource resource = new UrlResource(target.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
        } catch (MalformedURLException ignored) {
        }
        throw new NotFoundException("Logo oder Asset wurde nicht gefunden: " + filename);
    }

    public String getLastModifiedIso() {
        try {
            if (Files.exists(templateFile)) {
                OffsetDateTime odt = Files.getLastModifiedTime(templateFile).toInstant().atZone(ZoneId.systemDefault())
                        .toOffsetDateTime();
                return odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private void validateTemplate(String html) {
        if (html == null || html.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vorlageninhalt darf nicht leer sein.");
        }
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEMPLATE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Vorlage ist zu groß (max. 5 MB).");
        }
    }

    public String defaultTemplate() {
        return """
                <!DOCTYPE html>
                <html lang="de">
                <head>
                  <meta charset="UTF-8">
                  <style>
                    body { font-family: 'Inter', Arial, sans-serif; color: #111827; line-height: 1.6; margin: 0; padding: 32px; background: #f8fafc; }
                    .template-card { background: #ffffff; border-radius: 16px; padding: 32px; border: 1px solid #e5e7eb; box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08); }
                    .template-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; }
                    .logo-slot { width: 180px; height: 80px; border: 1px dashed #cbd5e1; display: flex; align-items: center; justify-content: center; color: #94a3b8; font-size: 13px; }
                    .meta { text-align: right; font-size: 14px; color: #475569; }
                    .meta .placeholder { color: #2563eb; font-weight: 600; }
                    .address { margin-top: 20px; padding: 16px; background: #f1f5f9; border-radius: 12px; }
                    .address .placeholder { color: #2563eb; font-weight: 600; }
                    h1 { font-size: 24px; margin: 0 0 12px 0; color: #0f172a; }
                  </style>
                </head>
                <body>
                  <div class="template-card">
                    <header class="template-header">
                      <div class="logo-slot">Firmenlogo hier platzieren</div>
                      <div class="meta">
                        <div><strong>Dokumentnummer:</strong> <span class="placeholder">{{DOKUMENTNUMMER}}</span></div>
                        <div><strong>Projektnummer:</strong> <span class="placeholder">{{PROJEKTNUMMER}}</span></div>
                      </div>
                    </header>
                    <section class="address">
                      <div><strong>Empfänger:</strong></div>
                      <div class="placeholder">{{KUNDENNAME}}</div>
                      <div class="placeholder">{{KUNDENADRESSE}}</div>
                    </section>
                    <main style="margin-top: 28px;">
                      <h1>Dokumenttitel</h1>
                      <p>Trage hier deinen Standardtext für Angebote, Rechnungen oder Auftragsbestätigungen ein.</p>
                    </main>
                  </div>
                </body>
                </html>
                """;
    }

    // Multi-template management methods

    public List<FormularTemplateListDto> listNamedTemplates() {
        try (Stream<Path> paths = Files.list(templatesDir)) {
            return paths.filter(p -> p.toString().endsWith(".json")).map(this::readTemplateMetadata)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(FormularTemplateListDto::getModified).reversed())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    @Transactional
    public NamedTemplateData copyNamedTemplate(String sourceName, String newName) {
        validateTemplateName(sourceName);
        validateTemplateName(newName);

        String sourceFilename = sanitizeFilename(sourceName) + ".json";
        Path sourceFile = templatesDir.resolve(sourceFilename).normalize();

        if (!sourceFile.startsWith(templatesDir) || !Files.exists(sourceFile)) {
            throw new NotFoundException("Quellvorlage nicht gefunden: " + sourceName);
        }

        try {
            Map<String, Object> sourceData = objectMapper.readValue(sourceFile.toFile(), Map.class);
            String html = (String) sourceData.getOrDefault("html", "");
            List<String> assignedTypes = resolveAssignments(sourceName,
                    normalizeDokumenttypen(sourceData.get("assignedDokumenttypen")));

            return saveNamedTemplate(newName, html, assignedTypes);
        } catch (IOException e) {
            throw new IllegalStateException("Vorlage konnte nicht kopiert werden.", e);
        }
    }

    @Transactional
    public NamedTemplateData saveNamedTemplate(String name, String html) {
        return saveNamedTemplate(name, html, null);
    }

    @Transactional
    public NamedTemplateData saveNamedTemplate(String name, String html, List<String> assignedDokumenttypen) {
        validateTemplate(html);
        validateTemplateName(name);
        List<String> normalizedDokumenttypen = normalizeDokumenttypen(assignedDokumenttypen);

        String filename = sanitizeFilename(name) + ".json";
        Path targetFile = templatesDir.resolve(filename).normalize();

        if (!targetFile.startsWith(templatesDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Vorlagenname.");
        }

        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());
            String isoTime = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            Map<String, Object> templateData = new HashMap<>();
            templateData.put("name", name);
            templateData.put("html", html);
            templateData.put("assignedDokumenttypen", normalizedDokumenttypen);

            if (Files.exists(targetFile)) {
                // Update existing - preserve created time
                Map<String, Object> existing = objectMapper.readValue(targetFile.toFile(), Map.class);
                templateData.put("created", existing.getOrDefault("created", isoTime));
                templateData.put("modified", isoTime);
            } else {
                // New template
                templateData.put("created", isoTime);
                templateData.put("modified", isoTime);
            }

            objectMapper.writeValue(targetFile.toFile(), templateData);
            persistAssignments(name, normalizedDokumenttypen);
            return new NamedTemplateData(name,
                    html,
                    normalizedDokumenttypen,
                    loadAssignedUserIds(name),
                    (String) templateData.get("created"),
                    (String) templateData.get("modified"));
        } catch (IOException e) {
            throw new IllegalStateException("Vorlage konnte nicht gespeichert werden.", e);
        }
    }

    public NamedTemplateData loadNamedTemplate(String name) {
        validateTemplateName(name);

        String filename = sanitizeFilename(name) + ".json";
        Path targetFile = templatesDir.resolve(filename).normalize();

        if (!targetFile.startsWith(templatesDir) || !Files.exists(targetFile)) {
            throw new NotFoundException("Vorlage nicht gefunden: " + name);
        }

        try {
            Map<String, Object> data = objectMapper.readValue(targetFile.toFile(), Map.class);
            String html = (String) data.getOrDefault("html", "");
            List<String> assigned = resolveAssignments(name, normalizeDokumenttypen(data.get("assignedDokumenttypen")));
            return new NamedTemplateData((String) data.getOrDefault("name", name), html, assigned,
                    loadAssignedUserIds(name), (String) data.get("created"), (String) data.get("modified"));
        } catch (IOException e) {
            throw new IllegalStateException("Vorlage konnte nicht geladen werden.", e);
        }
    }

    @Transactional
    public NamedTemplateData renameNamedTemplate(String oldName, String newName) {
        validateTemplateName(oldName);
        validateTemplateName(newName);
        if (oldName.equalsIgnoreCase(newName)) {
            return loadNamedTemplate(oldName);
        }

        String oldFilename = sanitizeFilename(oldName) + ".json";
        String newFilename = sanitizeFilename(newName) + ".json";

        Path oldFile = templatesDir.resolve(oldFilename).normalize();
        Path newFile = templatesDir.resolve(newFilename).normalize();

        if (!oldFile.startsWith(templatesDir) || !Files.exists(oldFile)) {
            throw new NotFoundException("Vorlage nicht gefunden: " + oldName);
        }
        if (!newFile.startsWith(templatesDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Vorlagenname.");
        }
        if (Files.exists(newFile)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vorlage mit diesem Namen existiert bereits.");
        }

        try {
            Map<String, Object> data = objectMapper.readValue(oldFile.toFile(), Map.class);
            String html = (String) data.getOrDefault("html", "");
            List<String> assigned = resolveAssignments(oldName,
                    normalizeDokumenttypen(data.get("assignedDokumenttypen")));

            data.put("name", newName);
            data.put("modified",
                    OffsetDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

            objectMapper.writeValue(newFile.toFile(), data);
            Files.deleteIfExists(oldFile);

            // Update assignments (global + user-spezifisch)
            List<FormularTemplateAssignment> existingAssignments = assignmentRepository
                    .findByTemplateNameIgnoreCase(oldName);
            if (!existingAssignments.isEmpty()) {
                existingAssignments.forEach(a -> a.setTemplateName(newName));
                assignmentRepository.saveAll(existingAssignments);
            } else {
                // Fallback: persist resolved assignments as global
                persistAssignments(newName, assigned);
            }

            return new NamedTemplateData(
                    newName,
                    html,
                    assigned,
                    loadAssignedUserIds(newName),
                    (String) data.get("created"),
                    (String) data.get("modified"));
        } catch (IOException e) {
            throw new IllegalStateException("Vorlage konnte nicht umbenannt werden.", e);
        }
    }

    @Transactional
    public void deleteNamedTemplate(String name) {
        validateTemplateName(name);

        String filename = sanitizeFilename(name) + ".json";
        Path targetFile = templatesDir.resolve(filename).normalize();

        if (!targetFile.startsWith(templatesDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Vorlagenname.");
        }

        try {
            if (Files.exists(targetFile)) {
                Files.delete(targetFile);
            }
            assignmentRepository.deleteByTemplateNameIgnoreCase(name);
        } catch (IOException e) {
            throw new IllegalStateException("Vorlage konnte nicht gelöscht werden.", e);
        }
    }

    private FormularTemplateListDto readTemplateMetadata(Path path) {
        try {
            Map<String, Object> data = objectMapper.readValue(path.toFile(), Map.class);
            String name = (String) data.getOrDefault("name", "Unbenannt");
            return new FormularTemplateListDto(name, (String) data.get("created"), (String) data.get("modified"),
                    resolveAssignments(name, normalizeDokumenttypen(data.get("assignedDokumenttypen"))));
        } catch (IOException e) {
            return null;
        }
    }

    private void validateTemplateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vorlagenname darf nicht leer sein.");
        }
        if (name.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vorlagenname ist zu lang.");
        }
    }

    private String sanitizeFilename(String name) {
        // Replace invalid characters with underscore
        return name.replaceAll("[^a-zA-Z0-9äöüÄÖÜß\\-_\\s]", "_").replaceAll("\\s+", "_").toLowerCase(Locale.ROOT);
    }
}
