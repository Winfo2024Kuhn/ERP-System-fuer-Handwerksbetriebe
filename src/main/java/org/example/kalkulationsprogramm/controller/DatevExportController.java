package org.example.kalkulationsprogramm.controller;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.DatevDto.*;
import org.example.kalkulationsprogramm.service.DatevExportService;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/zeitverwaltung/monatsabschluesse/datev")
public class DatevExportController {
 private final DatevExportService service;
 @PostMapping("/vorpruefung") public Vorpruefung pruefen(@RequestBody ExportRequest request,Authentication auth){return service.pruefen(request,auth);}
 @PostMapping("/export") public ResponseEntity<byte[]> exportieren(@RequestBody ExportRequest request,Authentication auth){var file=service.exportieren(request,auth);
  return ResponseEntity.ok().header("Content-Disposition",org.springframework.http.ContentDisposition.attachment().filename(file.dateiname()).build().toString())
   .header("Cache-Control","no-store").contentType(org.springframework.http.MediaType.parseMediaType(file.contentType()))
   .contentLength(file.inhalt().length).body(file.inhalt());}
}
