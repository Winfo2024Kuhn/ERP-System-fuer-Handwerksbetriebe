package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * Ablage der Dateien, die direkt an einem Lieferanten hängen: hochgeladene Dokumente unter
 * {@code lieferanten/<lieferantId>/} und Bilder (auch Reklamationsfotos) unter
 * {@code lieferanten/<lieferantId>/bilder/} – beides im Upload-Ordner {@code upload.path}.
 * Alle Stellen, die dort speichern oder löschen, gehen über diesen Service.
 *
 * <p>{@link LieferantDokumentDateiService} sucht Dokumente in demselben Ordner. Wird
 * {@code upload.path} umgestellt, ziehen Speichern und Suchen gemeinsam um – die vorhandenen
 * Ordner {@code lieferanten/} müssen dann mit umziehen, sonst fehlen ältere Bilder.
 *
 * <p>Path Traversal: Namen von außen werden auf den reinen Dateinamen gekürzt und bereinigt.
 * Jeder Pfad wird normalisiert und muss danach noch in seinem Ordner liegen – auch ein Name aus
 * der Datenbank führt so nie aus der Ablage heraus.
 */
@Service
@Slf4j
public class LieferantDateiablage {

    /**
     * Höchstlänge des Originalnamens in Bytes (UTF-8). Zusammen mit dem UUID-Präfix (37 Zeichen)
     * bleibt der gespeicherte Name unter 255 Bytes – der Grenze von ext4/NTFS.
     */
    static final int MAX_DATEINAME_BYTES = 200;

    /** Längere „Endungen“ sind keine – dann wird der Name ohne Rücksicht auf einen Punkt gekürzt. */
    private static final int MAX_ENDUNG_ZEICHEN = 16;

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
     * Was beim Speichern herauskam: der bereinigte Originalname für die Anzeige, der Name, unter
     * dem die Datei auf der Platte liegt, und ihr Pfad (z. B. für die KI-Analyse).
     */
    public record GespeicherteDatei(String originalDateiname, String gespeicherterDateiname, Path pfad) {
    }

    /** Speichert ein Dokument als {@code lieferanten/<lieferantId>/<UUID>_<Name>}. */
    public GespeicherteDatei speichereDokument(Long lieferantId, MultipartFile datei) throws IOException {
        return speichereDokument(lieferantId, datei, datei.getOriginalFilename());
    }

