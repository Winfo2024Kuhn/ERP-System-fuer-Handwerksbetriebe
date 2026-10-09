package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Bestellung.DokumentRef;
import org.example.kalkulationsprogramm.dto.Bestellung.Verbindung;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentVerknuepfungSperreRepository;
import org.example.kalkulationsprogramm.service.BestellungsUebersichtService;
import org.example.kalkulationsprogramm.service.KettenVorschlagService;
import org.example.kalkulationsprogramm.service.LieferantDokumentService;
import org.example.kalkulationsprogramm.service.LieferantDokumentAbgleich;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private LieferantDokumentVerknuepfungSperreRepository sperreRepository;
    @Mock
    private LieferantDokumentService lieferantDokumentService;
    @Mock
    private LieferantDokumentZugriffService zugriffService;

    private BestellungsUebersichtController controller;
    private Lieferanten lieferant;

    @BeforeEach
    void setUp() {
        RechnungsVorschlagService vorschlagService = new RechnungsVorschlagService(
                new LieferantDokumentAbgleich(new ObjectMapper()), dokumentRepository, sperreRepository);
        KettenVorschlagService kettenVorschlagService = new KettenVorschlagService(
                new LieferantDokumentAbgleich(new ObjectMapper()), dokumentRepository, sperreRepository);
        controller = new BestellungsUebersichtController(
                dokumentRepository, geschaeftsdokumentRepository, projektAnteilRepository,
                null, null, null, null, null, null,
                vorschlagService,
                kettenVorschlagService,
                lieferantDokumentService,
                new BestellungsUebersichtService(dokumentRepository, geschaeftsdokumentRepository,
                        projektAnteilRepository, vorschlagService, kettenVorschlagService),
                null,
                zugriffService);
        lieferant = new Lieferanten();
        lieferant.setId(1L);
        lieferant.setLieferantenname("Max Mustermann GmbH");
        lenient().when(projektAnteilRepository.findAll()).thenReturn(List.of());
        lenient().when(geschaeftsdokumentRepository.findAll()).thenReturn(List.of());
        lenient().when(zugriffService.sichtbareTypen(any(), any()))
                .thenReturn(Optional.of(EnumSet.allOf(LieferantDokumentTyp.class)));
        lenient().when(zugriffService.istSichtbar(any(), any())).thenReturn(true);
    }

    @Test
    void einzelnerLieferscheinLandetBeiLaufendenBestellungen() {
        LieferantDokument lieferschein = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN);
        when(dokumentRepository.findAll()).thenReturn(List.of(lieferschein));

        var dto = controller.getUebersicht(null, null).getBody();

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

        var dto = controller.getUebersicht(null, null).getBody();

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

        var dto = controller.getUebersicht(null, null).getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.abgeschlossen()).hasSize(1);
        assertThat(dto.laufendeBestellungen()).isEmpty();
    }

    @Test
    void nurAngebotBleibtOffeneAnfrage() {
        when(dokumentRepository.findAll()).thenReturn(List.of(dokument(1L, LieferantDokumentTyp.ANGEBOT)));

        var dto = controller.getUebersicht(null, null).getBody();

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

        var dto = controller.getUebersicht(null, null).getBody();

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

        var dto = controller.getUebersicht(null, null).getBody();

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

        var dto = controller.getUebersicht(null, null).getBody();

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

        var dto = controller.getUebersicht(null, null).getBody();

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
    void kettenVorschlaegeListetAlleOffenenRechnungen() {
        LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        ab.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 1)));
        LieferantDokument r1 = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        r1.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 5)));
        LieferantDokument r2 = dokument(3L, LieferantDokumentTyp.RECHNUNG);
        r2.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 6)));
        when(dokumentRepository.findAll()).thenReturn(List.of(ab, r1, r2));

        var antwort = controller.getKettenVorschlaege(List.of(1L), false, null, null, null);

        assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(antwort.getBody()).extracting(v -> v.dokument().id).containsExactlyInAnyOrder(2L, 3L);
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

        var dto = controller.getUebersicht(null, null).getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.laufendeBestellungen().get(0).rechnungsVorschlag()).isNull();
        // Im Fenster „Dokument zur Kette hinzufügen“ erst mit „alle Lieferanten“
        assertThat(controller.getKettenVorschlaege(List.of(1L), false, null, null, null).getBody()).isEmpty();
        assertThat(controller.getKettenVorschlaege(List.of(1L), true, null, null, null).getBody())
                .extracting(v -> v.dokument().id).containsExactly(2L);
    }

    @Test
    void schonZugeordneteUndAusgeblendeteRechnungWirdMitHinweisVorgeschlagen() {
        // Früher fehlte eine Rechnung, die schon an einer (auch ausgeblendeten) AB hing.
        // Bei Teilrechnungen/Teillieferungen gehört sie aber zu mehreren Bestellungen.
        LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        ab.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 1)));
        LieferantDokument andereAb = dokument(2L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        andereAb.setAusgeblendet(true);
        andereAb.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 5, 20)));
        andereAb.getGeschaeftsdaten().setDokumentNummer("AB-4711");
        LieferantDokument rechnung = dokument(3L, LieferantDokumentTyp.RECHNUNG);
        rechnung.setAusgeblendet(true);
        rechnung.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 5)));
        verknuepfe(rechnung, andereAb);
        when(dokumentRepository.findAll()).thenReturn(List.of(ab, andereAb, rechnung));

        var liste = controller.getKettenVorschlaege(List.of(1L), false, null, null, null).getBody();

        assertThat(liste).hasSize(1);
        assertThat(liste.get(0).dokument().id).isEqualTo(3L);
        assertThat(liste.get(0).dokument().ausgeblendet).isTrue();
        assertThat(liste.get(0).gehoertSchonZu()).isEqualTo("Auftragsbestätigung AB-4711");
    }

    @Test
    void dokumenteDieserKetteFehlenImFenster() {
        LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN);
        ls.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 1)));
        LieferantDokument schonDran = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        schonDran.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 5)));
        schonDran.setVerknuepfteDokumente(new HashSet<>(List.of(ls)));
        ls.setVerknuepftVon(new HashSet<>(List.of(schonDran)));
        LieferantDokument andere = dokument(3L, LieferantDokumentTyp.RECHNUNG);
        andere.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 6)));
        when(dokumentRepository.findAll()).thenReturn(List.of(ls, schonDran, andere));

        assertThat(controller.getKettenVorschlaege(List.of(1L), false, null, null, null).getBody())
                .extracting(v -> v.dokument().id).containsExactly(3L);
    }

    @Test
    void ausgeblendeteRechnungMachtKetteErledigt() {
        // Bezahlte Rechnungen werden ausgeblendet – ihr Lieferschein stand früher
        // trotzdem ewig unter „Rechnung fehlt“.
        LieferantDokument lieferschein = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument rechnung = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        rechnung.setAusgeblendet(true);
        verknuepfe(rechnung, lieferschein);
        when(dokumentRepository.findAll()).thenReturn(List.of(lieferschein, rechnung));

        var dto = controller.getUebersicht(null, null).getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.laufendeBestellungen()).isEmpty();
        assertThat(dto.ausgeblendet()).hasSize(1);
        var kette = dto.ausgeblendet().get(0);
        assertThat(kette.dokumente()).extracting(d -> d.id, d -> d.ausgeblendet)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, false),
                        org.assertj.core.groups.Tuple.tuple(2L, true));
    }

    @Test
    void eingeblendeteUnzugeordneteRechnungHatVorrang() {
        LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        LieferantDokument bezahlt = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        bezahlt.setAusgeblendet(true);
        verknuepfe(bezahlt, ab);
        LieferantDokument teilrechnung = dokument(3L, LieferantDokumentTyp.RECHNUNG);
        verknuepfe(teilrechnung, ab);
        when(dokumentRepository.findAll()).thenReturn(List.of(ab, bezahlt, teilrechnung));

        var dto = controller.getUebersicht(null, null).getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.abgeschlossen()).hasSize(1);
        assertThat(dto.abgeschlossen().get(0).dokumente()).hasSize(3);
        assertThat(dto.ausgeblendet()).isEmpty();
    }

    @Test
    void zugeordneteRechnungUndAllesAusgeblendet() {
        LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument rechnung = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        verknuepfe(rechnung, ls);
        var anteil = new org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil();
        anteil.setDokument(rechnung);
        when(projektAnteilRepository.findAll()).thenReturn(List.of(anteil));
        LieferantDokument versteckteAnfrage = dokument(3L, LieferantDokumentTyp.ANGEBOT);
        versteckteAnfrage.setAusgeblendet(true);
        LieferantDokument halbVersteckt = dokument(4L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        halbVersteckt.setAusgeblendet(true);
        LieferantDokument sichtbarerLs = dokument(5L, LieferantDokumentTyp.LIEFERSCHEIN);
        verknuepfe(sichtbarerLs, halbVersteckt);
        when(dokumentRepository.findAll()).thenReturn(List.of(ls, rechnung, versteckteAnfrage, halbVersteckt, sichtbarerLs));

        var dto = controller.getUebersicht(null, null).getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.zugeordnet()).hasSize(1);
        assertThat(dto.ausgeblendet()).extracting(k -> k.dokumente().get(0).id).containsExactly(3L);
        // Nur die AB ist ausgeblendet, der Lieferschein nicht -> weiter laufend
        assertThat(dto.laufendeBestellungen()).hasSize(1);
        assertThat(dto.laufendeBestellungen().get(0).dokumente()).hasSize(2);
    }

    @Test
    void verbindungenJedeKanteEinmal() {
        LieferantDokument ls1 = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument ls2 = dokument(2L, LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument rechnung = dokument(3L, LieferantDokumentTyp.RECHNUNG);
        LieferantDokument gutschrift = dokument(4L, LieferantDokumentTyp.GUTSCHRIFT);
        verknuepfe(rechnung, ls1);
        verknuepfe(rechnung, ls2);
        // Doppelt gespeichert (beide Richtungen) zählt nur einmal
        verknuepfe(ls2, rechnung);
        verknuepfe(gutschrift, rechnung);
        when(dokumentRepository.findAll()).thenReturn(List.of(ls1, ls2, rechnung, gutschrift));

        var dto = controller.getUebersicht(null, null).getBody();

        assertThat(dto).isNotNull();
        var kette = dto.abgeschlossen().get(0);
        assertThat(kette.dokumente()).hasSize(4);
        assertThat(kette.verbindungen()).hasSize(3)
                .contains(new Verbindung(3L, 1L),
                        new Verbindung(4L, 3L));
        assertThat(kette.verbindungen()).filteredOn(v -> v.vonId() + v.zuId() == 5L).hasSize(1);
    }

    @Test
    void kartenVorschlagAuchAusAusgeblendetenRechnungen() {
        LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        ab.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 1)));
        ab.getGeschaeftsdaten().setDokumentNummer("AB-77001");
        LieferantDokument bezahlt = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        bezahlt.setAusgeblendet(true);
        bezahlt.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 20)));
        bezahlt.getGeschaeftsdaten().setReferenzNummer("AB 77001");
        // Ohne Verknüpfung steht die AB laufend, die bezahlte Rechnung allein erledigt
        when(dokumentRepository.findAll()).thenReturn(List.of(ab, bezahlt));

        var dto = controller.getUebersicht(null, null).getBody();

        assertThat(dto).isNotNull();
        var vorschlag = dto.laufendeBestellungen().get(0).rechnungsVorschlag();
        assertThat(vorschlag).isNotNull();
        assertThat(vorschlag.rechnung().id).isEqualTo(2L);
        assertThat(vorschlag.rechnung().ausgeblendet).isTrue();
        assertThat(vorschlag.gehoertSchonZu()).isNull();
    }

    @Test
    void kartenVorschlagNurAusDemZeitfenster() {
        LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        ab.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 6, 1)));
        ab.getGeschaeftsdaten().setDokumentNummer("AB-77001");
        LieferantDokument vielSpaeter = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        vielSpaeter.setAusgeblendet(true);
        vielSpaeter.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2027, 3, 1)));
        vielSpaeter.getGeschaeftsdaten().setReferenzNummer("AB 77001");
        when(dokumentRepository.findAll()).thenReturn(List.of(ab, vielSpaeter));

        var dto = controller.getUebersicht(null, null).getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.laufendeBestellungen().get(0).rechnungsVorschlag()).isNull();
        // Das Fenster „Dokument zur Kette hinzufügen“ reicht ein Jahr weit
        assertThat(controller.getKettenVorschlaege(List.of(1L), false, null, null, null).getBody())
                .extracting(v -> v.dokument().id).containsExactly(2L);
    }

    @Test
    void kartenFensterGrenzen() {
        LieferantDokument rechnung = dokument(9L, LieferantDokumentTyp.RECHNUNG);
        List<LocalDate> bestellung = List.of(LocalDate.of(2026, 6, 1));
        rechnung.setGeschaeftsdaten(geschaeftsdaten(LocalDate.of(2026, 5, 2)));
        assertThat(BestellungsUebersichtService.imKartenFenster(rechnung, bestellung)).isTrue();
        rechnung.getGeschaeftsdaten().setDokumentDatum(LocalDate.of(2026, 5, 1));
        assertThat(BestellungsUebersichtService.imKartenFenster(rechnung, bestellung)).isFalse();
        rechnung.getGeschaeftsdaten().setDokumentDatum(LocalDate.of(2026, 11, 28));
        assertThat(BestellungsUebersichtService.imKartenFenster(rechnung, bestellung)).isTrue();
        rechnung.getGeschaeftsdaten().setDokumentDatum(LocalDate.of(2026, 11, 29));
        assertThat(BestellungsUebersichtService.imKartenFenster(rechnung, bestellung)).isFalse();
        // Ohne Datum bleibt sie im Rennen
        assertThat(BestellungsUebersichtService.imKartenFenster(rechnung, List.of())).isTrue();
        rechnung.getGeschaeftsdaten().setDokumentDatum(null);
        assertThat(BestellungsUebersichtService.imKartenFenster(rechnung, bestellung)).isTrue();
    }

    @Test
    void sehrLangeKetteOhneStackueberlauf() {
        List<LieferantDokument> alle = new java.util.ArrayList<>();
        LieferantDokument vorher = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN);
        alle.add(vorher);
        for (long id = 2; id <= 20_000; id++) {
            LieferantDokument ls = dokument(id, LieferantDokumentTyp.LIEFERSCHEIN);
            verknuepfe(ls, vorher);
            alle.add(ls);
            vorher = ls;
        }
        when(dokumentRepository.findAll()).thenReturn(alle);

        var dto = controller.getUebersicht(null, null).getBody();

        assertThat(dto.laufendeBestellungen()).hasSize(1);
        assertThat(dto.laufendeBestellungen().get(0).dokumente()).hasSize(20_000);
    }

    @Test
    void abhaengenLoestUndSperrt() {
        LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument rechnung = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        rechnung.getVerknuepfteDokumente().add(ls);
        ls.getVerknuepftVon().add(rechnung);
        when(dokumentRepository.findById(2L)).thenReturn(java.util.Optional.of(rechnung));
        when(dokumentRepository.findById(Long.MAX_VALUE)).thenReturn(java.util.Optional.empty());

        var antwort = controller.abhaengen(new BestellungsUebersichtController.AbhaengenRequest(2L), null);

        assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(antwort.getBody()).isEqualTo(java.util.Map.of("geloest", 1));
        assertThat(rechnung.getVerknuepfteDokumente()).isEmpty();
        org.mockito.Mockito.verify(sperreRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
        assertThat(controller.abhaengen(new BestellungsUebersichtController.AbhaengenRequest(Long.MAX_VALUE), null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.abhaengen(new BestellungsUebersichtController.AbhaengenRequest(0L), null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.abhaengen(new BestellungsUebersichtController.AbhaengenRequest(-1L), null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.abhaengen(new BestellungsUebersichtController.AbhaengenRequest(null), null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rechnungHochladenGibtDieNeueRechnungZurueck() throws Exception {
        var datei = new org.springframework.mock.web.MockMultipartFile(
                "datei", "rechnung.pdf", "application/pdf", new byte[] { 1, 2, 3 });
        LieferantDokument neu = dokument(9L, LieferantDokumentTyp.RECHNUNG);
        neu.setOriginalDateiname("rechnung.pdf");
        neu.setGespeicherterDateiname("x_rechnung.pdf");
        when(lieferantDokumentService.rechnungZuBestellungHochladen(1L, datei, null)).thenReturn(neu);

        var antwort = controller.rechnungHochladen(datei, 1L, null);

        assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
        var ref = (DokumentRef) antwort.getBody();
        assertThat(ref.id).isEqualTo(9L);
        assertThat(ref.typ).isEqualTo(LieferantDokumentTyp.RECHNUNG);
        assertThat(ref.pdfUrl).isEqualTo("/api/lieferanten/1/dokumente/9/download");
    }

    @Test
    void rechnungHochladenFehlerfaelle() throws Exception {
        var datei = new org.springframework.mock.web.MockMultipartFile(
                "datei", "../../etc/passwd.exe", "application/octet-stream", new byte[] { 1 });
        when(lieferantDokumentService.rechnungZuBestellungHochladen(1L, datei, null))
                .thenThrow(new org.example.kalkulationsprogramm.service.BelegAbgelehntException("Nur PDF, JPG oder PNG erlaubt."));
        when(lieferantDokumentService.rechnungZuBestellungHochladen(3L, datei, null))
                .thenThrow(new IllegalArgumentException("interne Meldung mit Details"));
        when(lieferantDokumentService.rechnungZuBestellungHochladen(Long.MAX_VALUE, datei, null))
                .thenThrow(new java.util.NoSuchElementException());
        when(lieferantDokumentService.rechnungZuBestellungHochladen(2L, datei, null))
                .thenThrow(new java.io.IOException("Platte voll"));

        var abgelehnt = controller.rechnungHochladen(datei, 1L, null);
        assertThat(abgelehnt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(abgelehnt.getBody()).isEqualTo(java.util.Map.of("message", "Nur PDF, JPG oder PNG erlaubt."));
        // Fremde Meldungen gehen nie nach außen
        var intern = controller.rechnungHochladen(datei, 3L, null);
        assertThat(intern.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(intern.getBody().toString()).doesNotContain("interne").contains("konnte nicht angelegt werden");
        assertThat(controller.rechnungHochladen(datei, Long.MAX_VALUE, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        var fehler = controller.rechnungHochladen(datei, 2L, null);
        assertThat(fehler.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        // Keine technische Meldung nach außen
        assertThat(fehler.getBody().toString()).doesNotContain("Platte");
        assertThat(controller.rechnungHochladen(datei, 0L, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.rechnungHochladen(datei, -5L, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void kettenVorschlaegeLehntUngueltigeIdsAb() {
        assertThat(controller.getKettenVorschlaege(List.of(), false, null, null, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.getKettenVorschlaege(List.of(-1L), false, null, null, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.getKettenVorschlaege(List.of(0L), false, null, null, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        List<Long> zuViele = java.util.stream.LongStream.rangeClosed(1, 51).boxed().toList();
        assertThat(controller.getKettenVorschlaege(zuViele, false, null, null, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void kettenVorschlaegeOhneBekanntesDokument() {
        // Es gibt Dokumente, aber nicht das angefragte
        when(dokumentRepository.findAll()).thenReturn(List.of(dokument(1L, LieferantDokumentTyp.ANGEBOT)));

        assertThat(controller.getKettenVorschlaege(List.of(Long.MAX_VALUE), false, null, null, null).getStatusCode())
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

    /** Wie JPA: beide Seiten der Verknüpfung (Nachfolger -> Vorgänger). */
    private static void verknuepfe(LieferantDokument nachfolger, LieferantDokument vorgaenger) {
        nachfolger.getVerknuepfteDokumente().add(vorgaenger);
        vorgaenger.getVerknuepftVon().add(nachfolger);
    }

    private LieferantGeschaeftsdokument geschaeftsdaten(LocalDate datum) {
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setDokumentDatum(datum);
        return gd;
    }

    // ------------------------------------------------------------ Dokumentrechte

    private void sichtbar(LieferantDokumentTyp... typen) {
        lenient().when(zugriffService.sichtbareTypen(any(), any())).thenReturn(Optional.of(
                typen.length == 0 ? EnumSet.noneOf(LieferantDokumentTyp.class) : EnumSet.copyOf(List.of(typen))));
    }

    @Test
    void uebersichtOhneAnmeldungGibt401() {
        when(zugriffService.sichtbareTypen(any(), any())).thenReturn(Optional.empty());

        assertThat(controller.getUebersicht("unbekannt", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.getKettenVorschlaege(List.of(1L), false, null, null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(dokumentRepository);
    }

    @Test
    void uebersichtZeigtNurSichtbareDokumenttypen() {
        sichtbar(LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument lieferschein = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument rechnung = dokument(2L, LieferantDokumentTyp.RECHNUNG);
        verknuepfe(rechnung, lieferschein);
        when(dokumentRepository.findAll()).thenReturn(List.of(lieferschein, rechnung));

        var dto = controller.getUebersicht(null, null).getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.abgeschlossen()).isEmpty();
        assertThat(dto.laufendeBestellungen()).hasSize(1);
        assertThat(dto.laufendeBestellungen().get(0).dokumente()).extracting(ref -> ref.id).containsExactly(1L);
    }

    @Test
    void kettenVorschlaegeFuerNichtSichtbaresDokumentGeben404() {
        sichtbar(LieferantDokumentTyp.RECHNUNG);
        when(dokumentRepository.findAll()).thenReturn(List.of(dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN)));

        assertThat(controller.getKettenVorschlaege(List.of(1L), false, null, null, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void geschaeftsdatenEinesNichtSichtbarenTypsGeben404UndWerdenNichtGeaendert() {
        sichtbar(LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantGeschaeftsdokument gd = geschaeftsdaten(LocalDate.of(2026, 9, 1));
        gd.setId(5L);
        gd.setDokumentNummer("RE-1");
        gd.setDokument(dokument(5L, LieferantDokumentTyp.RECHNUNG));
        when(geschaeftsdokumentRepository.findById(5L)).thenReturn(java.util.Optional.of(gd));
        var aenderung = new BestellungsUebersichtController.GeschaeftsdatenDto();
        aenderung.dokumentNummer = "RE-GEAENDERT";

        assertThat(controller.getGeschaeftsdaten(5L, null, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.updateGeschaeftsdaten(5L, aenderung, null, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(gd.getDokumentNummer()).isEqualTo("RE-1");
        org.mockito.Mockito.verify(geschaeftsdokumentRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void geschaeftsdatenEinesSichtbarenTypsWerdenGeliefert() {
        sichtbar(LieferantDokumentTyp.RECHNUNG);
        LieferantGeschaeftsdokument gd = geschaeftsdaten(LocalDate.of(2026, 9, 1));
        gd.setId(5L);
        gd.setDokumentNummer("RE-1");
        gd.setDokument(dokument(5L, LieferantDokumentTyp.RECHNUNG));
        when(geschaeftsdokumentRepository.findById(5L)).thenReturn(java.util.Optional.of(gd));

        var antwort = controller.getGeschaeftsdaten(5L, null, null);

        assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(antwort.getBody().dokumentNummer).isEqualTo("RE-1");
    }

    @Test
    void zuordnungenEinesNichtSichtbarenTypsSindLeer() {
        sichtbar(LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantGeschaeftsdokument gd = geschaeftsdaten(LocalDate.of(2026, 9, 1));
        gd.setId(5L);
        gd.setDokument(dokument(5L, LieferantDokumentTyp.RECHNUNG));
        when(geschaeftsdokumentRepository.findById(5L)).thenReturn(java.util.Optional.of(gd));

        assertThat(controller.getZuordnungen(5L, null, null).getBody()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(projektAnteilRepository);
    }
}
