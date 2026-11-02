package org.example.kalkulationsprogramm.service.datei;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Dateiablage in einem festen Unterordner von {@code ${file.upload-dir}}. Dateinamen erzeugt
 * ausschließlich das ERP (UUID + feste Endung); Namen von außen werden nur akzeptiert, wenn sie
 * genau diesem Muster entsprechen. Dadurch ist Path-Traversal ausgeschlossen, und der
 * Originalname einer hochgeladenen Datei landet nie im Dateisystem.
 */
public class UuidDateiablage {

    private static final int UUID_LAENGE = 36;

    private final Path basis;
    private final String endung;

    protected UuidDateiablage(String uploadDir, String unterordner, String endung) {
        this.basis = Path.of(uploadDir, unterordner).toAbsolutePath().normalize();
        this.endung = endung;
    }

    /** Speichert die Bytes und liefert den erzeugten Dateinamen. */
    public String speichere(byte[] inhalt) {
        String name = neuerName();
        try {
            Files.createDirectories(basis);
            Files.write(aufloesen(name), inhalt);
        } catch (IOException e) {
            throw new UncheckedIOException("Datei konnte nicht gespeichert werden", e);
        }
        return name;
    }

    /** Speichert den Datenstrom und liefert den erzeugten Dateinamen. */
    public String speichere(InputStream inhalt) {
        String name = neuerName();
        try (inhalt) {
            Files.createDirectories(basis);
            Files.copy(inhalt, aufloesen(name));
        } catch (IOException e) {
            throw new UncheckedIOException("Datei konnte nicht gespeichert werden", e);
        }
        return name;
    }

    /** Pfad einer gespeicherten Datei – wirft bei ungültigem Namen. */
    public Path pfad(String dateiName) {
        return aufloesen(dateiName);
    }

    public void loesche(String dateiName) {
        if (!istGueltigerName(dateiName, endung)) {
            return;
        }
        try {
            Files.deleteIfExists(aufloesen(dateiName));
        } catch (IOException e) {
            throw new UncheckedIOException("Datei konnte nicht gelöscht werden", e);
        }
    }

    /** Genau „UUID in Kleinbuchstaben + Endung“, sonst nichts. */
    public static boolean istGueltigerName(String name, String endung) {
        if (name == null || name.length() != UUID_LAENGE + endung.length() || !name.endsWith(endung)) {
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

    private String neuerName() {
        return UUID.randomUUID() + endung;
    }

    private Path aufloesen(String dateiName) {
        if (!istGueltigerName(dateiName, endung)) {
            throw new IllegalArgumentException("Ungültiger Dateiname");
        }
        Path ziel = basis.resolve(dateiName).normalize();
        if (!ziel.startsWith(basis)) {
            throw new IllegalArgumentException("Ungültiger Dateiname");
        }
        return ziel;
    }
}
