package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.service.LieferantDokumentAbgleich;
import org.example.kalkulationsprogramm.service.RechnungsVorschlagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Prüft, in welchen Bereich der Bestellübersicht eine Dokumenten-Kette einsortiert wird.
 */
@ExtendWith(MockitoExtension.class)
class BestellungsUebersichtControllerEinordnungTest {

    @Mock
    private LieferantDokumentRepository dokumentRepository;
    @Mock
    private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    @Mock
    private LieferantDokumentProjektAnteilRepository projektAnteilRepository;

    private BestellungsUebersichtController controller;
    private Lieferanten lieferant;

    @BeforeEach
    void setUp() {
        controller = new BestellungsUebersichtController(
                dokumentRepository, geschaeftsdokumentRepository, null, null, projektAnteilRepository,
                null, null, null, null, null, null,
                new RechnungsVorschlagService(new LieferantDokumentAbgleich(new ObjectMapper()), dokumentRepository));
        lieferant = new Lieferanten();
        lieferant.setId(1L);
        lieferant.setLieferantenname("Max Mustermann GmbH");
        lenient().when(projektAnteilRepository.findAll()).thenReturn(List.of());
        lenient().when(geschaeftsdokumentRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void einzelnerLieferscheinLandetBeiLaufendenBestellungen() {
        LieferantDokument lieferschein = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN);
        when(dokumentRepository.findAll()).thenReturn(List.of(lieferschein));

        var dto = controller.getUebersicht().getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.laufendeBestellungen()).hasSize(1);
        assertThat(dto.offeneAnfragen()).isEmpty();
        assertThat(dto.abgeschlossen()).isEmpty();
    }

