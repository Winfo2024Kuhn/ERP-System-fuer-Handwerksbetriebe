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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        // Nur noch nicht verarbeitete PDF/XML-Anhänge (keine Inline-Bilder).
        List<EmailAttachment> toProcess = new ArrayList<>();
        for (EmailAttachment attachment : attachments) {
            if (isProcessableAttachment(attachment) && !Boolean.TRUE.equals(attachment.getAiProcessed())) {
                toProcess.add(attachment);
            }
        }

        long pdfCount = toProcess.stream().filter(this::isPdf).count();
        long xmlCount = toProcess.stream().filter(this::isXml).count();
        Set<Long> consumed = new HashSet<>();

        // Durchgang 1: E-Rechnungs-XML mit passender PDF paaren.
        // Anzeige-Datei = PDF (für den Viewer), Metadaten = XML.
        // Verhindert, dass die XML im PDF-Viewer als Rohtext erscheint und die
        // echte PDF als "Duplikat" verworfen wird.
        for (EmailAttachment xml : toProcess) {
            if (!isXml(xml) || consumed.contains(xml.getId())) {
                continue;
            }
            EmailAttachment pdf = findMatchingPdf(xml, toProcess, consumed, pdfCount, xmlCount);
            if (pdf == null) {
                continue;
            }
            try {
                log.info("Paare PDF '{}' (Anzeige) mit XML '{}' (Metadaten), Email-ID: {}",
                        pdf.getOriginalFilename(), xml.getOriginalFilename(), email.getId());
                if (processAttachment(pdf, xml, lieferant.getId())) {
                    geschaeftsdokumenteErstellt++;
                }
            } catch (Exception e) {
                log.error("Fehler beim Paaren von PDF {} mit XML {}: {}",
                        pdf.getId(), xml.getId(), e.getMessage());
            }
            consumed.add(xml.getId());
            consumed.add(pdf.getId());
        }

        // Durchgang 2: verbleibende Einzel-Anhänge (PDF-only oder XML-only).
        for (EmailAttachment attachment : toProcess) {
            if (consumed.contains(attachment.getId())) {
                continue;
            }
            try {
                log.info("Starte Dokumentanalyse für Attachment: {} (Email-ID: {})",
                        attachment.getOriginalFilename(), email.getId());
                boolean success = processAttachment(attachment, attachment, lieferant.getId());
                log.info("Dokumentanalyse abgeschlossen für {}: Erfolg={}",
                        attachment.getOriginalFilename(), success);
                if (success) {
                    geschaeftsdokumenteErstellt++;
                }
            } catch (Exception e) {
                log.error("Fehler bei Verarbeitung von Attachment {}: {}",
                        attachment.getId(), e.getMessage());
            }
            consumed.add(attachment.getId());
        }

        return geschaeftsdokumenteErstellt;
    }

    /**
     * Einmaliger Backfill für bereits importierte Dokumente: Findet alle
     * Lieferanten-Dokumente, deren Anzeige-Datei eine XML ist (alter Bug: die XML
     * landete im PDF-Viewer), und stellt sie auf die zugehörige PDF aus derselben
     * Mail um. Die Metadaten bleiben unverändert (kamen bereits aus der XML).
     * <p>
     * Idempotent: Dokumente ohne passende PDF bleiben unangetastet; ein erneuter
     * Lauf findet bereits umgestellte Dokumente nicht mehr (referenzieren dann PDF).
     *
     * @return Anzahl der umgestellten Dokumente
     */
    @Transactional
    public int backfillXmlDokumenteAufPdf() {
        List<LieferantDokument> xmlDocs = lieferantDokumentRepository.findMitXmlAnzeigedatei();
        int umgestellt = 0;

        for (LieferantDokument doc : xmlDocs) {
            try {
                List<EmailAttachment> verknuepft = emailAttachmentRepository.findByLieferantDokumentId(doc.getId());
                EmailAttachment xmlAtt = verknuepft.stream().filter(this::isXml).findFirst().orElse(null);
                if (xmlAtt == null || xmlAtt.getEmail() == null) {
                    log.info("Backfill XML->PDF: kein XML-Attachment/Email für Dokument {}", doc.getId());
                    continue;
                }

                EmailAttachment pdf = findePdfImSelbenEmail(xmlAtt, doc);
                if (pdf == null) {
                    log.info("Backfill XML->PDF: kein passendes PDF für Dokument {} (XML '{}')",
                            doc.getId(), xmlAtt.getOriginalFilename());
                    continue;
                }

                // Dokument auf die PDF umstellen (Viewer zeigt jetzt PDF statt XML-Rohtext)
                doc.setGespeicherterDateiname(pdf.getStoredFilename());
                doc.setOriginalDateiname(pdf.getOriginalFilename());
                lieferantDokumentRepository.save(doc);

                // PDF-Attachment nachträglich verknüpfen, falls noch verwaist
                if (pdf.getLieferantDokument() == null) {
                    markProcessed(pdf, doc);
                }

                umgestellt++;
                log.info("Backfill XML->PDF: Dokument {} von '{}' auf '{}' umgestellt",
                        doc.getId(), xmlAtt.getOriginalFilename(), pdf.getOriginalFilename());
            } catch (Exception e) {
                log.error("Backfill XML->PDF fehlgeschlagen für Dokument {}: {}", doc.getId(), e.getMessage());
            }
        }

        log.info("Backfill XML->PDF abgeschlossen: {} von {} Dokumenten umgestellt", umgestellt, xmlDocs.size());
        return umgestellt;
    }

    /**
     * Sucht im selben Mail-Anhang die zur XML passende PDF (für den Backfill).
     * Nutzt die bereits extrahierte Rechnungsnummer aus den Geschäftsdaten,
     * sonst die XML-Datei selbst; Fallback bei genau 1 PDF + 1 XML.
     */
    private EmailAttachment findePdfImSelbenEmail(EmailAttachment xmlAtt, LieferantDokument doc) {
        List<EmailAttachment> atts = xmlAtt.getEmail().getAttachments();
        if (atts == null || atts.isEmpty()) {
            return null;
        }
        List<EmailAttachment> pdfs = new ArrayList<>();
        long xmlCount = 0;
        for (EmailAttachment a : atts) {
            if (Boolean.TRUE.equals(a.getInlineAttachment())) {
                continue;
            }
            if (isPdf(a)) {
                // Keine PDF hijacken, die bereits zu einem ANDEREN Dokument gehört.
                // Nur verwaiste PDFs (dup-skip-Fall) oder solche, die ohnehin schon
                // zu DIESEM Dokument verlinkt sind, sind gültige Anzeige-Kandidaten.
                LieferantDokument vorhanden = a.getLieferantDokument();
                boolean freiOderEigen = vorhanden == null || java.util.Objects.equals(vorhanden.getId(), doc.getId());
                // Die PDF muss auch physisch existieren – sonst würde der Backfill ein
                // noch anzeigbares XML-Dokument auf eine fehlende Datei umbiegen.
                Path pdfPath = resolveAttachmentPath(a);
                boolean existiert = pdfPath != null && Files.exists(pdfPath);
                if (freiOderEigen && existiert) {
                    pdfs.add(a);
                }
            } else if (isXml(a)) {
                xmlCount++;
            }
        }
        if (pdfs.isEmpty()) {
            return null;
        }

        // a) Nummern-Abgleich (bevorzugt aus bereits gespeicherten Geschäftsdaten)
        String nummer = doc.getGeschaeftsdaten() != null ? doc.getGeschaeftsdaten().getDokumentNummer() : null;
        if (nummer == null || nummer.isBlank()) {
            nummer = extractInvoiceNumberFromXml(resolveAttachmentPath(xmlAtt));
        }
        String needle = normalizeForMatch(nummer);
        if (!needle.isEmpty()) {
            for (EmailAttachment pdf : pdfs) {
                if (normalizeForMatch(pdf.getOriginalFilename()).contains(needle)) {
                    return pdf;
                }
            }
        }

        // b) Fallback: genau 1 PDF + 1 XML in der Mail
        if (pdfs.size() == 1 && xmlCount == 1) {
            return pdfs.get(0);
        }
        return null;
    }

    /**
     * Sucht zu einer E-Rechnungs-XML die zugehörige PDF im selben Mail-Anhang.
     * <p>
     * Strategie (siehe Bugfix PDF+XML-Import):
     * <ol>
     * <li><b>Nummern-Abgleich:</b> Steht die Rechnungsnummer aus der XML im
     * PDF-Dateinamen (z.B. XML "2026-0814" ↔ "ReNr. 2026-0814.pdf")?</li>
     * <li><b>Fallback:</b> Enthält die Mail genau eine PDF und genau eine XML,
     * werden sie als Paar behandelt.</li>
     * </ol>
     *
     * @return die passende PDF oder {@code null}, wenn kein eindeutiges Paar
     */
    private EmailAttachment findMatchingPdf(EmailAttachment xml, List<EmailAttachment> candidates,
            Set<Long> consumed, long pdfCount, long xmlCount) {
        List<EmailAttachment> freePdfs = new ArrayList<>();
        for (EmailAttachment candidate : candidates) {
            if (isPdf(candidate) && !consumed.contains(candidate.getId())) {
                freePdfs.add(candidate);
            }
        }
        if (freePdfs.isEmpty()) {
            return null;
        }

        // a) Nummern-Abgleich
        String invoiceNr = extractInvoiceNumberFromXml(resolveAttachmentPath(xml));
        String needle = normalizeForMatch(invoiceNr);
        if (!needle.isEmpty()) {
            for (EmailAttachment pdf : freePdfs) {
                if (normalizeForMatch(pdf.getOriginalFilename()).contains(needle)) {
                    return pdf;
                }
            }
        }

        // b) Fallback: genau 1 PDF + 1 XML in der Mail
        if (pdfCount == 1 && xmlCount == 1) {
            return freePdfs.get(0);
        }
        return null;
    }

    /** Liest die Rechnungsnummer (Invoice-ID) lightweight per Regex aus der XML. */
    private String extractInvoiceNumberFromXml(Path xmlPath) {
        if (xmlPath == null || !Files.exists(xmlPath)) {
            return null;
        }
        try {
            String xml = new String(Files.readAllBytes(xmlPath), StandardCharsets.UTF_8);
            // <cbc:ID>, <ram:ID> oder <ID> bzw. <InvoiceNumber>, ohne Customization-/ProfileID zu treffen.
            Matcher m = Pattern.compile(
                    "<(?:[^:>]+:)?(?:ID|InvoiceNumber)(?:\\s[^>]*)?>([^<]+)</",
                    Pattern.CASE_INSENSITIVE).matcher(xml);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Exception e) {
            log.debug("Konnte Rechnungsnummer nicht aus XML lesen ({}): {}", xmlPath, e.getMessage());
        }
        return null;
    }

    /** Normalisiert einen String für den Datei-/Nummern-Abgleich (nur a-z0-9). */
    private String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private boolean isPdf(EmailAttachment attachment) {
        String name = attachment.getOriginalFilename();
        return name != null && name.toLowerCase().endsWith(".pdf");
    }

    private boolean isXml(EmailAttachment attachment) {
        String name = attachment.getOriginalFilename();
        return name != null && name.toLowerCase().endsWith(".xml");
    }

    /**
     * Verarbeitet ein Attachment (bzw. ein PDF+XML-Paar) zu einem Geschäftsdokument.
     * <p>
     * Die <b>Anzeige-Datei</b> ({@code displayAttachment}) wird im Dokument
     * referenziert und im Viewer angezeigt – bei einem PDF+XML-Paar also die PDF.
     * Die <b>Metadaten-Datei</b> ({@code metaAttachment}) wird analysiert – bei
     * einem Paar die strukturierte XML. Für Einzel-Anhänge sind beide identisch.
     *
     * @return true wenn ein Geschäftsdokument erstellt wurde
     */
    private boolean processAttachment(EmailAttachment displayAttachment, EmailAttachment metaAttachment,
            Long lieferantId) {
        String displayFilename = displayAttachment.getOriginalFilename();
        String metaFilename = metaAttachment.getOriginalFilename();
        if (displayFilename == null || metaFilename == null) {
            return false;
        }

        Path metaPath = resolveAttachmentPath(metaAttachment);
        if (metaPath == null || !Files.exists(metaPath)) {
            log.warn("Attachment-Datei nicht gefunden: {}", metaAttachment.getStoredFilename());
            return false;
        }

        // Anzeige-Datei (PDF bei einem Paar) muss auf der Platte existieren – sonst
        // zeigt der Viewer wieder ins Leere. Fehlt sie, fallen wir auf die
        // (garantiert vorhandene) Metadaten-Datei als Anzeige zurück.
        EmailAttachment effektiveAnzeige = displayAttachment;
        if (!displayAttachment.getId().equals(metaAttachment.getId())) {
            Path displayPath = resolveAttachmentPath(displayAttachment);
            if (displayPath == null || !Files.exists(displayPath)) {
                log.warn("Anzeige-Datei fehlt auf der Platte ({}), nutze Metadaten-Datei als Anzeige",
                        displayAttachment.getStoredFilename());
                effektiveAnzeige = metaAttachment;
            }
        }
        String anzeigeFilename = effektiveAnzeige.getOriginalFilename();

        // 1. Analyse durchführen (InMemory, noch keine DB-Erstellung)
        // Das verhindert, dass leere Dokumente im Frontend auftauchen während die
        // Analyse läuft. Metadaten kommen aus der Metadaten-Datei (XML bei einem Paar).
        LieferantGeschaeftsdokument geschaeftsdaten = geminiAnalyseService.analyzeAndReturnData(metaPath, metaFilename);

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
                // Beide Attachments als verarbeitet markieren, aber kein neues Dokument erstellen
                markProcessed(effektiveAnzeige, null);
                markProcessed(metaAttachment, null);
                return false;
            }
        }

        // 3. Dokument erstellen (Atomic Save) – referenziert die Anzeige-Datei (PDF bei einem Paar)
        LieferantDokument dokument = new LieferantDokument();
        dokument.setLieferant(lieferant);
        dokument.setOriginalDateiname(anzeigeFilename);
        dokument.setGespeicherterDateiname(effektiveAnzeige.getStoredFilename()); // Korrekter Setter
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

        // 5. Beide Attachments mit dem Dokument verknüpfen (PDF + ggf. XML)
        markProcessed(effektiveAnzeige, dokument);
        if (!metaAttachment.getId().equals(effektiveAnzeige.getId())) {
            markProcessed(metaAttachment, dokument);
        }

        log.info("Geschäftsdokument atomar erstellt für: {} (Lieferant-ID: {}, Typ: {}, Data: {})",
                anzeigeFilename, lieferantId, dokument.getTyp(),
                geschaeftsdaten != null ? "Ja" : "Nein");

        return true;
    }

    /**
     * Markiert ein Attachment als verarbeitet und verknüpft es optional mit dem
     * erzeugten Dokument.
     */
    private void markProcessed(EmailAttachment attachment, LieferantDokument dokument) {
        attachment.setAiProcessed(true);
        attachment.setAiProcessedAt(java.time.LocalDateTime.now());
        if (dokument != null) {
            attachment.setLieferantDokument(dokument);
        }
        emailAttachmentRepository.save(attachment);
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
