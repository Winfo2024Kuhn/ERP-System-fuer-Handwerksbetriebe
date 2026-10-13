package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.UrlaubUebertrag;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.UrlaubUebertragRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoVersionRepository;
import org.example.kalkulationsprogramm.service.UrlaubskontoService.UrlaubsTag;
import org.example.kalkulationsprogramm.service.UrlaubsverfallEinstellungService.Verfallsregel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Resturlaub-Übertrag und Verfall (Issue #270, Punkt 4). Dummy-Daten: Max Mustermann.
 */
class UrlaubskontoServiceTest {

    private static final LocalDate VERFALL_2027 = LocalDate.of(2027, 3, 31);

    private static BigDecimal d(String wert) {
        return new BigDecimal(wert);
    }

    private static UrlaubsTag tag(int jahr, int monat, int tag, String anteil) {
        return new UrlaubsTag(LocalDate.of(jahr, monat, tag), d(anteil));
    }

    /** Die reine Rechnung ohne Datenbank. */
    @Nested
    class Rechnung {

        private Urlaubskonto rechne(String uebertrag, LocalDate verfallsdatum, List<UrlaubsTag> tage,
                LocalDate stichtag) {
            return UrlaubskontoService.rechne(2027, BigDecimal.valueOf(30), BigDecimal.ZERO, d(uebertrag), false, verfallsdatum, tage,
                    stichtag, stichtag);
        }

        @Test
        void urlaubVorDemVerfallZaehltZuerstGegenDenUebertrag() {
            Urlaubskonto konto = rechne("5", VERFALL_2027, List.of(
                    tag(2027, 2, 1, "1"), tag(2027, 2, 2, "1"), tag(2027, 2, 3, "1")), LocalDate.of(2027, 3, 15));

            assertThat(konto.ausVorjahrOffen()).isEqualByComparingTo("2");
            assertThat(konto.verfallen()).isEqualByComparingTo("0");
            assertThat(konto.verbleibend()).isEqualByComparingTo("32"); // 30 + 5 - 3
        }

        @Test
        void nachDemVerfallstagIstDerOffeneRestWeg() {
            Urlaubskonto konto = rechne("5", VERFALL_2027, List.of(
                    tag(2027, 2, 1, "1"), tag(2027, 2, 2, "1"), tag(2027, 2, 3, "1")), LocalDate.of(2027, 4, 1));

            assertThat(konto.verfallen()).isEqualByComparingTo("2");
            assertThat(konto.ausVorjahrOffen()).isEqualByComparingTo("0");
            assertThat(konto.verbleibend()).isEqualByComparingTo("30"); // 30 + 5 - 2 verfallen - 3
        }

        @Test
        void amVerfallstagSelbstZaehltDerRestNoch() {
            Urlaubskonto konto = rechne("2", VERFALL_2027, List.of(tag(2027, 3, 31, "1")), VERFALL_2027);

            assertThat(konto.verfallen()).isEqualByComparingTo("0");
            assertThat(konto.ausVorjahrOffen()).isEqualByComparingTo("1");
        }

        @Test
        void urlaubNachDemVerfallVerbrauchtDenUebertragNicht() {
            Urlaubskonto konto = rechne("5", VERFALL_2027, List.of(tag(2027, 5, 4, "1"), tag(2027, 5, 5, "1")),
                    LocalDate.of(2027, 6, 1));

            assertThat(konto.verfallen()).isEqualByComparingTo("5");
            assertThat(konto.verbleibend()).isEqualByComparingTo("28");
        }

        @Test
        void halbeTageWerdenGenauVerrechnet() {
            Urlaubskonto konto = rechne("1.5", VERFALL_2027, List.of(tag(2027, 1, 11, "0.5"), tag(2027, 1, 12, "0.5")),
                    LocalDate.of(2027, 5, 1));

            assertThat(konto.verfallen()).isEqualByComparingTo("0.5");
            assertThat(konto.genommen()).isEqualByComparingTo("1");
            assertThat(konto.verbleibend()).isEqualByComparingTo("30"); // 30 + 1,5 - 0,5 - 1
        }

        @Test
        void ohneVerfallBleibtDerRestDasGanzeJahr() {
            Urlaubskonto konto = rechne("5", null, List.of(tag(2027, 5, 4, "1"), tag(2027, 5, 5, "1")),
                    LocalDate.of(2027, 12, 31));

            assertThat(konto.verfallsdatum()).isNull();
            assertThat(konto.verfallen()).isEqualByComparingTo("0");
            assertThat(konto.ausVorjahrOffen()).isEqualByComparingTo("3");
            assertThat(konto.verbleibend()).isEqualByComparingTo("33");
        }

        @Test
        void heuteTrenntGenommenUndGeplant() {
            Urlaubskonto konto = UrlaubskontoService.rechne(2027, BigDecimal.valueOf(30), d("0.5"), BigDecimal.ZERO, false, VERFALL_2027,
                    List.of(tag(2027, 2, 1, "1"), tag(2027, 8, 2, "1")), LocalDate.of(2027, 6, 1),
                    LocalDate.of(2027, 6, 1));

            assertThat(konto.genommen()).isEqualByComparingTo("1");
            assertThat(konto.geplant()).isEqualByComparingTo("1");
            assertThat(konto.verbleibend()).isEqualByComparingTo("28.5");
        }

        @Test
        void restWirdNieNegativ_undOffenerVorjahresrestNieGroesserAlsDerRest() {
            List<UrlaubsTag> tage = new ArrayList<>();
            for (int i = 1; i <= 28; i++) {
                tage.add(tag(2027, 6, i, "1"));
            }
            Urlaubskonto konto = UrlaubskontoService.rechne(2027, BigDecimal.valueOf(30), d("-10"), d("5"), false, null, tage,
                    LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 1));

            assertThat(konto.verbleibend()).isEqualByComparingTo("0");
            assertThat(konto.ausVorjahrOffen()).isEqualByComparingTo("0");
        }

        @Test
        void stichtagWirdAufsJahrBegrenzt() {
            assertThat(UrlaubskontoService.stichtagImJahr(LocalDate.of(2026, 10, 13), 2027))
                    .isEqualTo(LocalDate.of(2027, 1, 1));
            assertThat(UrlaubskontoService.stichtagImJahr(LocalDate.of(2028, 2, 1), 2027))
                    .isEqualTo(LocalDate.of(2027, 12, 31));
            assertThat(UrlaubskontoService.stichtagImJahr(LocalDate.of(2027, 5, 5), 2027))
                    .isEqualTo(LocalDate.of(2027, 5, 5));
        }
    }

    /** Übertrag aus dem Vorjahr über die Datenbank-Schicht. */
    @Nested
    @ExtendWith(MockitoExtension.class)
    class Uebertrag {

        @Mock AbwesenheitRepository abwesenheitRepository;
        @Mock ZeitkontoKorrekturService korrekturService;
        @Mock TagesSollService tagesSollService;
        @Mock UrlaubUebertragRepository uebertragRepository;
        @Mock UrlaubsverfallEinstellungService einstellungen;
        @Mock ZeitkontoVersionRepository zeitkontoVersionRepository;

        private UrlaubskontoService service;
        private Mitarbeiter max;
        private final Map<Integer, List<Abwesenheit>> urlaubJeJahr = new HashMap<>();

        @BeforeEach
        void setUp() {
            // Heute: 15.02.2027 – der Übertrag aus 2026 ist noch offen.
            Clock clock = Clock.fixed(LocalDate.of(2027, 2, 15).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    ZoneId.systemDefault());
            service = new UrlaubskontoService(abwesenheitRepository, korrekturService, tagesSollService,
                    new UrlaubstageRechner(), uebertragRepository, einstellungen, clock, zeitkontoVersionRepository);
            max = new Mitarbeiter();
            max.setId(1L);
            max.setVorname("Max");
            max.setNachname("Mustermann");
            max.setJahresUrlaub(30);
            lenient().when(einstellungen.regel()).thenReturn(Verfallsregel.STANDARD);
            lenient().when(einstellungen.uebertragAbJahr()).thenReturn(2026);
            lenient().when(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(eq(1L), any(), any()))
                    .thenAnswer(inv -> urlaubJeJahr.getOrDefault(((LocalDate) inv.getArgument(1)).getYear(), List.of()));
            lenient().when(tagesSollService.arbeitsSollJeTag(eq(1L), any(), any())).thenAnswer(inv -> {
                Map<LocalDate, BigDecimal> soll = new HashMap<>();
                for (LocalDate t = inv.getArgument(1); !t.isAfter(inv.getArgument(2)); t = t.plusDays(1)) {
                    soll.put(t, d("8.00"));
                }
                return soll;
            });
            lenient().when(korrekturService.summiereAktiveUrlaubsKorrekturen(eq(1L), anyInt())).thenReturn(null);
        }

        /** Bucht {@code ganze} ganze und {@code halbe} halbe Urlaubstage ab dem ersten Werktag ab {@code start}. */
        private void bucheUrlaub(LocalDate start, int ganze, int halbe) {
            List<Abwesenheit> liste = urlaubJeJahr.computeIfAbsent(start.getYear(), j -> new ArrayList<>());
            LocalDate t = start;
            for (int i = 0; i < ganze + halbe; i++) {
                Abwesenheit a = new Abwesenheit();
                a.setTyp(AbwesenheitsTyp.URLAUB);
                a.setDatum(t);
                a.setStunden(i < ganze ? d("8.00") : d("4.00"));
                liste.add(a);
                t = t.plusDays(1);
            }
        }

        @Test
        void restDesVorjahresWirdAutomatischUebertragen_mitHalbenTagen() {
            bucheUrlaub(LocalDate.of(2026, 6, 1), 25, 1); // 25,5 Tage genommen

            Urlaubskonto konto = service.berechne(max, 2027);

            assertThat(konto.uebertrag()).isEqualByComparingTo("4.5");
            assertThat(konto.uebertragManuell()).isFalse();
            assertThat(konto.verfallsdatum()).isEqualTo(VERFALL_2027);
            assertThat(konto.ausVorjahrOffen()).isEqualByComparingTo("4.5");
            assertThat(konto.verbleibend()).isEqualByComparingTo("34.5");
        }

        @Test
        void mehrfachesAbfragenIstIdempotentUndSpeichertNichts() {
            bucheUrlaub(LocalDate.of(2026, 6, 1), 26, 0);

            Urlaubskonto erstes = service.berechne(max, 2027);
            Urlaubskonto zweites = service.berechne(max, 2027);

            assertThat(zweites).isEqualTo(erstes);
            assertThat(zweites.uebertrag()).isEqualByComparingTo("4");
            verify(uebertragRepository, never()).save(any());
        }

        @Test
        void nachtraeglicheBuchungImVorjahrWirktSofort() {
            bucheUrlaub(LocalDate.of(2026, 6, 1), 26, 0);
            assertThat(service.berechne(max, 2027).uebertrag()).isEqualByComparingTo("4");

            bucheUrlaub(LocalDate.of(2026, 12, 21), 2, 0);

            assertThat(service.berechne(max, 2027).uebertrag()).isEqualByComparingTo("2");
        }

        @Test
        void handEintragGehtVorUndAutomatischBleibtAbrufbar() {
            bucheUrlaub(LocalDate.of(2026, 6, 1), 26, 0);
            UrlaubUebertrag eintrag = new UrlaubUebertrag();
            eintrag.setTage(d("7.5"));
            when(uebertragRepository.findByMitarbeiterIdAndJahr(1L, 2027)).thenReturn(Optional.of(eintrag));

            Urlaubskonto konto = service.berechne(max, 2027);

            assertThat(konto.uebertrag()).isEqualByComparingTo("7.5");
            assertThat(konto.uebertragManuell()).isTrue();
            assertThat(service.automatischerUebertrag(max, 2027)).isEqualByComparingTo("4");
        }

        @Test
        void imStartjahrGibtEsKeinenAutomatischenUebertrag() {
            Urlaubskonto konto = service.berechne(max, 2026);

            assertThat(konto.uebertrag()).isEqualByComparingTo("0");
            assertThat(konto.verbleibend()).isEqualByComparingTo("30");
        }

        @Test
        void imEintrittsjahrGibtEsKeinenUebertrag() {
            max.setEintrittsdatum(LocalDate.of(2027, 1, 1));

            assertThat(service.berechne(max, 2027).uebertrag()).isEqualByComparingTo("0");
            verify(abwesenheitRepository, never()).findByMitarbeiterIdAndDatumBetween(1L,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        }

        @Test
        void geschaeftsfuehrungHatKeinenUebertrag() {
            max.setIstGeschaeftsfuehrer(true);

            assertThat(service.berechne(max, 2027).uebertrag()).isEqualByComparingTo("0");
            verifyNoInteractions(uebertragRepository, tagesSollService);
        }

        @Test
        void nachDemVerfallImFolgejahrWirdNurDerGenutzteTeilWeitergegeben() {
            // 2027: 4 Tage Übertrag, davon 1 bis 31.03. genommen, der Rest verfällt.
            // 2027 werden insgesamt 20 Tage genommen → 2028 bekommt 30 + 1 - 20 = 11.
            bucheUrlaub(LocalDate.of(2026, 6, 1), 26, 0);
            bucheUrlaub(LocalDate.of(2027, 3, 31), 1, 0);
            bucheUrlaub(LocalDate.of(2027, 7, 1), 19, 0);

            Urlaubskonto konto2028 = service.berechne(max, 2028);

            assertThat(konto2028.uebertrag()).isEqualByComparingTo("11");
            assertThat(konto2028.verfallsdatum()).isEqualTo(LocalDate.of(2028, 3, 31));
        }

        @Test
        void ohneVerfallWirdDerRestUeberMehrereJahreMitgenommen() {
            when(einstellungen.regel()).thenReturn(new Verfallsregel(false, MonthDay.of(3, 31)));
            bucheUrlaub(LocalDate.of(2026, 6, 1), 28, 0);   // Rest 2
            bucheUrlaub(LocalDate.of(2027, 7, 1), 29, 0);   // 30 + 2 - 29 = 3

            Urlaubskonto konto2028 = service.berechne(max, 2028);

            assertThat(konto2028.uebertrag()).isEqualByComparingTo("3");
            assertThat(konto2028.verfallsdatum()).isNull();
            assertThat(konto2028.verbleibend()).isEqualByComparingTo("33");
        }

        @Test
        void antragNachDemVerfallBekommtDenVorjahresrestNichtMehr() {
            bucheUrlaub(LocalDate.of(2026, 6, 1), 26, 0);

            assertThat(service.berechne(max, 2027, LocalDate.of(2027, 3, 1)).verbleibend())
                    .isEqualByComparingTo("34");
            assertThat(service.berechne(max, 2027, LocalDate.of(2027, 5, 3)).verbleibend())
                    .isEqualByComparingTo("30");
        }

        private static List<LocalDate> tage(LocalDate... daten) {
            return List.of(daten);
        }

        @Test
        void antragUeberDenVerfallstagReichtNichtWennDerRestDanachVerfaellt() {
            // 5 Tage Rest + 2 Tage Anspruch = 7 verfügbar; 6 Arbeitstage 31.03.–07.04.
            // 2 Tage Anspruch: 30 − 28 per Korrektur (weniger als 20 Vertragsurlaub ginge nicht).
            lenient().when(korrekturService.summiereAktiveUrlaubsKorrekturen(1L, 2027)).thenReturn(d("-28"));
            UrlaubUebertrag rest = new UrlaubUebertrag();
            rest.setTage(d("5"));
            when(uebertragRepository.findByMitarbeiterIdAndJahr(1L, 2027)).thenReturn(Optional.of(rest));

            UrlaubskontoService.Antragspruefung pruefung = service.pruefeAntrag(max, 2027, tage(
                    LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 2),
                    LocalDate.of(2027, 4, 5), LocalDate.of(2027, 4, 6), LocalDate.of(2027, 4, 7)));

            assertThat(pruefung.verbleibendVorher()).isEqualByComparingTo("7");
            assertThat(pruefung.restDanach()).isEqualByComparingTo("-3");
            assertThat(pruefung.reicht()).isFalse();
            assertThat(pruefung.vorjahresrestVerfaellt()).isTrue();
            assertThat(pruefung.verfallsdatum()).isEqualTo(VERFALL_2027);
        }

        @Test
        void antragVorDemVerfallNutztDenRestUndReicht() {
            // 2 Tage Anspruch: 30 − 28 per Korrektur (weniger als 20 Vertragsurlaub ginge nicht).
            lenient().when(korrekturService.summiereAktiveUrlaubsKorrekturen(1L, 2027)).thenReturn(d("-28"));
            UrlaubUebertrag rest = new UrlaubUebertrag();
            rest.setTage(d("5"));
            when(uebertragRepository.findByMitarbeiterIdAndJahr(1L, 2027)).thenReturn(Optional.of(rest));

            UrlaubskontoService.Antragspruefung pruefung = service.pruefeAntrag(max, 2027, tage(
                    LocalDate.of(2027, 3, 22), LocalDate.of(2027, 3, 23), LocalDate.of(2027, 3, 24),
                    LocalDate.of(2027, 3, 25), LocalDate.of(2027, 3, 26), LocalDate.of(2027, 3, 29)));

            assertThat(pruefung.restDanach()).isEqualByComparingTo("1");
            assertThat(pruefung.reicht()).isTrue();
            assertThat(pruefung.vorjahresrestVerfaellt()).isFalse();
        }

        @Test
        void eintrittsjahrHatAnteiligenAnspruchUndGibtDenRestWeiter() {
            // Betrieb zwölftelt laut Tarif: Eintritt 15.03.2026 → 9 volle Monate → 30 × 9/12 = 22,5 → 23 Tage.
            lenient().when(einstellungen.mehrurlaubImEintrittsjahrAnteilig()).thenReturn(true);
            max.setEintrittsdatum(LocalDate.of(2026, 3, 15));
            bucheUrlaub(LocalDate.of(2026, 7, 1), 20, 0);

            Urlaubskonto konto2026 = service.berechne(max, 2026);
            assertThat(konto2026.jahresanspruch()).isEqualByComparingTo("23");
            assertThat(konto2026.uebertrag()).isEqualByComparingTo("0");
            assertThat(konto2026.verbleibend()).isEqualByComparingTo("3");

            // Startjahr 2025: der Rest aus dem Eintrittsjahr geht normal ins Folgejahr.
            when(einstellungen.uebertragAbJahr()).thenReturn(2025);
            Urlaubskonto konto2027 = service.berechne(max, 2027);
            assertThat(konto2027.jahresanspruch()).isEqualByComparingTo("30");
            assertThat(konto2027.uebertrag()).isEqualByComparingTo("3");
        }

        @Test
        void uebertragStandRechnetNurDasVorjahr() {
            bucheUrlaub(LocalDate.of(2026, 6, 1), 26, 0);
            UrlaubUebertrag eintrag = new UrlaubUebertrag();
            eintrag.setTage(d("1.5"));
            lenient().when(uebertragRepository.findByMitarbeiterIdAndJahr(1L, 2027)).thenReturn(Optional.of(eintrag));

            UrlaubskontoService.UebertragStand stand = service.uebertragStand(max, 2027);

            assertThat(stand.tage()).isEqualByComparingTo("1.5");
            assertThat(stand.berechnet()).isEqualByComparingTo("4");
            assertThat(stand.manuell()).isTrue();
            assertThat(stand.verfallsdatum()).isEqualTo(VERFALL_2027);
            verify(abwesenheitRepository, never()).findByMitarbeiterIdAndDatumBetween(1L,
                    LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
        }

        @Test
        void schonGebuchteTageImAntragWerdenNichtDoppeltGezaehlt() {
            // 30 Tage, 28 schon gebucht – darunter der 02.08. Antrag 02.–03.08.: nur der 03.08. ist neu.
            bucheUrlaub(LocalDate.of(2027, 7, 6), 27, 0);
            bucheUrlaub(LocalDate.of(2027, 8, 2), 1, 0);

            UrlaubskontoService.Antragspruefung pruefung = service.pruefeAntrag(max, 2027,
                    tage(LocalDate.of(2027, 8, 2), LocalDate.of(2027, 8, 3)));

            assertThat(pruefung.beantragteTage()).isEqualByComparingTo("1");
            assertThat(pruefung.restDanach()).isEqualByComparingTo("1");
            assertThat(pruefung.reicht()).isTrue();
        }

        private org.example.kalkulationsprogramm.domain.ZeitkontoVersion version(String... stundenMoBisSa) {
            org.example.kalkulationsprogramm.domain.ZeitkontoVersion v = new org.example.kalkulationsprogramm.domain.ZeitkontoVersion();
            v.setMontagStunden(d(stundenMoBisSa[0]));
            v.setDienstagStunden(d(stundenMoBisSa[1]));
            v.setMittwochStunden(d(stundenMoBisSa[2]));
            v.setDonnerstagStunden(d(stundenMoBisSa[3]));
            v.setFreitagStunden(d(stundenMoBisSa[4]));
            v.setSamstagStunden(d(stundenMoBisSa[5]));
            return v;
        }

        @Test
        void anspruchNutztDieArbeitstageAusDemZeitkonto() {
            // Betrieb zwölftelt laut Tarif. 4-Tage-Woche, Eintritt 01.06.2026 → 7/12 von 24 = 14 → angehoben auf 16
            when(einstellungen.mehrurlaubImEintrittsjahrAnteilig()).thenReturn(true);
            max.setJahresUrlaub(24);
            max.setEintrittsdatum(LocalDate.of(2026, 6, 1));
            org.example.kalkulationsprogramm.domain.ZeitkontoVersion vierTage = version("8", "8", "8", "8", "0", "0");
            when(zeitkontoVersionRepository.findImZeitraum(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                    .thenReturn(List.of(vierTage));

            UrlaubskontoService.Anspruch anspruch = service.anspruch(max, 2026);

            assertThat(vierTage.getArbeitstageProWoche()).isEqualTo(4);
            assertThat(anspruch.tage()).isEqualByComparingTo("16");
            assertThat(anspruch.art()).isEqualTo(UrlaubskontoService.Anspruchsart.MINDESTURLAUB_EINTRITT);
            assertThat(service.berechne(max, 2026).jahresanspruch()).isEqualByComparingTo("16");
        }

        @Test
        void ohneZwoelftelnGibtEsBeiEintrittBis0107DenVollenVertragsurlaub() {
            max.setJahresUrlaub(30);
            max.setEintrittsdatum(LocalDate.of(2026, 3, 15));

            UrlaubskontoService.Anspruch anspruch = service.anspruch(max, 2026);

            assertThat(anspruch.tage()).isEqualByComparingTo("30");
            assertThat(anspruch.art()).isEqualTo(UrlaubskontoService.Anspruchsart.VOLL);
        }

        @Test
        void hoechsteArbeitstageImJahrZaehlen_auchWennDasZeitkontoSpaeterBeginnt() {
            // Vertrag 20 Tage, ab Sommer 6-Tage-Woche → Minimum 24, nicht 20 aus der Vorgabe ohne Zeitkonto
            max.setJahresUrlaub(20);
            when(zeitkontoVersionRepository.findImZeitraum(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                    .thenReturn(List.of(version("8", "8", "8", "8", "8", "0"), version("7", "7", "7", "7", "7", "5")));

            UrlaubskontoService.Anspruch anspruch = service.anspruch(max, 2026);

            assertThat(anspruch.tage()).isEqualByComparingTo("24");
            assertThat(anspruch.art()).isEqualTo(UrlaubskontoService.Anspruchsart.MINDESTURLAUB);
        }

        @Test
        void anspruchWirdInDerKetteJeJahrNurEinmalGeladen() {
            when(einstellungen.uebertragAbJahr()).thenReturn(2024);

            service.berechne(max, 2027);

            verify(zeitkontoVersionRepository, org.mockito.Mockito.times(1))
                    .findImZeitraum(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        }

        @Test
        void vorgeladeneAbwesenheitenWerdenNichtNochmalGelesen() {
            Abwesenheit tag = new Abwesenheit();
            tag.setTyp(AbwesenheitsTyp.URLAUB);
            tag.setDatum(LocalDate.of(2026, 8, 3));
            tag.setStunden(d("8.00"));

            Urlaubskonto konto = service.berechne(max, 2026, List.of(tag));

            assertThat(konto.genommen()).isEqualByComparingTo("1");
            verify(abwesenheitRepository, never()).findByMitarbeiterIdAndDatumBetween(any(), any(), any());
        }
    }

    /** Anspruch nach BUrlG: Mindesturlaub, Wartezeit, Zwölftel und Rundung (§§ 3–5). */
    @Nested
    class AnspruchNachGesetz {

        /** Gesetzliche Regel: Mehrurlaub bei erfüllter Wartezeit nicht zwölfteln. */
        private BigDecimal tage(Integer urlaub, LocalDate eintritt, int jahr, int arbeitstage) {
            return UrlaubskontoService.anspruchImJahr(urlaub, eintritt, jahr, arbeitstage, false).tage();
        }

        private UrlaubskontoService.Anspruchsart art(Integer urlaub, LocalDate eintritt, int jahr, int arbeitstage) {
            return UrlaubskontoService.anspruchImJahr(urlaub, eintritt, jahr, arbeitstage, false).art();
        }

        /** Vertrag/Tarif zwölftelt den Mehrurlaub im Eintrittsjahr. */
        private UrlaubskontoService.Anspruch gezwoelftelt(Integer urlaub, LocalDate eintritt, int jahr, int arbeitstage) {
            return UrlaubskontoService.anspruchImJahr(urlaub, eintritt, jahr, arbeitstage, true);
        }

        @Test
        void eintrittBis0107_ohneZwoelftelung_vollerVertragsurlaub() {
            // Gleichlauf von gesetzlichem und vertraglichem Urlaub (BAG 9 AZR 353/10)
            assertThat(tage(30, LocalDate.of(2027, 3, 15), 2027, 5)).isEqualByComparingTo("30");
            assertThat(art(30, LocalDate.of(2027, 3, 15), 2027, 5)).isEqualTo(UrlaubskontoService.Anspruchsart.VOLL);
            assertThat(tage(30, LocalDate.of(2027, 7, 1), 2027, 5)).isEqualByComparingTo("30");
            // Vertrag unter dem Minimum: Minimum
            assertThat(tage(18, LocalDate.of(2027, 5, 1), 2027, 5)).isEqualByComparingTo("20");
            assertThat(art(18, LocalDate.of(2027, 5, 1), 2027, 5)).isEqualTo(UrlaubskontoService.Anspruchsart.MINDESTURLAUB);
        }

        @Test
        void vollerAnspruchOhneEintrittOderInSpaeterenJahren() {
            assertThat(tage(30, null, 2027, 5)).isEqualByComparingTo("30");
            assertThat(tage(30, LocalDate.of(2020, 6, 1), 2027, 5)).isEqualByComparingTo("30");
            assertThat(tage(null, null, 2027, 5)).isEqualByComparingTo("30");
            assertThat(tage(28, LocalDate.of(2027, 1, 1), 2027, 5)).isEqualByComparingTo("28");
            assertThat(art(28, LocalDate.of(2027, 1, 1), 2027, 5)).isEqualTo(UrlaubskontoService.Anspruchsart.VOLL);
        }

        @Test
        void vertragsurlaubUnterDemMinimumWirdAngehoben() {
            assertThat(tage(18, null, 2027, 5)).isEqualByComparingTo("20");
            assertThat(art(18, null, 2027, 5)).isEqualTo(UrlaubskontoService.Anspruchsart.MINDESTURLAUB);
            assertThat(tage(14, null, 2027, 4)).isEqualByComparingTo("16");
        }

        @Test
        void mindesturlaubJeArbeitstageWoche() {
            assertThat(UrlaubskontoService.gesetzlicherMindesturlaub(5)).isEqualByComparingTo("20");
            assertThat(UrlaubskontoService.gesetzlicherMindesturlaub(4)).isEqualByComparingTo("16");
            assertThat(UrlaubskontoService.gesetzlicherMindesturlaub(6)).isEqualByComparingTo("24");
            assertThat(UrlaubskontoService.gesetzlicherMindesturlaub(7)).isEqualByComparingTo("24");
            assertThat(UrlaubskontoService.gesetzlicherMindesturlaub(0)).isEqualByComparingTo("4");
        }

        @Test
        void vorDemEintrittsjahrKeinAnspruch() {
            assertThat(tage(30, LocalDate.of(2028, 2, 1), 2027, 5)).isEqualByComparingTo("0");
            assertThat(art(30, LocalDate.of(2028, 2, 1), 2027, 5)).isEqualTo(UrlaubskontoService.Anspruchsart.VOR_EINTRITT);
        }

        @Test
        void eintrittBis0107_gezwoelftelt_mindestensGesetzlicherUrlaub() {
            // 15.03.: 9 Monate → 22,5 → 23, mehr als das Minimum
            assertThat(gezwoelftelt(30, LocalDate.of(2027, 3, 15), 2027, 5).tage()).isEqualByComparingTo("23");
            assertThat(gezwoelftelt(30, LocalDate.of(2027, 3, 15), 2027, 5).art()).isEqualTo(UrlaubskontoService.Anspruchsart.ANTEILIG_EINTRITT);
            // 01.06.: 7 Monate → 17,5 → 18, angehoben auf 20
            assertThat(gezwoelftelt(30, LocalDate.of(2027, 6, 1), 2027, 5).tage()).isEqualByComparingTo("20");
            assertThat(gezwoelftelt(30, LocalDate.of(2027, 6, 1), 2027, 5).art()).isEqualTo(UrlaubskontoService.Anspruchsart.MINDESTURLAUB_EINTRITT);
            // 01.07.: Wartezeit endet am 31.12. – noch erfüllt (BAG) → 15 angehoben auf 20
            assertThat(gezwoelftelt(30, LocalDate.of(2027, 7, 1), 2027, 5).tage()).isEqualByComparingTo("20");
            // 4-Tage-Woche: Minimum 16
            assertThat(gezwoelftelt(24, LocalDate.of(2027, 6, 1), 2027, 4).tage()).isEqualByComparingTo("16");
        }

        @Test
        void eintrittNach0107_gleichMitOderOhneZwoelftelung() {
            assertThat(gezwoelftelt(30, LocalDate.of(2027, 8, 1), 2027, 5).tage()).isEqualByComparingTo("13");
            assertThat(tage(30, LocalDate.of(2027, 8, 1), 2027, 5)).isEqualByComparingTo("13");
        }

        @Test
        void eintrittNach0107_nurEinZwoelftelJeMonat() {
            // 02.07.: Wartezeit nicht mehr im Jahr erfüllt, 5 volle Monate → 12,5 → 13
            assertThat(tage(30, LocalDate.of(2027, 7, 2), 2027, 5)).isEqualByComparingTo("13");
            assertThat(tage(30, LocalDate.of(2027, 8, 1), 2027, 5)).isEqualByComparingTo("13");
            assertThat(art(30, LocalDate.of(2027, 8, 1), 2027, 5)).isEqualTo(UrlaubskontoService.Anspruchsart.ANTEILIG_EINTRITT);
            // Rundung: ab 0,5 auf ganze Tage, kleinere Bruchteile auf einen halben Tag auf
            assertThat(tage(28, LocalDate.of(2027, 11, 1), 2027, 5)).isEqualByComparingTo("5");
            assertThat(tage(30, LocalDate.of(2027, 12, 1), 2027, 5)).isEqualByComparingTo("3");
            assertThat(tage(20, LocalDate.of(2027, 12, 1), 2027, 5)).isEqualByComparingTo("2");
            assertThat(tage(26, LocalDate.of(2027, 12, 1), 2027, 5)).isEqualByComparingTo("2.5");
            // Eintritt mitten im Dezember: kein voller Monat
            assertThat(tage(30, LocalDate.of(2027, 12, 15), 2027, 5)).isEqualByComparingTo("0");
        }
    }
}
