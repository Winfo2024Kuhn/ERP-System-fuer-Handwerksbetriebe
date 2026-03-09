package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.BwaUploadDto;
import org.example.kalkulationsprogramm.service.BwaService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/bwa")
@RequiredArgsConstructor
public class BwaController {

    private final BwaService bwaService;

    @org.springframework.beans.factory.annotation.Value("${file.mail-attachment-dir}")
    private String mailAttachmentDir;

    @GetMapping("/jahre")
    public ResponseEntity<List<Integer>> getAvailableYears() {
        return ResponseEntity.ok(bwaService.findAvailableYears());
    }

    @GetMapping("/jahr/{jahr}")
    public ResponseEntity<List<BwaUploadDto>> getByJahr(@PathVariable Integer jahr) {
        return ResponseEntity.ok(bwaService.findByJahr(jahr));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BwaUploadDto> getById(@PathVariable Long id) {
        BwaUploadDto dto = bwaService.findById(id);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Resource> downloadPdf(@PathVariable Long id) {
        java.util.Optional<String> filenameOpt = bwaService.findStoredFilename(id);
        
        if (filenameOpt.isPresent()) {
            String filename = filenameOpt.get();
            try {
                Path path = Path.of(mailAttachmentDir).resolve(filename).normalize();
                Resource resource = new UrlResource(path.toUri());
                if (resource.exists()) {
                     return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                        .body(resource);
                }
            } catch (MalformedURLException e) {
                // ignore
            }
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bwaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
