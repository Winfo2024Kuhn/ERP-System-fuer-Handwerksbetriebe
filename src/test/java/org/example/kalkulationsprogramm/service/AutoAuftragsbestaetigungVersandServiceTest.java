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
    @Mock ProjektEmailArchivService projektEmailArchivService;
    @Mock org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository dokumentFreigabeRepository;
    @Mock org.example.kalkulationsprogramm.service.mail.SentMailArchiver sentMailArchiver;

    private AutoAuftragsbestaetigungVersandService neuService() {
        return new AutoAuftragsbestaetigungVersandService(
                rechnungPdfService,
                systemSettingsService,
                emailTextTemplateService,
                ausgangsGeschaeftsDokumentRepository,
                formularTemplateService,
                formularTextbausteinDefaultService,
                emailSignatureService,
                projektEmailArchivService,
                dokumentFreigabeRepository,
                sentMailArchiver);
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

    // ------------- versendeNachAnnahme + Archiv-Retry -------------

    @Test
    void versendeNachAnnahme_abNichtVorhanden_liefertFalseOhneVersand() {
        when(ausgangsGeschaeftsDokumentRepository.findById(99L))
                .thenReturn(Optional.empty());

        boolean ok = neuService().versendeNachAnnahme(99L, "max@example.de", "uuid-1");

        assertThat(ok).isFalse();
        org.mockito.Mockito.verifyNoInteractions(projektEmailArchivService);
    }

    @Test
    void archivierung_wirdBeiLockTimeoutWiederholt() {
        // Produktions-Muster (siehe Funnel-Bestaetigung, anfrageId=146): der
        // IMAP-Import haelt Locks auf der email-Tabelle. Der zweite Versuch
        // nach der Pause muss die Mail archivieren — der IMAP-Sent-Poll ist
        // KEIN Sicherheitsnetz.
        AutoAuftragsbestaetigungVersandService service = neuService();
        service.archivRetryPauseMillis = 0L;
        AusgangsGeschaeftsDokument ab = baueAbMitProjekt();
        when(projektEmailArchivService.archiviereVersandteEmail(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new org.springframework.dao.PessimisticLockingFailureException("Lock wait timeout"))
                .thenReturn(null);

        service.archiviereAlsProjektEmail(ab, "max@example.de", "kontakt@example.de",
                "Subject", "<p>Body</p>", "<msgid@example.de>", java.nio.file.Path.of("egal.pdf"), "AB.pdf");

        org.mockito.Mockito.verify(projektEmailArchivService, org.mockito.Mockito.times(2))
                .archiviereVersandteEmail(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void archivierung_gibtNachDreiVersuchenAufOhneZuWerfen() {
        AutoAuftragsbestaetigungVersandService service = neuService();
        service.archivRetryPauseMillis = 0L;
        AusgangsGeschaeftsDokument ab = baueAbMitProjekt();
        when(projektEmailArchivService.archiviereVersandteEmail(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new org.springframework.dao.PessimisticLockingFailureException("Lock wait timeout"));

        // Darf nicht werfen — die SMTP-Mail ist bereits beim Kunden.
        service.archiviereAlsProjektEmail(ab, "max@example.de", "kontakt@example.de",
                "Subject", "<p>Body</p>", "<msgid@example.de>", java.nio.file.Path.of("egal.pdf"), "AB.pdf");

        org.mockito.Mockito.verify(projektEmailArchivService, org.mockito.Mockito.times(3))
                .archiviereVersandteEmail(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void archivierung_ohneProjektWirdUebersprungen() {
        AutoAuftragsbestaetigungVersandService service = neuService();
        AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
        ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
        ab.setDokumentNummer("AB-2026/07/0001");

        service.archiviereAlsProjektEmail(ab, "max@example.de", "kontakt@example.de",
                "Subject", "<p>Body</p>", "<msgid@example.de>", java.nio.file.Path.of("egal.pdf"), "AB.pdf");

        org.mockito.Mockito.verifyNoInteractions(projektEmailArchivService);
    }

    // ------------- Standard-Vor-/Nachtexte (Bug: AB enthielt nur Leistungen) -------------
    // Beim Typwechsel Angebot -> AB entfernt der DokumentService die Angebots-
    // Textbausteine und setzt nur ein Flag fuer den DocumentEditor. Die Auto-AB
    // ist aber sofort digital angenommen und laeuft nie durch den Editor —
    // die AB-Texte muessen deshalb im Backend eingesetzt werden.

    @Test
    void standardtexte_werdenVorDieErsteLeistungUndAnsEndeGesetzt() throws Exception {
        String json = "{\"blocks\":["
                + "{\"type\":\"SERVICE\",\"title\":\"Gitterrost\",\"quantity\":1,\"price\":100},"
                + "{\"type\":\"SUBTOTAL\"}"
                + "],\"standardTextbausteineErneuern\":true}";

        String neu = AutoAuftragsbestaetigungVersandService.baueJsonMitStandardtexten(
                json,
                defaults(textbaustein(1L, "<p>Guten Tag {{KUNDENNAME}},</p>"),
                        textbaustein(2L, "<p>Mit freundlichen Gruessen</p>")),
                java.util.Map.of("KUNDENNAME", "Max Mustermann"));

        com.fasterxml.jackson.databind.JsonNode blocks =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(neu).get("blocks");

        assertThat(blocks).hasSize(4);
        assertThat(blocks.get(0).get("type").asText()).isEqualTo("TEXT");
        assertThat(blocks.get(0).get("textbausteinRolle").asText()).isEqualTo("VOR");
        assertThat(blocks.get(0).get("content").asText()).isEqualTo("<p>Guten Tag Max Mustermann,</p>");
        assertThat(blocks.get(1).get("type").asText()).isEqualTo("SERVICE");
        assertThat(blocks.get(2).get("type").asText()).isEqualTo("SUBTOTAL");
        assertThat(blocks.get(3).get("textbausteinRolle").asText()).isEqualTo("NACH");
    }

    @Test
    void standardtexte_verbrauchenDasErneuernFlagUndErsetzenAlteBausteine() throws Exception {
        // Ein alter Angebots-Vortext darf nicht stehen bleiben, sondern wird ausgetauscht.
        String json = "{\"blocks\":["
                + "{\"type\":\"TEXT\",\"content\":\"Angebot Vortext\",\"textbausteinRolle\":\"VOR\"},"
                + "{\"type\":\"SERVICE\",\"title\":\"Gitterrost\",\"quantity\":1,\"price\":100}"
                + "],\"standardTextbausteineErneuern\":true}";

        String neu = AutoAuftragsbestaetigungVersandService.baueJsonMitStandardtexten(
                json, defaults(textbaustein(1L, "<p>AB Vortext</p>"), null), java.util.Map.of());

        com.fasterxml.jackson.databind.JsonNode root =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(neu);

        assertThat(root.has("standardTextbausteineErneuern")).isFalse();
        assertThat(root.get("blocks")).hasSize(2);
        assertThat(root.get("blocks").get(0).get("content").asText()).isEqualTo("<p>AB Vortext</p>");
        assertThat(root.get("blocks").get(1).get("type").asText()).isEqualTo("SERVICE");
    }

    @Test
    void standardtexte_ohneLeistungenWerdenAngehaengt() throws Exception {
        String neu = AutoAuftragsbestaetigungVersandService.baueJsonMitStandardtexten(
                "[]", defaults(textbaustein(1L, "<p>Vor</p>"), textbaustein(2L, "<p>Nach</p>")),
                java.util.Map.of());

        com.fasterxml.jackson.databind.JsonNode blocks =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(neu).get("blocks");

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).get("textbausteinRolle").asText()).isEqualTo("VOR");
        assertThat(blocks.get(1).get("textbausteinRolle").asText()).isEqualTo("NACH");
    }

    @Test
    void standardtexte_kaputtesJsonLaesstDokumentUnveraendert() {
        assertThat(AutoAuftragsbestaetigungVersandService.baueJsonMitStandardtexten(
                "nicht-json", defaults(textbaustein(1L, "<p>Vor</p>"), null), java.util.Map.of()))
                .isNull();
    }

    @Test
    void enthaeltStandardTextbausteine_erkenntBereitsGesetzteTexte() {
        assertThat(AutoAuftragsbestaetigungVersandService.enthaeltStandardTextbausteine(
                "{\"blocks\":[{\"type\":\"TEXT\",\"textbausteinRolle\":\"VOR\"}]}")).isTrue();
        assertThat(AutoAuftragsbestaetigungVersandService.enthaeltStandardTextbausteine(
                "{\"blocks\":[{\"type\":\"SERVICE\"}]}")).isFalse();
        assertThat(AutoAuftragsbestaetigungVersandService.enthaeltStandardTextbausteine(null)).isFalse();
        assertThat(AutoAuftragsbestaetigungVersandService.enthaeltStandardTextbausteine("nicht-json")).isFalse();
    }

    @Test
    void materialisiereStandardtexte_ohneVorlageBleibtPositionenJsonUnveraendert() {
        String json = "{\"blocks\":[{\"type\":\"SERVICE\",\"title\":\"Gitterrost\"}]}";
        AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
        ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
        ab.setDokumentNummer("AB-2026/07/0016");
        ab.setPositionenJson(json);
        when(ausgangsGeschaeftsDokumentRepository.findById(7L)).thenReturn(Optional.of(ab));
        when(formularTemplateService.getPreferredTemplateForDokumenttyp("Auftragsbestätigung", null))
                .thenReturn(Optional.empty());

        neuService().materialisiereStandardtexte(7L);

        assertThat(ab.getPositionenJson()).isEqualTo(json);
        org.mockito.Mockito.verifyNoInteractions(formularTextbausteinDefaultService);
    }

    @Test
    void materialisiereStandardtexte_schreibtVorUndNachtexteInDasDokument() {
        AusgangsGeschaeftsDokument ab = baueAbMitVorgaengerAngebot();
        ab.setPositionenJson("{\"blocks\":[{\"type\":\"SERVICE\",\"title\":\"Gitterrost\"}],"
                + "\"standardTextbausteineErneuern\":true}");
        when(ausgangsGeschaeftsDokumentRepository.findById(7L)).thenReturn(Optional.of(ab));
        when(formularTemplateService.getPreferredTemplateForDokumenttyp("Auftragsbestätigung", null))
                .thenReturn(Optional.of("standard-briefpapier"));
        when(formularTextbausteinDefaultService.loadForDokumenttyp("standard-briefpapier", "Auftragsbestätigung"))
                .thenReturn(defaults(textbaustein(1L, "<p>Auftragsbestaetigung Vortext</p>"),
                        textbaustein(2L, "<p>Unterschrift</p>")));

        neuService().materialisiereStandardtexte(7L);

        assertThat(ab.getPositionenJson()).contains("Auftragsbestaetigung Vortext", "Unterschrift", "\"VOR\"", "\"NACH\"");
        assertThat(ab.getPositionenJson()).doesNotContain("standardTextbausteineErneuern");
        org.mockito.Mockito.verify(ausgangsGeschaeftsDokumentRepository).save(ab);
    }

    private static org.example.kalkulationsprogramm.domain.Textbaustein textbaustein(Long id, String html) {
        org.example.kalkulationsprogramm.domain.Textbaustein tb =
                new org.example.kalkulationsprogramm.domain.Textbaustein();
        tb.setId(id);
        tb.setName("Dummy-Baustein " + id);
        tb.setHtml(html);
        return tb;
    }

    private static FormularTextbausteinDefaultService.DefaultsForDokumenttyp defaults(
            org.example.kalkulationsprogramm.domain.Textbaustein vor,
            org.example.kalkulationsprogramm.domain.Textbaustein nach) {
        return new FormularTextbausteinDefaultService.DefaultsForDokumenttyp(
                vor == null ? List.of() : List.of(vor),
                nach == null ? List.of() : List.of(nach));
    }

    private static AusgangsGeschaeftsDokument baueAbMitProjekt() {
        AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
        ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
        ab.setDokumentNummer("AB-2026/07/0001");
        ab.setProjekt(new org.example.kalkulationsprogramm.domain.Projekt());
        return ab;
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
