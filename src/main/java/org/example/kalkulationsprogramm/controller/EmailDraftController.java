package org.example.kalkulationsprogramm.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.dto.Email.EmailDraftDto;
import org.example.kalkulationsprogramm.service.EmailDraftService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/emails/drafts")
@RequiredArgsConstructor
public class EmailDraftController {
    private final EmailDraftService service;

    @GetMapping
    public List<EmailDraftDto> getAllDrafts() { return service.list(); }

    @GetMapping("/count")
    public Map<String, Long> getDraftCount() { return Map.of("count", service.count()); }

    @GetMapping("/{id}")
    public EmailDraftDto getDraft(@PathVariable Long id) { return service.get(id); }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public EmailDraftDto createDraft(@RequestBody EmailDraftDto draft) { return service.save(null, draft, null); }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EmailDraftDto updateDraft(@PathVariable Long id, @RequestBody EmailDraftDto draft) {
        return service.save(id, draft, null);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmailDraftDto createWithAttachments(@RequestPart("dto") EmailDraftDto draft,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        return service.save(null, draft, attachments == null ? List.of() : attachments);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmailDraftDto updateWithAttachments(@PathVariable Long id, @RequestPart("dto") EmailDraftDto draft,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        return service.save(id, draft, attachments == null ? List.of() : attachments);
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @PathVariable Long attachmentId) {
        var file = service.download(id, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.data());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDraft(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> draftError(org.springframework.web.server.ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("message", error.getReason() == null ? "Entwurf konnte nicht verarbeitet werden." : error.getReason()));
    }

}
