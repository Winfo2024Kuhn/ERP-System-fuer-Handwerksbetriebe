package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.dto.Freigabe.FreigabePositionDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentCounterRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Belegt, dass eine aus einem Artikel erzeugte Position die bestehende Kette
 * unveraendert durchlaeuft. Sie ist technisch ein SERVICE-Block mit
 * zusaetzlicher artikelId - genau deshalb muss an PDF, Auto-AB und
 * Freigabe-Ansicht nichts geaendert werden.
 *
 * <p>Schlaegt dieser Test fehl, ist die Grundannahme des Entwurfs verletzt.
 */
@ExtendWith(MockitoExtension.class)
class ArtikelPositionDurchlaufTest {

    /**
     * Wie der DocumentEditor eine Materialposition schreibt: Kurzbeschreibung
     * als title, Kundentext als description, artikelId als Herkunftsnachweis.
     */
    private static final String POSITIONEN_JSON = """
            {"blocks":[
              {"id":"b1","type":"TEXT","content":"<p>Sehr geehrter Herr Mustermann,</p>","fontSize":10},
              {"id":"b2","type":"SERVICE","title":"T-Stahl 40x40 Lager",
               "description":"<p>T-Stahl 40 x 40 x 5 mm, verzinkt</p>",
               "quantity":12,"unit":"lfm","price":8.40,"artikelId":7,"optional":false}
            ]}""";

    @Mock private AusgangsGeschaeftsDokumentRepository dokumentRepository;
    @Mock private AusgangsGeschaeftsDokumentCounterRepository counterRepository;
    @Mock private ProjektRepository projektRepository;
    @Mock private AnfrageRepository anfrageRepository;
    @Mock private KundeRepository kundeRepository;
    @Mock private FrontendUserProfileRepository frontendUserProfileRepository;
    @Mock private LeistungRepository leistungRepository;
    @Mock private ProduktkategorieRepository produktkategorieRepository;
    @Mock private ProjektDokumentRepository projektDokumentRepository;
    @Mock private ZeitbuchungRepository zeitbuchungRepository;
    @Mock private AusgangsGeschaeftsDokumentAuditService auditService;

    private AusgangsGeschaeftsDokumentService service;

    @BeforeEach
    void setUp() {
        service = new AusgangsGeschaeftsDokumentService(
                "uploads",
                dokumentRepository,
                counterRepository,
                projektRepository,
                anfrageRepository,
                kundeRepository,
                frontendUserProfileRepository,
                leistungRepository,
                produktkategorieRepository,
                projektDokumentRepository,
                zeitbuchungRepository,
                auditService);
    }

    @Test
    void wirdVonDerAutoAuftragsbestaetigungAlsPositionErkannt() {
        List<ContentBlockDto> blocks =
                AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks(POSITIONEN_JSON);

        ContentBlockDto service = blocks.stream()
                .filter(b -> "SERVICE".equals(b.type()))
                .findFirst().orElseThrow();

        assertThat(service.pos()).isEqualTo("1");
        assertThat(service.menge()).isEqualByComparingTo("12");
        assertThat(service.einheit()).isEqualTo("lfm");
        assertThat(service.einzelpreis()).isEqualByComparingTo("8.40");
        assertThat(service.gesamt()).isEqualByComparingTo("100.80");
    }

    @Test
    void derKurztextBleibtInnensichtUndDerKundentextGehtInsPdf() {
        List<ContentBlockDto> blocks =
                AutoAuftragsbestaetigungVersandService.parsePositionenJsonZuContentBlocks(POSITIONEN_JSON);

        ContentBlockDto service = blocks.stream()
                .filter(b -> "SERVICE".equals(b.type()))
                .findFirst().orElseThrow();

        // beschreibung() traegt den Kurztext, beschreibungHtml() den Kundentext.
        // Der RechnungPdfService druckt seit Commit 48dd24a allein letzteren.
        assertThat(service.beschreibung()).isEqualTo("T-Stahl 40x40 Lager");
        assertThat(service.beschreibungHtml()).contains("verzinkt");
    }

    @Test
    void zaehltInDieNettosummeEin() {
        assertThat(AutoAuftragsbestaetigungVersandService.summiereNettoAusJson(POSITIONEN_JSON))
                .isEqualByComparingTo("100.80");
    }

    @Test
    void eineOptionaleMaterialpositionZaehltNichtInDieBasissumme() {
        String mitOption = POSITIONEN_JSON.replace("\"optional\":false", "\"optional\":true");

        assertThat(AutoAuftragsbestaetigungVersandService.summiereNettoAusJson(mitOption))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void erscheintAufDerFreigabeSeiteMitPreisUndSumme() {
        List<FreigabePositionDto> positionen = service.baueKundenPositionen(POSITIONEN_JSON);

        FreigabePositionDto material = positionen.stream()
                .filter(p -> "SERVICE".equals(p.getTyp()))
                .findFirst().orElseThrow();

        assertThat(material.getMenge()).isEqualByComparingTo("12");
        assertThat(material.getEinheit()).isEqualTo("lfm");
        assertThat(material.getEinzelpreisNetto()).isEqualByComparingTo("8.40");
        assertThat(material.getGesamtpreisNetto()).isEqualByComparingTo("100.80");
        assertThat(material.getBeschreibungHtml()).contains("verzinkt");
    }

    @Test
    void schicktDenKurztextAlsBezeichnungMit() {
        FreigabePositionDto material = service.baueKundenPositionen(POSITIONEN_JSON).stream()
                .filter(p -> "SERVICE".equals(p.getTyp()))
                .findFirst().orElseThrow();

        // Dokumentiert bewusst den Ist-Zustand: bezeichnung traegt den internen
        // Kurztext. Das PDF druckt ihn seit 48dd24a nicht mehr - die Website darf
        // ihn deshalb ebenfalls nicht anzeigen (siehe Website-Agenten-Prompt).
        assertThat(material.getBezeichnung()).isEqualTo("T-Stahl 40x40 Lager");
    }
}