    /**
     * Wie {@link #speichereDokument(Long, MultipartFile)}, aber unter einem Namen, den der Aufrufer
     * schon geprüft hat (z. B. gegen erlaubte Endungen). Bereinigt und gekürzt wird er trotzdem.
     */
    public GespeicherteDatei speichereDokument(Long lieferantId, MultipartFile datei, String originalDateiname)
            throws IOException {
        String original = bereinigterDateiname(originalDateiname, ERSATZNAME_DOKUMENT);
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
     * Löscht die Datei eines Dokuments aus {@code lieferanten/<lieferantId>/}, falls es sie gibt.
     * Ein Name, der aus dem Ordner heraus- oder in einen Unterordner (etwa {@code bilder/}) führt,
     * löscht nichts. Ein Fehler wird nur protokolliert.
     */
    public void loescheDokument(Long lieferantId, String gespeicherterDateiname) {
        if (lieferantId == null || gespeicherterDateiname == null || gespeicherterDateiname.isBlank()) {
            return;
        }
        direktImOrdner(lieferantOrdner(lieferantId), gespeicherterDateiname)
                .filter(Files::isRegularFile)
                .ifPresent(LieferantDateiablage::loescheStill);
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
        findeBild(bild).ifPresent(LieferantDateiablage::loescheStill);
    }

    /**
     * Macht aus dem Dateinamen des Browsers einen reinen Dateinamen ohne Ordner-Anteil
     * ({@code ../../x.pdf} und {@code ..\..\x.pdf} werden zu {@code x.pdf}) und ersetzt Zeichen,
     * die in einem Dateinamen nichts zu suchen haben. Zu lange Namen werden direkt vor der Endung
     * auf {@link #MAX_DATEINAME_BYTES} Bytes gekürzt. Bleibt kein brauchbarer Name übrig, gilt
     * {@code ersatz} (darf {@code null} sein, wenn der Aufrufer dann ablehnen will).
     */
    public static String bereinigterDateiname(String rohName, String ersatz) {
        if (rohName == null) {
            return ersatz;
        }
        // Backslash zählt überall als Ordnertrenner, und was Windows in Pfaden verbietet, wird
        // vorher ersetzt – so kommt auf jedem Server derselbe Name heraus. Steuer- und
        // unsichtbare Formatzeichen (z. B. Schreibrichtungs-Umkehr) fliegen mit raus.
        String einheitlich = rohName.trim().replace('\\', '/')
                .replaceAll("[:*?\"<>|\\p{Cntrl}\\p{Cf}]", "_");
        return reinerName(einheitlich)
                .map(LieferantDateiablage::kuerzeAufHoechstlaenge)
                .orElse(ersatz);
    }

    /**
     * Kürzt den Namensteil so weit, dass Name und Endung zusammen höchstens
     * {@link #MAX_DATEINAME_BYTES} Bytes (UTF-8) lang sind. Die Endung bleibt.
     */
    private static String kuerzeAufHoechstlaenge(String name) {
        if (name.getBytes(StandardCharsets.UTF_8).length <= MAX_DATEINAME_BYTES) {
            return name;
        }
        int punkt = name.lastIndexOf('.');
        String endung = punkt >= 0 && name.length() - punkt <= MAX_ENDUNG_ZEICHEN ? name.substring(punkt) : "";
        int erlaubt = MAX_DATEINAME_BYTES - endung.getBytes(StandardCharsets.UTF_8).length;
        String gekuerzt = name.substring(0, name.length() - endung.length());
        // Jedes Zeichen braucht mindestens ein Byte: erst grob, dann Zeichen für Zeichen kürzen.
        if (gekuerzt.length() > erlaubt) {
            gekuerzt = gekuerzt.substring(0, erlaubt);
        }
        while (gekuerzt.getBytes(StandardCharsets.UTF_8).length > erlaubt) {
            gekuerzt = gekuerzt.substring(0, gekuerzt.length() - 1);
        }
        // Kein halbes Zeichen (z. B. halbes Emoji) am Ende stehen lassen.
        if (!gekuerzt.isEmpty() && Character.isHighSurrogate(gekuerzt.charAt(gekuerzt.length() - 1))) {
            gekuerzt = gekuerzt.substring(0, gekuerzt.length() - 1);
        }
        // Ohne Endung darf der Name nicht auf Punkt oder Leerzeichen enden – Windows lehnt das ab.
        if (endung.isEmpty()) {
            int ende = gekuerzt.length();
            while (ende > 1 && (gekuerzt.charAt(ende - 1) == '.' || gekuerzt.charAt(ende - 1) == ' ')) {
                ende--;
            }
            gekuerzt = gekuerzt.substring(0, ende);
        }
        return gekuerzt + endung;
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
        Path ziel = direktImOrdner(ordner, gespeichert)
                .orElseThrow(() -> new IOException("Ungültiger Dateiname"));
        Files.createDirectories(ordner);
        datei.transferTo(ziel);
        return new GespeicherteDatei(original, gespeichert, ziel);
    }

    private Path lieferantOrdner(Long lieferantId) {
        Objects.requireNonNull(lieferantId, "lieferantId");
        return lieferantenOrdner.resolve(lieferantId.toString());
    }

    private Path bilderOrdner(Long lieferantId) {
        return lieferantOrdner(lieferantId).resolve(BILDER);
    }

    /**
     * {@code name} in {@code ordner} – nur, wenn der normalisierte Pfad danach noch darin liegt.
     * Ein ungültiger Name (z. B. mit Null-Byte) gilt als nicht vorhanden.
     */
    private static Optional<Path> imOrdner(Path ordner, String name) {
        try {
            Path pfad = ordner.resolve(name).normalize();
            return pfad.startsWith(ordner) ? Optional.of(pfad) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /** Wie {@link #imOrdner}, aber nur direkt im Ordner – nicht in einem Unterordner. */
    private static Optional<Path> direktImOrdner(Path ordner, String name) {
        return imOrdner(ordner, name).filter(pfad -> ordner.equals(pfad.getParent()));
    }

    private static void loescheStill(Path pfad) {
        try {
            Files.deleteIfExists(pfad);
        } catch (IOException e) {
            // Nur die Fehlerart: Der Pfad enthält den Originalnamen vom Gerät (DSGVO).
            log.warn("Datei eines Lieferanten konnte nicht gelöscht werden: {}", e.getClass().getSimpleName());
        }
    }
}
