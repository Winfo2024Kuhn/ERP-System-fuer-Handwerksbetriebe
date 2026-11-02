package org.example.kalkulationsprogramm.service.firmendokumente;

import org.example.kalkulationsprogramm.service.datei.UuidDateiablage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Ablage der Firmen-PDFs unter {@code ${file.upload-dir}/firmendokumente/} als {@code <UUID>.pdf}. */
@Component
public class FirmendokumentDateiablage extends UuidDateiablage {

    public FirmendokumentDateiablage(@Value("${file.upload-dir}") String uploadDir) {
        super(uploadDir, "firmendokumente", ".pdf");
    }
}
