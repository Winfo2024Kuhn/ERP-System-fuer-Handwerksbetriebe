package org.example.kalkulationsprogramm.service;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Prüft, speichert und teilt den gemeinsamen Datei-Ordner
 * (HiCAD-/Tenado-Zeichnungen, Excel, Filesharing) aus der Ersteinrichtung.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DateiOrdnerService {

    private static final int MAX_PFAD_LAENGE = 500;
    private static final String SHARE_NAME = "ERP-Dateien";

    private final SystemSettingsService settingsService;
    private final SmbShareRunner smbShareRunner;

    /** Prüft einen Pfad, legt den Ordner bei Bedarf an und testet Schreibrechte. */
    public TestResult pruefeOrdner(String pfad) {
        Path ordner = validiere(pfad);
        if (ordner == null) {
            return validierungsFehler(pfad);
        }
        try {
            Files.createDirectories(ordner);
            Path probe = ordner.resolve(".erp-schreibtest.tmp");
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
            return TestResult.success("Ordner gefunden und beschreibbar: " + ordner);
        } catch (Exception e) {
            log.info("Datei-Ordner-Prüfung fehlgeschlagen für {}: {}", ordner, e.getMessage());
            return TestResult.failure("Auf den Ordner kann nicht zugegriffen werden: " + e.getMessage()
                    + " – bitte Pfad und Berechtigungen prüfen.");
        }
    }

    /** Validiert und speichert Pfad + optionale Netzwerk-Adresse (UNC). */
    public TestResult speichereOrdner(String pfad, String networkUrl) {
        TestResult pruefung = pruefeOrdner(pfad);
        if (!pruefung.success()) {
            return pruefung;
        }
        String unc = networkUrl == null ? "" : networkUrl.trim();
        if (!unc.isBlank() && !unc.startsWith("\\\\")) {
            return TestResult.failure(
                    "Die Netzwerk-Adresse muss mit \\\\ beginnen (z. B. \\\\server\\zeichnungen).");
        }
        if (unc.contains("..")) {
            return TestResult.failure("Die Netzwerk-Adresse darf keine '..'-Bestandteile enthalten.");
        }
        settingsService.saveDateiOrdner(pfad.trim(), unc);
        return TestResult.success("Datei-Ordner gespeichert.");
    }

    /** Gibt den bereits GESPEICHERTEN Ordner frei – nie einen Request-Wert (keine Injection-Fläche). */
    public TestResult gebeOrdnerFrei() {
        String gespeichert = settingsService.getDateiOrdnerPfad();
        Path ordner = validiere(gespeichert);
        if (ordner == null) {
            return TestResult.failure("Bitte zuerst einen gültigen Ordner speichern.");
        }
        return smbShareRunner.freigeben(ordner, SHARE_NAME);
    }

    /** @return normalisierter absoluter Pfad oder null, wenn ungültig. */
    private Path validiere(String pfad) {
        if (pfad == null || pfad.isBlank() || pfad.length() > MAX_PFAD_LAENGE || pfad.contains("..")) {
            return null;
        }
        try {
            Path p = Path.of(pfad.trim());
            if (!p.isAbsolute()) {
                return null;
            }
            return p.toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private TestResult validierungsFehler(String pfad) {
        if (pfad == null || pfad.isBlank()) {
            return TestResult.failure("Bitte einen Ordner-Pfad eintragen.");
        }
        if (pfad.length() > MAX_PFAD_LAENGE) {
            return TestResult.failure("Der Pfad ist zu lang (maximal " + MAX_PFAD_LAENGE + " Zeichen).");
        }
        if (pfad.contains("..")) {
            return TestResult.failure("Der Pfad darf keine '..'-Bestandteile enthalten.");
        }
        return TestResult.failure(
                "Bitte einen vollständigen Pfad angeben (z. B. C:\\Zeichnungen, Z:\\Zeichnungen oder \\\\server\\ordner).");
    }
}
