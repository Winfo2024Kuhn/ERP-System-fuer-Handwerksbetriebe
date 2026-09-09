package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests für die KI-gestützte Verarbeitung von Steuerberater-E-Mails:
 * Sammel-PDF-Split, Mitarbeiter-Zuweisung und Korrektur-Ersetzung.
 */
@ExtendWith(MockitoExtension.class)
class SteuerberaterEmailProcessingServiceTest {

    @Mock
    private SteuerberaterKontaktRepository steuerberaterRepository;
    @Mock
    private MitarbeiterRepository mitarbeiterRepository;
    @Mock
    private LohnabrechnungRepository lohnabrechnungRepository;
    @Mock
    private BwaUploadRepository bwaUploadRepository;
    @Mock
    private EmailRepository emailRepository;
    @Mock
    private GeminiDokumentAnalyseService geminiService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SteuerberaterEmailProcessingService service;

    @TempDir
    Path mailAttachmentDir;

    @TempDir
    Path lohnabrechnungDir;

    private SteuerberaterKontakt steuerberater;
    private Mitarbeiter max;
    private Mitarbeiter erika;
    private Email email;
    private EmailAttachment attachment;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(service, "mailAttachmentDir", mailAttachmentDir.toString());
        ReflectionTestUtils.setField(service, "lohnabrechnungDir", lohnabrechnungDir.toString());
        ReflectionTestUtils.setField(service, "bwaDir", lohnabrechnungDir.toString());

        steuerberater = new SteuerberaterKontakt();
        steuerberater.setName("Steuerkanzlei Test");
        steuerberater.setEmail("kanzlei@steuerkanzlei-test.example");
        steuerberater.setAktiv(true);
        steuerberater.setAutoProcessEmails(true);

        max = new Mitarbeiter();
        ReflectionTestUtils.setField(max, "id", 1L);
        max.setVorname("Max");
        max.setNachname("Mustermann");
        max.setAktiv(true);

        erika = new Mitarbeiter();
        ReflectionTestUtils.setField(erika, "id", 2L);
        erika.setVorname("Erika");
        erika.setNachname("Musterfrau");
        erika.setAktiv(true);

        // Zweiseitige Test-PDF als E-Mail-Anhang ablegen
        byte[] pdf = erzeugePdf(2);
        Files.write(mailAttachmentDir.resolve("stored-uuid.pdf"), pdf);

        attachment = new EmailAttachment();
        attachment.setOriginalFilename("2026-05.pdf"); // bewusst OHNE "lohn"-Keyword
        attachment.setStoredFilename("stored-uuid.pdf");

