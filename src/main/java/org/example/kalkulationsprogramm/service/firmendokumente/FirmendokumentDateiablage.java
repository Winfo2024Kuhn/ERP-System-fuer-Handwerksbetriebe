package org.example.kalkulationsprogramm.service.firmendokumente;

import org.example.kalkulationsprogramm.service.datei.UuidDateiablage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ablage der Firmendokumente (alle Formate) unter {@code ${file.upload-dir}/firmendokumente/} als {@code <UUID>.bin}:
 * Die echte Endung steht nur im Originalnamen in der Datenbank, nie im Dateisystem. Dateien aus der Zeit, als nur
 * PDFs erlaubt waren, liegen als {@code <UUID>.pdf} und bleiben les- und löschbar.
 */
@Component
public class FirmendokumentDateiablage extends UuidDateiablage {

    public FirmendokumentDateiablage(@Value("${file.upload-dir}") String uploadDir) {
        super(uploadDir, "firmendokumente", ".bin", ".pdf");
    }
}
