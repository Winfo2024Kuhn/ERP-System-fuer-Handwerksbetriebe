package org.example.kalkulationsprogramm.service;

import tools.jackson.databind.json.JsonMapper;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    @Mock FirmeninformationService firmeninformationService;
    @Mock WiderrufsbelehrungPdfService widerrufsbelehrungPdfService;

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
                sentMailArchiver,
                firmeninformationService,
                widerrufsbelehrungPdfService);
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

    /**
     * Kette bei digitaler Annahme: Der Kunde nimmt an → die AB wird automatisch
     * erzeugt UND versendet. Das PDF dieser Mail baut dieser Service aus dem
     * positionenJson der AB — also aus dem bereits bereinigten JSON.
     *
     * <p>Der Test verdrahtet beide Schritte, damit die Bereinigung nicht an dieser
     * Naht verloren geht: Was der Kunde abgewählt hat, darf im versendeten PDF
     * weder als Position noch als Nummernlücke auftauchen.</p>
     *
     * <p>DSGVO: ausschliesslich Dummy-Daten.</p>
     */
    @Test
    void parser_abNachAnnahme_enthaeltNurBeauftragtesUndNummeriertLueckenlos() {
        String angebotJson = """
            [
              {"id":"a","type":"SERVICE","title":"Stahlkonstruktion","quantity":1,"price":6800},
              {"id":"b","type":"SERVICE","title":"Gelaender Edelstahl","quantity":1,"price":1240,
               "optional":true,"alternativGruppe":"Gelaender"},
              {"id":"c","type":"SERVICE","title":"Gelaender verzinkt","quantity":1,"price":890,
               "optional":true,"alternativGruppe":"Gelaender"},
              {"id":"d","type":"SERVICE","title":"Blende","quantity":1,"price":340,"optional":true}
            ]
            """;
        // Schritt 1: Aufbereitung wie in DokumentFreigabeService.erzeugeAutoAuftragsbestaetigung.
        String abJson = neuesDokumentService()
                .markiereAlternativenAlsBeauftragt(angebotJson, java.util.Set.of("b"));

        // Schritt 2: PDF-Blöcke der automatisch versendeten Mail.
        List<ContentBlockDto> bloecke =
                AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks(abJson);

        assertThat(bloecke).extracting(ContentBlockDto::beschreibung)
                .containsExactly("Stahlkonstruktion", "Gelaender Edelstahl");
        assertThat(bloecke).noneMatch(ContentBlockDto::optional);
        // Keine Nummernluecke, wo die abgewaehlten Positionen standen.
        assertThat(bloecke).extracting(ContentBlockDto::pos).containsExactly("1", "2");
    }

    /** Reine JSON-Logik — die Repository-Mocks des Service werden dafür nicht gebraucht. */
    private AusgangsGeschaeftsDokumentService neuesDokumentService() {
        return new AusgangsGeschaeftsDokumentService(
                "uploads", null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Regression: In der automatischen Auftragsbestätigung stand die komplette
     * Leistungsbeschreibung fett im PDF, obwohl im Richtext nur einzelne Wörter
     * fett markiert waren.
     *
     * <p>Ursache: Der DocumentEditor schreibt beim Einfügen einer Leistung
     * {@code fett: extractBoldFromHtml(html)} und {@code fontSize:
     * extractFontSizeFromHtml(html)} in den Block — beides wird schon bei EINEM
     * formatierten Wort gesetzt und landet so im {@code positionenJson}. Der
     * {@link RechnungPdfService} nutzt diese Werte als {@code defaultBold} /
     * {@code defaultFontSize} für jeden Text-Chunk ausserhalb der
     * {@code <strong>}/{@code <span>}-Tags. Beim manuellen Export überschreibt
     * das Frontend sie deshalb bewusst mit neutral (siehe
     * {@code document-editor/index.tsx} → contentBlocks-Mapping); der
     * Backend-Parser hat das bisher nicht getan.</p>
     */
    @Test
    void parser_serviceUebernimmtBlockFormatierungNichtAlsDefault() {
        String json = "[{\"type\":\"SERVICE\",\"title\":\"Handlauf\",\"quantity\":1,\"price\":100,"
                + "\"description\":\"<p>Rohr <strong>V2A</strong> geschliffen</p>\","
                + "\"fett\":true,\"fontSize\":14}]";

        List<ContentBlockDto> result = AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks(json);

        assertThat(result).hasSize(1);
        ContentBlockDto block = result.get(0);
        // Neutral: die Formatierung steckt im HTML, nicht im Block-Default.
        assertThat(block.fett()).isFalse();
        assertThat(block.fontSize()).isEqualTo(10);
        assertThat(block.beschreibungHtml()).isEqualTo("<p>Rohr <strong>V2A</strong> geschliffen</p>");
    }

    @Test
    void parser_textUebernimmtBlockFormatierungNichtAlsDefault() {
        String html = "<p>Sehr geehrte <strong>Frau Mustermann</strong></p>";
        String json = "[{\"type\":\"TEXT\",\"content\":\"" + html + "\",\"fett\":true,\"fontSize\":16}]";

        List<ContentBlockDto> result = AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fett()).isFalse();
        assertThat(result.get(0).fontSize()).isEqualTo(10);
        // Das HTML selbst bleibt unangetastet — nur die Block-Defaults werden neutralisiert.
        assertThat(result.get(0).text()).isEqualTo(html);
    }

    /**
     * Gegenprobe zur Neutralisierung: Eine Leistung ohne Rich-Text-Beschreibung
     * verliert nichts. Der {@link RechnungPdfService} rendert den Titel in
     * diesem Fall über die feste Label-Schrift und liest {@code fett}/
     * {@code fontSize} gar nicht — der Titel bleibt also hervorgehoben.
     */
    @Test
    void parser_serviceOhneBeschreibungBehaeltTitel() {
        String json = "[{\"type\":\"SERVICE\",\"title\":\"Montage vor Ort\",\"quantity\":2,\"price\":75}]";

        List<ContentBlockDto> result = AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).beschreibung()).isEqualTo("Montage vor Ort");
        assertThat(result.get(0).beschreibungHtml()).isNull();
        assertThat(result.get(0).fett()).isFalse();
    }

    /**
     * Struktur-Blöcke tragen keine eigene Textformatierung — sie werden im
     * PDF-Service über feste Schriften gerendert. Die {@code fontSize 0} wird
     * von deren Renderern gar nicht gelesen: {@code addSectionHeader},
     * {@code addSubtotalRow} und {@code addSeparatorLine} bekommen den Wert
     * nicht übergeben. Der Test hält damit fest, dass der Parser hier nichts
     * aus dem JSON durchreicht — den echten Regressionspin trägt der
     * SECTION_HEADER, der im {@code positionenJson} ein {@code fett} tragen kann.
     */
    @Test
    void parser_strukturBloeckeBleibenOhneFormatierung() {
        String json = "[{\"type\":\"SECTION_HEADER\",\"sectionLabel\":\"Aussenanlagen\",\"fett\":true,\"fontSize\":18},"
                + "{\"type\":\"SUBTOTAL\"},{\"type\":\"SEPARATOR\"},{\"type\":\"CLOSURE\"}]";

        List<ContentBlockDto> result = AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks(json);

        assertThat(result).hasSize(4);
        assertThat(result).allSatisfy(block -> {
            assertThat(block.fett()).isFalse();
            assertThat(block.fontSize()).isZero();
        });
        assertThat(result.get(0).sectionLabel()).isEqualTo("Aussenanlagen");
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
                org.mockito.ArgumentMatchers.anyList()))
                .thenThrow(new org.springframework.dao.PessimisticLockingFailureException("Lock wait timeout"))
                .thenReturn(null);

        service.archiviereAlsProjektEmail(ab, "max@example.de", "kontakt@example.de",
                "Subject", "<p>Body</p>", "<msgid@example.de>", java.nio.file.Path.of("egal.pdf"), "AB.pdf");

        org.mockito.Mockito.verify(projektEmailArchivService, org.mockito.Mockito.times(2))
                .archiviereVersandteEmail(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyList());
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
                org.mockito.ArgumentMatchers.anyList()))
                .thenThrow(new org.springframework.dao.PessimisticLockingFailureException("Lock wait timeout"));

        // Darf nicht werfen — die SMTP-Mail ist bereits beim Kunden.
        service.archiviereAlsProjektEmail(ab, "max@example.de", "kontakt@example.de",
                "Subject", "<p>Body</p>", "<msgid@example.de>", java.nio.file.Path.of("egal.pdf"), "AB.pdf");

        org.mockito.Mockito.verify(projektEmailArchivService, org.mockito.Mockito.times(3))
                .archiviereVersandteEmail(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyList());
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

        tools.jackson.databind.JsonNode blocks =
                JsonMapper.builderWithJackson2Defaults().build().readTree(neu).get("blocks");

        assertThat(blocks).hasSize(4);
        assertThat(blocks.get(0).get("type").asString("")).isEqualTo("TEXT");
        assertThat(blocks.get(0).get("textbausteinRolle").asString("")).isEqualTo("VOR");
        assertThat(blocks.get(0).get("content").asString("")).isEqualTo("<p>Guten Tag Max Mustermann,</p>");
        assertThat(blocks.get(1).get("type").asString("")).isEqualTo("SERVICE");
        assertThat(blocks.get(2).get("type").asString("")).isEqualTo("SUBTOTAL");
        assertThat(blocks.get(3).get("textbausteinRolle").asString("")).isEqualTo("NACH");
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

        tools.jackson.databind.JsonNode root =
                JsonMapper.builderWithJackson2Defaults().build().readTree(neu);

        assertThat(root.has("standardTextbausteineErneuern")).isFalse();
        assertThat(root.get("blocks")).hasSize(2);
        assertThat(root.get("blocks").get(0).get("content").asString("")).isEqualTo("<p>AB Vortext</p>");
        assertThat(root.get("blocks").get(1).get("type").asString("")).isEqualTo("SERVICE");
    }

    @Test
    void standardtexte_ohneLeistungenWerdenAngehaengt() throws Exception {
        String neu = AutoAuftragsbestaetigungVersandService.baueJsonMitStandardtexten(
                "[]", defaults(textbaustein(1L, "<p>Vor</p>"), textbaustein(2L, "<p>Nach</p>")),
                java.util.Map.of());

        tools.jackson.databind.JsonNode blocks =
                JsonMapper.builderWithJackson2Defaults().build().readTree(neu).get("blocks");

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).get("textbausteinRolle").asString("")).isEqualTo("VOR");
        assertThat(blocks.get(1).get("textbausteinRolle").asString("")).isEqualTo("NACH");
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

    // ------------- Versand: AB + Widerrufsbelehrung als zwei PDFs -------------

    @SuppressWarnings("unchecked")
    private java.util.List<org.example.email.EmailService.Attachment> versendeUndHoleAnhaenge(
            AutoAuftragsbestaetigungVersandService service, DokumentFreigabe freigabe, boolean[] erfolg) {
        when(systemSettingsService.getDokumentMailKonto()).thenReturn(new SystemSettingsService.MailKonto(
                "smtp.example.com", 465, "info@example.com", "geheim", "info@example.com", "Musterbetrieb"));
        when(rechnungPdfService.generatePdfBytes(org.mockito.ArgumentMatchers.any()))
                .thenReturn("%PDF-AB".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        java.util.List<java.util.List<org.example.email.EmailService.Attachment>> gesendet = new java.util.ArrayList<>();
        org.example.email.EmailService mail = org.mockito.Mockito.mock(org.example.email.EmailService.class);
        try {
            when(mail.sendEmailWithMultipleAttachments(
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyList()))
                    .thenAnswer(inv -> {
                        gesendet.add(inv.getArgument(6));
                        return "<msg@example.com>";
                    });
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        service.emailServiceFabrik = konto -> mail;
        erfolg[0] = service.versende(baueAbMitProjekt(), "max@example.com", freigabe);
        assertThat(gesendet).hasSize(1);
        return gesendet.get(0);
    }

    @Test
    void versende_nachAnnahme_schicktAbUndWiderrufsbelehrungUndArchiviertBeide() {
        AutoAuftragsbestaetigungVersandService service = neuService();
        DokumentFreigabe f = angenommeneFreigabe();
        f.setVorzeitigerBeginn(true);
        f.setWiderrufStand("2026-10-13");
        when(widerrufsbelehrungPdfService.erzeuge(f)).thenReturn(new WiderrufsbelehrungPdfService.Belehrung(
                "%PDF-W".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                "Widerrufsbelehrung_Stand_2026-10-13.pdf", java.time.LocalDate.of(2026, 10, 13), 2));
        boolean[] erfolg = new boolean[1];

        java.util.List<org.example.email.EmailService.Attachment> anhaenge = versendeUndHoleAnhaenge(service, f, erfolg);

        assertThat(erfolg[0]).isTrue();
        assertThat(anhaenge).extracting(org.example.email.EmailService.Attachment::filename)
                .containsExactly("Auftragsbestaetigung_AB-2026_07_0001.pdf", "Widerrufsbelehrung_Stand_2026-10-13.pdf");
        assertThat(anhaenge).allSatisfy(a -> assertThat(a.mimeType()).isEqualTo("application/pdf"));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.List<ProjektEmailArchivService.PdfAnhang>> archiv =
                org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        org.mockito.Mockito.verify(projektEmailArchivService).archiviereVersandteEmail(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), archiv.capture());
        assertThat(archiv.getValue()).extracting(ProjektEmailArchivService.PdfAnhang::dateiname)
                .containsExactly("Auftragsbestaetigung_AB-2026_07_0001.pdf", "Widerrufsbelehrung_Stand_2026-10-13.pdf");
        // Temp-Dateien sind nach dem Versand wieder weg.
        assertThat(archiv.getValue()).allSatisfy(a -> assertThat(java.nio.file.Files.exists(a.quelle())).isFalse());
        // Nachweis: welche Fassung verschickt wurde, steht an der Freigabe.
        assertThat(f.getWiderrufVersandStand()).isEqualTo("2026-10-13");
        assertThat(f.getWiderrufVersandFassung()).isEqualTo(2);
        org.mockito.Mockito.verify(dokumentFreigabeRepository).saveAndFlush(f);
    }

    @Test
    void versende_nachweisSpeichernScheitert_versandBleibtErfolgreich() {
        AutoAuftragsbestaetigungVersandService service = neuService();
        DokumentFreigabe f = angenommeneFreigabe();
        when(widerrufsbelehrungPdfService.erzeuge(f)).thenReturn(new WiderrufsbelehrungPdfService.Belehrung(
                "%PDF-W".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                "Widerrufsbelehrung_Stand_2026-11-02.pdf", java.time.LocalDate.of(2026, 11, 2), 0));
        when(dokumentFreigabeRepository.saveAndFlush(f)).thenThrow(new IllegalStateException("DB weg"));
        boolean[] erfolg = new boolean[1];

        java.util.List<org.example.email.EmailService.Attachment> anhaenge = versendeUndHoleAnhaenge(service, f, erfolg);

        assertThat(erfolg[0]).isTrue();
        assertThat(anhaenge).hasSize(2);
    }

    @Test
    void warneBeiAbweichendemStand_wirftNie() {
        DokumentFreigabe f = angenommeneFreigabe();
        WiderrufsbelehrungPdfService.Belehrung b = new WiderrufsbelehrungPdfService.Belehrung(
                new byte[0], "x.pdf", java.time.LocalDate.of(2026, 11, 2), 1);

        AutoAuftragsbestaetigungVersandService.warneBeiAbweichendemStand(f, b);
        f.setWiderrufStand("2026-11-02");
        AutoAuftragsbestaetigungVersandService.warneBeiAbweichendemStand(f, b);
        f.setWiderrufStand("2026-10-13");
        AutoAuftragsbestaetigungVersandService.warneBeiAbweichendemStand(f, b);

        assertThat(f.getWiderrufVersandStand()).isNull();
    }

    @Test
    void versende_belehrungScheitert_abGehtTrotzdemAllein() {
        AutoAuftragsbestaetigungVersandService service = neuService();
        DokumentFreigabe f = angenommeneFreigabe();
        when(widerrufsbelehrungPdfService.erzeuge(f)).thenThrow(new IllegalStateException("PDF kaputt"));
        boolean[] erfolg = new boolean[1];

        java.util.List<org.example.email.EmailService.Attachment> anhaenge = versendeUndHoleAnhaenge(service, f, erfolg);

        assertThat(erfolg[0]).isTrue();
        assertThat(anhaenge).extracting(org.example.email.EmailService.Attachment::filename)
                .containsExactly("Auftragsbestaetigung_AB-2026_07_0001.pdf");
    }

    @Test
    void versende_ohneFreigabe_ohneBelehrung() {
        AutoAuftragsbestaetigungVersandService service = neuService();
        boolean[] erfolg = new boolean[1];

        java.util.List<org.example.email.EmailService.Attachment> anhaenge = versendeUndHoleAnhaenge(service, null, erfolg);

        assertThat(erfolg[0]).isTrue();
        assertThat(anhaenge).hasSize(1);
        org.mockito.Mockito.verifyNoInteractions(widerrufsbelehrungPdfService);
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

    // ============== Annahme-Beleg: "Waren Sie das nicht?" und Code-Bestätigung ==============

    private static DokumentFreigabe angenommeneFreigabe() {
        DokumentFreigabe f = new DokumentFreigabe();
        f.setDokumentNummer("AN-2026-0001");
        f.setAkzeptiertAm(LocalDateTime.of(2026, 10, 11, 14, 5));
        f.setHashAcceptance("0123456789abcdef0123456789abcdef");
        f.setUnterzeichnerName("Max Mustermann");
        f.setCodeBestaetigtAm(LocalDateTime.of(2026, 10, 11, 14, 5));
        f.setCodeBestaetigtEmail("max@example.de");
        return f;
    }

    @Test
    void annahmeBeleg_mitCodeUndTelefon_nenntBeidesDeutlich() {
        String html = AutoAuftragsbestaetigungVersandService.buildAnnahmeBelegHtml(angenommeneFreigabe(), "09369 / 23 23");

        assertThat(html)
                .contains("Annahme durch <strong>Max Mustermann</strong> am <strong>11.10.2026 um 14:05 Uhr</strong>")
                .contains("Bestätigt per Einmal-Code an <strong>max@example.de</strong>")
                .contains("<strong>Waren Sie das nicht?</strong> Dann rufen Sie uns bitte sofort an: <strong>09369 / 23 23</strong>.")
                .contains("0123456789abcdef…");
    }

    @Test
    void annahmeBeleg_altAnnahmeOhneCodeUndOhneTelefon() {
        DokumentFreigabe f = angenommeneFreigabe();
        f.setCodeBestaetigtAm(null);
        f.setCodeBestaetigtEmail(null);
        f.setUnterzeichnerName("  ");
        f.setAkzeptiertAm(null);
        f.setHashAcceptance(null);

        String html = AutoAuftragsbestaetigungVersandService.buildAnnahmeBelegHtml(f, null);

        assertThat(html)
                .doesNotContain("Einmal-Code")
                .contains("Annahme am <strong>—</strong>")
                .contains("<strong>Waren Sie das nicht?</strong> Dann melden Sie sich bitte sofort bei uns.")
                .doesNotContain("rufen Sie uns");
    }

    @Test
    void annahmeBeleg_codeOhneAdresseUndUnterzeichnerNull_escaptUndBleibtLesbar() {
        DokumentFreigabe f = angenommeneFreigabe();
        f.setCodeBestaetigtEmail(null);
        f.setUnterzeichnerName(null);
        f.setDokumentNummer("<b>AN</b>");

        String html = AutoAuftragsbestaetigungVersandService.buildAnnahmeBelegHtml(f, "<i>0800</i>");

        assertThat(html)
                .contains("Bestätigt per Einmal-Code an <strong></strong>")
                .contains("Annahme am <strong>11.10.2026 um 14:05 Uhr</strong>")
                .contains("&lt;b&gt;AN&lt;/b&gt;")
                .contains("&lt;i&gt;0800&lt;/i&gt;");
    }

    @Test
    void annahmeBeleg_vorzeitigerBeginnVerlangt_nenntZustimmungUndStand() {
        DokumentFreigabe f = angenommeneFreigabe();
        f.setVorzeitigerBeginn(true);
        f.setWiderrufStand("2026-10-13");
        f.setKnopfText("Zahlungspflichtig beauftragen");

        String html = AutoAuftragsbestaetigungVersandService.buildAnnahmeBelegHtml(f, null, true);

        assertThat(html)
                .contains("Auftragsbestätigung und unsere Widerrufsbelehrung als PDF")
                .contains("Beginn vor Ablauf der Widerrufsfrist:")
                .contains("Widerrufsbelehrung Stand 13.10.2026, ausgelöst mit „Zahlungspflichtig beauftragen“")
                .doesNotContain("erst nach Ablauf der 14-tägigen Frist");
    }

    @Test
    void annahmeBeleg_ohneAngehaengteBelehrung_kuendigtSieNichtAn() {
        DokumentFreigabe f = angenommeneFreigabe();
        f.setWiderrufStand("2020-01-01"); // abweichender Stand: nur Log-Warnung, Beleg bleibt lesbar

        String html = AutoAuftragsbestaetigungVersandService.buildAnnahmeBelegHtml(f, null, false);

        assertThat(html)
                .contains("Im Anhang finden Sie Ihre verbindliche Auftragsbestätigung als PDF.")
                .doesNotContain("unsere Widerrufsbelehrung als PDF")
                .contains("Widerrufsbelehrung Stand 01.01.2020");
    }

    @Test
    void annahmeBeleg_vorzeitigerBeginnNichtVerlangt_kuendigtWartezeitAn() {
        DokumentFreigabe f = angenommeneFreigabe();
        f.setVorzeitigerBeginn(false);
        f.setWiderrufStand("2026-10-13");
        f.setKnopfText("<b>X</b>");

        String html = AutoAuftragsbestaetigungVersandService.buildAnnahmeBelegHtml(f, null);

        assertThat(html)
                .contains("erst nach Ablauf der 14-tägigen Frist")
                .contains("&lt;b&gt;X&lt;/b&gt;")
                .doesNotContain("Sie haben ausdrücklich verlangt");
    }

    @Test
    void annahmeBeleg_altAnnahmeOhneRechtsnachweis_ohneBeginnUndStandzeile() {
        DokumentFreigabe f = angenommeneFreigabe();

        String html = AutoAuftragsbestaetigungVersandService.buildAnnahmeBelegHtml(f, null);

        assertThat(html)
                .doesNotContain("Beginn der Arbeiten")
                .doesNotContain("Beginn vor Ablauf")
                .doesNotContain("Widerrufsbelehrung Stand");
    }

    @Test
    void annahmeBeleg_standOhneKnopftext() {
        DokumentFreigabe f = angenommeneFreigabe();
        f.setWiderrufStand("2026-10-13");

        String html = AutoAuftragsbestaetigungVersandService.buildAnnahmeBelegHtml(f, null);

        assertThat(html).contains("Widerrufsbelehrung Stand 13.10.2026<br>").doesNotContain("ausgelöst mit");
    }

    @Test
    void firmentelefon_nurWennGepflegtUndLadbar() {
        AutoAuftragsbestaetigungVersandService service = neuService();

        when(firmeninformationService.getFirmeninformation()).thenReturn(null);
        assertThat(service.firmentelefon()).isNull();

        FirmeninformationDto dto = new FirmeninformationDto();
        when(firmeninformationService.getFirmeninformation()).thenReturn(dto);
        assertThat(service.firmentelefon()).isNull();

        dto.setTelefon(" ");
        assertThat(service.firmentelefon()).isNull();

        dto.setTelefon(" 09369 / 23 23 ");
        assertThat(service.firmentelefon()).isEqualTo("09369 / 23 23");

        when(firmeninformationService.getFirmeninformation()).thenThrow(new IllegalStateException("DB weg"));
        assertThat(service.firmentelefon()).isNull();
    }

    @Test
    void mitAnnahmeBeleg_haengtBelegAnOderErsetztLeerenText() {
        AutoAuftragsbestaetigungVersandService service = neuService();
        FirmeninformationDto dto = new FirmeninformationDto();
        dto.setTelefon("09369 / 23 23");
        when(firmeninformationService.getFirmeninformation()).thenReturn(dto);

        org.example.email.EmailService.EmailContent mitText = service.mitAnnahmeBeleg(
                new org.example.email.EmailService.EmailContent("Betreff", "<p>Hallo</p>"), angenommeneFreigabe());
        assertThat(mitText.subject()).isEqualTo("Betreff");
        assertThat(mitText.htmlBody()).startsWith("<p>Hallo</p><div").contains("09369 / 23 23");

        org.example.email.EmailService.EmailContent ohneText = service.mitAnnahmeBeleg(
                new org.example.email.EmailService.EmailContent("Betreff", null), angenommeneFreigabe());
        assertThat(ohneText.htmlBody()).startsWith("<div").contains("Waren Sie das nicht?");
    }
}
