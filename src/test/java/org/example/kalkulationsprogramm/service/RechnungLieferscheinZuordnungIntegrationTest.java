package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.config.LieferantDokumentAbgleichBackfillRunner;
import org.example.kalkulationsprogramm.controller.BestellungsUebersichtController;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentVerknuepfungSperreRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

/**
 * Integrationstest gegen eine echte (H2-)Datenbank: Rechnungen finden ihre
 * Lieferscheine und Auftragsbestätigungen – beim Neu-Verknüpfen, in der
 * Bestellübersicht, beim Verknüpfen/Abhängen von Hand und über den Belegscanner.
 *
 * <p>Nur die KI (Gemini-Aufruf) und Nachbardienste ohne Bezug zur Zuordnung sind
 * gemockt. Alle Nummern sind erfunden, der Lieferant ist ein Dummy.
 */
@DataJpaTest
@Import({ GeminiDokumentAnalyseService.class, LieferantDokumentAbgleich.class, RechnungsVorschlagService.class,
        BelegZuordnungService.class, BelegKiAnalyseService.class, SystemSettingsService.class,
        BestellungsUebersichtController.class,
        RechnungLieferscheinZuordnungIntegrationTest.Konfiguration.class })
class RechnungLieferscheinZuordnungIntegrationTest {

    @TestConfiguration
    static class Konfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        /** Hintergrundaufgaben laufen im Test sofort. */
        @Bean(name = "taskExecutor")
        TaskExecutor taskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired private GeminiDokumentAnalyseService analyseService;
    @Autowired private RechnungsVorschlagService vorschlagService;
    @Autowired private BelegKiAnalyseService belegKiAnalyseService;
    @Autowired private SystemSettingsService systemSettingsService;
    @Autowired private BestellungsUebersichtController uebersichtController;
    @Autowired private LieferantDokumentRepository dokumentRepository;
    @Autowired private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    @Autowired private LieferantDokumentVerknuepfungSperreRepository sperreRepository;
    @Autowired private LieferantenRepository lieferantenRepository;
    @Autowired private BelegRepository belegRepository;
    @Autowired private EntityManager entityManager;

    // Externes: KI-Aufruf (über Spy) und Dienste ohne Bezug zur Zuordnung
    @SpyBean private GeminiDokumentAnalyseService analyseSpy;
    @MockBean private ZugferdExtractorService zugferdExtractorService;
    @MockBean private BelegKiKostenkontoService kostenkontoService;
    @MockBean private BelegSplitService belegSplitService;
    @MockBean private BelegAuditService belegAuditService;
    @MockBean private BelegService belegService;
    @MockBean private LieferantDokumentService lieferantDokumentService;

    private Lieferanten lieferant;

    @BeforeEach
    void legeLieferantAn() {
        lieferant = new Lieferanten();
        lieferant.setLieferantenname("Muster GmbH");
        lieferant = lieferantenRepository.saveAndFlush(lieferant);
    }

    private LieferantDokument dokument(LieferantDokumentTyp typ, String nummer, LocalDate datum) {
        LieferantDokument d = new LieferantDokument();
        d.setLieferant(lieferant);
        d.setTyp(typ);
        d.setUploadDatum(LocalDateTime.of(2026, 9, 1, 8, 0));
        d = dokumentRepository.saveAndFlush(d);
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setDokument(d);
        gd.setDokumentNummer(nummer);
        gd.setDokumentDatum(datum);
        geschaeftsdokumentRepository.saveAndFlush(gd);
        d.setGeschaeftsdaten(gd);
        return d;
    }

    private LieferantGeschaeftsdokument daten(LieferantDokument d) {
        return d.getGeschaeftsdaten();
    }

    /** Speichert alles und liest beim nächsten Zugriff frisch aus der Datenbank. */
    private void speichern() {
        entityManager.flush();
        entityManager.clear();
    }

    private LieferantDokument neuGeladen(LieferantDokument d) {
        return dokumentRepository.findById(d.getId()).orElseThrow();
    }

    private List<Long> vorgaengerIds(LieferantDokument d) {
        return neuGeladen(d).getVerknuepfteDokumente().stream().map(LieferantDokument::getId).toList();
    }

