package org.example.kalkulationsprogramm.controller;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.dto.Email.EmailDraftDto;
import org.example.kalkulationsprogramm.service.EmailDraftService;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
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
    private final PostfachSichtbarkeitService sichtbarkeitService;

    /**
     * Entwürfe zu einer Antwort oder Weiterleitung enthalten meist den zitierten Text der Mail.
     * Wer die Mail nicht öffnen darf, sieht auch den Entwurf nicht. Ebenso Entwürfe einer neuen
     * Mail aus einem Postfach, das er nicht sieht (z. B. chef@). Entwürfe ohne Bezug bleiben frei.
     */
    @GetMapping
    public List<EmailDraftDto> getAllDrafts(Authentication authentication) {
        List<EmailDraftDto> drafts = service.list();
        Sperren sperren = sperren(drafts, authentication);
        return sperren.keine() ? drafts : drafts.stream().filter(d -> !sperren.betrifft(d)).toList();
    }

    @GetMapping("/count")
    public Map<String, Long> getDraftCount(Authentication authentication) {
        List<EmailDraftDto> drafts = service.list();
        Sperren sperren = sperren(drafts, authentication);
        long anzahl = sperren.keine() ? drafts.size() : drafts.stream().filter(d -> !sperren.betrifft(d)).count();
        return Map.of("count", anzahl);
    }

    @GetMapping("/{id}")
    public EmailDraftDto getDraft(@PathVariable Long id, Authentication authentication) {
        return pruefeZugriff(service.get(id), authentication);
    }

    /** Entwurf nur, wenn der Benutzer Mail und Postfach dazu sieht – sonst 404 wie „gibt es nicht“. */
    private EmailDraftDto pruefeZugriff(EmailDraftDto draft, Authentication authentication) {
        if (sperren(List.of(draft), authentication).betrifft(draft)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entwurf nicht gefunden.");
        }
        return draft;
    }

    /** Mails und Postfächer, deren Entwürfe der Benutzer nicht sehen darf. */
    private record Sperren(Set<Long> mails, Set<Long> postfaecher) {

        Sperren {
            mails = mails == null ? Set.of() : mails;
            postfaecher = postfaecher == null ? Set.of() : postfaecher;
        }

        boolean keine() {
            return mails.isEmpty() && postfaecher.isEmpty();
        }

        boolean betrifft(EmailDraftDto draft) {
            return (draft.replyEmailId() != null && mails.contains(draft.replyEmailId()))
                    || (draft.weitergeleitetVonEmailId() != null && mails.contains(draft.weitergeleitetVonEmailId()))
                    || (draft.postfachId() != null && postfaecher.contains(draft.postfachId()));
        }
    }

    private Sperren sperren(List<EmailDraftDto> drafts, Authentication authentication) {
        List<Long> mails = new ArrayList<>();
        List<Long> postfaecher = new ArrayList<>();
        for (EmailDraftDto d : drafts) {
            if (d.replyEmailId() != null) mails.add(d.replyEmailId());
            if (d.weitergeleitetVonEmailId() != null) mails.add(d.weitergeleitetVonEmailId());
            if (d.postfachId() != null) postfaecher.add(d.postfachId());
        }
        if (mails.isEmpty() && postfaecher.isEmpty()) return new Sperren(Set.of(), Set.of());
        var sicht = sichtbarkeitService.fuer(authentication);
        return new Sperren(
                mails.isEmpty() ? Set.of() : sichtbarkeitService.nichtLesbareEmailIds(mails, sicht),
                postfaecher.isEmpty() ? Set.of() : sichtbarkeitService.nichtSichtbarePostfachIds(postfaecher, sicht));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public EmailDraftDto createDraft(@RequestBody EmailDraftDto draft) { return service.save(null, draft, null); }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EmailDraftDto updateDraft(@PathVariable Long id, @RequestBody EmailDraftDto draft,
            Authentication authentication) {
        pruefeZugriff(service.get(id), authentication);
        return service.save(id, draft, null);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmailDraftDto createWithAttachments(@RequestPart("dto") EmailDraftDto draft,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        return service.save(null, draft, attachments == null ? List.of() : attachments);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmailDraftDto updateWithAttachments(@PathVariable Long id, @RequestPart("dto") EmailDraftDto draft,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments,
            Authentication authentication) {
        pruefeZugriff(service.get(id), authentication);
        return service.save(id, draft, attachments == null ? List.of() : attachments);
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @PathVariable Long attachmentId,
            Authentication authentication) {
        pruefeZugriff(service.get(id), authentication);
        var file = service.download(id, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.data());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDraft(@PathVariable Long id, Authentication authentication) {
        pruefeZugriff(service.get(id), authentication);
        service.delete(id);
        return ResponseEntity.ok().build();
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> draftError(org.springframework.web.server.ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("message", error.getReason() == null ? "Entwurf konnte nicht verarbeitet werden." : error.getReason()));
    }

}
