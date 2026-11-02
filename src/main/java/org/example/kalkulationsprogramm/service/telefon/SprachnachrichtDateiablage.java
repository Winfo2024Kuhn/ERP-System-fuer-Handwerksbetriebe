package org.example.kalkulationsprogramm.service.telefon;

import org.example.kalkulationsprogramm.service.datei.UuidDateiablage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ablage der Aufnahmen unter {@code ${file.upload-dir}/sprachnachrichten/}.
 * Dateinamen erzeugt ausschließlich das ERP (UUID + ".wav"); Namen von außen
 * werden nur akzeptiert, wenn sie genau diesem Muster entsprechen.
 */
@Component
public class SprachnachrichtDateiablage extends UuidDateiablage {

    private static final String ENDUNG = ".wav";

    public SprachnachrichtDateiablage(@Value("${file.upload-dir}") String uploadDir) {
        super(uploadDir, "sprachnachrichten", ENDUNG);
    }
}
