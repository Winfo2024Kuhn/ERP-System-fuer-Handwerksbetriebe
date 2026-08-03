package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.Beschaeftigungsart;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.domain.Gewerk;
import org.example.kalkulationsprogramm.domain.Krankenkasse;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.SvSatz;
import org.example.kalkulationsprogramm.domain.SvSatzTyp;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnUebernehmenRequest;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.FeiertagRepository;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LohnabrechnungRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterStundenlohnRepository;
import org.example.kalkulationsprogramm.repository.SvSatzRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests fuer den VerrechnungslohnService - Lohn/Stunden/Gemeinkosten-Block,
 * Modus-Switch und globales Uebernehmen.
 *
 * Pure Mockito-Tests; alle Mitarbeiter sind dummy ("Max Mustermann", "Erika
 * Musterfrau").
 */
class VerrechnungslohnServiceTest {

    private MitarbeiterRepository mitarbeiterRepository;
    private MitarbeiterStundenlohnRepository stundenlohnRepository;
    private LohnabrechnungRepository lohnabrechnungRepository;
    private ZeitbuchungRepository zeitbuchungRepository;
    private ZeitkontoRepository zeitkontoRepository;
    private AbwesenheitRepository abwesenheitRepository;
    private FeiertagRepository feiertagRepository;
    private SvSatzRepository svSatzRepository;
    private FirmeninformationRepository firmeninformationRepository;
    private LieferantDokumentProjektAnteilRepository anteilRepository;
    private AbteilungRepository abteilungRepository;
    private ArbeitsgangRepository arbeitsgangRepository;
    private ArbeitsgangStundensatzRepository stundensatzRepository;
    private BelegRepository belegRepository;
    private BelegKostenstellenAnteilRepository belegKostenstellenAnteilRepository;

    private VerrechnungslohnService service;

    @BeforeEach
    void setUp() {
        mitarbeiterRepository = mock(MitarbeiterRepository.class);
        stundenlohnRepository = mock(MitarbeiterStundenlohnRepository.class);
        lohnabrechnungRepository = mock(LohnabrechnungRepository.class);
        zeitbuchungRepository = mock(ZeitbuchungRepository.class);
        zeitkontoRepository = mock(ZeitkontoRepository.class);
        abwesenheitRepository = mock(AbwesenheitRepository.class);
        feiertagRepository = mock(FeiertagRepository.class);
        svSatzRepository = mock(SvSatzRepository.class);
        firmeninformationRepository = mock(FirmeninformationRepository.class);
        anteilRepository = mock(LieferantDokumentProjektAnteilRepository.class);
        abteilungRepository = mock(AbteilungRepository.class);
        arbeitsgangRepository = mock(ArbeitsgangRepository.class);
        stundensatzRepository = mock(ArbeitsgangStundensatzRepository.class);
        belegRepository = mock(BelegRepository.class);
        when(belegRepository.findValidierteFixkostenBelegeImZeitraum(any(), any()))
                .thenReturn(Collections.emptyList());
        belegKostenstellenAnteilRepository = mock(BelegKostenstellenAnteilRepository.class);
        when(belegKostenstellenAnteilRepository.findAll()).thenReturn(Collections.emptyList());

        service = new VerrechnungslohnService(
                mitarbeiterRepository,
                stundenlohnRepository,
                lohnabrechnungRepository,
                zeitbuchungRepository,
                zeitkontoRepository,
                abwesenheitRepository,
                feiertagRepository,
                svSatzRepository,
                firmeninformationRepository,
                anteilRepository,
                abteilungRepository,
                arbeitsgangRepository,
                stundensatzRepository,
                belegRepository,
                belegKostenstellenAnteilRepository
        );

        // Defaults: keine Personen, keine Anteile, keine Feiertage, kein BG-Satz.
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(Collections.emptyList());
        when(anteilRepository.findAll()).thenReturn(Collections.emptyList());
        when(abteilungRepository.findAll()).thenReturn(Collections.emptyList());
        when(arbeitsgangRepository.findAll()).thenReturn(Collections.emptyList());
        when(feiertagRepository.findByJahrAndBundesland(anyInt(), anyString())).thenReturn(Collections.emptyList());
        when(firmeninformationRepository.findById(1L)).thenReturn(Optional.empty());
        when(svSatzRepository.findFirstBySatzTypAndGueltigAbLessThanEqualOrderByGueltigAbDesc(any(SvSatzTyp.class), any(LocalDate.class)))
                .thenReturn(Optional.empty());
    }

    // ==================== berechne(): Modus-Logik ====================

    @Test
    void berechneOhneMitarbeiterUndOhneGemeinkostenLiefertNullSelbstkosten() {
        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        assertThat(dto.getJahr()).isEqualTo(2024);
        assertThat(dto.getModus()).isEqualTo(VerrechnungslohnErgebnisDto.Modus.RUECKWIRKEND);
        assertThat(dto.getLohnsummeGesamt()).isEqualByComparingTo("0");
        assertThat(dto.getVerkaeuflicheStundenGesamt()).isEqualByComparingTo("0");
        assertThat(dto.getGemeinkostenGesamt()).isEqualByComparingTo("0");
        assertThat(dto.getSelbstkostenProStunde()).isEqualByComparingTo("0");
    }

    @Test
    void berechneFuerLaufendesJahrSetztModusHochrechnung() {
        VerrechnungslohnErgebnisDto dto = service.berechne(Year.now().getValue());
        assertThat(dto.getModus()).isEqualTo(VerrechnungslohnErgebnisDto.Modus.HOCHRECHNUNG);
    }

    @Test
    void berechneFuerZukuenftigesJahrSetztModusHochrechnung() {
        VerrechnungslohnErgebnisDto dto = service.berechne(Year.now().getValue() + 1);
        assertThat(dto.getModus()).isEqualTo(VerrechnungslohnErgebnisDto.Modus.HOCHRECHNUNG);
    }

    // ==================== berechne(): GF-Sonderbehandlung ====================

    @Test
    void geschaeftsfuehrerKalkulatorischerLohnFliesstVollInLohnsumme() {
        Mitarbeiter gf = mitarbeiter(1L, "Max", "Mustermann");
        gf.setIstGeschaeftsfuehrer(true);
        gf.setBeschaeftigungsart(Beschaeftigungsart.GF_SV_FREI);
        gf.setKalkulatorischerLohnMonat(new BigDecimal("5000.00"));
        gf.setGeldwertVorteilMonat(new BigDecimal("500.00"));
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(gf));

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        // 5000*12 (kalk) + 500*12 (geldwert) = 66000, keine SV (GF_SV_FREI)
        assertThat(dto.getLohnsummeGesamt()).isEqualByComparingTo("66000.00");
        assertThat(dto.getLohnzeilen()).hasSize(1);
        VerrechnungslohnErgebnisDto.MitarbeiterLohnZeile zeile = dto.getLohnzeilen().get(0);
        assertThat(zeile.isIstGeschaeftsfuehrer()).isTrue();
        assertThat(zeile.getBruttoJahr()).isEqualByComparingTo("60000");
        assertThat(zeile.getGeldwerterVorteilJahr()).isEqualByComparingTo("6000");
        assertThat(zeile.getAgAnteilSv()).isEqualByComparingTo("0");
        assertThat(zeile.getQuelle()).isEqualTo(VerrechnungslohnErgebnisDto.LohnQuelle.KALKULATORISCH);
    }

    // ==================== berechne(): RUECKWIRKEND mit Lohnabrechnungen ====================

    @Test
    void rueckwirkendNutztLohnabrechnungBruttoSumme() {
        Mitarbeiter ma = mitarbeiter(2L, "Erika", "Musterfrau");
        ma.setBeschaeftigungsart(Beschaeftigungsart.REGULAER);
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        when(lohnabrechnungRepository.sumBruttolohnByMitarbeiterIdAndJahr(2L, 2024))
                .thenReturn(new BigDecimal("36000.00"));
        when(lohnabrechnungRepository.countByMitarbeiterIdAndJahr(2L, 2024)).thenReturn(12L);

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        assertThat(dto.getLohnzeilen()).hasSize(1);
        VerrechnungslohnErgebnisDto.MitarbeiterLohnZeile z = dto.getLohnzeilen().get(0);
        assertThat(z.getBruttoJahr()).isEqualByComparingTo("36000.00");
        assertThat(z.getQuelle()).isEqualTo(VerrechnungslohnErgebnisDto.LohnQuelle.LOHNABRECHNUNG);
        assertThat(z.isBruttoIstDefault()).isFalse();
    }

    @Test
    void rueckwirkendOhneLohnabrechnungenLegtDatenLueckeAnUndFaelltAufStammlohnZurueck() {
        Mitarbeiter ma = mitarbeiter(3L, "Hans", "Beispiel");
        ma.setStundenlohn(new BigDecimal("25.00"));
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        when(lohnabrechnungRepository.sumBruttolohnByMitarbeiterIdAndJahr(3L, 2024)).thenReturn(BigDecimal.ZERO);
        when(lohnabrechnungRepository.countByMitarbeiterIdAndJahr(3L, 2024)).thenReturn(0L);
        when(stundenlohnRepository.findFirstByMitarbeiterIdAndGueltigAbLessThanEqualOrderByGueltigAbDesc(eq(3L), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        // Je eine Luecke fuer fehlende Lohnabrechnungen, fehlende Zeitbuchungen
        // und das nicht hinterlegte Arbeitszeitmodell (kein Zeitkonto gemockt).
        assertThat(dto.getDatenLuecken()).hasSize(3);
        assertThat(dto.getDatenLuecken()).anyMatch(l -> l.getProblem().contains("Keine Lohnabrechnungen"));
        assertThat(dto.getDatenLuecken()).anyMatch(l -> l.getProblem().contains("Keine Zeitbuchungen"));
        assertThat(dto.getDatenLuecken()).anyMatch(l -> l.getProblem().contains("Keine Arbeitszeiten hinterlegt"));
        // 25 EUR × 2080h = 52000
        assertThat(dto.getLohnzeilen().get(0).getBruttoJahr()).isEqualByComparingTo("52000.00");
        assertThat(dto.getLohnzeilen().get(0).getQuelle())
                .isEqualTo(VerrechnungslohnErgebnisDto.LohnQuelle.STAMMSTUNDENLOHN);
    }

    // ==================== berechne(): SV-Berechnung ====================

    @Test
    void sozialversicherungswertWirdMitHaelfteDerSvSaetzeBerechnet() {
        Mitarbeiter ma = mitarbeiter(4L, "Anna", "Mustermann");
        ma.setBeschaeftigungsart(Beschaeftigungsart.REGULAER);
        Krankenkasse kk = new Krankenkasse();
        kk.setName("Test-KK");
        kk.setZusatzbeitragProzent(new BigDecimal("1.60"));
        ma.setKrankenkasse(kk);
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        when(lohnabrechnungRepository.sumBruttolohnByMitarbeiterIdAndJahr(4L, 2024))
                .thenReturn(new BigDecimal("40000.00"));
        when(lohnabrechnungRepository.countByMitarbeiterIdAndJahr(4L, 2024)).thenReturn(12L);
        // KV 14.6 / PV 3.4 / RV 18.6 / AV 2.6 → AG-Anteil je halbe = 7.30 / 1.70 / 9.30 / 1.30 = 19.6%
        // KK-Zusatz 1.60% / 2 = 0.80 → gesamt 20.4%, plus U1=0.9, U2=0.24, Insolvenz=0.06 (Beispielwerte aus Migrations-Seed)
        sv(SvSatzTyp.KV_GESAMT, "14.60");
        sv(SvSatzTyp.PV_GESAMT, "3.40");
        sv(SvSatzTyp.RV_GESAMT, "18.60");
        sv(SvSatzTyp.AV_GESAMT, "2.60");
        sv(SvSatzTyp.U1_UMLAGE, "0.00");
        sv(SvSatzTyp.U2_UMLAGE, "0.00");
        sv(SvSatzTyp.INSOLVENZGELDUMLAGE, "0.00");

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        // 40000 × 0.204 = 8160 (gerundet)
        BigDecimal agSv = dto.getLohnzeilen().get(0).getAgAnteilSv();
        assertThat(agSv).isEqualByComparingTo("8160.00");
    }

    // ==================== berechne(): Gemeinkosten ====================

    @Test
    void selbstkostenProStundeBeruecksichtigenLohnUndGemeinkostenUndStunden() {
        Mitarbeiter ma = mitarbeiter(5L, "Otto", "Mustermann");
        ma.setBeschaeftigungsart(Beschaeftigungsart.GF_SV_FREI);
        ma.setIstGeschaeftsfuehrer(true);
        ma.setKalkulatorischerLohnMonat(new BigDecimal("5000.00"));
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        // Zeitkonto 40h Wochenstunden → 2080h Soll, abzüglich 5% intern + Default Krank 8d × 8h = 64
        Zeitkonto zk = zeitkontoFuer(ma);
        when(zeitkontoRepository.findByMitarbeiterId(5L)).thenReturn(Optional.of(zk));

        VerrechnungslohnErgebnisDto dto = service.berechne(Year.now().getValue());

        assertThat(dto.getLohnsummeGesamt()).isEqualByComparingTo("60000.00");
        assertThat(dto.getVerkaeuflicheStundenGesamt()).isGreaterThan(BigDecimal.ZERO);
        BigDecimal selbst = dto.getLohnsummeGesamt()
                .add(dto.getGemeinkostenGesamt())
                .divide(dto.getVerkaeuflicheStundenGesamt(), 2, java.math.RoundingMode.HALF_UP);
        assertThat(dto.getSelbstkostenProStunde()).isEqualByComparingTo(selbst);
    }

    // ==================== berechne(): BG-Satz aus Firmeninformation ====================

    @Test
    void bgSatzWirdAusFirmeninformationOverrideGenutzt() {
        Firmeninformation firma = new Firmeninformation();
        Gewerk gewerk = new Gewerk();
        gewerk.setName("Schlosserei");
        gewerk.setBgName("BG-BAU");
        gewerk.setBgSatzProzent(new BigDecimal("2.00"));
        firma.setGewerk(gewerk);
        firma.setBgSatzOverride(new BigDecimal("3.50"));
        when(firmeninformationRepository.findById(1L)).thenReturn(Optional.of(firma));

        Mitarbeiter ma = mitarbeiter(6L, "Lisa", "Mustermann");
        ma.setBeschaeftigungsart(Beschaeftigungsart.REGULAER);
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        when(lohnabrechnungRepository.sumBruttolohnByMitarbeiterIdAndJahr(6L, 2024))
                .thenReturn(new BigDecimal("10000.00"));
        when(lohnabrechnungRepository.countByMitarbeiterIdAndJahr(6L, 2024)).thenReturn(12L);

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        // 10.000 × 3.5% = 350 (Override schlaegt Gewerk)
        assertThat(dto.getLohnzeilen().get(0).getBgBeitrag()).isEqualByComparingTo("350.00");
    }

    // ==================== uebernehmen() ====================

    @Test
    void uebernehmenSetztSatzAufAlleArbeitsgaenge() {
        Abteilung abt = abteilung(10L, "Schweisserei");
        Arbeitsgang ag = new Arbeitsgang();
        ag.setId(100L);
        ag.setBeschreibung("Schweissen");
        ag.setAbteilung(abt);
        when(arbeitsgangRepository.findAll()).thenReturn(List.of(ag));
        when(stundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(100L, 2026))
                .thenReturn(Optional.empty());
        when(stundensatzRepository.save(any(ArbeitsgangStundensatz.class))).thenAnswer(inv -> inv.getArgument(0));

        VerrechnungslohnUebernehmenRequest req = new VerrechnungslohnUebernehmenRequest();
        req.setJahr(2026);
        req.setBasisSatz(new BigDecimal("75.00"));

        int count = service.uebernehmen(req);

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<ArbeitsgangStundensatz> captor = ArgumentCaptor.forClass(ArbeitsgangStundensatz.class);
        verify(stundensatzRepository, times(1)).save(captor.capture());
        ArbeitsgangStundensatz saved = captor.getValue();
        assertThat(saved.getJahr()).isEqualTo(2026);
        assertThat(saved.getSatz()).isEqualByComparingTo("75.00");
        assertThat(saved.getArbeitsgang()).isSameAs(ag);
    }

    @Test
    void uebernehmenAddiertAbteilungsAufschlag() {
        Abteilung schweiss = abteilung(10L, "Schweisserei");
        Abteilung schloss = abteilung(20L, "Schlosserei");
        Arbeitsgang ag1 = new Arbeitsgang();
        ag1.setId(100L);
        ag1.setBeschreibung("Schweissen");
        ag1.setAbteilung(schweiss);
        Arbeitsgang ag2 = new Arbeitsgang();
        ag2.setId(200L);
        ag2.setBeschreibung("Bohren");
        ag2.setAbteilung(schloss);
        when(arbeitsgangRepository.findAll()).thenReturn(List.of(ag1, ag2));
        when(stundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(anyLong(), anyInt()))
                .thenReturn(Optional.empty());
        when(stundensatzRepository.save(any(ArbeitsgangStundensatz.class))).thenAnswer(inv -> inv.getArgument(0));

        VerrechnungslohnUebernehmenRequest req = new VerrechnungslohnUebernehmenRequest();
        req.setJahr(2026);
        req.setBasisSatz(new BigDecimal("75.00"));
        VerrechnungslohnUebernehmenRequest.AbteilungAufschlag aufschlag = new VerrechnungslohnUebernehmenRequest.AbteilungAufschlag();
        aufschlag.setAbteilungId(10L);
        aufschlag.setAufschlagEuro(new BigDecimal("10.00"));
        req.setAbteilungAufschlaege(List.of(aufschlag));

        service.uebernehmen(req);

        ArgumentCaptor<ArbeitsgangStundensatz> captor = ArgumentCaptor.forClass(ArbeitsgangStundensatz.class);
        verify(stundensatzRepository, times(2)).save(captor.capture());
        List<ArbeitsgangStundensatz> saved = captor.getAllValues();
        // Schweisserei: 75 + 10 = 85
        assertThat(saved.stream()
                .filter(s -> s.getArbeitsgang().getId().equals(100L))
                .findFirst().orElseThrow()
                .getSatz()).isEqualByComparingTo("85.00");
        // Schlosserei: 75 + 0 = 75
        assertThat(saved.stream()
                .filter(s -> s.getArbeitsgang().getId().equals(200L))
                .findFirst().orElseThrow()
                .getSatz()).isEqualByComparingTo("75.00");
    }

    @Test
    void uebernehmenAktualisiertExistierendenSatzStattNeuAnzulegen() {
        Abteilung abt = abteilung(10L, "Schweisserei");
        Arbeitsgang ag = new Arbeitsgang();
        ag.setId(100L);
        ag.setBeschreibung("Schweissen");
        ag.setAbteilung(abt);
        ArbeitsgangStundensatz vorhanden = new ArbeitsgangStundensatz();
        vorhanden.setId(999L);
        vorhanden.setArbeitsgang(ag);
        vorhanden.setJahr(2026);
        vorhanden.setSatz(new BigDecimal("60.00"));

        when(arbeitsgangRepository.findAll()).thenReturn(List.of(ag));
        when(stundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(100L, 2026))
                .thenReturn(Optional.of(vorhanden));
        when(stundensatzRepository.save(any(ArbeitsgangStundensatz.class))).thenAnswer(inv -> inv.getArgument(0));

        VerrechnungslohnUebernehmenRequest req = new VerrechnungslohnUebernehmenRequest();
        req.setJahr(2026);
        req.setBasisSatz(new BigDecimal("80.00"));

        service.uebernehmen(req);

        ArgumentCaptor<ArbeitsgangStundensatz> captor = ArgumentCaptor.forClass(ArbeitsgangStundensatz.class);
        verify(stundensatzRepository).save(captor.capture());
        ArbeitsgangStundensatz saved = captor.getValue();
        // gleicher Eintrag (gleiche ID), neuer Wert
        assertThat(saved.getId()).isEqualTo(999L);
        assertThat(saved.getSatz()).isEqualByComparingTo("80.00");
    }

    // ==================== Regressionen: Stunden-Block ====================

    @Test
    void mitarbeiterOhneZeitkontoBekommtWerktagsSollStattNullStunden() {
        // Bug: ohne Zeitkonto lieferte der Stunden-Block 0 verkaeufliche Stunden,
        // waehrend der Lohn-Block denselben Mitarbeiter mit 2080 h ansetzte.
        // Der Stundensatz schoss dadurch nach oben.
        Mitarbeiter ma = mitarbeiter(7L, "Max", "Mustermann");
        ma.setBeschaeftigungsart(Beschaeftigungsart.REGULAER);
        ma.setStundenlohn(new BigDecimal("25.00"));
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        when(zeitkontoRepository.findByMitarbeiterId(7L)).thenReturn(Optional.empty());

        VerrechnungslohnErgebnisDto dto = service.berechne(Year.now().getValue());

        VerrechnungslohnErgebnisDto.MitarbeiterStundenZeile zeile = dto.getStundenzeilen().get(0);
        assertThat(zeile.isSollIstDefault()).isTrue();
        // ca. 250-262 Werktage x 8 h
        assertThat(zeile.getSollstunden()).isGreaterThan(new BigDecimal("1900"));
        assertThat(zeile.getVerkaeuflicheStunden()).isGreaterThan(BigDecimal.ZERO);
        assertThat(dto.getVerkaeuflicheStundenGesamt()).isGreaterThan(BigDecimal.ZERO);
        assertThat(dto.getDatenLuecken())
                .anyMatch(l -> l.getProblem().contains("Keine Arbeitszeiten hinterlegt"));
    }

    @Test
    void vierTageWocheBewertetUrlaubstagMitVollenAchtStunden() {
        // Bug: stundenProTag teilte fix durch 5. Bei 4x8h kamen 6,4 h je
        // Urlaubstag heraus statt 8 h -- der Urlaub wurde zu niedrig bewertet.
        Mitarbeiter ma = mitarbeiter(8L, "Erika", "Musterfrau");
        ma.setBeschaeftigungsart(Beschaeftigungsart.REGULAER);
        ma.setJahresUrlaub(25);
        Zeitkonto zk = zeitkontoFuer(ma);
        zk.setFreitagStunden(new BigDecimal("0.00"));
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        when(zeitkontoRepository.findByMitarbeiterId(8L)).thenReturn(Optional.of(zk));

        VerrechnungslohnErgebnisDto dto = service.berechne(Year.now().getValue());

        // 25 Urlaubstage x 8 h (32 h / 4 Arbeitstage), nicht 25 x 6,4 h = 160
        VerrechnungslohnErgebnisDto.MitarbeiterStundenZeile zeile = dto.getStundenzeilen().get(0);
        assertThat(zeile.getUrlaubsstunden()).isEqualByComparingTo("200.00");
        assertThat(zeile.isUrlaubIstDefault()).isTrue();
    }

    @Test
    void internProzentAusDerOberflaecheSteuertDieInternenStunden() {
        // Bug: die Oberflaeche schickte internProzent mit, der Service rechnete
        // aber immer mit fest verdrahteten 5 %. Der Regler hatte keine Wirkung.
        Mitarbeiter ma = mitarbeiter(9L, "Otto", "Mustermann");
        ma.setBeschaeftigungsart(Beschaeftigungsart.REGULAER);
        Zeitkonto zk = zeitkontoFuer(ma);
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        when(zeitkontoRepository.findByMitarbeiterId(9L)).thenReturn(Optional.of(zk));

        VerrechnungslohnErgebnisDto standard = service.berechne(Year.now().getValue());
        VerrechnungslohnErgebnisDto mitZwanzig = service.berechne(Year.now().getValue(), 20);

        assertThat(standard.getInterneQuoteProzent()).isEqualTo(5);
        assertThat(mitZwanzig.getInterneQuoteProzent()).isEqualTo(20);

        BigDecimal soll = mitZwanzig.getStundenzeilen().get(0).getSollstunden();
        assertThat(mitZwanzig.getStundenzeilen().get(0).getInterneStunden())
                .isEqualByComparingTo(soll.multiply(new BigDecimal("0.20")).setScale(2, java.math.RoundingMode.HALF_UP));
        // mehr interne Stunden => weniger verkaeufliche Stunden
        assertThat(mitZwanzig.getVerkaeuflicheStundenGesamt())
                .isLessThan(standard.getVerkaeuflicheStundenGesamt());
    }

    @Test
    void ungueltigeInterneQuoteFaelltAufDenStandardZurueck() {
        VerrechnungslohnErgebnisDto zuHoch = service.berechne(Year.now().getValue(), 150);
        VerrechnungslohnErgebnisDto negativ = service.berechne(Year.now().getValue(), -5);

        assertThat(zuHoch.getInterneQuoteProzent()).isEqualTo(5);
        assertThat(negativ.getInterneQuoteProzent()).isEqualTo(5);
    }

    // ==================== Regression: Gemeinkosten ====================

    @Test
    void belegMitKostenstellenSplitZaehltNichtZusaetzlichUeberSeineDirekteKostenstelle() {
        // Bug: am PC bleibt beim Anlegen eines Splits die direkte
        // Beleg.kostenstelle stehen. Der Beleg floss dadurch doppelt in die
        // Gemeinkosten -- einmal voll, einmal anteilig.
        Kostenstelle ks = new Kostenstelle();
        ks.setId(50L);
        ks.setBezeichnung("Werkstattmiete");

        Beleg beleg = new Beleg();
        beleg.setId(500L);
        beleg.setKostenstelle(ks);
        beleg.setBetragNetto(new BigDecimal("1000.00"));

        when(belegRepository.findValidierteFixkostenBelegeImZeitraum(any(), any()))
                .thenReturn(List.of(beleg));
        // vollstaendig aufgeteilt: 1000 von 1000
        when(belegKostenstellenAnteilRepository.summiereAufgeteilteBetraegeProBeleg())
                .thenReturn(List.<Object[]>of(new Object[]{500L, new BigDecimal("1000.00")}));

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        assertThat(dto.getGemeinkostenGesamt()).isEqualByComparingTo("0.00");
    }

    @Test
    void belegOhneSplitFliesstWeiterhinInDieGemeinkosten() {
        Kostenstelle ks = new Kostenstelle();
        ks.setId(51L);
        ks.setBezeichnung("Telefon");

        Beleg beleg = new Beleg();
        beleg.setId(501L);
        beleg.setKostenstelle(ks);
        beleg.setBetragNetto(new BigDecimal("240.00"));

        when(belegRepository.findValidierteFixkostenBelegeImZeitraum(any(), any()))
                .thenReturn(List.of(beleg));
        when(belegKostenstellenAnteilRepository.summiereAufgeteilteBetraegeProBeleg())
                .thenReturn(Collections.emptyList());

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        assertThat(dto.getGemeinkostenGesamt()).isEqualByComparingTo("240.00");
        assertThat(dto.getKostenstellen()).hasSize(1);
    }

    @Test
    void teilweiseAufgeteilterBelegVerliertDenNichtAufgeteiltenRestNicht() {
        // Regression: der erste Doppelzaehlungs-Fix hat den ganzen Beleg verworfen,
        // sobald irgendein Anteil existierte. Bei 60 % Aufteilung gingen so 40 %
        // der Gemeinkosten ersatzlos verloren -- der Stundensatz fiel zu niedrig aus.
        Kostenstelle ks = new Kostenstelle();
        ks.setId(53L);
        ks.setBezeichnung("Strom");

        Beleg beleg = new Beleg();
        beleg.setId(503L);
        beleg.setKostenstelle(ks);
        beleg.setBetragNetto(new BigDecimal("1000.00"));

        when(belegRepository.findValidierteFixkostenBelegeImZeitraum(any(), any()))
                .thenReturn(List.of(beleg));
        // nur 600 von 1000 aufgeteilt
        when(belegKostenstellenAnteilRepository.summiereAufgeteilteBetraegeProBeleg())
                .thenReturn(List.<Object[]>of(new Object[]{503L, new BigDecimal("600.00")}));

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        // die restlichen 400 bleiben auf der direkten Kostenstelle
        assertThat(dto.getGemeinkostenGesamt()).isEqualByComparingTo("400.00");
        assertThat(dto.getKostenstellen()).hasSize(1);
        assertThat(dto.getKostenstellen().get(0).getJahresbetrag()).isEqualByComparingTo("400.00");
    }

    @Test
    void vertraegtLeereSummeAusDerAufteilungsAbfrage() {
        // SUM(...) liefert null, wenn kein Anteil einen berechneten Betrag hat.
        Kostenstelle ks = new Kostenstelle();
        ks.setId(54L);
        ks.setBezeichnung("Versicherung");

        Beleg beleg = new Beleg();
        beleg.setId(504L);
        beleg.setKostenstelle(ks);
        beleg.setBetragNetto(new BigDecimal("500.00"));

        when(belegRepository.findValidierteFixkostenBelegeImZeitraum(any(), any()))
                .thenReturn(List.of(beleg));
        when(belegKostenstellenAnteilRepository.summiereAufgeteilteBetraegeProBeleg())
                .thenReturn(List.<Object[]>of(new Object[]{504L, null}));

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        assertThat(dto.getGemeinkostenGesamt()).isEqualByComparingTo("500.00");
    }

    @Test
    void beiEinemSplitGiltDieAufteilungUndNichtMehrDieDirekteKostenstelle() {
        // Bewusste Regel: der Split ist die zuletzt getroffene Zuordnung.
        // Liegt er auf einer Nicht-Fixkosten-Kostenstelle oder in einem anderen
        // Streckungsjahr, faellt der Beleg komplett aus den Gemeinkosten --
        // die veraltete direkte Kostenstelle darf ihn nicht zurueckholen.
        Kostenstelle fix = new Kostenstelle();
        fix.setId(52L);
        fix.setBezeichnung("Werkstattmiete");

        Beleg beleg = new Beleg();
        beleg.setId(502L);
        beleg.setKostenstelle(fix);
        beleg.setBetragNetto(new BigDecimal("800.00"));

        when(belegRepository.findValidierteFixkostenBelegeImZeitraum(any(), any()))
                .thenReturn(List.of(beleg));
        // Voll aufgeteilt, aber der Anteil wird von der Fixkosten-Jahres-Query
        // nicht geliefert (z.B. weil er auf einer Nicht-Fixkosten-Kostenstelle
        // liegt oder in ein anderes Streckungsjahr faellt).
        when(belegKostenstellenAnteilRepository.summiereAufgeteilteBetraegeProBeleg())
                .thenReturn(List.<Object[]>of(new Object[]{502L, new BigDecimal("800.00")}));
        when(belegKostenstellenAnteilRepository.findAktiveFixkostenAnteileImJahr(2024))
                .thenReturn(Collections.emptyList());

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        assertThat(dto.getGemeinkostenGesamt()).isEqualByComparingTo("0.00");
        assertThat(dto.getKostenstellen()).isEmpty();
    }

    // ==================== Regression: uebernehmen ====================

    @Test
    void uebernehmenLehntAbschlagAbDerDenStundensatzNegativMacht() {
        Abteilung abt = abteilung(10L, "Schweisserei");
        Arbeitsgang ag = new Arbeitsgang();
        ag.setId(100L);
        ag.setBeschreibung("Schweissen");
        ag.setAbteilung(abt);
        when(arbeitsgangRepository.findAll()).thenReturn(List.of(ag));

        VerrechnungslohnUebernehmenRequest req = new VerrechnungslohnUebernehmenRequest();
        req.setJahr(2026);
        req.setBasisSatz(new BigDecimal("50.00"));
        VerrechnungslohnUebernehmenRequest.AbteilungAufschlag abschlag =
                new VerrechnungslohnUebernehmenRequest.AbteilungAufschlag();
        abschlag.setAbteilungId(10L);
        abschlag.setAufschlagEuro(new BigDecimal("-60.00"));
        req.setAbteilungAufschlaege(List.of(abschlag));

        assertThatThrownBy(() -> service.uebernehmen(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schweisserei");
        verify(stundensatzRepository, never()).save(any(ArbeitsgangStundensatz.class));
    }

    @Test
    void uebernehmenLehntAbschlagAbDerGenauAufNullFuehrt() {
        // Ein 0-EUR-Stundensatz zerstoert die Kalkulation genauso still wie ein negativer.
        Abteilung abt = abteilung(10L, "Schweisserei");
        Arbeitsgang ag = new Arbeitsgang();
        ag.setId(100L);
        ag.setBeschreibung("Schweissen");
        ag.setAbteilung(abt);
        when(arbeitsgangRepository.findAll()).thenReturn(List.of(ag));

        VerrechnungslohnUebernehmenRequest req = new VerrechnungslohnUebernehmenRequest();
        req.setJahr(2026);
        req.setBasisSatz(new BigDecimal("50.00"));
        VerrechnungslohnUebernehmenRequest.AbteilungAufschlag abschlag =
                new VerrechnungslohnUebernehmenRequest.AbteilungAufschlag();
        abschlag.setAbteilungId(10L);
        abschlag.setAufschlagEuro(new BigDecimal("-50.00"));
        req.setAbteilungAufschlaege(List.of(abschlag));

        assertThatThrownBy(() -> service.uebernehmen(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schweisserei");
        verify(stundensatzRepository, never()).save(any(ArbeitsgangStundensatz.class));
    }

    @Test
    void uebernehmenErlaubtAbschlagSolangeDerSatzPositivBleibt() {
        Abteilung abt = abteilung(10L, "Schweisserei");
        Arbeitsgang ag = new Arbeitsgang();
        ag.setId(100L);
        ag.setBeschreibung("Schweissen");
        ag.setAbteilung(abt);
        when(arbeitsgangRepository.findAll()).thenReturn(List.of(ag));
        when(stundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(100L, 2026))
                .thenReturn(Optional.empty());
        when(stundensatzRepository.save(any(ArbeitsgangStundensatz.class))).thenAnswer(inv -> inv.getArgument(0));

        VerrechnungslohnUebernehmenRequest req = new VerrechnungslohnUebernehmenRequest();
        req.setJahr(2026);
        req.setBasisSatz(new BigDecimal("50.00"));
        VerrechnungslohnUebernehmenRequest.AbteilungAufschlag abschlag =
                new VerrechnungslohnUebernehmenRequest.AbteilungAufschlag();
        abschlag.setAbteilungId(10L);
        abschlag.setAufschlagEuro(new BigDecimal("-15.00"));
        req.setAbteilungAufschlaege(List.of(abschlag));

        service.uebernehmen(req);

        ArgumentCaptor<ArbeitsgangStundensatz> captor = ArgumentCaptor.forClass(ArbeitsgangStundensatz.class);
        verify(stundensatzRepository).save(captor.capture());
        assertThat(captor.getValue().getSatz()).isEqualByComparingTo("35.00");
    }

    @Test
    void uebernehmenOhneBasisSatzWirftException() {
        VerrechnungslohnUebernehmenRequest req = new VerrechnungslohnUebernehmenRequest();
        req.setJahr(2026);
        req.setBasisSatz(null);

        assertThatThrownBy(() -> service.uebernehmen(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("basisSatz");
    }

    // ==================== Helpers ====================

    private static Mitarbeiter mitarbeiter(Long id, String vorname, String nachname) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        m.setVorname(vorname);
        m.setNachname(nachname);
        m.setAktiv(true);
        return m;
    }

    private static Abteilung abteilung(Long id, String name) {
        Abteilung a = new Abteilung();
        a.setId(id);
        a.setName(name);
        return a;
    }

    private static Zeitkonto zeitkontoFuer(Mitarbeiter ma) {
        Zeitkonto zk = new Zeitkonto(ma);
        zk.setMontagStunden(new BigDecimal("8.00"));
        zk.setDienstagStunden(new BigDecimal("8.00"));
        zk.setMittwochStunden(new BigDecimal("8.00"));
        zk.setDonnerstagStunden(new BigDecimal("8.00"));
        zk.setFreitagStunden(new BigDecimal("8.00"));
        zk.setSamstagStunden(new BigDecimal("0.00"));
        zk.setSonntagStunden(new BigDecimal("0.00"));
        return zk;
    }

    private void sv(SvSatzTyp typ, String prozent) {
        SvSatz s = new SvSatz();
        s.setSatzTyp(typ);
        s.setProzent(new BigDecimal(prozent));
        s.setGueltigAb(LocalDate.of(2020, 1, 1));
        when(svSatzRepository.findFirstBySatzTypAndGueltigAbLessThanEqualOrderByGueltigAbDesc(eq(typ), any(LocalDate.class)))
                .thenReturn(Optional.of(s));
    }
}
