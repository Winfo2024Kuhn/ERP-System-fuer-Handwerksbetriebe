package org.example.kalkulationsprogramm.service.telefon;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Ablage der Aufnahmen unter {@code ${file.upload-dir}/sprachnachrichten/}.
 * Dateinamen erzeugt ausschließlich das ERP (UUID + ".wav"); Namen von außen
 * werden nur akzeptiert, wenn sie genau diesem Muster entsprechen.
 */
@Component
public class SprachnachrichtDateiablage {

    private static final String ENDUNG = ".wav";
    private static final int UUID_LAENGE = 36;

    private final Path basis;

    public SprachnachrichtDateiablage(@Value("${file.upload-dir}") String uploadDir) {
        this.basis = Path.of(uploadDir, "sprachnachrichten").toAbsolutePath().normalize();
    }

    /** Speichert die Aufnahme und liefert den erzeugten Dateinamen. */
    public String speichere(byte[] wav) {
        String name = UUID.randomUUID() + ENDUNG;
        Path ziel = aufloesen(name);
        try {
            Files.createDirectories(basis);
            Files.write(ziel, wav);
        } catch (IOException e) {
            throw new UncheckedIOException("Aufnahme konnte nicht gespeichert werden", e);
        }
        return name;
    }

    /** Pfad einer gespeicherten Aufnahme – wirft bei ungültigem Namen. */
    public Path pfad(String dateiName) {
        return aufloesen(dateiName);
    }

    public void loesche(String dateiName) {
        if (!istGueltigerName(dateiName)) {
            return;
        }
        try {
            Files.deleteIfExists(aufloesen(dateiName));
        } catch (IOException e) {
            throw new UncheckedIOException("Aufnahme konnte nicht gelöscht werden", e);
        }
    }

    static boolean istGueltigerName(String name) {
        if (name == null || name.length() != UUID_LAENGE + ENDUNG.length() || !name.endsWith(ENDUNG)) {
            return false;
        }
        for (int i = 0; i < UUID_LAENGE; i++) {
            char c = name.charAt(i);
            boolean bindestrich = i == 8 || i == 13 || i == 18 || i == 23;
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (bindestrich ? c != '-' : !hex) {
                return false;
            }
        }
        return true;
    }

    private Path aufloesen(String dateiName) {
        if (!istGueltigerName(dateiName)) {
            throw new IllegalArgumentException("Ungültiger Dateiname");
        }
        Path ziel = basis.resolve(dateiName).normalize();
        if (!ziel.startsWith(basis)) {
            throw new IllegalArgumentException("Ungültiger Dateiname");
        }
        return ziel;
    }
}
