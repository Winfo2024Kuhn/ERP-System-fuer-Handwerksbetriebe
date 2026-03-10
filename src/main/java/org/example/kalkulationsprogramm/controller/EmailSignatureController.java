package org.example.kalkulationsprogramm.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.EmailSignatureImage;
import org.example.kalkulationsprogramm.service.EmailSignatureService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/email/signatures")
public class EmailSignatureController {

    private final EmailSignatureService service;

    @GetMapping
    public List<EmailSignature> list() {
        return service.list();
    }

    @GetMapping("/default")
    public ResponseEntity<EmailSignature> getDefault(@RequestParam(name = "frontendUserId", required = false) Long frontendUserId,
                                                     @RequestParam(name = "frontendUserName", required = false) String frontendUserName) {
        return service.getDefaultForFrontendUser(frontendUserId, frontendUserName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<EmailSignature> save(@RequestBody SaveSignatureRequest req) {
        EmailSignature sig = new EmailSignature();
        if (req.id != null) sig.setId(req.id);
        sig.setName(Optional.ofNullable(req.name).orElse("Signatur"));
        sig.setHtml(Optional.ofNullable(req.html).orElse(""));
        boolean defaultRequested = Boolean.TRUE.equals(req.defaultSignature);
        sig.setDefaultSignature(defaultRequested);
        EmailSignature saved = service.saveOrUpdate(sig);
        saved.setDefaultSignature(defaultRequested);
        return ResponseEntity.ok(saved);
    }

    @PostMapping(path = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<EmailSignatureImage>> upload(@PathVariable Long id,
                                                            @RequestPart("files") List<MultipartFile> files) {
        try {
            List<EmailSignatureImage> out = new java.util.ArrayList<>();
            for (MultipartFile f : files) {
                out.add(service.addImage(id, f));
            }
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Map<String, String>> preview(@PathVariable Long id, @RequestParam(name = "user", required = false) String user) {
        return service.list().stream().filter(s -> s.getId().equals(id)).findFirst()
                .map(s -> ResponseEntity.ok(Map.of("html", service.renderSignatureHtmlForPreview(s, user))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmailSignature> get(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/images/{imageId}")
    public ResponseEntity<byte[]> image(@PathVariable Long id, @PathVariable Long imageId) {
        Optional<EmailSignature> sig = service.list().stream().filter(s -> s.getId().equals(id)).findFirst();
        if (sig.isEmpty()) return ResponseEntity.notFound().build();
        Optional<EmailSignatureImage> img = sig.get().getImages().stream().filter(i -> i.getId().equals(imageId)).findFirst();
        if (img.isEmpty()) return ResponseEntity.notFound().build();
        var pathOpt = service.resolveImagePath(img.get());
        if (pathOpt.isEmpty()) return ResponseEntity.notFound().build();
        try {
            byte[] bytes = Files.readAllBytes(pathOpt.get());
            String ctype = Optional.ofNullable(img.get().getContentType()).orElse("application/octet-stream");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, ctype)
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        try {
            service.deleteImage(id, imageId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Data
    public static class SaveSignatureRequest {
        public Long id;
        public String name;
        public String html;
        public Boolean defaultSignature;
    }
}
