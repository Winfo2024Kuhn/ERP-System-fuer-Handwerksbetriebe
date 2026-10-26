package org.example.kalkulationsprogramm.controller;

import java.nio.charset.StandardCharsets;

import org.example.kalkulationsprogramm.dto.OpenFileLauncherBefehlDto;
import org.example.kalkulationsprogramm.dto.OpenFileLauncherPaketDto;
import org.example.kalkulationsprogramm.service.openfile.OpenFileLauncherService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * OpenFile Launcher: Einrichtung für Windows (Datei) und Mac (Terminal-Befehl mit Einmal-Link) –
 * erzeugen dürfen sie nur Admins in den Einstellungen. Ohne Anmeldung, nur aus dem eigenen Netz,
 * gehen die Update-Abfrage der installierten Launcher (Antwort unterschrieben, siehe Service) und
 * der Abruf eines Einmal-Links aus dem Terminal – beide laufen außerhalb des Browsers.
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

    @PostMapping("/api/openfile-launcher/einrichtung/mac/befehl")
    public ResponseEntity<OpenFileLauncherBefehlDto> einrichtungMacBefehl(@RequestParam String adresse,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.einrichtungMacBefehl(adresse, request.getServerName()));
    }

    /** Abruf des Einmal-Links aus dem Terminal-Befehl ({@code curl}, ohne Anmeldung). */
    @GetMapping("/openfile-launcher/mac/{schluessel}")
    public ResponseEntity<byte[]> einrichtungMac(@PathVariable String schluessel, HttpServletRequest request) {
        return service.einrichtungMac(schluessel, request.getServerName())
                .map(skript -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                        .header("X-Content-Type-Options", "nosniff")
                        .body(skript.getBytes(StandardCharsets.UTF_8)))
                .orElseGet(() -> ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build());
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
