package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentEingaben.fehler;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.web.multipart.MultipartFile;

/**
 * Lässt nur echte PDFs bis 50 MB durch. Entscheidend ist der Inhalt ({@code %PDF-} in den ersten
 * 1024 Bytes, wie es die PDF-Norm erlaubt), nicht Dateiendung oder Content-Type – die kann jeder fälschen.
 */
final class FirmendokumentPdfPruefung {

    static final long MAX_BYTES = 50L * 1024 * 1024;
    private static final int KOPF_BYTES = 1024;
    private static final byte[] PDF_KENNUNG = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private FirmendokumentPdfPruefung() {
    }

    static void pruefe(MultipartFile datei) {
        if (datei == null || datei.isEmpty()) {
            throw fehler("Bitte eine Datei auswählen.");
        }
        if (datei.getSize() > MAX_BYTES) {
            throw fehler("Die Datei ist größer als 50 MB. Bitte eine kleinere PDF wählen oder die Datei verkleinern.");
        }
        try (InputStream in = datei.getInputStream()) {
            if (!enthaeltKennung(in.readNBytes(KOPF_BYTES))) {
                throw fehler("Es können nur PDF-Dateien abgelegt werden.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Hochgeladene Datei konnte nicht gelesen werden", e);
        }
    }

    static boolean enthaeltKennung(byte[] kopf) {
        for (int start = 0; start + PDF_KENNUNG.length <= kopf.length; start++) {
            if (passtAn(kopf, start)) {
                return true;
            }
        }
        return false;
    }

    private static boolean passtAn(byte[] kopf, int start) {
        for (int i = 0; i < PDF_KENNUNG.length; i++) {
            if (kopf[start + i] != PDF_KENNUNG[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Originalname nur zur Anzeige: ohne Pfadanteile, ohne Steuer- und Sonderzeichen, höchstens 255 Zeichen.
     * Er landet nie im Dateisystem (dort liegt die Datei unter einer UUID).
     */
    static String anzeigeName(String original) {
        if (original == null || original.isBlank()) {
            return "dokument.pdf";
        }
        String ohnePfad = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        String sauber = ohnePfad.replaceAll("[:*?\"<>|\\p{Cntrl}]", "_").strip();
        if (sauber.isEmpty()) {
            return "dokument.pdf";
        }
        return sauber.length() > 255 ? sauber.substring(0, 255) : sauber;
    }
}