    // ---------------------------------------------------------------- NeuVerknuepfen

    @Test
    void muster1_vierTeillieferungenEineRechnung() {
        List<LieferantDokument> lieferscheine = new java.util.ArrayList<>();
        for (int tag : new int[] { 2, 4, 6, 9 }) {
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-7000" + tag, LocalDate.of(2026, 3, tag));
            daten(ls).setBestellnummer("47711111");
            daten(ls).setReferenzNummer("2299000001");
            lieferscheine.add(ls);
        }
        LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-5550001", LocalDate.of(2026, 3, 31));
        daten(rechnung).setBestellnummer("47711111");
        daten(rechnung).setReferenzNummer("2299000001");
        speichern();

        analyseService.relinkAlleDokumente();
        speichern();

        assertThat(vorgaengerIds(rechnung))
                .containsExactlyInAnyOrderElementsOf(lieferscheine.stream().map(LieferantDokument::getId).toList());
    }

    @Test
    void muster2_abLieferscheinUndZweiTeilrechnungen() {
        LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "RL 12 - 98700001/1", LocalDate.of(2026, 8, 1));
        daten(ab).setBestellnummer("telef. vom 30.07.2026");
        LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "RL 12 - 98700001/01", LocalDate.of(2026, 8, 10));
        LieferantDokument teil1 = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1001", LocalDate.of(2026, 8, 15));
        daten(teil1).setReferenzNummer("98700001");
        daten(teil1).setBestellnummer("telef. vom 30.07.2026");
        LieferantDokument teil2 = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1002", LocalDate.of(2026, 9, 20));
        daten(teil2).setReferenzNummer("98700001");
        speichern();

        analyseService.relinkAlleDokumente();
        speichern();

        assertThat(vorgaengerIds(teil1)).containsExactlyInAnyOrder(ab.getId(), ls.getId());
        assertThat(vorgaengerIds(teil2)).containsExactlyInAnyOrder(ab.getId(), ls.getId());
    }

    @Test
    void kundennummerUeberMonateVerknuepftNichts() {
        for (int monat : new int[] { 1, 3, 5, 7 }) {
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-60" + monat, LocalDate.of(2026, monat, 12));
            daten(ls).setReferenzNummer("9900123");
        }
        LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-80001", LocalDate.of(2026, 7, 28));
        daten(rechnung).setReferenzNummer("Kd-Nr. 9900123");
        speichern();

        assertThat(analyseService.relinkAlleDokumente()).isZero();
        speichern();

        assertThat(vorgaengerIds(rechnung)).isEmpty();
    }

    @Test
    void zweiBestellungenMitGleicherKundennummer_keineKreuzverknuepfung() {
        // Junger Lieferant: Die Kundennummer steht erst auf zwei Lieferscheinen in 30 Tagen.
        LieferantDokument lsA = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-A1", LocalDate.of(2026, 3, 1));
        daten(lsA).setReferenzNummer("Kd 9900123");
        daten(lsA).setBestellnummer("BE 55500011");
        LieferantDokument lsB = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-B1", LocalDate.of(2026, 3, 31));
        daten(lsB).setReferenzNummer("Kd 9900123");
        daten(lsB).setBestellnummer("BE 55500022");
        LieferantDokument rechnungA = dokument(LieferantDokumentTyp.RECHNUNG, "RE-A1", LocalDate.of(2026, 4, 10));
        daten(rechnungA).setReferenzNummer("Kd 9900123");
        daten(rechnungA).setBestellnummer("55500011");
        LieferantDokument rechnungB = dokument(LieferantDokumentTyp.RECHNUNG, "RE-B1", LocalDate.of(2026, 4, 30));
        daten(rechnungB).setReferenzNummer("Kd 9900123");
        daten(rechnungB).setBestellnummer("55500022");
        speichern();

        analyseService.relinkAlleDokumente();
        speichern();

        assertThat(vorgaengerIds(rechnungA)).containsExactly(lsA.getId());
        assertThat(vorgaengerIds(rechnungB)).containsExactly(lsB.getId());
    }

    @Test
    void ausgeblendeteRechnungWirdTrotzdemVerknuepft() {
        LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "AU-990017", LocalDate.of(2026, 7, 20));
        LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-80002", LocalDate.of(2026, 7, 28));
        daten(rechnung).setReferenzNummer("AU-990017");
        rechnung.setAusgeblendet(true);
        speichern();

        analyseService.relinkAlleDokumente();
        speichern();

        assertThat(vorgaengerIds(rechnung)).containsExactly(ls.getId());
    }

    @Test
    void runnerLaeuftJeVersionNurEinmal() {
        LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "AU-990018", LocalDate.of(2026, 7, 20));
        LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-80003", LocalDate.of(2026, 7, 28));
        daten(rechnung).setReferenzNummer("AU-990018");
        speichern();
        var runner = new LieferantDokumentAbgleichBackfillRunner(analyseService, systemSettingsService, Runnable::run);

        assertThat(runner.verknuepfeFallsNoetig()).isTrue();
        speichern();
        assertThat(vorgaengerIds(rechnung)).containsExactly(ls.getId());
        assertThat(systemSettingsService.get("lieferant.abgleich.version", null))
                .isEqualTo(String.valueOf(LieferantDokumentAbgleichBackfillRunner.ABGLEICH_VERSION));

        // Zweiter Start: Marke steht in der Datenbank -> nichts mehr zu tun
        assertThat(runner.verknuepfeFallsNoetig()).isFalse();
        org.mockito.Mockito.verify(analyseSpy, org.mockito.Mockito.times(1)).relinkAlleDokumente();
    }

    // ---------------------------------------------------------------- Uebersicht

    @Test
    void ausgeblendeteRechnungMachtLieferscheinErledigt() {
        LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-81551", LocalDate.of(2026, 6, 2));
        LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-81552", LocalDate.of(2026, 6, 20));
        rechnung.getVerknuepfteDokumente().add(ls);
        rechnung.setAusgeblendet(true);
        speichern();

        var dto = uebersichtController.getUebersicht().getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.laufendeBestellungen()).isEmpty();
        assertThat(dto.ausgeblendet()).hasSize(1);
        var kette = dto.ausgeblendet().get(0);
        assertThat(kette.dokumente()).extracting(d -> d.id).containsExactlyInAnyOrder(ls.getId(), rechnung.getId());
        assertThat(kette.verbindungen())
                .containsExactly(new BestellungsUebersichtController.Verbindung(rechnung.getId(), ls.getId()));
    }

    @Test
    void eingeblendeteUnzugeordneteRechnungHatVorrang() {
        LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-3001", LocalDate.of(2026, 6, 1));
        LieferantDokument bezahlt = dokument(LieferantDokumentTyp.RECHNUNG, "RE-3002", LocalDate.of(2026, 6, 10));
        bezahlt.getVerknuepfteDokumente().add(ab);
        bezahlt.setAusgeblendet(true);
        LieferantDokument offen = dokument(LieferantDokumentTyp.RECHNUNG, "RE-3003", LocalDate.of(2026, 7, 10));
        offen.getVerknuepfteDokumente().add(ab);
        speichern();

        var dto = uebersichtController.getUebersicht().getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.abgeschlossen()).hasSize(1);
        assertThat(dto.ausgeblendet()).isEmpty();
        assertThat(dto.abgeschlossen().get(0).verbindungen()).containsExactlyInAnyOrder(
                new BestellungsUebersichtController.Verbindung(bezahlt.getId(), ab.getId()),
                new BestellungsUebersichtController.Verbindung(offen.getId(), ab.getId()));
    }

    // ---------------------------------------------------------------- VerknuepfenUndAbhaengen

    @Test
    void schonVerknuepfteRechnungAnZweitenLieferscheinVerschmilztKetten() {
        LieferantDokument ls1 = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LocalDate.of(2026, 5, 2));
        LieferantDokument ls2 = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-2", LocalDate.of(2026, 5, 9));
        LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 5, 31));
        rechnung.getVerknuepfteDokumente().add(ls1);
        speichern();
        assertThat(uebersichtController.getUebersicht().getBody().laufendeBestellungen()).hasSize(1);
        speichern();

        vorschlagService.verknuepfe(ls2.getId(), rechnung.getId(), null);
        speichern();

        var dto = uebersichtController.getUebersicht().getBody();
        assertThat(dto.laufendeBestellungen()).isEmpty();
        assertThat(dto.abgeschlossen()).hasSize(1);
        assertThat(dto.abgeschlossen().get(0).dokumente()).hasSize(3);
        assertThat(dto.abgeschlossen().get(0).verbindungen()).hasSize(2);
    }

    @Test
    void abhaengenSperrtGegenBackfillUndVerknuepfenHebtDieSperreAuf() {
        LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "AU-220001", LocalDate.of(2026, 5, 2));
        LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-220002", LocalDate.of(2026, 5, 20));
        daten(rechnung).setReferenzNummer("AU-220001");
        LieferantDokument gutschrift = dokument(LieferantDokumentTyp.GUTSCHRIFT, "GS-220003", LocalDate.of(2026, 5, 25));
        daten(gutschrift).setReferenzNummer("RE-220002");
        speichern();
        analyseService.relinkAlleDokumente();
        speichern();
        assertThat(vorgaengerIds(rechnung)).containsExactly(ls.getId());
        assertThat(vorgaengerIds(gutschrift)).containsExactly(rechnung.getId());

        int geloest = vorschlagService.haengeAb(rechnung.getId(), null);
        speichern();

        assertThat(geloest).isEqualTo(2);
        assertThat(vorgaengerIds(rechnung)).isEmpty();
        assertThat(vorgaengerIds(gutschrift)).isEmpty();
        assertThat(sperreRepository.findAll()).hasSize(2);

        // Neu-Verknüpfen (z. B. nach dem nächsten Start) holt das Paar NICHT zurück
        analyseService.relinkAlleDokumente();
        speichern();
        assertThat(vorgaengerIds(rechnung)).isEmpty();
        assertThat(vorgaengerIds(gutschrift)).isEmpty();

        // Von Hand wieder verknüpft: Sperre weg, Verknüpfung da
        vorschlagService.verknuepfe(ls.getId(), rechnung.getId(), null);
        speichern();
        assertThat(vorgaengerIds(rechnung)).containsExactly(ls.getId());
        assertThat(sperreRepository.findAll())
                .extracting(s -> s.getDokumentId(), s -> s.getVerknuepftId())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(gutschrift.getId(), rechnung.getId()));
    }

    // ---------------------------------------------------------------- Belegscanner

    private Beleg beleg(Lieferanten belegLieferant) {
        Beleg b = new Beleg();
        b.setLieferant(belegLieferant);
        b.setOriginalDateiname("scan.jpg");
        b.setGespeicherterDateiname("muster-scan.jpg");
        b.setMimeType("image/jpeg");
        b.setUploadDatum(LocalDateTime.of(2026, 6, 12, 9, 0));
        return belegRepository.saveAndFlush(b);
    }

    private LieferantDokumentDto.AnalyzeResponse kiRechnung(String lieferantName) {
        return LieferantDokumentDto.AnalyzeResponse.builder()
                .dokumentTyp(LieferantDokumentTyp.RECHNUNG)
                .dokumentNummer("RE-33000777")
                .dokumentDatum(LocalDate.of(2026, 6, 10))
                .betragNetto(new BigDecimal("100.00"))
                .betragBrutto(new BigDecimal("119.00"))
                .referenzNummer("LS-31000555")
                .lieferantName(lieferantName)
                .build();
    }

    @Test
    void ohneLieferantEntstehtNichts() {
        dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-31000555", LocalDate.of(2026, 6, 2));
        Beleg beleg = beleg(null);
        // Die KI nennt einen Namen, den es in den Stammdaten nicht gibt
        doReturn(kiRechnung("Unbekannte Erika Musterfrau KG")).when(analyseSpy).analyzeFile(any(), anyString());
        speichern();

        belegKiAnalyseService.analysiereBelegAsync(beleg.getId());
        speichern();

        assertThat(dokumentRepository.findByBelegId(beleg.getId())).isEmpty();
        assertThat(dokumentRepository.findAll()).hasSize(1);
    }
}
