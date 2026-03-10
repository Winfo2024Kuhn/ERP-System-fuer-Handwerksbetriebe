package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.GaebImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class GaebImportController {

    private static final Logger log = LoggerFactory.getLogger(GaebImportController.class);

    private final GaebImportService gaebImportService;

    @PostMapping("/gaeb")
    public ResponseEntity<List<Map<String, Object>>> importGaeb(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            List<Map<String, Object>> blocks = gaebImportService.parseGaebXml(file.getInputStream());
            return ResponseEntity.ok(blocks);
        } catch (Exception e) {
            log.error("Fehler beim GAEB-Import", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