        email = new Email();
        ReflectionTestUtils.setField(email, "id", 42L);
        email.setFromAddress("kanzlei@steuerkanzlei-test.example");
        email.getAttachments().add(attachment);
    }

    private byte[] erzeugePdf(int seiten) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < seiten; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private void mockSteuerberaterErkannt() {
        when(steuerberaterRepository.findByEmailIgnoreCase(anyString()))
                .thenReturn(Optional.of(steuerberater));
    }

    /**
     * Regressionstest: PDFs ohne "lohn"-Keyword im Dateinamen wurden früher
     * komplett übersprungen – jetzt klassifiziert die KI jede Steuerberater-PDF.
     */
    @Test
    void sammelPdfOhneKeywordWirdGesplittetUndProMitarbeiterZugewiesen() {
        mockSteuerberaterErkannt();
        when(mitarbeiterRepository.findAktiveMenschen()).thenReturn(List.of(max, erika));
        when(lohnabrechnungRepository.existsBySourceEmailIdAndOriginalDateiname(anyLong(), anyString()))
                .thenReturn(false);
        when(lohnabrechnungRepository.findByMitarbeiterIdAndJahrAndMonat(anyLong(), any(), any()))
                .thenReturn(Optional.empty());
        when(geminiService.rufGeminiApiMitPrompt(any(), anyString(), anyString(), anyBoolean()))
                .thenReturn("""
                        {
                          "dokumentTyp": "LOHNABRECHNUNG",
                          "abrechnungen": [
                            {"mitarbeiterName": "Max Mustermann", "seiten": "1", "monat": 5, "jahr": 2026,
                             "bruttolohn": 2500.00, "nettolohn": 1800.50},
                            {"mitarbeiterName": "Erika Musterfrau", "seiten": "2", "monat": 5, "jahr": 2026,
                             "bruttolohn": 3000.00, "nettolohn": 2100.00}
                          ]
                        }
                        """);

        boolean verarbeitet = service.processSteuerberaterEmail(email);

        assertThat(verarbeitet).isTrue();
        ArgumentCaptor<Lohnabrechnung> captor = ArgumentCaptor.forClass(Lohnabrechnung.class);
        verify(lohnabrechnungRepository, times(2)).save(captor.capture());

        List<Lohnabrechnung> gespeichert = captor.getAllValues();
        assertThat(gespeichert).extracting(la -> la.getMitarbeiter().getNachname())
                .containsExactlyInAnyOrder("Mustermann", "Musterfrau");
        assertThat(gespeichert).allSatisfy(la -> {
            assertThat(la.getJahr()).isEqualTo(2026);
            assertThat(la.getMonat()).isEqualTo(5);
            assertThat(la.getStatus()).isEqualTo(LohnabrechnungStatus.ANALYSIERT);
            // Jede Abrechnung bekommt ihre eigene gesplittete Teil-PDF (1 Seite)
            Path teilPdf = lohnabrechnungDir.resolve(la.getGespeicherterDateiname());
            assertThat(teilPdf).exists();
            try (PDDocument teil = Loader.loadPDF(teilPdf.toFile())) {
                assertThat(teil.getNumberOfPages()).isEqualTo(1);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    /**
     * Korrektur-Mail: Der Steuerberater schickt eine geänderte Abrechnung für
     * denselben Monat – der alte Eintrag wird aktualisiert, nicht dupliziert.
     */
    @Test
    void korrekturMailErsetztBestehendeAbrechnungDesselbenMonats() throws Exception {
        mockSteuerberaterErkannt();
        when(mitarbeiterRepository.findAktiveMenschen()).thenReturn(List.of(max));
        when(lohnabrechnungRepository.existsBySourceEmailIdAndOriginalDateiname(anyLong(), anyString()))
                .thenReturn(false);

        // Bestehende Abrechnung mit alter Split-Datei
        Files.write(lohnabrechnungDir.resolve("alte-version.pdf"), new byte[]{9});
        Lohnabrechnung bestehend = new Lohnabrechnung();
        ReflectionTestUtils.setField(bestehend, "id", 7L);
        bestehend.setMitarbeiter(max);
        bestehend.setJahr(2026);
        bestehend.setMonat(5);
        bestehend.setGespeicherterDateiname("alte-version.pdf");
        when(lohnabrechnungRepository.findByMitarbeiterIdAndJahrAndMonat(1L, 2026, 5))
                .thenReturn(Optional.of(bestehend));

        when(geminiService.rufGeminiApiMitPrompt(any(), anyString(), anyString(), anyBoolean()))
                .thenReturn("""
                        {
                          "dokumentTyp": "LOHNABRECHNUNG",
                          "abrechnungen": [
                            {"mitarbeiterName": "Max Mustermann", "seiten": "1-2", "monat": 5, "jahr": 2026,
                             "bruttolohn": 2600.00, "nettolohn": 1900.00}
                          ]
                        }
                        """);

        service.processSteuerberaterEmail(email);

        ArgumentCaptor<Lohnabrechnung> captor = ArgumentCaptor.forClass(Lohnabrechnung.class);
        verify(lohnabrechnungRepository).save(captor.capture());

        Lohnabrechnung aktualisiert = captor.getValue();
        assertThat(aktualisiert.getId()).isEqualTo(7L); // gleiche Entity, kein Duplikat
        assertThat(aktualisiert.getBruttolohn()).isEqualByComparingTo("2600.00");
        assertThat(aktualisiert.getGespeicherterDateiname()).isNotEqualTo("alte-version.pdf");
        // Alte Split-Datei wurde aufgeräumt
        assertThat(lohnabrechnungDir.resolve("alte-version.pdf")).doesNotExist();
    }

    /**
     * Idempotenz (Backfill): dieselbe E-Mail + Datei wird nicht doppelt importiert.
     */
    @Test
    void bereitsImportierteAnhaengeWerdenUebersprungen() {
        mockSteuerberaterErkannt();
        when(lohnabrechnungRepository.existsBySourceEmailIdAndOriginalDateiname(42L, "2026-05.pdf"))
                .thenReturn(true);

        service.processSteuerberaterEmail(email);

        verify(geminiService, never()).rufGeminiApiMitPrompt(any(), anyString(), anyString(), anyBoolean());
        verify(lohnabrechnungRepository, never()).save(any());
    }

    /**
     * Nicht zuordenbare Segmente dürfen keine Abrechnung erzeugen,
     * die zuordenbaren aber schon.
     */
    @Test
    void unbekannterMitarbeiterWirdUebersprungenAndereTrotzdemAngelegt() {
        mockSteuerberaterErkannt();
        when(mitarbeiterRepository.findAktiveMenschen()).thenReturn(List.of(max));
        when(lohnabrechnungRepository.existsBySourceEmailIdAndOriginalDateiname(anyLong(), anyString()))
                .thenReturn(false);
        when(lohnabrechnungRepository.findByMitarbeiterIdAndJahrAndMonat(anyLong(), any(), any()))
                .thenReturn(Optional.empty());
        when(geminiService.rufGeminiApiMitPrompt(any(), anyString(), anyString(), anyBoolean()))
                .thenReturn("""
                        {
                          "dokumentTyp": "LOHNABRECHNUNG",
                          "abrechnungen": [
                            {"mitarbeiterName": "Max Mustermann", "seiten": "1", "monat": 5, "jahr": 2026},
                            {"mitarbeiterName": "Unbekannte Person", "seiten": "2", "monat": 5, "jahr": 2026}
                          ]
                        }
                        """);

        service.processSteuerberaterEmail(email);

        ArgumentCaptor<Lohnabrechnung> captor = ArgumentCaptor.forClass(Lohnabrechnung.class);
        verify(lohnabrechnungRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getMitarbeiter().getNachname()).isEqualTo("Mustermann");
    }

    /**
     * Als SONSTIGES klassifizierte PDFs (z.B. Honorarrechnung der Kanzlei)
     * erzeugen keine Lohnabrechnung.
     */
    @Test
    void sonstigePdfsErzeugenKeineLohnabrechnung() {
        mockSteuerberaterErkannt();
        when(lohnabrechnungRepository.existsBySourceEmailIdAndOriginalDateiname(anyLong(), anyString()))
                .thenReturn(false);
        when(geminiService.rufGeminiApiMitPrompt(any(), anyString(), anyString(), anyBoolean()))
                .thenReturn("{\"dokumentTyp\": \"SONSTIGES\", \"abrechnungen\": []}");

        service.processSteuerberaterEmail(email);

        verify(lohnabrechnungRepository, never()).save(any());
    }

    /**
     * E-Mails fremder Absender werden nicht als Steuerberater-Mail verarbeitet.
     */
    @Test
    void fremdeAbsenderWerdenIgnoriert() {
        when(steuerberaterRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(steuerberaterRepository.findByAktivTrueAndAutoProcessEmailsTrue()).thenReturn(List.of());
        email.setFromAddress("max.mustermann@example.com");

        boolean verarbeitet = service.processSteuerberaterEmail(email);

        assertThat(verarbeitet).isFalse();
        verifyNoInteractions(geminiService);
    }

    /**
     * Domain-Match: Auch andere Absender derselben Kanzlei-Domain werden erkannt.
     */
    @Test
    void andereAbsenderDerSteuerberaterDomainWerdenErkannt() {
        when(steuerberaterRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(steuerberaterRepository.findByAktivTrueAndAutoProcessEmailsTrue())
                .thenReturn(List.of(steuerberater));
        when(lohnabrechnungRepository.existsBySourceEmailIdAndOriginalDateiname(anyLong(), anyString()))
                .thenReturn(true); // Verarbeitung selbst hier nicht relevant
        email.setFromAddress("sekretariat@steuerkanzlei-test.example");

        boolean verarbeitet = service.processSteuerberaterEmail(email);

        assertThat(verarbeitet).isTrue();
    }
}
