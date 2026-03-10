package org.example.kalkulationsprogramm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateAssetResponse;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateCopyRequest;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateDto;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateListDto;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateSaveRequest;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateUpdateRequest;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateSelectionRequest;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateRenameRequest;
import org.example.kalkulationsprogramm.service.FormularTemplateService;
import org.example.kalkulationsprogramm.service.FormularTemplateService.NamedTemplateData;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@Validated
@RequiredArgsConstructor
public class FormularTemplateController {

    private final FormularTemplateService formularTemplateService;

    @GetMapping("/api/formulare/template")
    public FormularTemplateDto getTemplate(@RequestParam(name = "preset", required = false) String preset) {
        boolean defaultRequested = preset != null && "default".equalsIgnoreCase(preset.trim());
        FormularTemplateDto dto = new FormularTemplateDto();
        dto.setHtml(
                defaultRequested ? formularTemplateService.defaultTemplate() : formularTemplateService.loadTemplate());
        dto.setLastModified(defaultRequested ? null : formularTemplateService.getLastModifiedIso());
        dto.setPlaceholders(formularTemplateService.getSupportedPlaceholders());
        dto.setAssignedDokumenttypen(List.of());
        dto.setAssignedUserIds(List.of());
        return dto;
    }

    @PutMapping("/api/formulare/template")
    public FormularTemplateDto saveTemplate(@Valid @RequestBody FormularTemplateUpdateRequest request) {
        String html = formularTemplateService.saveTemplate(request.getHtml());
        FormularTemplateDto dto = new FormularTemplateDto();
        dto.setHtml(html);
        dto.setLastModified(formularTemplateService.getLastModifiedIso());
        dto.setPlaceholders(formularTemplateService.getSupportedPlaceholders());
        dto.setAssignedDokumenttypen(List.of());
        return dto;
    }

    @PostMapping(path = "/api/formulare/template/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FormularTemplateAssetResponse> uploadLogo(@RequestPart("file") MultipartFile file) {
        String url = formularTemplateService.storeLogo(file);
        String filename = url.substring(url.lastIndexOf('/') + 1);
        return ResponseEntity.ok(new FormularTemplateAssetResponse(url, filename));
    }

    @GetMapping("/api/formulare/template/assets/{filename:.+}")
    public ResponseEntity<Resource> getAsset(@PathVariable String filename) {
        Resource resource = formularTemplateService.loadAsset(filename);
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        try {
            contentType = Optional.ofNullable(Files.probeContentType(resource.getFile().toPath()))
                    .orElse(contentType);
        } catch (IOException ignored) {
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    // Multi-template management endpoints

    @GetMapping("/api/formulare/templates")
    public ResponseEntity<List<FormularTemplateListDto>> listTemplates() {
        List<FormularTemplateListDto> templates = formularTemplateService.listNamedTemplates();
        return ResponseEntity.ok(templates);
    }

    @PostMapping("/api/formulare/templates")
    public ResponseEntity<FormularTemplateDto> saveNamedTemplate(
            @Valid @RequestBody FormularTemplateSaveRequest request) {
        NamedTemplateData saved = formularTemplateService.saveNamedTemplate(
                request.getName(),
                request.getHtml(),
                request.getAssignedDokumenttypen());
        return ResponseEntity.ok(toDto(saved));
    }

    @PostMapping("/api/formulare/templates/{name}/copy")
    public ResponseEntity<FormularTemplateDto> copyNamedTemplate(
            @PathVariable String name,
            @Valid @RequestBody FormularTemplateCopyRequest request) {
        NamedTemplateData copy = formularTemplateService.copyNamedTemplate(name, request.getNewName());
        return ResponseEntity.ok(toDto(copy));
    }

    @GetMapping("/api/formulare/templates/{name}")
    public ResponseEntity<FormularTemplateDto> loadNamedTemplate(@PathVariable String name) {
        NamedTemplateData template = formularTemplateService.loadNamedTemplate(name);
        return ResponseEntity.ok(toDto(template));
    }

    @PutMapping("/api/formulare/templates/{name}/rename")
    public ResponseEntity<FormularTemplateDto> renameTemplate(
            @PathVariable String name,
            @Valid @RequestBody FormularTemplateRenameRequest request) {
        NamedTemplateData renamed = formularTemplateService.renameNamedTemplate(name, request.getNewName());
        return ResponseEntity.ok(toDto(renamed));
    }

    @DeleteMapping("/api/formulare/templates/{name}")
    public ResponseEntity<Void> deleteNamedTemplate(@PathVariable String name) {
        formularTemplateService.deleteNamedTemplate(name);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/formulare/templates/selection")
    public ResponseEntity<String> getTemplateSelection(
            @RequestParam String dokumenttyp,
            @RequestParam(required = false) Long userId) {
        return formularTemplateService.getPreferredTemplateForDokumenttyp(dokumenttyp, userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/formulare/templates/selection")
    public ResponseEntity<Void> setTemplateSelection(
            @Valid @RequestBody FormularTemplateSelectionRequest request) {
        formularTemplateService.setPreferredTemplateForDokumenttyp(
                request.getDokumenttyp(),
                request.getTemplateName(),
                request.getUserIds() != null && !request.getUserIds().isEmpty()
                        ? request.getUserIds()
                        : (request.getUserId() != null ? java.util.List.of(request.getUserId()) : java.util.List.of()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/formulare/placeholders")
    public ResponseEntity<Map<String, String>> resolvePlaceholders(
            @RequestParam(required = false) Long projektId,
            @RequestParam(defaultValue = "false") boolean generateDoknr) {
        Map<String, String> map = formularTemplateService.resolvePlaceholders(projektId, generateDoknr);
        return ResponseEntity.ok(map);
    }

    @PostMapping("/api/formulare/dokumentnummer")
    public ResponseEntity<String> generateDokumentnummer() {
        return ResponseEntity.ok(formularTemplateService.generateDokumentnummer());
    }

    private FormularTemplateDto toDto(NamedTemplateData data) {
        FormularTemplateDto dto = new FormularTemplateDto();
        dto.setName(data.name());
        dto.setHtml(data.html());
        dto.setPlaceholders(formularTemplateService.getSupportedPlaceholders());
        dto.setAssignedDokumenttypen(data.assignedDokumenttypen());
        dto.setAssignedUserIds(data.assignedUserIds());
        dto.setCreated(data.created());
        dto.setModified(data.modified());
        dto.setLastModified(data.modified());
        return dto;
    }
}
