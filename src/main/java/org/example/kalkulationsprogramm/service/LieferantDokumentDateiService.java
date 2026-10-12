package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Findet die Datei auf der Platte, die zu einem {@link LieferantDokument} gehört.
 *
 * <p>Lieferanten-Dokumente liegen je nach Herkunft in verschiedenen Ordnern: E-Mail-Import,
 * Zuordnung zum Lieferanten, Rechnungs-Import, manueller Upload, „Belege &amp; Kasse“ und
 * Alt-Daten. Vorschau, Download, Gesamt-PDF und KI-Analyse suchen alle hier – so finden sie
 * dieselbe Datei.
 *
 * <p>Gesucht wird in dieser Reihenfolge (jeweils in allen konfigurierten Basis-Ordnern, in
 * denen der Ort vorkommt; doppelte Ordner werden nur einmal geprüft):
 * <ol>
 * <li>direkt im Upload-Ordner – auch Belege, deren Name {@code belege/<datei>} lautet</li>
 * <li>{@code email/<lieferantId>/} im Mail-Anhang-Ordner – dem Lieferanten zugeordnete Anhänge</li>
 * <li>{@code lieferanten/<lieferantId>/} – manuell hochgeladene Dokumente</li>
 * <li>{@code <lieferantId>/} im Lieferanten-Mail-Ordner ({@code attachments/lieferanten/})</li>
 * <li>{@code vendor-invoices/} im Mail-Anhang-Ordner – Rechnungs-Import</li>
 * <li>der Mail-Anhang-Ordner selbst – so speichert der E-Mail-Import</li>
 * <li>{@code <emailId>/} und {@code <lieferantId>/} im Mail-Anhang-Ordner – Alt-Daten, wie sie
 * auch {@link EmailAttachmentProcessingService} kennt</li>
 * <li>{@code lieferant-emails/} und {@code email/} im Upload-Ordner – Alt-Daten</li>
 * </ol>
 * Zuerst wird an allen Orten mit dem Dateinamen des E-Mail-Anhangs gesucht, danach mit dem
 * eigenen gespeicherten Namen des Dokuments. Der erste Name ist der aus
 * {@link LieferantDokument#getEffektiverGespeicherterDateiname()}, zu dem die Aufrufer Anzeigename
 * und Dateityp aus dem Anhang nehmen. Fehlt die Datei des Anhangs und wird sie erst unter dem
 * eigenen Namen gefunden, können Anzeigename und Dateityp davon abweichen.
 *
 * <p>Path Traversal: Jeder Kandidat wird normalisiert und muss danach noch in dem Ordner
 * liegen, in dem gesucht wird – und damit in seinem Basis-Ordner. Ein {@code ../} oder ein
 * absoluter Pfad im gespeicherten Namen führt so nie aus der Ablage heraus.
 */
@Service
@Slf4j
public class LieferantDokumentDateiService {

    /** Die PDF-Spezifikation erlaubt die Kennung {@code %PDF-} irgendwo in den ersten 1024 Bytes. */
    private static final int PDF_KENNUNG_SUCHBEREICH = 1024;

    private final Path uploadPath;
    private final Path uploadDir;
    private final Path mailAnhangDir;
    private final Path lieferantenMailAnhangDir;

    public LieferantDokumentDateiService(
            @Value("${upload.path:uploads}") String uploadPath,
            @Value("${file.upload-dir:uploads}") String uploadDir,
            @Value("${file.mail-attachment-dir:uploads/attachments}") String mailAnhangDir,
            @Value("${file.supplier-mail-attachment-dir:uploads/attachments/lieferanten}")
            String lieferantenMailAnhangDir) {
        this.uploadPath = basis(uploadPath, "uploads");
        this.uploadDir = basis(uploadDir, "uploads");
        this.mailAnhangDir = basis(mailAnhangDir, "uploads/attachments");
        this.lieferantenMailAnhangDir = basis(lieferantenMailAnhangDir, "uploads/attachments/lieferanten");
    }

    /**
     * Die erste vorhandene Datei zum Dokument – oder leer, wenn es sie nirgends gibt.
     */
    public Optional<Path> findeDatei(LieferantDokument dokument) {
        return vorhandeneDateien(dokument).findFirst();
    }

    /**
     * Wie {@link #findeDatei}, nimmt aber nur echte PDFs. Liegt unter dem einen Namen eine
     * XML-E-Rechnung oder ein Foto und unter dem anderen die PDF, wird die PDF gefunden.
     * Entscheidend ist der Inhalt, nicht die Endung.
     */
    public Optional<Path> findePdf(LieferantDokument dokument) {
        return vorhandeneDateien(dokument).filter(LieferantDokumentDateiService::istPdf).findFirst();
    }

    /**
     * {@code true}, wenn der Pfad in einem der Ablage-Ordner liegt, in denen dieser Service
     * sucht. Für Aufrufer, die einen gefundenen Pfad vor dem Lesen nochmals absichern.
     */
    public boolean liegtInAblage(Path pfad) {
        Path normalisiert = pfad.toAbsolutePath().normalize();
        return Stream.of(uploadPath, uploadDir, mailAnhangDir, lieferantenMailAnhangDir)
                .anyMatch(normalisiert::startsWith);
    }

    private Stream<Path> vorhandeneDateien(LieferantDokument dokument) {
        if (dokument == null) {
            return Stream.empty();
        }
        List<String> dateinamen = dateinamen(dokument);
        if (dateinamen.isEmpty()) {
            return Stream.empty();
        }
        List<Path> suchordner = suchordner(dokument);
        return dateinamen.stream()
                .flatMap(name -> suchordner.stream().map(ordner -> imOrdner(ordner, name)))
                .flatMap(Optional::stream)
                .distinct()
                .filter(Files::isRegularFile);
    }

    private static List<String> dateinamen(LieferantDokument dokument) {
        Set<String> namen = new LinkedHashSet<>();
        EmailAttachment anhang = dokument.getAttachment();
        if (anhang != null && hatText(anhang.getStoredFilename())) {
            namen.add(anhang.getStoredFilename());
        }
        if (hatText(dokument.getGespeicherterDateiname())) {
            namen.add(dokument.getGespeicherterDateiname());
        }
        return List.copyOf(namen);
    }

    private List<Path> suchordner(LieferantDokument dokument) {
        String lieferantId = dokument.getLieferant() != null && dokument.getLieferant().getId() != null
                ? dokument.getLieferant().getId().toString()
                : null;
        EmailAttachment anhang = dokument.getAttachment();
        String emailId = anhang != null && anhang.getEmail() != null && anhang.getEmail().getId() != null
                ? anhang.getEmail().getId().toString()
                : null;

        Set<Path> ordner = new LinkedHashSet<>();
        ordner.add(uploadDir);
        ordner.add(uploadPath);
        if (lieferantId != null) {
            ordner.add(mailAnhangDir.resolve("email").resolve(lieferantId));
            ordner.add(uploadPath.resolve("attachments").resolve("email").resolve(lieferantId));
            ordner.add(uploadDir.resolve("lieferanten").resolve(lieferantId));
            ordner.add(uploadPath.resolve("lieferanten").resolve(lieferantId));
            ordner.add(lieferantenMailAnhangDir.resolve(lieferantId));
            ordner.add(uploadDir.resolve("attachments").resolve("lieferanten").resolve(lieferantId));
            ordner.add(uploadPath.resolve("attachments").resolve("lieferanten").resolve(lieferantId));
        }
        ordner.add(mailAnhangDir.resolve("vendor-invoices"));
        ordner.add(uploadDir.resolve("attachments").resolve("vendor-invoices"));
        ordner.add(uploadPath.resolve("attachments").resolve("vendor-invoices"));
        ordner.add(mailAnhangDir);
        ordner.add(uploadPath.resolve("attachments"));
        if (emailId != null) {
            ordner.add(mailAnhangDir.resolve(emailId));
            ordner.add(uploadPath.resolve("attachments").resolve(emailId));
        }
        if (lieferantId != null) {
            ordner.add(mailAnhangDir.resolve(lieferantId));
        }
        ordner.add(uploadPath.resolve("lieferant-emails"));
        ordner.add(uploadPath.resolve("email"));
        return new ArrayList<>(ordner);
    }

    /**
     * {@code dateiname} in {@code ordner} – nur, wenn der normalisierte Pfad danach noch in
     * diesem Ordner liegt. Ein ungültiger Name (z. B. mit Null-Byte) gilt als nicht vorhanden.
     */
    private static Optional<Path> imOrdner(Path ordner, String dateiname) {
        try {
            Path pfad = ordner.resolve(dateiname).normalize();
            return pfad.startsWith(ordner) ? Optional.of(pfad) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /**
     * {@code true}, wenn die Datei eine PDF ist: Die Kennung {@code %PDF-} steht in den ersten
     * 1024 Bytes. Eine nicht lesbare Datei gilt als keine PDF.
     */
    private static boolean istPdf(Path datei) {
        try (var in = Files.newInputStream(datei)) {
            byte[] anfang = in.readNBytes(PDF_KENNUNG_SUCHBEREICH);
            return new String(anfang, StandardCharsets.ISO_8859_1).contains("%PDF-");
        } catch (IOException e) {
            // Nur die Fehlerart: Pfad und Dateiname können Namen aus der Rechnung enthalten (DSGVO).
            log.warn("Lieferanten-Dokument konnte nicht gelesen werden: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    private static Path basis(String konfiguriert, String standard) {
        String wert = hatText(konfiguriert) ? konfiguriert : standard;
        return Path.of(wert).toAbsolutePath().normalize();
    }

    private static boolean hatText(String wert) {
        return wert != null && !wert.isBlank();
    }
}
