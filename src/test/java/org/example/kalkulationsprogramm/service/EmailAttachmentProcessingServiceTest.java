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

    @TempDir
    Path tempDir;

    private EmailAttachmentProcessingService service;

    @BeforeEach
    void setUp() {
        service = new EmailAttachmentProcessingService(
                emailRepository, emailAttachmentRepository, lieferantDokumentRepository,
                lieferantenRepository, lieferantGeschaeftsdokumentRepository, geminiAnalyseService);
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
}
