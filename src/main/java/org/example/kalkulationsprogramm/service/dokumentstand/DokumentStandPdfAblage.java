package org.example.kalkulationsprogramm.service.dokumentstand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Findet das beim Export gespeicherte PDF eines Stands im Upload-Ordner.
 *
 * <p>{@code AusgangsGeschaeftsDokumentService#speicherePdfFuerDokument} legt jeden Export als
 * eigene Datei ({@code <UUID>.pdf}) ab und überschreibt nichts – deshalb kann jeder Stand auf
 * „sein“ PDF zeigen. Nur reine Dateinamen auf {@code .pdf} direkt im Upload-Ordner werden
 * angenommen (kein Pfad, kein „..“), und das Ziel muss nach dem Normalisieren im Ordner liegen.</p>
 */
@Component
public class DokumentStandPdfAblage {

    private final Path ordner;

    public DokumentStandPdfAblage(@Value("${file.upload-dir}") String uploadDir) {
        this.ordner = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /** Die Datei, wenn der Name sicher ist und sie noch existiert. */
    public Optional<Path> finde(String dateiname) {
        if (dateiname == null || dateiname.isBlank() || dateiname.contains("/") || dateiname.contains("\\")
                || dateiname.contains("..") || !dateiname.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return Optional.empty();
        }
        Path ziel = ordner.resolve(dateiname).normalize();
        if (!ziel.startsWith(ordner) || !Files.isRegularFile(ziel)) {
            return Optional.empty();
        }
        return Optional.of(ziel);
    }
}
