package org.example.kalkulationsprogramm.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.OutOfOfficeSchedule;
import org.example.kalkulationsprogramm.repository.EmailSignatureRepository;
import org.example.kalkulationsprogramm.repository.OutOfOfficeScheduleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/email/outofoffice")
public class OutOfOfficeController {

    private final OutOfOfficeScheduleRepository repo;
    private final EmailSignatureRepository signatureRepo;

    @GetMapping
    public List<OutOfOfficeSchedule> list() {
        return repo.findAll();
    }

    @GetMapping("/active")
    public ResponseEntity<OutOfOfficeSchedule> active() {
        LocalDate now = LocalDate.now();
        return repo.findFirstByActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(now, now)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<OutOfOfficeSchedule> save(@RequestBody SaveOooRequest req) {
        EmailSignature sig = null;
        if (req.signatureId != null) {
            sig = signatureRepo.findById(req.signatureId).orElse(null);
        }
        OutOfOfficeSchedule s = req.id == null ? new OutOfOfficeSchedule()
                : repo.findById(req.id).orElse(new OutOfOfficeSchedule());
        s.setTitle(req.title);
        s.setStartAt(req.startDate);
        s.setEndAt(req.endDate);
        s.setActive(Boolean.TRUE.equals(req.active));
        s.setSubjectTemplate(req.subject);
        s.setBodyTemplate(req.message);
        s.setSignature(sig);
        return ResponseEntity.ok(repo.save(s));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @Data
    public static class SaveOooRequest {
        public Long id;
        public String title;
        public LocalDate startDate;
        public LocalDate endDate;
        public Long signatureId;
        public Boolean active;
        public String subject;
        public String message;
    }
}
