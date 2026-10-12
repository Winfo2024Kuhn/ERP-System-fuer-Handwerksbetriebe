package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.example.kalkulationsprogramm.domain.LieferantBild;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

/**
 * Ablage der Dateien, die direkt an einem Lieferanten hängen: manuell hochgeladene Dokumente
 * unter {@code lieferanten/<lieferantId>/} und Bilder (auch Reklamationsfotos) unter
 * {@code lieferanten/<lieferantId>/bilder/} – beides im Upload-Ordner {@code upload.path}.
 *
 * <p>Das ist derselbe Ordner, in den {@link LieferantDokumentService} beim Upload speichert und
 * in dem {@link LieferantDokumentDateiService} Dokumente sucht. Wird {@code upload.path}
 * umgestellt, ziehen Speichern und Suchen gemeinsam um.
 *
 * <p>Path Traversal: Namen von außen werden auf den reinen Dateinamen gekürzt und bereinigt.
 * Jeder Pfad wird normalisiert und muss danach noch in seinem Ordner liegen – auch ein Name aus
 * der Datenbank führt so nie aus der Ablage heraus.
 */
@Service
@Slf4j
public class LieferantDateiablage {

    private static final String STANDARD_UPLOAD_PATH = "uploads";
    private static final String BILDER = "bilder";
    private static final String ERSATZNAME_DOKUMENT = "dokument";
    private static final String ERSATZNAME_BILD = "foto";

    /** {@code <upload.path>/lieferanten}, absolut und normalisiert. */
    private final Path lieferantenOrdner;

    public LieferantDateiablage(@Value("${upload.path:uploads}") String uploadPath) {
        String basis = uploadPath != null && !uploadPath.isBlank() ? uploadPath : STANDARD_UPLOAD_PATH;
        this.lieferantenOrdner = Path.of(basis, "lieferanten").toAbsolutePath().normalize();
    }

    /**
     * Was beim Speichern herauskam: der bereinigte Originalname für die Anzeige und der Name,
     * unter dem die Datei auf der Platte liegt.
     */
    public record GespeicherteDatei(String originalDateiname, String gespeicherterDateiname) {
    }

    /** Speichert ein Dokument als {@code lieferanten/<lieferantId>/<UUID>_<Name>}. */
    public GespeicherteDatei speichereDokument(Long lieferantId, MultipartFile datei) throws IOException {
        String original = bereinigterDateiname(datei.getOriginalFilename(), ERSATZNAME_DOKUMENT);
        return speichere(lieferantOrdner(lieferantId), datei, original, UUID.randomUUID() + "_" + original);
    }

    /** Speichert ein Bild als {@code lieferanten/<lieferantId>/bilder/<UUID>_<Name>}. */
    public GespeicherteDatei speichereBild(Long lieferantId, MultipartFile datei) throws IOException {
        return speichereBild(lieferantId, datei, "");
    }

    /**
     * Wie {@link #speichereBild(Long, MultipartFile)}, hängt aber {@code endung} an den
     * gespeicherten Namen, wenn der Originalname nicht schon darauf endet. Für Fotos vom Handy:
     * Deren Endung kommt aus dem geprüften Bildtyp, nicht aus dem Namen des Absenders.
     */
    public GespeicherteDatei speichereBild(Long lieferantId, MultipartFile datei, String endung) throws IOException {
        String original = bereinigterDateiname(datei.getOriginalFilename(), ERSATZNAME_BILD);
        String anhang = original.toLowerCase(Locale.ROOT).endsWith(endung) ? "" : endung;
        return speichere(bilderOrdner(lieferantId), datei, original, UUID.randomUUID() + "_" + original + anhang);
    }

