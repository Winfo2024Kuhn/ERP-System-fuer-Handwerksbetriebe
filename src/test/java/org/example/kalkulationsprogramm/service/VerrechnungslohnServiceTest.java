package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegAufteilungsModus;
import org.example.kalkulationsprogramm.domain.BelegKostenstellenAnteil;
import org.example.kalkulationsprogramm.domain.Beschaeftigungsart;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.domain.Gewerk;
import org.example.kalkulationsprogramm.domain.Krankenkasse;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhase;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhaseTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.SvSatz;
import org.example.kalkulationsprogramm.domain.SvSatzTyp;
import org.example.kalkulationsprogramm.domain.ZeitkontoVersion;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
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
import org.example.kalkulationsprogramm.repository.LangzeitkrankmeldungPhaseRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LohnabrechnungRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterStundenlohnRepository;
import org.example.kalkulationsprogramm.repository.SvSatzRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoVersionRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
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
    private ZeitkontoVersionRepository zeitkontoVersionRepository;
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
    private LangzeitkrankmeldungPhaseRepository phaseRepository;

    private VerrechnungslohnService service;

    @BeforeEach
    void setUp() {
        mitarbeiterRepository = mock(MitarbeiterRepository.class);
        stundenlohnRepository = mock(MitarbeiterStundenlohnRepository.class);
        lohnabrechnungRepository = mock(LohnabrechnungRepository.class);
        zeitbuchungRepository = mock(ZeitbuchungRepository.class);
        zeitkontoVersionRepository = mock(ZeitkontoVersionRepository.class);
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
        phaseRepository = mock(LangzeitkrankmeldungPhaseRepository.class);

        service = new VerrechnungslohnService(
                mitarbeiterRepository,
                stundenlohnRepository,
                lohnabrechnungRepository,
                zeitbuchungRepository,
                zeitkontoVersionRepository,
                abwesenheitRepository,
                feiertagRepository,
                svSatzRepository,
                firmeninformationRepository,
                anteilRepository,
                abteilungRepository,
                arbeitsgangRepository,
                stundensatzRepository,
                belegRepository,
                belegKostenstellenAnteilRepository,
                phaseRepository
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
        // Ohne Langzeitkrankmeldung keine Phasen -- damit bleiben alle vorhandenen
        // Zahlen unveraendert (Task 13, Verhaltensaenderung 3 aus dem Plan).
        when(phaseRepository.findImZeitraum(any(), any(), any())).thenReturn(Collections.emptyList());
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
        ZeitkontoVersion zk = zeitkontoFuer(ma);
        when(zeitkontoVersionRepository.findImZeitraum(eq(5L), any(), any())).thenReturn(List.of(zk));

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
        when(zeitkontoVersionRepository.findImZeitraum(eq(7L), any(), any())).thenReturn(List.of());

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
        ZeitkontoVersion zk = zeitkontoFuer(ma);
        zk.setFreitagStunden(new BigDecimal("0.00"));
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        when(zeitkontoVersionRepository.findImZeitraum(eq(8L), any(), any())).thenReturn(List.of(zk));

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
        ZeitkontoVersion zk = zeitkontoFuer(ma);
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        when(zeitkontoVersionRepository.findImZeitraum(eq(9L), any(), any())).thenReturn(List.of(zk));

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

    @Test
    void mischbelegBringtNurDenFirmenanteilInDieGemeinkosten() {
        // Supermarkt-Bon ueber 100 EUR, davon 40 EUR Buerokaffee fuer die Firma.
        // Vorher floss der volle Betrag in die Gemeinkosten -- der private
        // Wocheneinkauf trieb den Stundensatz hoch.
        Kostenstelle ks = new Kostenstelle();
        ks.setId(55L);
        ks.setBezeichnung("Bueromaterial");

        Beleg beleg = new Beleg();
        beleg.setId(505L);
        beleg.setKostenstelle(ks);
        beleg.setBetragNetto(new BigDecimal("100.00"));
        beleg.setBetragBrutto(new BigDecimal("119.00"));
        beleg.setAufteilungsModus(BelegAufteilungsModus.TEILWEISE);
        beleg.setBetragFirmaNetto(new BigDecimal("40.00"));
        beleg.setBetragFirmaBrutto(new BigDecimal("47.60"));

        when(belegRepository.findValidierteFixkostenBelegeImZeitraum(any(), any()))
                .thenReturn(List.of(beleg));

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        assertThat(dto.getGemeinkostenGesamt()).isEqualByComparingTo("40.00");
    }

    @Test
    void vollstaendigerBelegZaehltWeiterhinKomplett() {
        Kostenstelle ks = new Kostenstelle();
        ks.setId(56L);
        ks.setBezeichnung("Telefon");

        Beleg beleg = new Beleg();
        beleg.setId(506L);
        beleg.setKostenstelle(ks);
        beleg.setBetragNetto(new BigDecimal("100.00"));
        beleg.setAufteilungsModus(BelegAufteilungsModus.VOLLSTAENDIG);

        when(belegRepository.findValidierteFixkostenBelegeImZeitraum(any(), any()))
                .thenReturn(List.of(beleg));

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        assertThat(dto.getGemeinkostenGesamt()).isEqualByComparingTo("100.00");
    }

    @Test
    void mischbelegMitAufteilungZaehltRestUndAnteilZusammenGenauDenFirmenanteil() {
        // 100 EUR Bon, 40 EUR Firma, davon 25 EUR bereits auf eine andere
        // Kostenstelle aufgeteilt. Erwartung: 25 (Anteil) + 15 (Rest) = 40 --
        // exakt der Firmenanteil, kein Cent mehr, kein Cent weniger.
        Kostenstelle direkt = new Kostenstelle();
        direkt.setId(57L);
        direkt.setBezeichnung("Bueromaterial");
        Kostenstelle ausSplit = new Kostenstelle();
        ausSplit.setId(58L);
        ausSplit.setBezeichnung("Werkstatt");

        Beleg beleg = new Beleg();
        beleg.setId(507L);
        beleg.setKostenstelle(direkt);
        beleg.setBetragNetto(new BigDecimal("100.00"));
        beleg.setAufteilungsModus(BelegAufteilungsModus.TEILWEISE);
        beleg.setBetragFirmaNetto(new BigDecimal("40.00"));

        BelegKostenstellenAnteil anteil = new BelegKostenstellenAnteil();
        anteil.setBeleg(beleg);
        anteil.setKostenstelle(ausSplit);
        anteil.setAbsoluterBetrag(new BigDecimal("25.00"));
        anteil.setStreckungJahre(1);
        anteil.setStreckungStartJahr(2024);
        anteil.berechneAnteil(new BigDecimal("40.00"), new BigDecimal("40.00"));

        when(belegRepository.findValidierteFixkostenBelegeImZeitraum(any(), any()))
                .thenReturn(List.of(beleg));
        when(belegKostenstellenAnteilRepository.summiereAufgeteilteBetraegeProBeleg())
                .thenReturn(List.<Object[]>of(new Object[]{507L, new BigDecimal("25.00")}));
        when(belegKostenstellenAnteilRepository.findAktiveFixkostenAnteileImJahr(2024))
                .thenReturn(List.of(anteil));

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        assertThat(dto.getGemeinkostenGesamt()).isEqualByComparingTo("40.00");
        assertThat(dto.getKostenstellen()).hasSize(2);
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

    // ==================== Task 13: Krankheitsphasen im Verrechnungslohn ====================

    @Test
    void krankengeldPhaseKlammertKalendertageUndAnteiligeLohnkostenAus() {
        // Langzeitfall: Krankengeld 01.03.2024-30.06.2024 (122 Kalendertage,
        // davon 86 Werktage). Das Jahressoll sinkt um genau die Werktagsstunden
        // dieses Zeitraums, die Lohnkosten werden anteilig gekuerzt, und die
        // Krankheitsstunden zeigen nur noch die Lohnfortzahlungs-Tage.
        Mitarbeiter ma = mitarbeiter(20L, "Klaus", "Mustermann");
        ma.setBeschaeftigungsart(Beschaeftigungsart.REGULAER);
        ZeitkontoVersion zk = zeitkontoFuer(ma);
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        when(zeitkontoVersionRepository.findImZeitraum(eq(20L), any(), any())).thenReturn(List.of(zk));
        when(lohnabrechnungRepository.sumBruttolohnByMitarbeiterIdAndJahr(20L, 2024))
                .thenReturn(new BigDecimal("48000.00"));
        when(lohnabrechnungRepository.countByMitarbeiterIdAndJahr(20L, 2024)).thenReturn(12L);

        LangzeitkrankmeldungPhase krankengeld = new LangzeitkrankmeldungPhase();
        krankengeld.setTyp(LangzeitkrankmeldungPhaseTyp.KRANKENGELD);
        krankengeld.setVonDatum(LocalDate.of(2024, 3, 1));
        krankengeld.setBisDatum(LocalDate.of(2024, 6, 30));
        when(phaseRepository.findImZeitraum(eq(20L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(krankengeld));

        // Vorausgegangene Lohnfortzahlungs-Wochen bleiben im Abzug -- 48 h,
        // klar unterscheidbar vom KRANKHEITSTAGE_DEFAULT-Fallback (64 h).
        when(abwesenheitRepository.sumStundenOhnePhasenTypen(
                eq(20L), eq(AbwesenheitsTyp.KRANKHEIT), eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 12, 31)),
                eq(List.of(LangzeitkrankmeldungPhaseTyp.KRANKENGELD, LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG))))
                .thenReturn(new BigDecimal("48.00"));

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        VerrechnungslohnErgebnisDto.MitarbeiterStundenZeile stdZeile = dto.getStundenzeilen().get(0);
        assertThat(stdZeile.getAusgeklammerteTage()).isEqualTo(122);
        assertThat(stdZeile.getAusgeklammerteStunden()).isEqualByComparingTo("688.00");
        // Jahressoll ohne Ausklammerung waere 2096.00 h (262 Werktage x 8h in 2024).
        assertThat(stdZeile.getSollstunden()).isEqualByComparingTo("1408.00");
        assertThat(stdZeile.getKrankheitsstunden()).isEqualByComparingTo("48.00");
        assertThat(stdZeile.isKrankheitIstDefault()).isFalse();

        VerrechnungslohnErgebnisDto.MitarbeiterLohnZeile lohnZeile = dto.getLohnzeilen().get(0);
        assertThat(lohnZeile.getAusgeklammerteTage()).isEqualTo(122);
        // (366 - 122) / 366 = 2/3, HALF_UP auf 4 Nachkommastellen. Der Faktor
        // wird trotzdem berechnet und gemeldet (fuer die UI) -- er fliesst nur
        // bei dieser Quelle (LOHNABRECHNUNG) nicht in die Gesamtkosten ein,
        // siehe naechster Test.
        assertThat(lohnZeile.getAnwesenheitsFaktor()).isEqualByComparingTo("0.6667");
        // Nachbesserung Abschnitt 2, Befund 1: das Brutto stammt hier aus
        // echten Lohnabrechnungen (Quelle LOHNABRECHNUNG) -- der Betrieb hat
        // waehrend des Krankengeldbezugs tatsaechlich weniger gezahlt, der
        // Ausfall steckt also schon in den 48000. Der anwesenheitsFaktor darf
        // hier NICHT zusaetzlich kuerzen (das waere eine doppelte Kuerzung).
        // Roter Testlauf vor dem Fix hat 32001.60 geliefert (48000 x 0.6667).
        assertThat(lohnZeile.getQuelle()).isEqualTo(VerrechnungslohnErgebnisDto.LohnQuelle.LOHNABRECHNUNG);
        assertThat(lohnZeile.getGesamtkosten()).isEqualByComparingTo("48000.00");
    }

    @Test
    void hochgerechnetesBruttoWirdBeiKrankengeldWeiterhinUeberDenAnwesenheitsfaktorGekuerzt() {
        // Gegenstueck zum vorigen Test: bei einer hochgerechneten Quelle (hier
        // STAMMSTUNDENLOHN, mangels Lohnabrechnungen) kennt das Brutto den
        // Krankheitsausfall NICHT -- der anwesenheitsFaktor muss hier weiter
        // greifen. Ohne diesen Test wuerde eine zu grosse Korrektur von
        // Befund 1 (Faktor ueberall abschalten) unbemerkt durchrutschen.
        Mitarbeiter ma = mitarbeiter(22L, "Sven", "Mustermann");
        ma.setBeschaeftigungsart(Beschaeftigungsart.REGULAER);
        ma.setStundenlohn(new BigDecimal("20.00"));
        ZeitkontoVersion zk = zeitkontoFuer(ma);
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        when(zeitkontoVersionRepository.findImZeitraum(eq(22L), any(), any())).thenReturn(List.of(zk));
        when(lohnabrechnungRepository.sumBruttolohnByMitarbeiterIdAndJahr(22L, 2024)).thenReturn(BigDecimal.ZERO);
        when(lohnabrechnungRepository.countByMitarbeiterIdAndJahr(22L, 2024)).thenReturn(0L);
        when(stundenlohnRepository.findFirstByMitarbeiterIdAndGueltigAbLessThanEqualOrderByGueltigAbDesc(eq(22L), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        LangzeitkrankmeldungPhase krankengeld = new LangzeitkrankmeldungPhase();
        krankengeld.setTyp(LangzeitkrankmeldungPhaseTyp.KRANKENGELD);
        krankengeld.setVonDatum(LocalDate.of(2024, 3, 1));
        krankengeld.setBisDatum(LocalDate.of(2024, 6, 30));
        when(phaseRepository.findImZeitraum(eq(22L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(krankengeld));

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        VerrechnungslohnErgebnisDto.MitarbeiterLohnZeile lohnZeile = dto.getLohnzeilen().get(0);
        assertThat(lohnZeile.getQuelle()).isEqualTo(VerrechnungslohnErgebnisDto.LohnQuelle.STAMMSTUNDENLOHN);
        // Brutto = 20 EUR x 2080 h (40h-Woche x 52) = 41600.00, unabhaengig von
        // der Krankengeldphase -- die Kuerzung passiert erst ueber den Faktor.
        assertThat(lohnZeile.getBruttoJahr()).isEqualByComparingTo("41600.00");
        // 41600 x 0.6667 = 27734.72
        assertThat(lohnZeile.getAnwesenheitsFaktor()).isEqualByComparingTo("0.6667");
        assertThat(lohnZeile.getGesamtkosten()).isEqualByComparingTo("27734.72");
    }

    @Test
    void geschaeftsfuehrerKalkulatorischerLohnWirdBeiKrankengeldUeberDenAnwesenheitsfaktorGekuerzt() {
        // Nachbesserung Abschnitt 2, Befund 2: berechneStundenZeile kuerzt das
        // Jahressoll fuer JEDEN aktiven Mitarbeiter, auch den Geschaeftsfuehrer.
        // Bliebe seine Lohnseite ungekuerzt, sink en die Stunden waehrend die
        // Kosten stehen bleiben -- der Stundensatz schoesse nach oben. Dieser
        // Test war zuvor nicht vorhanden: der Reviewer konnte den Faktor im
        // GF-Zweig entfernen, ohne dass ein Test rot wurde.
        Mitarbeiter gf = mitarbeiter(23L, "Peter", "Mustermann");
        gf.setIstGeschaeftsfuehrer(true);
        gf.setBeschaeftigungsart(Beschaeftigungsart.GF_SV_FREI);
        gf.setKalkulatorischerLohnMonat(new BigDecimal("5000.00"));
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(gf));

        LangzeitkrankmeldungPhase krankengeld = new LangzeitkrankmeldungPhase();
        krankengeld.setTyp(LangzeitkrankmeldungPhaseTyp.KRANKENGELD);
        krankengeld.setVonDatum(LocalDate.of(2024, 3, 1));
        krankengeld.setBisDatum(LocalDate.of(2024, 6, 30));
        when(phaseRepository.findImZeitraum(eq(23L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(krankengeld));

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        VerrechnungslohnErgebnisDto.MitarbeiterLohnZeile lohnZeile = dto.getLohnzeilen().get(0);
        assertThat(lohnZeile.getQuelle()).isEqualTo(VerrechnungslohnErgebnisDto.LohnQuelle.KALKULATORISCH);
        assertThat(lohnZeile.getAusgeklammerteTage()).isEqualTo(122);
        assertThat(lohnZeile.getAnwesenheitsFaktor()).isEqualByComparingTo("0.6667");
        // 5000 x 12 = 60000, x 0.6667 = 40002.00
        assertThat(lohnZeile.getBruttoJahr()).isEqualByComparingTo("60000");
        assertThat(lohnZeile.getGesamtkosten()).isEqualByComparingTo("40002.00");
    }

    @Test
    void normalerKrankheitstagOhneLangzeitkrankmeldungWirdWeiterhinVollGezaehlt() {
        // Der Normalfall (kein Langzeitkrankmeldung-Bezug ueberhaupt): die neue
        // Query sumStundenOhnePhasenTypen ersetzt die alte Summierung. Ein
        // impliziter Pfad ueber die nullable Phase haette solche Tage per
        // INNER JOIN stillschweigend rausgeworfen (siehe kriterien.md) -- hier
        // wird sichergestellt, dass ein ganz normaler Krankheitstag ohne jeden
        // Phasenbezug trotzdem voll durchgereicht wird, nicht durch den
        // KRANKHEITSTAGE_DEFAULT-Fallback ersetzt.
        Mitarbeiter ma = mitarbeiter(21L, "Petra", "Mustermann");
        ma.setBeschaeftigungsart(Beschaeftigungsart.REGULAER);
        ZeitkontoVersion zk = zeitkontoFuer(ma);
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        when(zeitkontoVersionRepository.findImZeitraum(eq(21L), any(), any())).thenReturn(List.of(zk));
        // Kein Bezug zu einer Langzeitkrankmeldung -- phaseRepository liefert
        // ueber den Default-Stub aus @BeforeEach eine leere Liste.
        when(abwesenheitRepository.sumStundenOhnePhasenTypen(
                eq(21L), eq(AbwesenheitsTyp.KRANKHEIT), any(LocalDate.class), any(LocalDate.class), anyList()))
                .thenReturn(new BigDecimal("8.00"));

        VerrechnungslohnErgebnisDto dto = service.berechne(2024);

        VerrechnungslohnErgebnisDto.MitarbeiterStundenZeile zeile = dto.getStundenzeilen().get(0);
        assertThat(zeile.getAusgeklammerteTage()).isEqualTo(0);
        assertThat(zeile.getAusgeklammerteStunden()).isEqualByComparingTo("0.00");
        assertThat(zeile.getKrankheitsstunden()).isEqualByComparingTo("8.00");
        assertThat(zeile.isKrankheitIstDefault()).isFalse();
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

    @Test
    void jahreswechselUndHeutigerSchalterVeraendernHistorischesSollNicht() {
        Mitarbeiter ma = mitarbeiter(101L, "Max", "Mustermann");
        ma.setStundenlohn(new BigDecimal("25"));
        ma.setFuehrtZeitkonto(false);
        ma.setJahresUrlaub(10);
        ZeitkontoVersion alt = zeitkontoFuer(ma);
        alt.setGueltigBis(LocalDate.of(2024, 6, 30));
        ZeitkontoVersion neu = zeitkontoFuer(ma);
        neu.setGueltigVon(LocalDate.of(2024, 7, 1));
        neu.setMontagStunden(new BigDecimal("4"));
        neu.setDienstagStunden(new BigDecimal("4"));
        neu.setMittwochStunden(new BigDecimal("4"));
        neu.setDonnerstagStunden(new BigDecimal("4"));
        neu.setFreitagStunden(new BigDecimal("4"));
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        when(zeitkontoVersionRepository.findImZeitraum(101L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
                .thenReturn(List.of(alt, neu));

        var dto = service.berechne(2024);
        assertThat(dto.getStundenzeilen().getFirst().getSollstunden()).isEqualByComparingTo("1568.00");
        assertThat(dto.getStundenzeilen().getFirst().getUrlaubsstunden()).isEqualByComparingTo("59.80");
        BigDecimal lohn = BigDecimal.valueOf(40 * 182 + 20 * 184).multiply(BigDecimal.valueOf(52 * 25))
                .divide(BigDecimal.valueOf(366), 2, java.math.RoundingMode.HALF_UP);
        assertThat(dto.getLohnzeilen().getFirst().getBruttoJahr()).isEqualByComparingTo(lohn);
        verify(zeitkontoVersionRepository, times(1)).findImZeitraum(101L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        verify(zeitkontoVersionRepository, never()).findAm(any(), any());
        verify(zeitkontoVersionRepository, never()).save(any());
    }

    @Test
    void systemMitarbeiterWirdVorAllenBerechnungenAusgeschlossen() {
        Mitarbeiter system = mitarbeiter(102L, "Max", "Mustermann");
        system.setArt(MitarbeiterArt.SYSTEM);
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(system));
        var dto = service.berechne(2024);
        assertThat(dto.getLohnzeilen()).isEmpty();
        assertThat(dto.getStundenzeilen()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(zeitkontoVersionRepository, phaseRepository);
    }

    @Test
    void lueckeWirdNurAnFehlendenTagenKalkulatorischErgaenzt() {
        Mitarbeiter ma = mitarbeiter(103L, "Max", "Mustermann");
        ZeitkontoVersion v = zeitkontoFuer(ma);
        v.setGueltigVon(LocalDate.of(2024, 1, 2));
        v.setMontagStunden(new BigDecimal("4"));
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(ma));
        when(zeitkontoVersionRepository.findImZeitraum(eq(103L), any(), any())).thenReturn(List.of(v));
        var dto = service.berechne(2024);
        // 262 Werktage * 8, 52 gültige Montage je 4 Stunden weniger;
        // der fehlende 1. Januar erhält den kenntlich gemachten 8h-Default.
        assertThat(dto.getStundenzeilen().getFirst().getSollstunden()).isEqualByComparingTo("1888.00");
        assertThat(dto.getStundenzeilen().getFirst().isSollIstDefault()).isTrue();
        assertThat(dto.getDatenLuecken()).anyMatch(l -> l.getProblem().contains("kalkulatorisch"));
        verify(zeitkontoVersionRepository, never()).save(any());
    }

    private static ZeitkontoVersion zeitkontoFuer(Mitarbeiter ma) {
        ZeitkontoVersion zk = new ZeitkontoVersion();
        zk.setMitarbeiter(ma);
        zk.setGueltigVon(LocalDate.of(1000, 1, 1));
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
