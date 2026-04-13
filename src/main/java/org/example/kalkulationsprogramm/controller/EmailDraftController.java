package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.EmailDraft;
import org.example.kalkulationsprogramm.repository.EmailDraftRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/emails/drafts")
@RequiredArgsConstructor
public class EmailDraftController {

    private final EmailDraftRepository draftRepository;

    @GetMapping
    public List<EmailDraft> getAllDrafts() {
        return draftRepository.findAllByOrderByUpdatedAtDesc();
    }

    @GetMapping("/count")
    public Map<String, Long> getDraftCount() {
        return Map.of("count", draftRepository.count());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<EmailDraft> createDraft(@RequestBody EmailDraft draft) {
        draft.setId(null); // enforce new
        EmailDraft saved = draftRepository.save(draft);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<EmailDraft> updateDraft(@PathVariable Long id, @RequestBody EmailDraft draft) {
        EmailDraft existing = draftRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        existing.setRecipient(draft.getRecipient());
        existing.setCc(draft.getCc());
        existing.setSubject(draft.getSubject());
        existing.setBody(draft.getBody());
        existing.setFromAddress(draft.getFromAddress());
        existing.setReplyEmailId(draft.getReplyEmailId());
        existing.setProjektId(draft.getProjektId());
        existing.setAnfrageId(draft.getAnfrageId());
        return ResponseEntity.ok(draftRepository.save(existing));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteDraft(@PathVariable Long id) {
        if (!draftRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        draftRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
