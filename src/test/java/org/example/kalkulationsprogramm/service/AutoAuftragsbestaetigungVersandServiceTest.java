package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests für den positionenJson-Parser des AutoAuftragsbestaetigungVersandService.
 * Der Parser ist die einzige nicht-triviale Logik des Service — der Rest ist
 * Verdrahtung von bestehenden Bausteinen (PDF-Service, EmailService).
 */
@ExtendWith(MockitoExtension.class)
class AutoAuftragsbestaetigungVersandServiceTest {

    @Mock RechnungPdfService rechnungPdfService;
    @Mock SystemSettingsService systemSettingsService;
    @Mock EmailTextTemplateService emailTextTemplateService;
    @Mock AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository;
    @Mock FormularTemplateService formularTemplateService;
    @Mock FormularTextbausteinDefaultService formularTextbausteinDefaultService;
    @Mock EmailSignatureService emailSignatureService;

    private AutoAuftragsbestaetigungVersandService neuService() {
        return new AutoAuftragsbestaetigungVersandService(
                rechnungPdfService,
                systemSettingsService,
                emailTextTemplateService,
                ausgangsGeschaeftsDokumentRepository,
                formularTemplateService,
                formularTextbausteinDefaultService,
                emailSignatureService);
    }

    @Test
    void parser_leererInputLiefertLeereListe() {
        assertThat(AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks(null)).isEmpty();
        assertThat(AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks("")).isEmpty();
        assertThat(AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks("   ")).isEmpty();
    }

    @Test
    void parser_unterstuetztArrayUndBlocksObjekt() {
        String arrayJson = "[{\"type\":\"TEXT\",\"content\":\"Hallo\"}]";
        String objektJson = "{\"blocks\":[{\"type\":\"TEXT\",\"content\":\"Hallo\"}]}";

        List<ContentBlockDto> a = AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks(arrayJson);
        List<ContentBlockDto> b = AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks(objektJson);

        assertThat(a).hasSize(1);
        assertThat(b).hasSize(1);
        assertThat(a.get(0).type()).isEqualTo("TEXT");
        assertThat(a.get(0).text()).isEqualTo("Hallo");
    }

    @Test
    void parser_serviceBlockBerechnetGesamtMitRabatt() {
        String json = "[{\"type\":\"SERVICE\",\"title\":\"Stahlträger\",\"quantity\":10,\"price\":50,\"unit\":\"m\",\"discount\":10}]";

        List<ContentBlockDto> result = AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks(json);

        assertThat(result).hasSize(1);
        ContentBlockDto block = result.get(0);
        assertThat(block.type()).isEqualTo("SERVICE");
        assertThat(block.pos()).isEqualTo("1");
        assertThat(block.beschreibung()).isEqualTo("Stahlträger");
        assertThat(block.menge()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(block.einzelpreis()).isEqualByComparingTo("50");
        // 10 * 50 = 500, abzüglich 10% Rabatt = 450
        assertThat(block.gesamt()).isEqualByComparingTo("450.00");
        assertThat(block.einheit()).isEqualTo("m");
        assertThat(block.rabattProzent()).isEqualByComparingTo("10");
    }

    @Test
    void parser_sectionHeaderVerschachteltGibtHierarchischePositionen() {
        String json = "[{\"type\":\"SECTION_HEADER\",\"sectionLabel\":\"Aussenanlagen\",\"children\":["
                + "{\"type\":\"SERVICE\",\"title\":\"Tor\",\"quantity\":1,\"price\":1000},"
                + "{\"type\":\"SERVICE\",\"title\":\"Zaun\",\"quantity\":50,\"price\":80}"
                + "]}]";

        List<ContentBlockDto> result = AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks(json);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).type()).isEqualTo("SECTION_HEADER");
        assertThat(result.get(0).pos()).isEqualTo("1");
        assertThat(result.get(0).sectionLabel()).isEqualTo("Aussenanlagen");
        assertThat(result.get(1).type()).isEqualTo("SERVICE");
        assertThat(result.get(1).pos()).isEqualTo("1.1");
        assertThat(result.get(2).type()).isEqualTo("SERVICE");
        assertThat(result.get(2).pos()).isEqualTo("1.2");
    }

    @Test
    void parser_unbekannterBlockTypWirdIgnoriert() {
        String json = "[{\"type\":\"UNKNOWN\",\"content\":\"x\"},{\"type\":\"TEXT\",\"content\":\"echo\"}]";

        List<ContentBlockDto> result = AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo("TEXT");
    }

    @Test
    void parser_kaputtesJsonLiefertLeereListe() {
        List<ContentBlockDto> result = AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks("nicht-json");
        assertThat(result).isEmpty();
    }

    // ------------- ladeTemplateName: Fallback-Kette -------------
    // Hintergrund: Inhaber pflegen im Formularwesen typischerweise eine
    // einzige Briefpapier-Vorlage mit Vor-/Nachtexten für ALLE Dokumenttypen
    // und weisen diese nur dem Angebot zu. Ohne Fallback käme die Auto-AB
    // nach digitaler Annahme ohne Vor-/Nachtexte raus.

    @Test
    void ladeTemplateName_explizitAbZuordnungWirdBevorzugt() {
        when(formularTemplateService.getPreferredTemplateForDokumenttyp("Auftragsbestätigung", null))
                .thenReturn(Optional.of("AB-spezial"));

        AusgangsGeschaeftsDokument ab = baueAbMitVorgaengerAngebot();

        assertThat(neuService().ladeTemplateName(ab)).contains("AB-spezial");
    }

    @Test
    void ladeTemplateName_falltAufVorgaengerVorlageZurueckWennAbNichtZugewiesen() {
        when(formularTemplateService.getPreferredTemplateForDokumenttyp("Auftragsbestätigung", null))
                .thenReturn(Optional.empty());
        when(formularTemplateService.getPreferredTemplateForDokumenttyp("Angebot", null))
                .thenReturn(Optional.of("standard-briefpapier"));

        AusgangsGeschaeftsDokument ab = baueAbMitVorgaengerAngebot();

        assertThat(neuService().ladeTemplateName(ab)).contains("standard-briefpapier");
    }

    @Test
    void ladeTemplateName_ohneVorgaengerUndOhneAbZuordnungLiefertEmpty() {
        when(formularTemplateService.getPreferredTemplateForDokumenttyp("Auftragsbestätigung", null))
                .thenReturn(Optional.empty());

        AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
        ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);

        assertThat(neuService().ladeTemplateName(ab)).isEmpty();
    }

    private static AusgangsGeschaeftsDokument baueAbMitVorgaengerAngebot() {
        AusgangsGeschaeftsDokument angebot = new AusgangsGeschaeftsDokument();
        angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
        angebot.setDokumentNummer("AN-2026/05/0042");

        AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
        ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
        ab.setDokumentNummer("AB-2026/05/0042");
        ab.setVorgaenger(angebot);
        return ab;
    }
}
