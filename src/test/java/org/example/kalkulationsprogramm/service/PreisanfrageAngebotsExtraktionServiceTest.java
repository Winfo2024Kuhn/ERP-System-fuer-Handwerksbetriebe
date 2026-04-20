package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Preisanfrage;
import org.example.kalkulationsprogramm.domain.PreisanfrageAngebot;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferant;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferantStatus;
import org.example.kalkulationsprogramm.domain.PreisanfragePosition;
import org.example.kalkulationsprogramm.repository.PreisanfrageAngebotRepository;
import org.example.kalkulationsprogramm.repository.PreisanfrageLieferantRepository;
import org.example.kalkulationsprogramm.repository.PreisanfragePositionRepository;
import org.example.kalkulationsprogramm.repository.PreisanfrageRepository;
import org.example.kalkulationsprogramm.service.ArtikelMatchingAgentService.Ergebnis;
import org.example.kalkulationsprogramm.service.ArtikelMatchingAgentService.MatchingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit-Tests fuer {@link PreisanfrageAngebotsExtraktionService}. Gemini wird
 * komplett gemockt — keine echten HTTP-Calls, kein Netzwerk.
 */
@ExtendWith(MockitoExtension.class)
class PreisanfrageAngebotsExtraktionServiceTest {

    @Mock private PreisanfrageRepository preisanfrageRepository;
    @Mock private PreisanfrageLieferantRepository preisanfrageLieferantRepository;
    @Mock private PreisanfragePositionRepository preisanfragePositionRepository;
    @Mock private PreisanfrageAngebotRepository preisanfrageAngebotRepository;
    @Mock private GeminiDokumentAnalyseService geminiService;
    @Mock private ArtikelMatchingAgentService artikelMatchingAgentService;

    @TempDir Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PreisanfrageAngebotsExtraktionService service;

    @BeforeEach
    void setUp() {
        service = new PreisanfrageAngebotsExtraktionService(
                preisanfrageRepository,
                preisanfrageLieferantRepository,
                preisanfragePositionRepository,
                preisanfrageAngebotRepository,
                geminiService,
                artikelMatchingAgentService,
                objectMapper);
        service.setAttachmentDirForTest(tempDir.toString());
    }

    // ─────────────────────────────────────────────────────────────
    // Prompt-Build
    // ─────────────────────────────────────────────────────────────

    @Test
    void prompt_listet_positionen_mit_id_und_produktname() {
        PreisanfragePosition p1 = buildPosition(10L, "Flachstahl 30x5", "FL30-5", "S235JR", new BigDecimal("100"), "kg");
        PreisanfragePosition p2 = buildPosition(11L, "Rundrohr 25", "RO25", "S235JR", new BigDecimal("20"), "m");

        String prompt = service.baueExtraktionsPrompt(List.of(p1, p2));

        assertThat(prompt).contains("positionId=10").contains("Flachstahl 30x5").contains("FL30-5");
        assertThat(prompt).contains("positionId=11").contains("Rundrohr 25");
        assertThat(prompt).contains("Zusatzpositionen").contains("DIENSTLEISTUNG");
        assertThat(prompt).contains("'angebote'").contains("\"zusatzpositionen\"");
    }

    // ─────────────────────────────────────────────────────────────
    // extrahiereFuerLieferant — Happy Path
    // ─────────────────────────────────────────────────────────────

