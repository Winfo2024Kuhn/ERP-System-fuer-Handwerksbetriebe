package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailAttachmentProcessingServiceTest {

    @Mock private EmailRepository emailRepository;
    @Mock private EmailAttachmentRepository emailAttachmentRepository;
    @Mock private LieferantDokumentRepository lieferantDokumentRepository;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    @Mock private GeminiDokumentAnalyseService geminiAnalyseService;
    @Mock private LieferantStandardKostenstelleAutoAssigner standardKostenstelleAutoAssigner;

    @TempDir
    Path tempDir;

    private EmailAttachmentProcessingService service;

    @BeforeEach
    void setUp() {
        service = new EmailAttachmentProcessingService(
                emailRepository, emailAttachmentRepository, lieferantDokumentRepository,
                lieferantenRepository, lieferantGeschaeftsdokumentRepository, geminiAnalyseService,
                standardKostenstelleAutoAssigner);
        ReflectionTestUtils.setField(service, "attachmentDir", tempDir.toString());
    }

    private Email erstelleEmailMitLieferant(Long emailId, Lieferanten lieferant) {
        Email email = new Email();
        email.setId(emailId);
        email.setFromAddress("rechnung@lieferant.de");
        email.setDirection(EmailDirection.IN);
        email.assignToLieferant(lieferant);
        email.setAttachments(new ArrayList<>());
        return email;
    }

    private Lieferanten erstelleLieferant(Long id) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname("Test Lieferant");
        return l;
    }

    private EmailAttachment erstellePdfAttachment(String filename) {
        EmailAttachment att = new EmailAttachment();
        att.setId(1L);
        att.setOriginalFilename(filename);
        att.setStoredFilename("uuid_" + filename);
        att.setInlineAttachment(false);
        att.setAiProcessed(false);
        return att;
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.4.1 Verarbeitet PDF-Anhänge mit Lieferant-Zuordnung
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class PdfAnhangVerarbeitung {

        @Test
        void verarbeitetPdfAnhaengeMitLieferantZuordnung() throws IOException {
            Lieferanten lieferant = erstelleLieferant(10L);
            Email email = erstelleEmailMitLieferant(1L, lieferant);
            EmailAttachment pdfAtt = erstellePdfAttachment("Rechnung_2025.pdf");
            pdfAtt.setEmail(email);
            email.getAttachments().add(pdfAtt);

            // Datei im temp-Verzeichnis anlegen
            Files.write(tempDir.resolve(pdfAtt.getStoredFilename()), new byte[]{0x25, 0x50, 0x44, 0x46});

            LieferantGeschaeftsdokument geschaeftsdaten = new LieferantGeschaeftsdokument();
            geschaeftsdaten.setDokumentNummer("RE-2025-001");
            geschaeftsdaten.setDetectedTyp(LieferantDokumentTyp.RECHNUNG);

            when(emailRepository.findById(1L)).thenReturn(Optional.of(email));
            when(geminiAnalyseService.analyzeAndReturnData(any(Path.class), eq("Rechnung_2025.pdf")))
                    .thenReturn(geschaeftsdaten);
            when(lieferantenRepository.findById(10L)).thenReturn(Optional.of(lieferant));
            when(lieferantDokumentRepository.save(any(LieferantDokument.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            int result = service.processLieferantAttachments(email);

            assertThat(result).isEqualTo(1);
            verify(lieferantDokumentRepository).save(any(LieferantDokument.class));
            verify(emailAttachmentRepository).save(pdfAtt);
            assertThat(pdfAtt.getAiProcessed()).isTrue();
        }

        @Test
        void gibtNullZurueckOhneLieferantZuordnung() {
            Email email = new Email();
            email.setId(99L);

            // Email ohne Lieferant
            Email freshEmail = new Email();
            freshEmail.setId(99L);
            when(emailRepository.findById(99L)).thenReturn(Optional.of(freshEmail));

            int result = service.processLieferantAttachments(email);

            assertThat(result).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.4.2 Dokumenttyp-Erkennung: KI > Nummernmuster > Default
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class DokumenttypErkennung {

        @Test
        void kiErkannterTypHatPrioritaet() throws IOException {
            Lieferanten lieferant = erstelleLieferant(10L);
            Email email = erstelleEmailMitLieferant(1L, lieferant);
            EmailAttachment pdfAtt = erstellePdfAttachment("Dokument.pdf");
            pdfAtt.setEmail(email);
            email.getAttachments().add(pdfAtt);

            Files.write(tempDir.resolve(pdfAtt.getStoredFilename()), new byte[]{0x25, 0x50, 0x44, 0x46});

            LieferantGeschaeftsdokument geschaeftsdaten = new LieferantGeschaeftsdokument();
            geschaeftsdaten.setDokumentNummer("AB-2025-001");
            geschaeftsdaten.setDetectedTyp(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);

            when(emailRepository.findById(1L)).thenReturn(Optional.of(email));
            when(geminiAnalyseService.analyzeAndReturnData(any(Path.class), anyString()))
                    .thenReturn(geschaeftsdaten);
            when(lieferantenRepository.findById(10L)).thenReturn(Optional.of(lieferant));
            when(lieferantDokumentRepository.save(any(LieferantDokument.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.processLieferantAttachments(email);

            ArgumentCaptor<LieferantDokument> captor = ArgumentCaptor.forClass(LieferantDokument.class);
            verify(lieferantDokumentRepository).save(captor.capture());

            // KI-erkannter Typ hat Vorrang
            assertThat(captor.getValue().getTyp()).isEqualTo(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        }

        @Test
        void nummernmusterFallbackBeiKeinemKiTyp() throws IOException {
            Lieferanten lieferant = erstelleLieferant(10L);
            Email email = erstelleEmailMitLieferant(1L, lieferant);
            EmailAttachment pdfAtt = erstellePdfAttachment("Dokument.pdf");
            pdfAtt.setEmail(email);
            email.getAttachments().add(pdfAtt);

            Files.write(tempDir.resolve(pdfAtt.getStoredFilename()), new byte[]{0x25, 0x50, 0x44, 0x46});

            LieferantGeschaeftsdokument geschaeftsdaten = new LieferantGeschaeftsdokument();
            geschaeftsdaten.setDokumentNummer("RE-2025-999");
            geschaeftsdaten.setDetectedTyp(null); // Kein KI-Typ

            when(emailRepository.findById(1L)).thenReturn(Optional.of(email));
            when(geminiAnalyseService.analyzeAndReturnData(any(Path.class), anyString()))
                    .thenReturn(geschaeftsdaten);
            when(lieferantenRepository.findById(10L)).thenReturn(Optional.of(lieferant));
            when(lieferantDokumentRepository.save(any(LieferantDokument.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.processLieferantAttachments(email);

            ArgumentCaptor<LieferantDokument> captor = ArgumentCaptor.forClass(LieferantDokument.class);
            verify(lieferantDokumentRepository).save(captor.capture());

            // "RE-" am Anfang → RECHNUNG
            assertThat(captor.getValue().getTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.4.3 Dateipfad-Auflösung mit 3 Fallback-Strategien
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class DateipfadAufloesung {

        @Test
        void ignorierteAnhaengeOhneStoredFilename() {
            Lieferanten lieferant = erstelleLieferant(10L);
            Email email = erstelleEmailMitLieferant(1L, lieferant);
            EmailAttachment pdfAtt = erstellePdfAttachment("test.pdf");
            pdfAtt.setStoredFilename(null); // Kein gespeicherter Dateiname
            pdfAtt.setEmail(email);
            email.getAttachments().add(pdfAtt);

            when(emailRepository.findById(1L)).thenReturn(Optional.of(email));

            // File does not exist, so processAttachment returns false
            int result = service.processLieferantAttachments(email);

            assertThat(result).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.4.4 Ignoriert bereits verarbeitete Anhänge
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class BereitsVerarbeiteteAnhaenge {

        @Test
        void ignoriertBereitsVerarbeiteteAnhaenge() {
            Lieferanten lieferant = erstelleLieferant(10L);
            Email email = erstelleEmailMitLieferant(1L, lieferant);

            EmailAttachment processed = erstellePdfAttachment("already_processed.pdf");
            processed.setAiProcessed(true);
            processed.setEmail(email);
            email.getAttachments().add(processed);

            when(emailRepository.findById(1L)).thenReturn(Optional.of(email));

            int result = service.processLieferantAttachments(email);

            assertThat(result).isEqualTo(0);
            verify(geminiAnalyseService, never()).analyzeAndReturnData(any(), any());
        }

        @Test
        void ignoriertInlineAttachments() {
            Lieferanten lieferant = erstelleLieferant(10L);
            Email email = erstelleEmailMitLieferant(1L, lieferant);

            EmailAttachment inlineImage = new EmailAttachment();
            inlineImage.setId(2L);
            inlineImage.setOriginalFilename("logo.png");
            inlineImage.setInlineAttachment(true);
            inlineImage.setAiProcessed(false);
            inlineImage.setEmail(email);
            email.getAttachments().add(inlineImage);

            when(emailRepository.findById(1L)).thenReturn(Optional.of(email));

            int result = service.processLieferantAttachments(email);

            assertThat(result).isEqualTo(0);
            verify(geminiAnalyseService, never()).analyzeAndReturnData(any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.4.5 Erstellt Dokument atomar (erst in-memory, dann DB)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class AtomaresDokumentErstellen {

        @Test
        void erstelltDokumentMitGeschaeftsdatenAtomar() throws IOException {
            Lieferanten lieferant = erstelleLieferant(10L);
            Email email = erstelleEmailMitLieferant(1L, lieferant);
            EmailAttachment pdfAtt = erstellePdfAttachment("Rechnung.pdf");
            pdfAtt.setEmail(email);
            email.getAttachments().add(pdfAtt);

            Files.write(tempDir.resolve(pdfAtt.getStoredFilename()), new byte[]{0x25, 0x50, 0x44, 0x46});

            LieferantGeschaeftsdokument geschaeftsdaten = new LieferantGeschaeftsdokument();
            geschaeftsdaten.setDokumentNummer("RE-2025-100");
            geschaeftsdaten.setDetectedTyp(LieferantDokumentTyp.RECHNUNG);
            geschaeftsdaten.setDatenquelle("AI");

            when(emailRepository.findById(1L)).thenReturn(Optional.of(email));
            when(geminiAnalyseService.analyzeAndReturnData(any(Path.class), eq("Rechnung.pdf")))
                    .thenReturn(geschaeftsdaten);
            when(lieferantenRepository.findById(10L)).thenReturn(Optional.of(lieferant));
            when(lieferantDokumentRepository.save(any(LieferantDokument.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.processLieferantAttachments(email);

            ArgumentCaptor<LieferantDokument> captor = ArgumentCaptor.forClass(LieferantDokument.class);
            verify(lieferantDokumentRepository).save(captor.capture());

            LieferantDokument saved = captor.getValue();
            assertThat(saved.getLieferant()).isEqualTo(lieferant);
            assertThat(saved.getOriginalDateiname()).isEqualTo("Rechnung.pdf");
            assertThat(saved.getGeschaeftsdaten()).isNotNull();
            assertThat(saved.getGeschaeftsdaten().getDokumentNummer()).isEqualTo("RE-2025-100");

            // Verify relink was also called
            verify(geminiAnalyseService).performRelink(saved);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.4.6 PDF + XML aus derselben Mail werden zu EINEM Dokument gepaart
    //        (PDF = Anzeige im Viewer, XML = Metadaten)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class PdfXmlPaarung {

        private EmailAttachment erstelleAttachment(Long id, String filename) {
            EmailAttachment att = new EmailAttachment();
            att.setId(id);
            att.setOriginalFilename(filename);
            att.setStoredFilename("uuid_" + id + "_" + filename);
            att.setInlineAttachment(false);
            att.setAiProcessed(false);
            return att;
        }

        /**
         * Regression: Kommt eine Rechnung als PDF + XRechnung-XML, darf NICHT die
         * XML im PDF-Viewer landen. Das erzeugte Dokument muss die PDF referenzieren,
         * die Metadaten aber aus der XML stammen. Pairing per Nummern-Abgleich
         * (XML-Nummer 2026-0814 ↔ "ReNr. 2026-0814.pdf").
         */
        @Test
        void paartPdfUndXmlPerNummernAbgleich_referenziertPdf() throws IOException {
            Lieferanten lieferant = erstelleLieferant(10L);
            Email email = erstelleEmailMitLieferant(1L, lieferant);

            EmailAttachment pdfAtt = erstelleAttachment(1L, "ReNr. 2026-0814.pdf");
            EmailAttachment xmlAtt = erstelleAttachment(2L, "Rechnung2026-0814.xml");
            pdfAtt.setEmail(email);
            xmlAtt.setEmail(email);
            email.getAttachments().add(pdfAtt);
            email.getAttachments().add(xmlAtt);

            Files.write(tempDir.resolve(pdfAtt.getStoredFilename()), new byte[]{0x25, 0x50, 0x44, 0x46});
            Files.writeString(tempDir.resolve(xmlAtt.getStoredFilename()),
                    "<ubl:Invoice xmlns:cbc=\"urn:cbc\"><cbc:ID>2026-0814</cbc:ID></ubl:Invoice>");

            LieferantGeschaeftsdokument geschaeftsdaten = new LieferantGeschaeftsdokument();
            geschaeftsdaten.setDokumentNummer("2026-0814");
            geschaeftsdaten.setDetectedTyp(LieferantDokumentTyp.RECHNUNG);
            geschaeftsdaten.setDatenquelle("XML");

            when(emailRepository.findById(1L)).thenReturn(Optional.of(email));
            when(geminiAnalyseService.analyzeAndReturnData(any(Path.class), eq("Rechnung2026-0814.xml")))
                    .thenReturn(geschaeftsdaten);
            when(lieferantenRepository.findById(10L)).thenReturn(Optional.of(lieferant));
            when(lieferantGeschaeftsdokumentRepository
                    .existsByLieferantIdAndDokumentNummer(10L, "2026-0814")).thenReturn(false);
            when(lieferantDokumentRepository.save(any(LieferantDokument.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            int result = service.processLieferantAttachments(email);

            // Genau EIN Dokument (nicht zwei getrennte)
            assertThat(result).isEqualTo(1);

            ArgumentCaptor<LieferantDokument> captor = ArgumentCaptor.forClass(LieferantDokument.class);
            verify(lieferantDokumentRepository).save(captor.capture());
            LieferantDokument saved = captor.getValue();

            // Referenziert die PDF (Viewer zeigt PDF, nicht XML-Rohtext)
            assertThat(saved.getGespeicherterDateiname()).isEqualTo(pdfAtt.getStoredFilename());
            assertThat(saved.getOriginalDateiname()).isEqualTo("ReNr. 2026-0814.pdf");

            // Metadaten kommen aus der XML -> nur die XML wurde analysiert, nie die PDF
            verify(geminiAnalyseService).analyzeAndReturnData(any(Path.class), eq("Rechnung2026-0814.xml"));
            verify(geminiAnalyseService, never())
                    .analyzeAndReturnData(any(Path.class), eq("ReNr. 2026-0814.pdf"));

            // Beide Anhänge sind verarbeitet und mit dem Dokument verknüpft
            assertThat(pdfAtt.getAiProcessed()).isTrue();
            assertThat(xmlAtt.getAiProcessed()).isTrue();
            assertThat(pdfAtt.getLieferantDokument()).isEqualTo(saved);
            assertThat(xmlAtt.getLieferantDokument()).isEqualTo(saved);
        }

        /**
         * Fallback: Stimmen die Dateinamen nicht überein, aber die Mail enthält
         * genau eine PDF und genau eine XML, werden sie trotzdem gepaart.
         */
        @Test
        void paartGenauEinePdfUndEineXmlPerFallback() throws IOException {
            Lieferanten lieferant = erstelleLieferant(10L);
            Email email = erstelleEmailMitLieferant(1L, lieferant);

            EmailAttachment pdfAtt = erstelleAttachment(1L, "scan.pdf");
            EmailAttachment xmlAtt = erstelleAttachment(2L, "invoice.xml");
            pdfAtt.setEmail(email);
            xmlAtt.setEmail(email);
            email.getAttachments().add(pdfAtt);
            email.getAttachments().add(xmlAtt);

            Files.write(tempDir.resolve(pdfAtt.getStoredFilename()), new byte[]{0x25, 0x50, 0x44, 0x46});
            Files.writeString(tempDir.resolve(xmlAtt.getStoredFilename()),
                    "<ubl:Invoice xmlns:cbc=\"urn:cbc\"><cbc:ID>X-1</cbc:ID></ubl:Invoice>");

            LieferantGeschaeftsdokument geschaeftsdaten = new LieferantGeschaeftsdokument();
            geschaeftsdaten.setDokumentNummer("X-1");
            geschaeftsdaten.setDatenquelle("XML");

            when(emailRepository.findById(1L)).thenReturn(Optional.of(email));
            when(geminiAnalyseService.analyzeAndReturnData(any(Path.class), eq("invoice.xml")))
                    .thenReturn(geschaeftsdaten);
            when(lieferantenRepository.findById(10L)).thenReturn(Optional.of(lieferant));
            when(lieferantGeschaeftsdokumentRepository
                    .existsByLieferantIdAndDokumentNummer(10L, "X-1")).thenReturn(false);
            when(lieferantDokumentRepository.save(any(LieferantDokument.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            int result = service.processLieferantAttachments(email);

            assertThat(result).isEqualTo(1);
            ArgumentCaptor<LieferantDokument> captor = ArgumentCaptor.forClass(LieferantDokument.class);
            verify(lieferantDokumentRepository).save(captor.capture());
            assertThat(captor.getValue().getGespeicherterDateiname()).isEqualTo(pdfAtt.getStoredFilename());
            verify(geminiAnalyseService, never())
                    .analyzeAndReturnData(any(Path.class), eq("scan.pdf"));
        }

        /**
         * Robustheit: Fehlt die PDF auf der Platte, darf das Dokument NICHT auf die
         * fehlende PDF zeigen (sonst zeigt der Viewer wieder ins Leere). Es fällt auf
         * die vorhandene XML als Anzeige-Datei zurück.
         */
        @Test
        void faelltAufXmlZurueckWennPdfDateiFehlt() throws IOException {
            Lieferanten lieferant = erstelleLieferant(10L);
            Email email = erstelleEmailMitLieferant(1L, lieferant);

            EmailAttachment pdfAtt = erstelleAttachment(1L, "ReNr. 2026-0814.pdf");
            EmailAttachment xmlAtt = erstelleAttachment(2L, "Rechnung2026-0814.xml");
            pdfAtt.setEmail(email);
            xmlAtt.setEmail(email);
            email.getAttachments().add(pdfAtt);
            email.getAttachments().add(xmlAtt);

            // NUR die XML existiert auf der Platte, die PDF fehlt
            Files.writeString(tempDir.resolve(xmlAtt.getStoredFilename()),
                    "<ubl:Invoice xmlns:cbc=\"urn:cbc\"><cbc:ID>2026-0814</cbc:ID></ubl:Invoice>");

            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setDokumentNummer("2026-0814");
            gd.setDatenquelle("XML");

            when(emailRepository.findById(1L)).thenReturn(Optional.of(email));
            when(geminiAnalyseService.analyzeAndReturnData(any(Path.class), eq("Rechnung2026-0814.xml")))
                    .thenReturn(gd);
            when(lieferantenRepository.findById(10L)).thenReturn(Optional.of(lieferant));
            when(lieferantGeschaeftsdokumentRepository
                    .existsByLieferantIdAndDokumentNummer(10L, "2026-0814")).thenReturn(false);
            when(lieferantDokumentRepository.save(any(LieferantDokument.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            int result = service.processLieferantAttachments(email);

            assertThat(result).isEqualTo(1);
            ArgumentCaptor<LieferantDokument> captor = ArgumentCaptor.forClass(LieferantDokument.class);
            verify(lieferantDokumentRepository).save(captor.capture());
            // Fällt auf die vorhandene XML zurück statt auf die fehlende PDF
            assertThat(captor.getValue().getGespeicherterDateiname()).isEqualTo(xmlAtt.getStoredFilename());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.4.7 Backfill: bereits importierte XML-Dokumente auf PDF umstellen
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class BackfillXmlAufPdf {

        @Test
        void stelltXmlDokumentAufPdfDerselbenMailUm() throws IOException {
            Lieferanten lieferant = erstelleLieferant(10L);
            Email email = erstelleEmailMitLieferant(1L, lieferant);

            EmailAttachment pdfAtt = new EmailAttachment();
            pdfAtt.setId(1L);
            pdfAtt.setOriginalFilename("ReNr. 2026-0814.pdf");
            pdfAtt.setStoredFilename("uuid_pdf.pdf");
            pdfAtt.setInlineAttachment(false);
            pdfAtt.setEmail(email);
            // PDF muss physisch existieren, sonst gilt sie nicht als Backfill-Kandidat
            Files.write(tempDir.resolve(pdfAtt.getStoredFilename()), new byte[]{0x25, 0x50, 0x44, 0x46});

            EmailAttachment xmlAtt = new EmailAttachment();
            xmlAtt.setId(2L);
            xmlAtt.setOriginalFilename("Rechnung2026-0814.xml");
            xmlAtt.setStoredFilename("uuid_xml.xml");
            xmlAtt.setInlineAttachment(false);
            xmlAtt.setEmail(email);

            email.getAttachments().add(pdfAtt);
            email.getAttachments().add(xmlAtt);

            // Bestehendes (falsch importiertes) Dokument zeigt auf die XML
            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setDokumentNummer("2026-0814");
            LieferantDokument doc = new LieferantDokument();
            doc.setId(100L);
            doc.setLieferant(lieferant);
            doc.setOriginalDateiname("Rechnung2026-0814.xml");
            doc.setGespeicherterDateiname("uuid_xml.xml");
            doc.setGeschaeftsdaten(gd);
            xmlAtt.setLieferantDokument(doc);

            when(lieferantDokumentRepository.findMitXmlAnzeigedatei()).thenReturn(List.of(doc));
            when(emailAttachmentRepository.findByLieferantDokumentId(100L)).thenReturn(List.of(xmlAtt));

            int result = service.backfillXmlDokumenteAufPdf();

            assertThat(result).isEqualTo(1);
            // Dokument referenziert jetzt die PDF
            assertThat(doc.getGespeicherterDateiname()).isEqualTo("uuid_pdf.pdf");
            assertThat(doc.getOriginalDateiname()).isEqualTo("ReNr. 2026-0814.pdf");
            verify(lieferantDokumentRepository).save(doc);
            // PDF-Attachment wurde nachträglich verknüpft
            assertThat(pdfAtt.getLieferantDokument()).isEqualTo(doc);
            assertThat(pdfAtt.getAiProcessed()).isTrue();
        }

        @Test
        void laesstDokumentUnberuehrtWennKeinePdfInMail() {
            Lieferanten lieferant = erstelleLieferant(10L);
            Email email = erstelleEmailMitLieferant(1L, lieferant);

            EmailAttachment xmlAtt = new EmailAttachment();
            xmlAtt.setId(2L);
            xmlAtt.setOriginalFilename("Rechnung.xml");
            xmlAtt.setStoredFilename("uuid_xml.xml");
            xmlAtt.setInlineAttachment(false);
            xmlAtt.setEmail(email);
            email.getAttachments().add(xmlAtt);

            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setDokumentNummer("RE-1");
            LieferantDokument doc = new LieferantDokument();
            doc.setId(100L);
            doc.setGespeicherterDateiname("uuid_xml.xml");
            doc.setGeschaeftsdaten(gd);
            xmlAtt.setLieferantDokument(doc);

            when(lieferantDokumentRepository.findMitXmlAnzeigedatei()).thenReturn(List.of(doc));
            when(emailAttachmentRepository.findByLieferantDokumentId(100L)).thenReturn(List.of(xmlAtt));

            int result = service.backfillXmlDokumenteAufPdf();

            assertThat(result).isEqualTo(0);
            assertThat(doc.getGespeicherterDateiname()).isEqualTo("uuid_xml.xml"); // unverändert
            verify(lieferantDokumentRepository, never()).save(any(LieferantDokument.class));
        }

        /**
         * Sicherheit: Eine PDF, die bereits zu einem ANDEREN Dokument gehört, darf
         * der Backfill nicht "hijacken". Das XML-Dokument bleibt unverändert.
         */
        @Test
        void hijacktKeinePdfDieBereitsEinemAnderenDokumentGehoert() {
            Lieferanten lieferant = erstelleLieferant(10L);
            Email email = erstelleEmailMitLieferant(1L, lieferant);

            EmailAttachment pdfAtt = new EmailAttachment();
            pdfAtt.setId(1L);
            pdfAtt.setOriginalFilename("ReNr. 2026-0814.pdf");
            pdfAtt.setStoredFilename("uuid_pdf.pdf");
            pdfAtt.setInlineAttachment(false);
            pdfAtt.setEmail(email);

            EmailAttachment xmlAtt = new EmailAttachment();
            xmlAtt.setId(2L);
            xmlAtt.setOriginalFilename("Rechnung2026-0814.xml");
            xmlAtt.setStoredFilename("uuid_xml.xml");
            xmlAtt.setInlineAttachment(false);
            xmlAtt.setEmail(email);

            email.getAttachments().add(pdfAtt);
            email.getAttachments().add(xmlAtt);

            // PDF gehört bereits einem ANDEREN Dokument (id 200)
            LieferantDokument fremdesDoc = new LieferantDokument();
            fremdesDoc.setId(200L);
            pdfAtt.setLieferantDokument(fremdesDoc);

            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setDokumentNummer("2026-0814");
            LieferantDokument doc = new LieferantDokument();
            doc.setId(100L);
            doc.setGespeicherterDateiname("uuid_xml.xml");
            doc.setGeschaeftsdaten(gd);
            xmlAtt.setLieferantDokument(doc);

            when(lieferantDokumentRepository.findMitXmlAnzeigedatei()).thenReturn(List.of(doc));
            when(emailAttachmentRepository.findByLieferantDokumentId(100L)).thenReturn(List.of(xmlAtt));

            int result = service.backfillXmlDokumenteAufPdf();

            assertThat(result).isEqualTo(0);
            assertThat(doc.getGespeicherterDateiname()).isEqualTo("uuid_xml.xml"); // unverändert
            verify(lieferantDokumentRepository, never()).save(any(LieferantDokument.class));
        }

        /**
         * Robustheit: Existiert die PDF-Datei nicht auf der Platte, darf der Backfill
         * ein noch anzeigbares XML-Dokument NICHT auf die fehlende PDF umbiegen.
         */
        @Test
        void laesstDokumentUnberuehrtWennPdfDateiFehlt() {
            Lieferanten lieferant = erstelleLieferant(10L);
            Email email = erstelleEmailMitLieferant(1L, lieferant);

            EmailAttachment pdfAtt = new EmailAttachment();
            pdfAtt.setId(1L);
            pdfAtt.setOriginalFilename("ReNr. 2026-0814.pdf");
            pdfAtt.setStoredFilename("uuid_pdf_fehlt.pdf"); // Datei wird NICHT angelegt
            pdfAtt.setInlineAttachment(false);
            pdfAtt.setEmail(email);

            EmailAttachment xmlAtt = new EmailAttachment();
            xmlAtt.setId(2L);
            xmlAtt.setOriginalFilename("Rechnung2026-0814.xml");
            xmlAtt.setStoredFilename("uuid_xml.xml");
            xmlAtt.setInlineAttachment(false);
            xmlAtt.setEmail(email);

            email.getAttachments().add(pdfAtt);
            email.getAttachments().add(xmlAtt);

            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setDokumentNummer("2026-0814");
            LieferantDokument doc = new LieferantDokument();
            doc.setId(100L);
            doc.setGespeicherterDateiname("uuid_xml.xml");
            doc.setGeschaeftsdaten(gd);
            xmlAtt.setLieferantDokument(doc);

            when(lieferantDokumentRepository.findMitXmlAnzeigedatei()).thenReturn(List.of(doc));
            when(emailAttachmentRepository.findByLieferantDokumentId(100L)).thenReturn(List.of(xmlAtt));

            int result = service.backfillXmlDokumenteAufPdf();

            assertThat(result).isEqualTo(0);
            assertThat(doc.getGespeicherterDateiname()).isEqualTo("uuid_xml.xml"); // unverändert
            verify(lieferantDokumentRepository, never()).save(any(LieferantDokument.class));
        }
    }
}
