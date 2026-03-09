package org.example.kalkulationsprogramm.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Controller für System-Utilities und Downloads.
 */
@RestController
@RequestMapping("/api/system")
public class SystemUtilityController {

    @Value("${app.downloads.path:downloads}")
    private String downloadsPath;

    /**
     * Liefert das OpenFile Launcher Setup als ZIP-Download.
     * Enthält sowohl die .bat (für einfaches Ausführen) als auch die .ps1 Datei.
     */
    @GetMapping("/openfile-launcher-setup")
    public ResponseEntity<byte[]> downloadOpenFileLauncherSetup() throws IOException {
        Path downloadsDir = Path.of(downloadsPath);
        Path batFile = downloadsDir.resolve("OpenFileLauncher-Install.bat");
        Path ps1File = downloadsDir.resolve("OpenFileLauncher-Setup.ps1");
        
        // Prüfe ob Dateien existieren
        if (!Files.exists(batFile) || !Files.exists(ps1File)) {
            return ResponseEntity.notFound().build();
        }
        
        // Erstelle ZIP im Speicher
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Füge .bat Datei hinzu
            addToZip(zos, batFile, "OpenFileLauncher-Install.bat");
            // Füge .ps1 Datei hinzu
            addToZip(zos, ps1File, "OpenFileLauncher-Setup.ps1");
        }
        
        byte[] zipContent = baos.toByteArray();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "OpenFileLauncher-Setup.zip");
        headers.setContentLength(zipContent.length);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(zipContent);
    }
    
    private void addToZip(ZipOutputStream zos, Path file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        Files.copy(file, zos);
        zos.closeEntry();
    }
}
