package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service zur Verarbeitung von Email-Attachments für Lieferanten.
 * 
 * Workflow:
 * 1. Email ist Lieferanten-Email mit Anhängen
 * 2. Für jeden PDF/XML Anhang → LieferantDokument erstellen (eigene
 * Transaktion!)
 * 3. GeminiDokumentAnalyseService aufrufen (handhabt ZUGFeRD, XML und KI)
 * 4. Wenn kein Geschäftsdokument erkannt → LieferantDokument löschen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAttachmentProcessingService {

    private final EmailRepository emailRepository;
    private final EmailAttachmentRepository emailAttachmentRepository;
    private final LieferantDokumentRepository lieferantDokumentRepository;
    private final LieferantenRepository lieferantenRepository;
    private final LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    private final GeminiDokumentAnalyseService geminiAnalyseService;
    private final LieferantStandardKostenstelleAutoAssigner standardKostenstelleAutoAssigner;

    // Self-injection für transactional proxy calls auf eigene Methoden
    // Setter-Injection um zirkuläre Abhängigkeit zu vermeiden
    @Setter(onMethod_ = { @Autowired, @Lazy })
    private EmailAttachmentProcessingService self;

    @Value("${file.mail-attachment-dir:uploads/email}")
    private String attachmentDir;

    /**
     * Verarbeitet alle Anhänge einer Lieferanten-Email.
     * Erstellt LieferantDokumente und analysiert sie.
     * Jedes Attachment wird in eigener Transaktion verarbeitet.
     * 
     * @param email Die Email (muss Lieferant-Zuordnung haben)
     * @return Anzahl erstellter Geschäftsdokumente
     */
    @org.springframework.transaction.annotation.Transactional
    public int processLieferantAttachments(Email email) {
        // Frisch laden um LazyInitializationException zu vermeiden
        Email freshEmail = emailRepository.findById(email.getId()).orElse(null);
        if (freshEmail == null || freshEmail.getLieferant() == null) {
            log.warn("Email {} hat keine Lieferant-Zuordnung", email.getId());
            return 0;
        }

        Lieferanten lieferant = freshEmail.getLieferant();
        List<EmailAttachment> attachments = freshEmail.getAttachments();

        if (attachments == null || attachments.isEmpty()) {
            return 0;
        }

        int geschaeftsdokumenteErstellt = 0;

        for (EmailAttachment attachment : attachments) {
            // Nur PDF und XML verarbeiten, keine Inline-Bilder
            if (!isProcessableAttachment(attachment)) {
                continue;
            }

            // Prüfe ob bereits verarbeitet
            if (Boolean.TRUE.equals(attachment.getAiProcessed())) {
                continue;
            }

            try {
                log.info("Starte Dokumentanalyse für Attachment: {} (Email-ID: {})",
                        attachment.getOriginalFilename(), email.getId());
                boolean success = processAttachment(attachment, lieferant.getId());
                log.info("Dokumentanalyse abgeschlossen für {}: Erfolg={}",
                        attachment.getOriginalFilename(), success);
                if (success) {
                    geschaeftsdokumenteErstellt++;
                }
            } catch (Exception e) {
                log.error("Fehler bei Verarbeitung von Attachment {}: {}",
                        attachment.getId(), e.getMessage());
            }
        }

        return geschaeftsdokumenteErstellt;
    }

    /**
     * Verarbeitet ein einzelnes Attachment.
     * 
     * @return true wenn Geschäftsdokument erstellt wurde
     */
    private boolean processAttachment(EmailAttachment attachment, Long lieferantId) {
        String filename = attachment.getOriginalFilename();
        if (filename == null) {
            return false;
        }

        Path filePath = resolveAttachmentPath(attachment);

        if (filePath == null || !Files.exists(filePath)) {
            log.warn("Attachment-Datei nicht gefunden: {}", attachment.getStoredFilename());
            return false;
        }

        // 1. Analyse durchführen (InMemory, noch keine DB-Erstellung)
        // Das verhindert, dass leere Dokumente im Frontend auftauchen während die
        // Analyse läuft.
        LieferantGeschaeftsdokument geschaeftsdaten = geminiAnalyseService.analyzeAndReturnData(filePath, filename);

        // 2. Lieferant laden (Referenz)
        Lieferanten lieferant = lieferantenRepository.findById(lieferantId).orElse(null);
        if (lieferant == null) {
            log.error("Lieferant ID {} nicht gefunden", lieferantId);
            return false;
        }

        // 2b. Duplikat-Check: Gleiche Dokumentnummer beim selben Lieferanten?
        if (geschaeftsdaten != null && geschaeftsdaten.getDokumentNummer() != null
                && !geschaeftsdaten.getDokumentNummer().isBlank()) {
            boolean duplikat = lieferantGeschaeftsdokumentRepository.existsByLieferantIdAndDokumentNummer(
                    lieferantId, geschaeftsdaten.getDokumentNummer());
            if (duplikat) {
                log.info("Duplikat erkannt: Dokumentnummer {} existiert bereits bei Lieferant {}. Überspringe.",
                        geschaeftsdaten.getDokumentNummer(), lieferantId);
                // Attachment als verarbeitet markieren, aber kein neues Dokument erstellen
                attachment.setAiProcessed(true);
                attachment.setAiProcessedAt(java.time.LocalDateTime.now());
                emailAttachmentRepository.save(attachment);
                return false;
            }
        }

        // 3. Dokument erstellen (Atomic Save)
        LieferantDokument dokument = new LieferantDokument();
        dokument.setLieferant(lieferant);
        dokument.setOriginalDateiname(filename);
        dokument.setGespeicherterDateiname(attachment.getStoredFilename()); // Korrekter Setter
        dokument.setUploadDatum(LocalDateTime.now());

        // Typ setzen basierend auf KI-Analyse, Nummer oder Default
        LieferantDokumentTyp typ = LieferantDokumentTyp.SONSTIG;

        // 1. Priorität: Von KI erkannter Typ
        if (geschaeftsdaten != null && geschaeftsdaten.getDetectedTyp() != null) {
            typ = geschaeftsdaten.getDetectedTyp();
        }
        // 2. Priorität: Inferenz aus Nummer
        else if (geschaeftsdaten != null && geschaeftsdaten.getDokumentNummer() != null) {
            LieferantDokumentTyp inferred = inferDokumentTyp(geschaeftsdaten.getDokumentNummer());
            if (inferred != null)
                typ = inferred;
        }
        dokument.setTyp(typ);

        // Daten verknüpfen
        if (geschaeftsdaten != null) {
            dokument.setGeschaeftsdaten(geschaeftsdaten);
            geschaeftsdaten.setDokument(dokument);
        } else {
            // Sollte eigentlich nicht passieren da analyzeAndReturnData Fehler-Objekte
            // liefert,
            // aber als Fallback erstellen wir ein leeres Fail-Objekt
            LieferantGeschaeftsdokument empty = new LieferantGeschaeftsdokument();
            empty.setDokument(dokument);
            empty.setManuellePruefungErforderlich(true);
            empty.setDatenquelle("ERROR_NO_RESULT");
            empty.setAnalysiertAm(LocalDateTime.now());
            dokument.setGeschaeftsdaten(empty);
        }

        // 4. Speichern (Kaskadiert zu Geschaeftsdaten)
        dokument = lieferantDokumentRepository.save(dokument);

        // Relink Logic (nachträgliche Verknüpfung)
        if (geschaeftsdaten != null) {
            geminiAnalyseService.performRelink(dokument);
        }

        // Auto-Zuweisung der Standard-Kostenstelle (falls beim Lieferanten hinterlegt)
        try {
            standardKostenstelleAutoAssigner.applyIfApplicable(dokument);
        } catch (Exception e) {
            log.warn("Auto-Zuweisung Standard-Kostenstelle fehlgeschlagen für Dokument {}: {}",
                    dokument.getId(), e.getMessage());
        }

        // 5. Attachment verknüpfen
        attachment.setAiProcessed(true);
        attachment.setAiProcessedAt(java.time.LocalDateTime.now());
        attachment.setLieferantDokument(dokument);
        emailAttachmentRepository.save(attachment);

        log.info("Geschäftsdokument atomar erstellt für: {} (Lieferant-ID: {}, Typ: {}, Data: {})",
                filename, lieferantId, dokument.getTyp(),
                geschaeftsdaten != null ? "Ja" : "Nein");

        return true;
    }

    private LieferantDokumentTyp inferDokumentTyp(String nummer) {
        if (nummer == null)
            return LieferantDokumentTyp.SONSTIG;
        String upper = nummer.toUpperCase();
        if (upper.startsWith("RE") || upper.contains("RECHNUNG"))
            return LieferantDokumentTyp.RECHNUNG;
        if (upper.startsWith("AB") || upper.contains("AUFTRAGS"))
            return LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG;
        if (upper.startsWith("LS") || upper.contains("LIEFER"))
            return LieferantDokumentTyp.LIEFERSCHEIN;
        if (upper.startsWith("AN") || upper.contains("ANGEBOT"))
            return LieferantDokumentTyp.ANGEBOT;
        if (upper.startsWith("GS") || upper.contains("GUTSCHRIFT"))
            return LieferantDokumentTyp.GUTSCHRIFT;
        return LieferantDokumentTyp.SONSTIG;
    }

    private boolean isProcessableAttachment(EmailAttachment attachment) {
        if (Boolean.TRUE.equals(attachment.getInlineAttachment())) {
            return false; // Keine Inline-Bilder (Signaturen etc.)
        }

        String filename = attachment.getOriginalFilename();
        if (filename == null) {
            return false;
        }

        String lower = filename.toLowerCase();
        // PDFs (inkl. ZUGFeRD-PDFs) UND standalone XML (XRechnung/ZUGFeRD-XML) verarbeiten
        return lower.endsWith(".pdf") || lower.endsWith(".xml");
    }

    private Path resolveAttachmentPath(EmailAttachment attachment) {
        if (attachment.getStoredFilename() == null) {
            return null;
        }

        // Versuche verschiedene Pfade
        Path basePath = Path.of(attachmentDir).toAbsolutePath().normalize();

        // 1. Direkt im Attachment-Verzeichnis (Flat Structure - User Request)
        Path directPath = basePath.resolve(attachment.getStoredFilename());
        if (Files.exists(directPath)) {
            return directPath;
        }

        // 2. Im Email-ID Unterverzeichnis (Legacy/Falllback)
        if (attachment.getEmail() != null) {
            Path emailSubDirPath = basePath
                    .resolve(String.valueOf(attachment.getEmail().getId()))
                    .resolve(attachment.getStoredFilename());
            if (Files.exists(emailSubDirPath)) {
                return emailSubDirPath;
            }
        }

        // 3. Im Lieferant-Unterverzeichnis (Alt-Daten Struktur)
        if (attachment.getEmail() != null && attachment.getEmail().getLieferant() != null) {
            Path lieferantPath = basePath
                    .resolve(String.valueOf(attachment.getEmail().getLieferant().getId()))
                    .resolve(attachment.getStoredFilename());
            if (Files.exists(lieferantPath)) {
                return lieferantPath;
            }
        }

        // Fallback: Wenn wir hier sind, wurde die Datei nicht gefunden.
        // Wir geben den Pfad zurück, wo sie SEIN SOLLTE (Email-ID Subdir),
        // damit die Fehlermeldung sinnvoll ist.
        if (attachment.getEmail() != null) {
            return basePath
                    .resolve(String.valueOf(attachment.getEmail().getId()))
                    .resolve(attachment.getStoredFilename());
        }

        return directPath;
    }
}