    @Test
    void happy_path_legt_angebote_an_und_triggert_artikelstamm_match() throws Exception {
        PreisanfrageLieferant pal = buildPalMitAnhang(10L, "angebot.pdf");
        PreisanfragePosition p1 = buildPosition(1L, "Flachstahl 30x5", "FL30-5", "S235JR", new BigDecimal("100"), "kg");
        PreisanfragePosition p2 = buildPosition(2L, "Rundrohr 25", "RO25", "S235JR", new BigDecimal("20"), "m");

        when(preisanfrageLieferantRepository.findById(10L)).thenReturn(Optional.of(pal));
        when(preisanfrageAngebotRepository.findByPreisanfrageLieferantId(10L)).thenReturn(List.of());
        when(preisanfrageAngebotRepository.save(any(PreisanfrageAngebot.class))).thenAnswer(i -> i.getArgument(0));
        when(artikelMatchingAgentService.matcheOderSchlageAn(any(), any(), any()))
                .thenReturn(new MatchingResult(Ergebnis.PREIS_AKTUALISIERT, "ok"));

        String json = """
                {
                  "angebote": [
                    {"positionId": 1, "einzelpreis": 1.85, "preiseinheit": "kg",
                     "mwstProzent": 19, "lieferzeitTage": 7, "gueltigBis": "2026-05-30",
                     "bemerkung": "Staffelpreis"},
                    {"positionId": 2, "einzelpreis": 12.50, "preiseinheit": "m"}
                  ],
                  "zusatzpositionen": [
                    {"bezeichnung": "Zuschnitt", "einzelpreis": 3.5, "positionsTyp": "DIENSTLEISTUNG"}
                  ]
                }
                """;
        when(geminiService.rufGeminiApiMitPrompt(any(byte[].class), eq("application/pdf"), anyString(), anyBoolean()))
                .thenReturn(json);

        schreibeLeeresPdfAlsAttachment(pal);

        int angelegt = service.extrahiereFuerLieferant(10L, List.of(p1, p2));

        assertThat(angelegt).isEqualTo(2);

        ArgumentCaptor<PreisanfrageAngebot> captor = ArgumentCaptor.forClass(PreisanfrageAngebot.class);
        verify(preisanfrageAngebotRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(PreisanfrageAngebot::getEinzelpreis)
                .extracting(BigDecimal::doubleValue)
                .containsExactlyInAnyOrder(1.85, 12.5);
        assertThat(captor.getAllValues()).allMatch(a -> "ki-extraktion".equals(a.getErfasstDurch()));

        // Artikelstamm-Match wird fuer beide Positionen getriggert (DRY: bestehender Agent)
        verify(artikelMatchingAgentService, org.mockito.Mockito.times(2))
                .matcheOderSchlageAn(any(), any(Lieferanten.class), any());
    }

    // ─────────────────────────────────────────────────────────────
    // Zusatzpositionen landen NICHT im Artikelstamm
    // ─────────────────────────────────────────────────────────────

    @Test
    void zusatzpositionen_triggern_kein_artikelstamm_match() throws Exception {
        PreisanfrageLieferant pal = buildPalMitAnhang(20L, "dienstleistung.pdf");
        PreisanfragePosition p1 = buildPosition(1L, "Flachstahl 30x5", "FL30-5", "S235JR", new BigDecimal("100"), "kg");
        when(preisanfrageLieferantRepository.findById(20L)).thenReturn(Optional.of(pal));
        when(preisanfrageAngebotRepository.findByPreisanfrageLieferantId(20L)).thenReturn(List.of());
        when(preisanfrageAngebotRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(artikelMatchingAgentService.matcheOderSchlageAn(any(), any(), any()))
                .thenReturn(new MatchingResult(Ergebnis.PREIS_AKTUALISIERT, "ok"));

        String json = """
                {
                  "angebote": [
                    {"positionId": 1, "einzelpreis": 1.85, "preiseinheit": "kg"}
                  ],
                  "zusatzpositionen": [
                    {"bezeichnung": "Zuschnitt", "einzelpreis": 3.5, "positionsTyp": "DIENSTLEISTUNG"},
                    {"bezeichnung": "Fracht", "einzelpreis": 25.0, "positionsTyp": "NEBENKOSTEN"}
                  ]
                }
                """;
        when(geminiService.rufGeminiApiMitPrompt(any(byte[].class), eq("application/pdf"), anyString(), anyBoolean()))
                .thenReturn(json);
        schreibeLeeresPdfAlsAttachment(pal);

        int angelegt = service.extrahiereFuerLieferant(20L, List.of(p1));

        assertThat(angelegt).isEqualTo(1);
        // GENAU 1 Artikelstamm-Match — die 2 Zusatzpositionen werden nicht an den Agent weitergereicht
        verify(artikelMatchingAgentService, org.mockito.Mockito.times(1))
                .matcheOderSchlageAn(any(), any(), any());
    }

    // ─────────────────────────────────────────────────────────────
    // Idempotenz: bestehende Angebote werden nicht ueberschrieben
    // ─────────────────────────────────────────────────────────────

    @Test
    void bestehende_angebote_werden_nicht_ueberschrieben() throws Exception {
        PreisanfrageLieferant pal = buildPalMitAnhang(30L, "nochmal.pdf");
        PreisanfragePosition p1 = buildPosition(1L, "Flachstahl 30x5", "FL30-5", "S235JR", new BigDecimal("100"), "kg");

        PreisanfrageAngebot existierend = new PreisanfrageAngebot();
        existierend.setPreisanfrageLieferant(pal);
        existierend.setPreisanfragePosition(p1);
        existierend.setEinzelpreis(new BigDecimal("1.50")); // manuell eingetragen
        existierend.setErfasstDurch("manuell");

        when(preisanfrageLieferantRepository.findById(30L)).thenReturn(Optional.of(pal));
        when(preisanfrageAngebotRepository.findByPreisanfrageLieferantId(30L)).thenReturn(List.of(existierend));

        String json = """
                { "angebote": [ {"positionId": 1, "einzelpreis": 2.99, "preiseinheit": "kg"} ] }
                """;
        when(geminiService.rufGeminiApiMitPrompt(any(byte[].class), eq("application/pdf"), anyString(), anyBoolean()))
                .thenReturn(json);
        schreibeLeeresPdfAlsAttachment(pal);

        int angelegt = service.extrahiereFuerLieferant(30L, List.of(p1));

        assertThat(angelegt).isZero();
        verify(preisanfrageAngebotRepository, never()).save(any());
        verify(artikelMatchingAgentService, never()).matcheOderSchlageAn(any(), any(), any());
    }

    // ─────────────────────────────────────────────────────────────
    // Fehlerfaelle
    // ─────────────────────────────────────────────────────────────

    @Test
    void leere_gemini_antwort_wirft_illegalState() throws Exception {
        PreisanfrageLieferant pal = buildPalMitAnhang(40L, "leer.pdf");
        PreisanfragePosition p1 = buildPosition(1L, "Flachstahl 30x5", "FL30-5", "S235JR", new BigDecimal("100"), "kg");
        when(preisanfrageLieferantRepository.findById(40L)).thenReturn(Optional.of(pal));
        when(geminiService.rufGeminiApiMitPrompt(any(byte[].class), eq("application/pdf"), anyString(), anyBoolean()))
                .thenReturn("");
        schreibeLeeresPdfAlsAttachment(pal);

        assertThatThrownBy(() -> service.extrahiereFuerLieferant(40L, List.of(p1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("leere Antwort");
    }

    @Test
    void truncated_json_wirft_illegalState() throws Exception {
        PreisanfrageLieferant pal = buildPalMitAnhang(50L, "trunc.pdf");
        PreisanfragePosition p1 = buildPosition(1L, "Flachstahl 30x5", "FL30-5", "S235JR", new BigDecimal("100"), "kg");
        when(preisanfrageLieferantRepository.findById(50L)).thenReturn(Optional.of(pal));
        when(geminiService.rufGeminiApiMitPrompt(any(byte[].class), eq("application/pdf"), anyString(), anyBoolean()))
                .thenReturn("{\"angebote\": [ {\"positionId\": 1, \"einzel"); // abgeschnitten
        schreibeLeeresPdfAlsAttachment(pal);

        assertThatThrownBy(() -> service.extrahiereFuerLieferant(50L, List.of(p1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    void fehlender_pdf_anhang_wirft_illegalState() {
        PreisanfrageLieferant pal = buildPalOhneAnhang(60L);
        when(preisanfrageLieferantRepository.findById(60L)).thenReturn(Optional.of(pal));

        assertThatThrownBy(() -> service.extrahiereFuerLieferant(60L, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keinen PDF-Anhang");
    }

    @Test
    void extrahiereFuerPreisanfrage_negativeId_wirftIllegalArgument() {
        assertThatThrownBy(() -> service.extrahiereFuerPreisanfrage(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extrahiereFuerPreisanfrage_unbekannteId_wirftIllegalArgument() {
        when(preisanfrageRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.extrahiereFuerPreisanfrage(9999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9999");
    }

    @Test
    void extrahiereFuerPreisanfrage_ueberspringt_pal_mit_bestehenden_angeboten() {
        Preisanfrage pa = new Preisanfrage();
        pa.setId(100L);
        PreisanfrageLieferant palMitAngeboten = buildPalOhneAnhang(200L);
        palMitAngeboten.setStatus(PreisanfrageLieferantStatus.BEANTWORTET);
        Email mail = new Email();
        palMitAngeboten.setAntwortEmail(mail);

        when(preisanfrageRepository.findById(100L)).thenReturn(Optional.of(pa));
        when(preisanfrageLieferantRepository.findByPreisanfrageIdOrderByLieferant_LieferantennameAsc(100L))
                .thenReturn(List.of(palMitAngeboten));
        when(preisanfragePositionRepository.findByPreisanfrageIdOrderByReihenfolgeAsc(100L))
                .thenReturn(List.of());
        PreisanfrageAngebot existierend = new PreisanfrageAngebot();
        when(preisanfrageAngebotRepository.findByPreisanfrageLieferantId(200L))
                .thenReturn(List.of(existierend));

        PreisanfrageAngebotsExtraktionService.ExtraktionsErgebnis ergebnis =
                service.extrahiereFuerPreisanfrage(100L);

        assertThat(ergebnis.verarbeiteteLieferanten()).isZero();
        assertThat(ergebnis.extrahierteAngebote()).isZero();
        assertThat(ergebnis.hinweise()).hasSize(1);
        assertThat(ergebnis.hinweise().get(0)).contains("Angebote vorhanden");
    }

    // ─────────────────────────────────────────────────────────────
    // Test-Helfer
    // ─────────────────────────────────────────────────────────────

    private PreisanfragePosition buildPosition(Long id, String name, String extNr,
                                               String werkstoff, BigDecimal menge, String einheit) {
        PreisanfragePosition p = new PreisanfragePosition();
        p.setId(id);
        p.setProduktname(name);
        p.setExterneArtikelnummer(extNr);
        p.setWerkstoffName(werkstoff);
        p.setMenge(menge);
        p.setEinheit(einheit);
        return p;
    }

    private PreisanfrageLieferant buildPalMitAnhang(Long palId, String filename) {
        Lieferanten l = new Lieferanten();
        l.setId(99L);
        l.setLieferantenname("Max Mustermann Stahl");

        PreisanfrageLieferant pal = new PreisanfrageLieferant();
        pal.setId(palId);
        pal.setLieferant(l);
        pal.setStatus(PreisanfrageLieferantStatus.BEANTWORTET);

        Email mail = new Email();
        mail.setId(palId + 1000); // irgendeine ID
        mail.setAttachments(new ArrayList<>());

        EmailAttachment att = new EmailAttachment();
        att.setOriginalFilename(filename);
        att.setStoredFilename(filename);
        att.setInlineAttachment(false);
        att.setEmail(mail);
        mail.getAttachments().add(att);

        pal.setAntwortEmail(mail);
        return pal;
    }

    private PreisanfrageLieferant buildPalOhneAnhang(Long palId) {
        Lieferanten l = new Lieferanten();
        l.setId(99L);
        l.setLieferantenname("Max Mustermann Stahl");

        PreisanfrageLieferant pal = new PreisanfrageLieferant();
        pal.setId(palId);
        pal.setLieferant(l);
        pal.setStatus(PreisanfrageLieferantStatus.BEANTWORTET);

        Email mail = new Email();
        mail.setId(palId + 1000);
        mail.setAttachments(new ArrayList<>());
        pal.setAntwortEmail(mail);
        return pal;
    }

    /** Legt das PDF-File im TempDir ab, damit die Pfad-Resolution erfolgreich ist. */
    private void schreibeLeeresPdfAlsAttachment(PreisanfrageLieferant pal) throws Exception {
        EmailAttachment att = pal.getAntwortEmail().getAttachments().get(0);
        Path p = tempDir.resolve(att.getStoredFilename());
        Files.write(p, "PDF-DUMMY".getBytes());
    }
}