    @Test
    void angebotMitLieferscheinIstLaufendUndKeineOffeneAnfrage() {
        LieferantDokument angebot = dokument(1L, LieferantDokumentTyp.ANGEBOT);
        LieferantDokument lieferschein = dokument(2L, LieferantDokumentTyp.LIEFERSCHEIN);
        angebot.setVerknuepfteDokumente(new HashSet<>(List.of(lieferschein)));
        when(dokumentRepository.findAll()).thenReturn(List.of(angebot, lieferschein));

        var dto = controller.getUebersicht().getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.laufendeBestellungen()).hasSize(1);
        assertThat(dto.laufendeBestellungen().get(0).dokumente()).hasSize(2);
        assertThat(dto.offeneAnfragen()).isEmpty();
    }

    @Test
    void lieferscheinMitRechnungIstAbgeschlossen() {
        LieferantDokument lieferschein = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument rechnung = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        lieferschein.setVerknuepfteDokumente(new HashSet<>(List.of(rechnung)));
        when(dokumentRepository.findAll()).thenReturn(List.of(lieferschein, rechnung));

        var dto = controller.getUebersicht().getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.abgeschlossen()).hasSize(1);
        assertThat(dto.laufendeBestellungen()).isEmpty();
    }

    @Test
    void nurAngebotBleibtOffeneAnfrage() {
        when(dokumentRepository.findAll()).thenReturn(List.of(dokument(1L, LieferantDokumentTyp.ANGEBOT)));

        var dto = controller.getUebersicht().getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.offeneAnfragen()).hasSize(1);
        assertThat(dto.laufendeBestellungen()).isEmpty();
    }

    @Test
    void eingangsDatumWirdMitgeliefertUndOhneBelegdatumZumSortieren() {
        LieferantDokument alt = dokument(1L, LieferantDokumentTyp.ANGEBOT);
        alt.setUploadDatum(LocalDateTime.of(2026, 1, 10, 9, 0));
        LieferantDokument neu = dokument(2L, LieferantDokumentTyp.ANGEBOT);
        neu.setUploadDatum(LocalDateTime.of(2026, 9, 1, 9, 0));
        when(dokumentRepository.findAll()).thenReturn(List.of(alt, neu));

        var dto = controller.getUebersicht().getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.offeneAnfragen()).hasSize(2);
        var erste = dto.offeneAnfragen().get(0).dokumente().get(0);
        assertThat(erste.id).isEqualTo(2L);
        assertThat(erste.eingangsDatum).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(erste.dokumentDatum).isNull();
    }

    @Test
    void belegdatumGehtBeimSortierenVorEingangsdatum() {
        LieferantDokument spaetEingegangen = dokument(1L, LieferantDokumentTyp.ANGEBOT);
        spaetEingegangen.setUploadDatum(LocalDateTime.of(2026, 9, 20, 9, 0));
        spaetEingegangen.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 2, 1)));
        LieferantDokument frueherEingegangen = dokument(2L, LieferantDokumentTyp.ANGEBOT);
        frueherEingegangen.setUploadDatum(LocalDateTime.of(2026, 9, 1, 9, 0));
        when(dokumentRepository.findAll()).thenReturn(List.of(spaetEingegangen, frueherEingegangen));

        var dto = controller.getUebersicht().getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.offeneAnfragen().get(0).dokumente().get(0).id).isEqualTo(2L);
    }

    @Test
    void abMitRechnungNurInRueckrichtungErscheintNichtBeiLaufenden() {
        // Gespeichert ist nur Rechnung -> AB; die AB kennt die Rechnung über verknuepftVon.
        LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        LieferantDokument rechnung = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        rechnung.setVerknuepfteDokumente(new HashSet<>(List.of(ab)));
        ab.setVerknuepftVon(new HashSet<>(List.of(rechnung)));
        // AB zuerst – genau diese Reihenfolge erzeugte früher eine zweite Kette ohne Rechnung
        when(dokumentRepository.findAll()).thenReturn(List.of(ab, rechnung));

        var dto = controller.getUebersicht().getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.laufendeBestellungen()).isEmpty();
        assertThat(dto.abgeschlossen()).hasSize(1);
        assertThat(dto.abgeschlossen().get(0).dokumente()).hasSize(2);
    }

    @Test
    void laufendeBestellungBekommtWahrscheinlichsteRechnungAlsVorschlag() {
        LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        ab.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 1)));
        ab.getGeschaeftsdaten().setDokumentNummer("AB-77001");
        LieferantDokument rechnung = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        rechnung.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 20)));
        rechnung.getGeschaeftsdaten().setDokumentNummer("RE-9");
        rechnung.getGeschaeftsdaten().setReferenzNummer("AB 77001");
        when(dokumentRepository.findAll()).thenReturn(List.of(ab, rechnung));

        var dto = controller.getUebersicht().getBody();

        assertThat(dto).isNotNull();
        var vorschlag = dto.laufendeBestellungen().get(0).rechnungsVorschlag();
        assertThat(vorschlag).isNotNull();
        assertThat(vorschlag.rechnung().id).isEqualTo(2L);
        assertThat(vorschlag.bestellDokumentId()).isEqualTo(1L);
        assertThat(vorschlag.trefferquote()).isGreaterThanOrEqualTo(LieferantDokumentAbgleich.QUOTE_SICHER);
        assertThat(vorschlag.eindeutig()).isTrue();
        assertThat(vorschlag.gruende()).contains("Belegnummer wird genannt");
        // Abgeschlossene Ketten bekommen keinen Vorschlag
        assertThat(dto.abgeschlossen().get(0).rechnungsVorschlag()).isNull();
    }

    @Test
    void rechnungVorschlaegeListetAlleOffenenRechnungen() {
        LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        ab.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 1)));
        LieferantDokument r1 = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        r1.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 5)));
        LieferantDokument r2 = dokument(3L, LieferantDokumentTyp.RECHNUNG);
        r2.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 6)));
        when(dokumentRepository.findAll()).thenReturn(List.of(ab, r1, r2));

        var antwort = controller.getRechnungsVorschlaege(List.of(1L));

        assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(antwort.getBody()).extracting(v -> v.rechnung().id).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void rechnungVorschlaegeMarkiertGleichstandUndBegrenztDieListe() {
        LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        ab.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 1)));
        List<LieferantDokument> alle = new java.util.ArrayList<>(List.of(ab));
        for (long id = 2; id <= 251; id++) {
            LieferantDokument r = dokument(id, LieferantDokumentTyp.RECHNUNG);
            r.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 5)));
            alle.add(r);
        }
        when(dokumentRepository.findAll()).thenReturn(alle);

        var liste = controller.getRechnungsVorschlaege(List.of(1L)).getBody();

        assertThat(liste).hasSize(200);
        // alle gleich gut (nur zeitlich nah) -> keiner ist eindeutig
        assertThat(liste).allSatisfy(v -> assertThat(v.eindeutig()).isFalse());
    }

    @Test
    void kartenVorschlagNurVomGleichenLieferanten() {
        LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        ab.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 1)));
        ab.getGeschaeftsdaten().setDokumentNummer("AB-77001");
        LieferantDokument fremd = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        Lieferanten anderer = new Lieferanten();
        anderer.setId(2L);
        anderer.setLieferantenname("Erika Musterfrau KG");
        fremd.setLieferant(anderer);
        fremd.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 20)));
        fremd.getGeschaeftsdaten().setReferenzNummer("AB-77001");
        when(dokumentRepository.findAll()).thenReturn(List.of(ab, fremd));

        var dto = controller.getUebersicht().getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.laufendeBestellungen().get(0).rechnungsVorschlag()).isNull();
        // Im Fenster „Rechnung suchen“ taucht die fremde Rechnung trotzdem auf
        assertThat(controller.getRechnungsVorschlaege(List.of(1L)).getBody())
                .extracting(v -> v.rechnung().id).containsExactly(2L);
    }

    @Test
    void rechnungMitAusgeblendeterAbWirdNichtVorgeschlagen() {
        LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        ab.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 1)));
        LieferantDokument versteckteAb = dokument(2L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        versteckteAb.setAusgeblendet(true);
        LieferantDokument rechnung = dokument(3L, LieferantDokumentTyp.RECHNUNG);
        rechnung.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 5)));
        rechnung.setVerknuepfteDokumente(new HashSet<>(List.of(versteckteAb)));
        when(dokumentRepository.findAll()).thenReturn(List.of(ab, versteckteAb, rechnung));

        assertThat(controller.getRechnungsVorschlaege(List.of(1L)).getBody()).isEmpty();
    }

    @Test
    void rechnungVorschlaegeLehntUngueltigeIdsAb() {
        assertThat(controller.getRechnungsVorschlaege(List.of()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.getRechnungsVorschlaege(List.of(-1L)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.getRechnungsVorschlaege(List.of(0L)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        List<Long> zuViele = java.util.stream.LongStream.rangeClosed(1, 51).boxed().toList();
        assertThat(controller.getRechnungsVorschlaege(zuViele).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rechnungVorschlaegeOhneBestelldokument() {
        when(dokumentRepository.findAll()).thenReturn(List.of(dokument(1L, LieferantDokumentTyp.ANGEBOT)));

        assertThat(controller.getRechnungsVorschlaege(List.of(1L, Long.MAX_VALUE)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rechnungVerknuepfenHappyPathUndFehler() {
        LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        LieferantDokument rechnung = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        when(dokumentRepository.findById(1L)).thenReturn(java.util.Optional.of(ab));
        when(dokumentRepository.findById(2L)).thenReturn(java.util.Optional.of(rechnung));
        when(dokumentRepository.findById(Long.MAX_VALUE)).thenReturn(java.util.Optional.empty());

        assertThat(controller.rechnungVerknuepfen(
                new BestellungsUebersichtController.RechnungVerknuepfenRequest(1L, 2L), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rechnung.getVerknuepfteDokumente()).containsExactly(ab);

        assertThat(controller.rechnungVerknuepfen(
                new BestellungsUebersichtController.RechnungVerknuepfenRequest(2L, 1L), null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.rechnungVerknuepfen(
                new BestellungsUebersichtController.RechnungVerknuepfenRequest(Long.MAX_VALUE, 2L), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private LieferantDokument dokument(long id, LieferantDokumentTyp typ) {
        LieferantDokument d = new LieferantDokument();
        d.setId(id);
        d.setLieferant(lieferant);
        d.setTyp(typ);
        d.setUploadDatum(LocalDateTime.of(2026, 9, 1, 8, 0));
        return d;
    }

    private LieferantGeschaeftsdokument geschaeftsdaten(LocalDate datum) {
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setDokumentDatum(datum);
        return gd;
    }
}
