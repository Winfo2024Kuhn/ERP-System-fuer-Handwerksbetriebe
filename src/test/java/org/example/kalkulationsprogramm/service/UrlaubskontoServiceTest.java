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

        private UrlaubskontoService service;
        private Mitarbeiter max;
        private final Map<Integer, List<Abwesenheit>> urlaubJeJahr = new HashMap<>();

        @BeforeEach
        void setUp() {
            // Heute: 15.02.2027 – der Übertrag aus 2026 ist noch offen.
            Clock clock = Clock.fixed(LocalDate.of(2027, 2, 15).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    ZoneId.systemDefault());
            service = new UrlaubskontoService(abwesenheitRepository, korrekturService, tagesSollService,
                    new UrlaubstageRechner(), uebertragRepository, einstellungen, clock);
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
            max.setJahresUrlaub(2);
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
            max.setJahresUrlaub(2);
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
            // Eintritt 15.03.2026 → 9 volle Monate → 30 × 9/12 = 22,5 → 23 Tage (§ 5 Abs. 2 BUrlG).
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

    /** Anteiliger Anspruch im Eintrittsjahr mit Rundung nach § 5 Abs. 2 BUrlG. */
    @Nested
    class AnspruchImEintrittsjahr {

        @Test
        void vollerAnspruchOhneEintrittOderInSpaeterenJahren() {
            assertThat(UrlaubskontoService.anspruchImJahr(30, null, 2027)).isEqualByComparingTo("30");
            assertThat(UrlaubskontoService.anspruchImJahr(30, LocalDate.of(2020, 6, 1), 2027)).isEqualByComparingTo("30");
            assertThat(UrlaubskontoService.anspruchImJahr(null, null, 2027)).isEqualByComparingTo("30");
            assertThat(UrlaubskontoService.anspruchImJahr(28, LocalDate.of(2027, 1, 1), 2027)).isEqualByComparingTo("28");
        }

        @Test
        void vorDemEintrittsjahrKeinAnspruch() {
            assertThat(UrlaubskontoService.anspruchImJahr(30, LocalDate.of(2028, 2, 1), 2027)).isEqualByComparingTo("0");
        }

        @Test
        void einZwoelftelJeVollemMonatMitRundung() {
            // ab 0,5 auf ganze Tage auf, kleinere Bruchteile auf einen halben Tag auf
            assertThat(UrlaubskontoService.anspruchImJahr(30, LocalDate.of(2027, 3, 15), 2027)).isEqualByComparingTo("23");
            assertThat(UrlaubskontoService.anspruchImJahr(28, LocalDate.of(2027, 11, 1), 2027)).isEqualByComparingTo("5");
            assertThat(UrlaubskontoService.anspruchImJahr(30, LocalDate.of(2027, 12, 1), 2027)).isEqualByComparingTo("3");
            assertThat(UrlaubskontoService.anspruchImJahr(20, LocalDate.of(2027, 12, 1), 2027)).isEqualByComparingTo("2");
            assertThat(UrlaubskontoService.anspruchImJahr(26, LocalDate.of(2027, 12, 1), 2027)).isEqualByComparingTo("2.5");
            assertThat(UrlaubskontoService.anspruchImJahr(24, LocalDate.of(2027, 7, 1), 2027)).isEqualByComparingTo("12");
            // Eintritt mitten im Dezember: kein voller Monat
            assertThat(UrlaubskontoService.anspruchImJahr(30, LocalDate.of(2027, 12, 15), 2027)).isEqualByComparingTo("0");
        }
    }
}