    /**
     * Die Datei eines Lieferantenbildes – leer, wenn es sie nicht gibt oder der gespeicherte
     * Name aus dem Bilder-Ordner des Lieferanten herausführt.
     *
     * <p>Der Name stammt aus der Datenbank, ist damit aber nicht automatisch harmlos: Ältere
     * Uploads wurden nur mit {@code StringUtils.cleanPath} bereinigt, und das behält führende
     * {@code ../}. Deshalb zählt nur der reine Dateiname.
     */
    public Optional<Path> findeBild(LieferantBild bild) {
        if (bild == null || bild.getLieferant() == null || bild.getLieferant().getId() == null) {
            return Optional.empty();
        }
        return reinerName(bild.getGespeicherterDateiname())
                .flatMap(name -> imOrdner(bilderOrdner(bild.getLieferant().getId()), name))
                .filter(Files::isRegularFile);
    }

    /**
     * Löscht die Datei eines Lieferantenbildes, falls es sie gibt. Ein Fehler wird nur
     * protokolliert, damit der Datensatz trotzdem gelöscht werden kann.
     */
    public void loescheBild(LieferantBild bild) {
        findeBild(bild).ifPresent(pfad -> {
            try {
                Files.deleteIfExists(pfad);
            } catch (IOException e) {
                // Nur die Fehlerart: Der Pfad enthält den Originalnamen vom Handy (DSGVO).
                log.warn("Bild eines Lieferanten konnte nicht gelöscht werden: {}", e.getClass().getSimpleName());
            }
        });
    }

    /**
     * Macht aus dem Dateinamen des Browsers einen reinen Dateinamen ohne Ordner-Anteil
     * ({@code ../../x.pdf} und {@code ..\..\x.pdf} werden zu {@code x.pdf}) und ersetzt Zeichen,
     * die in einem Dateinamen nichts zu suchen haben. Bleibt kein brauchbarer Name übrig, gilt
     * {@code ersatz}.
     */
    static String bereinigterDateiname(String rohName, String ersatz) {
        if (rohName == null) {
            return ersatz;
        }
        // Backslash zählt überall als Ordnertrenner, und was Windows in Pfaden verbietet, wird
        // vorher ersetzt – so kommt auf jedem Server derselbe Name heraus. Steuer- und
        // unsichtbare Formatzeichen (z. B. Schreibrichtungs-Umkehr) fliegen mit raus.
        String einheitlich = rohName.trim().replace('\\', '/')
                .replaceAll("[:*?\"<>|\\p{Cntrl}\\p{Cf}]", "_");
        return reinerName(einheitlich)
                .map(name -> name.replaceAll("[\\\\/:*?\"<>|]", "_"))
                .orElse(ersatz);
    }

    /** Der letzte Namensteil – leer bei leeren oder ungültigen Namen und bei {@code .}/{@code ..}. */
    private static Optional<String> reinerName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            Path nurName = Path.of(name).getFileName();
            String text = nurName == null ? "" : nurName.toString();
            return text.isBlank() || text.equals(".") || text.equals("..") ? Optional.empty() : Optional.of(text);
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private GespeicherteDatei speichere(Path ordner, MultipartFile datei, String original, String gespeichert)
            throws IOException {
        Path ziel = imOrdner(ordner, gespeichert)
                .orElseThrow(() -> new IOException("Ungültiger Dateiname"));
        Files.createDirectories(ordner);
        datei.transferTo(ziel);
        return new GespeicherteDatei(original, gespeichert);
    }

    private Path lieferantOrdner(Long lieferantId) {
        Objects.requireNonNull(lieferantId, "lieferantId");
        return lieferantenOrdner.resolve(lieferantId.toString());
    }

    private Path bilderOrdner(Long lieferantId) {
        return lieferantOrdner(lieferantId).resolve(BILDER);
    }

    /** {@code name} in {@code ordner} – nur, wenn der normalisierte Pfad danach noch darin liegt. */
    private static Optional<Path> imOrdner(Path ordner, String name) {
        Path pfad = ordner.resolve(name).normalize();
        return pfad.startsWith(ordner) ? Optional.of(pfad) : Optional.empty();
    }
}
