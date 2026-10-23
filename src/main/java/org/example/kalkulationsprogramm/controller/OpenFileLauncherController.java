package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.dto.OpenFileLauncherPaketDto;
import org.example.kalkulationsprogramm.service.openfile.OpenFileLauncherService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * OpenFile Launcher: Einrichtungs-Dateien zum Herunterladen (nur Admins, Einstellungen) und die
 * Update-Abfrage der installierten Launcher (ohne Anmeldung, nur aus dem eigenen Netz – der
 * Launcher läuft außerhalb des Browsers; die Antwort ist unterschrieben, siehe Service).
 */
@RestController
@RequiredArgsConstructor
public class OpenFileLauncherController {

    private final OpenFileLauncherService service;

    @GetMapping("/api/openfile-launcher/einrichtung/windows")
    public ResponseEntity<byte[]> einrichtungWindows(@RequestParam String adresse, HttpServletRequest request) {
        return datei(service.einrichtungWindows(adresse, request.getServerName()),
                "ERP-Dateien-oeffnen-Windows.bat", MediaType.APPLICATION_OCTET_STREAM);
    }

    @GetMapping("/api/openfile-launcher/einrichtung/mac")
    public ResponseEntity<byte[]> einrichtungMac(@RequestParam String adresse, HttpServletRequest request) {
        return datei(service.einrichtungMac(adresse, request.getServerName()),
                "ERP-Dateien-oeffnen-Mac.zip", MediaType.parseMediaType("application/zip"));
    }

    @GetMapping("/openfile-launcher/aktuell")
    public ResponseEntity<OpenFileLauncherPaketDto> aktuell(@RequestParam String system,
            @RequestParam(required = false) String version, @RequestParam String adresse,
            HttpServletRequest request) {
        return service.aktuellesPaket(system, version, adresse, request.getServerName())
                .map(paket -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .contentType(MediaType.APPLICATION_JSON).body(paket))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    private static ResponseEntity<byte[]> datei(byte[] inhalt, String name, MediaType typ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(typ)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(inhalt);
    }
}
