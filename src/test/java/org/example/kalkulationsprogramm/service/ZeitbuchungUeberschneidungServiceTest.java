package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.UeberschneidungErgebnis;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidung;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungUeberschneidungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

/**
 * "Handy gewinnt": Büro-Einträge weichen einer Handy-Buchung. Die Buchungen leben in einer
 * kleinen Map statt in der Datenbank, damit Kürzen, Teilen und Entfernen nachprüfbar sind.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZeitbuchungUeberschneidungServiceTest {

    private static final Long MAX_ID = 7L;
    private static final LocalDateTime TAG = LocalDateTime.of(2026, 10, 28, 0, 0);

    @Mock private ZeitbuchungRepository zeitbuchungRepository;
    @Mock private ZeitbuchungUeberschneidungRepository ueberschneidungRepository;
    @Mock private ZeitbuchungAuditService auditService;
    @Mock private MonatsSaldoService monatsSaldoService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ZeitbuchungUeberschneidungService service;
    private Mitarbeiter max;
    private final Map<Long, Zeitbuchung> buchungen = new HashMap<>();
    private final List<ZeitbuchungUeberschneidung> meldungen = new ArrayList<>();
    private long naechsteId = 500;

    @BeforeEach
    void setUp() {
        service = new ZeitbuchungUeberschneidungService(zeitbuchungRepository, ueberschneidungRepository,
                auditService, monatsSaldoService, eventPublisher);
        max = new Mitarbeiter();
        max.setId(MAX_ID);
        max.setVorname("Max");
        max.setNachname("Mustermann");

        when(zeitbuchungRepository.findUeberlappende(eq(MAX_ID), any(), any())).thenAnswer(inv -> {
            LocalDateTime von = inv.getArgument(1);
            LocalDateTime bis = inv.getArgument(2);
            return buchungen.values().stream()
                    .filter(z -> z.getStartZeit().isBefore(bis)
                            && (z.getEndeZeit() == null || z.getEndeZeit().isAfter(von)))
                    .toList();
        });
        when(zeitbuchungRepository.findByMitarbeiterIdAndEndeZeitIsNull(MAX_ID)).thenAnswer(inv ->
                buchungen.values().stream().filter(z -> z.getEndeZeit() == null).toList());
        when(zeitbuchungRepository.existsById(anyLong())).thenAnswer(inv -> buchungen.containsKey(inv.getArgument(0)));
        when(zeitbuchungRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(buchungen.get(inv.getArgument(0))));
        when(zeitbuchungRepository.existsByMitarbeiterIdAndStartZeit(eq(MAX_ID), any())).thenAnswer(inv ->
                buchungen.values().stream().anyMatch(z -> z.getStartZeit().equals(inv.getArgument(1))));
        when(zeitbuchungRepository.saveAndFlush(any(Zeitbuchung.class))).thenAnswer(inv -> {
            Zeitbuchung z = inv.getArgument(0);
            if (z.getId() == null) {
                z.setId(naechsteId++);
            }
            buchungen.put(z.getId(), z);
            return z;
        });
        when(zeitbuchungRepository.save(any(Zeitbuchung.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doAnswer(inv -> {
            buchungen.remove(((Zeitbuchung) inv.getArgument(0)).getId());
            return null;
        }).when(zeitbuchungRepository).delete(any(Zeitbuchung.class));

        when(ueberschneidungRepository.findZuBuchung(anyLong())).thenAnswer(inv -> meldungen.stream()
                .filter(u -> inv.getArgument(0).equals(u.getBueroBuchungId())
                        || inv.getArgument(0).equals(u.getRestBuchungId()))
                .toList());
        when(ueberschneidungRepository.save(any(ZeitbuchungUeberschneidung.class))).thenAnswer(inv -> {
            ZeitbuchungUeberschneidung u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId((long) meldungen.size() + 1);
                meldungen.add(u);
            }
            return u;
        });
        when(ueberschneidungRepository.findByHandyBuchungId(anyLong())).thenAnswer(inv -> meldungen.stream()
                .filter(u -> inv.getArgument(0).equals(u.getHandyBuchungId()))
                .toList());
        when(monatsSaldoService.isMonatFestgeschrieben(anyLong(), anyInt(), anyInt())).thenReturn(false);
    }

    private static LocalDateTime um(int stunde, int minute) {
        return TAG.withHour(stunde).withMinute(minute);
    }

    private Zeitbuchung buero(long id, LocalDateTime start, LocalDateTime ende) {
        Zeitbuchung z = new Zeitbuchung();
        z.setId(id);
        z.setMitarbeiter(max);
        z.setStartZeit(start);
        z.setEndeZeit(ende);
        z.setErfasstVia(ErfassungsQuelle.DESKTOP);
        z.setTyp(BuchungsTyp.ARBEIT);
        z.setVersion(1);
        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Musterhaus");
        z.setProjekt(projekt);
        Arbeitsgang ag = new Arbeitsgang();
        ag.setBeschreibung("Montage");
        z.setArbeitsgang(ag);
        buchungen.put(id, z);
        return z;
    }

    private Zeitbuchung handy(long id, LocalDateTime start, LocalDateTime ende) {
        Zeitbuchung z = buero(id, start, ende);
        z.setErfasstVia(ErfassungsQuelle.MOBILE_APP);
        return z;
    }

    @Test
    void bueroEintragDerVorneHineinragtWirdGekuerzt() {
        Zeitbuchung eintrag = buero(1, um(9, 0), um(11, 0));
        Zeitbuchung handy = handy(2, um(10, 0), um(12, 0));

        List<ZeitbuchungUeberschneidung> ergebnis = service.nachHandyBuchung(handy, max);

        assertThat(eintrag.getEndeZeit()).isEqualTo(um(10, 0));
        assertThat(eintrag.getAnzahlInStunden()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(eintrag.getVersion()).isEqualTo(2);
        assertThat(ergebnis).singleElement().satisfies(u -> {
            assertThat(u.getErgebnis()).isEqualTo(UeberschneidungErgebnis.GEKUERZT);
            assertThat(u.getBueroVon()).isEqualTo(um(9, 0));
            assertThat(u.getBueroBis()).isEqualTo(um(11, 0));
            assertThat(u.getHandyBuchungId()).isEqualTo(2L);
            assertThat(u.getBueroBezeichnung()).isEqualTo("Musterhaus · Montage");
        });
        verify(auditService).protokolliereAenderung(eq(eintrag), eq(max), eq(ErfassungsQuelle.SYSTEM),
                org.mockito.ArgumentMatchers.contains("Handy hat Vorrang"));
        verify(eventPublisher).publishEvent(any(ZeitbuchungUeberschneidungService.UeberschneidungGemeldet.class));
    }

    @Test
    void bueroEintragDerHintenHinausragtBeginntNachDerHandyBuchung() {
        Zeitbuchung eintrag = buero(1, um(11, 0), um(14, 0));
        Zeitbuchung handy = handy(2, um(10, 0), um(12, 0));

        service.nachHandyBuchung(handy, max);

        assertThat(eintrag.getStartZeit()).isEqualTo(um(12, 0));
        assertThat(eintrag.getEndeZeit()).isEqualTo(um(14, 0));
        assertThat(meldungen).singleElement()
                .extracting(ZeitbuchungUeberschneidung::getErgebnis).isEqualTo(UeberschneidungErgebnis.GEKUERZT);
    }

    @Test
    void hintenHinausragenderEintragBleibtWennDortSchonEtwasBeginnt() {
        Zeitbuchung eintrag = buero(1, um(11, 0), um(14, 0));
        handy(3, um(12, 0), um(13, 0));
        Zeitbuchung handy = handy(2, um(10, 0), um(12, 0));

        service.nachHandyBuchung(handy, max);

        assertThat(eintrag.getStartZeit()).isEqualTo(um(11, 0));
        assertThat(meldungen).singleElement().extracting(ZeitbuchungUeberschneidung::getErgebnis)
                .isEqualTo(UeberschneidungErgebnis.ZEIT_BELEGT);
        assertThat(service.anzeigeFuerMitarbeiter(meldungen.getFirst()).text())
                .contains("weil danach schon ein anderer Eintrag beginnt")
                .doesNotContain("Monat");
    }

    @Test
    void bueroEintragGanzInDerHandyBuchungWirdStorniert() {
        Zeitbuchung eintrag = buero(1, um(10, 30), um(11, 30));
        Zeitbuchung handy = handy(2, um(10, 0), um(12, 0));

        service.nachHandyBuchung(handy, max);

        assertThat(buchungen).doesNotContainKey(1L);
        verify(auditService).protokolliereStorno(eq(eintrag), eq(max), eq(ErfassungsQuelle.SYSTEM), anyString());
        assertThat(meldungen).singleElement().extracting(ZeitbuchungUeberschneidung::getErgebnis)
                .isEqualTo(UeberschneidungErgebnis.ENTFERNT);
        assertThat(service.anzeigeFuerMitarbeiter(meldungen.getFirst()).text())
                .isEqualTo("Deine Handy-Buchung 10:00–12:00 hatte Vorrang. Büro-Eintrag „Musterhaus · Montage“ "
                        + "10:30–11:30 wurde entfernt.");
    }

    @Test
    void umschliessenderBueroEintragWirdGeteilt() {
        Zeitbuchung eintrag = buero(1, um(7, 0), um(16, 0));
        Zeitbuchung handy = handy(2, um(10, 0), um(12, 0));

        service.nachHandyBuchung(handy, max);

        assertThat(eintrag.getEndeZeit()).isEqualTo(um(10, 0));
        ZeitbuchungUeberschneidung meldung = meldungen.getFirst();
        assertThat(meldung.getErgebnis()).isEqualTo(UeberschneidungErgebnis.GETEILT);
        Zeitbuchung rest = buchungen.get(meldung.getRestBuchungId());
        assertThat(rest.getStartZeit()).isEqualTo(um(12, 0));
        assertThat(rest.getEndeZeit()).isEqualTo(um(16, 0));
        assertThat(rest.getAnzahlInStunden()).isEqualByComparingTo("4.00");
        assertThat(rest.getErfasstVia()).isEqualTo(ErfassungsQuelle.DESKTOP);
        assertThat(rest.getProjekt()).isSameAs(eintrag.getProjekt());
        verify(auditService).protokolliereErstellung(rest, max, ErfassungsQuelle.SYSTEM);

        assertThat(service.anzeigeFuerBuero(meldung)).satisfies(a -> {
            assertThat(a.titel()).isEqualTo("Max Mustermann am 28.10.2026");
            assertThat(a.text()).isEqualTo("Handy-Buchung 10:00–12:00 hatte Vorrang. Büro-Eintrag "
                    + "„Musterhaus · Montage“ 07:00–16:00 → jetzt 07:00–10:00 und 12:00–16:00.");
        });
    }

    @Test
    void folgendeHandyBuchungenAktualisierenDieselbeMeldung() {
        buero(1, um(7, 0), um(16, 0));
        service.nachHandyBuchung(handy(2, um(10, 0), um(12, 0)), max);
        Long restId = meldungen.getFirst().getRestBuchungId();

        // Direkter Wechsel um 12:00: neue Handy-Buchung beginnt, Rest rückt nach hinten …
        List<ZeitbuchungUeberschneidung> vorher = service.vorNeuerHandyBuchung(max, um(12, 0));
        handy(3, um(12, 0), null);
        service.handyBuchungZuordnen(vorher, 3L);
        assertThat(buchungen.get(restId).getStartZeit()).isEqualTo(um(12, 1));
        // … und bei Feierabend um 14:00 auf 14:00.
        buchungen.get(3L).setEndeZeit(um(14, 0));
        service.nachHandyBuchung(buchungen.get(3L), max);

        assertThat(buchungen.get(restId).getStartZeit()).isEqualTo(um(14, 0));
        assertThat(meldungen).hasSize(1);
        ZeitbuchungUeberschneidung meldung = meldungen.getFirst();
        assertThat(meldung.getHandyVon()).isEqualTo(um(10, 0));
        assertThat(meldung.getHandyBis()).isEqualTo(um(14, 0));
        assertThat(meldung.getErgebnis()).isEqualTo(UeberschneidungErgebnis.GETEILT);
        assertThat(meldung.getHandyBuchungId()).isEqualTo(2L);
        // Nur ein Push für die erste Meldung, nicht für jede Aktualisierung.
        verify(eventPublisher, org.mockito.Mockito.times(1)).publishEvent(any(Object.class));
    }

    @Test
    void imBueroLaufendeBuchungEndetBeimAnstechen() {
        Zeitbuchung laufend = buero(1, um(8, 0), null);

        List<ZeitbuchungUeberschneidung> ergebnis = service.vorNeuerHandyBuchung(max, um(10, 0));

        assertThat(laufend.getEndeZeit()).isEqualTo(um(10, 0));
        assertThat(ergebnis).singleElement().extracting(ZeitbuchungUeberschneidung::getErgebnis)
                .isEqualTo(UeberschneidungErgebnis.BEENDET);
        assertThat(ergebnis.getFirst().getHandyBuchungId()).isNull();
        service.handyBuchungZuordnen(ergebnis, 42L);
        assertThat(ergebnis.getFirst().getHandyBuchungId()).isEqualTo(42L);
        assertThat(service.anzeigeFuerMitarbeiter(ergebnis.getFirst()).text())
                .endsWith("Büro-Eintrag „Musterhaus · Montage“ ab 08:00 → jetzt 08:00–10:00.");
    }

    @Test
    void beimAnstechenStehtAbStattPlatzhalterUndSpaeterDasEchteEnde() {
        buero(1, um(10, 30), null);

        List<ZeitbuchungUeberschneidung> ergebnis = service.vorNeuerHandyBuchung(max, um(10, 0));
        Zeitbuchung handy = handy(2, um(10, 0), null);
        service.handyBuchungZuordnen(ergebnis, 2L);

        ZeitbuchungUeberschneidung meldung = ergebnis.getFirst();
        assertThat(meldung.getErgebnis()).isEqualTo(UeberschneidungErgebnis.ENTFERNT);
        assertThat(meldung.getHandyBis()).isNull();
        assertThat(service.anzeigeFuerMitarbeiter(meldung).text()).startsWith("Deine Handy-Buchung ab 10:00 hatte");

        // Feierabend um 12:00: Der Büro-Eintrag ist schon weg, die Meldung bekommt trotzdem das Ende.
        handy.setEndeZeit(um(12, 0));
        service.nachHandyBuchung(handy, max);
        assertThat(meldung.getHandyBis()).isEqualTo(um(12, 0));
        assertThat(service.anzeigeFuerMitarbeiter(meldung).text()).startsWith("Deine Handy-Buchung 10:00–12:00");
    }

    @Test
    void endeImFestgeschriebenenMonatZaehltAuch() {
        Zeitbuchung eintrag = buero(1, LocalDateTime.of(2026, 9, 30, 22, 0), LocalDateTime.of(2026, 10, 1, 6, 0));
        when(monatsSaldoService.isMonatFestgeschrieben(MAX_ID, 2026, 9)).thenReturn(true);

        service.nachHandyBuchung(handy(2, LocalDateTime.of(2026, 9, 30, 23, 0), LocalDateTime.of(2026, 10, 1, 1, 0)),
                max);

        assertThat(eintrag.getEndeZeit()).isEqualTo(LocalDateTime.of(2026, 10, 1, 6, 0));
        assertThat(meldungen.getFirst().getErgebnis()).isEqualTo(UeberschneidungErgebnis.NICHT_GEAENDERT);
    }

    @Test
    void imBueroSpaeterGestarteteLaufendeBuchungWirdEntfernt() {
        buero(1, um(11, 0), null);

        List<ZeitbuchungUeberschneidung> ergebnis = service.vorNeuerHandyBuchung(max, um(10, 0));

        assertThat(buchungen).isEmpty();
        assertThat(ergebnis.getFirst().getErgebnis()).isEqualTo(UeberschneidungErgebnis.ENTFERNT);
    }

    @Test
    void festgeschriebenerMonatBleibtUnveraendert() {
        Zeitbuchung eintrag = buero(1, um(9, 0), um(11, 0));
        when(monatsSaldoService.isMonatFestgeschrieben(MAX_ID, 2026, 10)).thenReturn(true);

        service.nachHandyBuchung(handy(2, um(10, 0), um(12, 0)), max);

        assertThat(eintrag.getEndeZeit()).isEqualTo(um(11, 0));
        verify(auditService, never()).protokolliereAenderung(any(), any(), any(), anyString());
        ZeitbuchungUeberschneidung meldung = meldungen.getFirst();
        assertThat(meldung.getErgebnis()).isEqualTo(UeberschneidungErgebnis.NICHT_GEAENDERT);
        assertThat(service.anzeigeFuerMitarbeiter(meldung).text()).contains("Monat schon abgeschlossen");
    }

    @Test
    void eigeneHandyBuchungenUndAltdatenOhneQuelleBleibenUnberuehrt() {
        Zeitbuchung eigene = handy(1, um(9, 0), um(11, 0));
        Zeitbuchung altdaten = buero(3, um(9, 30), um(10, 30));
        altdaten.setErfasstVia(null);

        assertThat(service.nachHandyBuchung(handy(2, um(10, 0), um(12, 0)), max)).isEmpty();

        assertThat(eigene.getEndeZeit()).isEqualTo(um(11, 0));
        assertThat(altdaten.getEndeZeit()).isEqualTo(um(10, 30));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void ohneZeitraumPassiertNichts() {
        assertThat(service.nachHandyBuchung(handy(2, um(10, 0), null), max)).isEmpty();
        assertThat(service.nachHandyBuchung(handy(3, um(10, 0), um(10, 0)), max)).isEmpty();
        verify(zeitbuchungRepository, never()).findUeberlappende(anyLong(), any(), any());
    }

    @Test
    void pauseImBueroHeisstPause() {
        Zeitbuchung pause = buero(1, um(9, 0), um(11, 0));
        pause.setTyp(BuchungsTyp.PAUSE);

        service.nachHandyBuchung(handy(2, um(10, 0), um(12, 0)), max);

        assertThat(meldungen.getFirst().getBueroBezeichnung()).isEqualTo("Pause");
    }

    @Test
    void gesehenNurFuerEigeneMeldung() {
        ZeitbuchungUeberschneidung u = new ZeitbuchungUeberschneidung();
        when(ueberschneidungRepository.findByIdAndMitarbeiterId(3L, MAX_ID)).thenReturn(Optional.of(u));
        when(ueberschneidungRepository.findByIdAndMitarbeiterId(3L, 8L)).thenReturn(Optional.empty());

        assertThat(service.alsGesehenMarkieren(3L, MAX_ID)).isTrue();
        assertThat(u.getMitarbeiterGesehenAm()).isNotNull();
        assertThat(service.alsGesehenMarkieren(3L, 8L)).isFalse();
    }

    @Test
    void neuerStandZeigtSichAmHandyWiederAlsUngelesen() {
        buero(1, um(7, 0), um(16, 0));
        service.nachHandyBuchung(handy(2, um(10, 0), um(12, 0)), max);
        meldungen.getFirst().setMitarbeiterGesehenAm(um(13, 0));

        service.nachHandyBuchung(handy(3, um(12, 0), um(14, 0)), max);

        assertThat(meldungen.getFirst().getMitarbeiterGesehenAm()).isNull();
    }

    @Test
    void pushEnthaeltMitarbeiterUndText() {
        buero(1, um(9, 0), um(11, 0));
        service.nachHandyBuchung(handy(2, um(10, 0), um(12, 0)), max);

        ArgumentCaptor<ZeitbuchungUeberschneidungService.UeberschneidungGemeldet> captor =
                ArgumentCaptor.forClass(ZeitbuchungUeberschneidungService.UeberschneidungGemeldet.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().mitarbeiterId()).isEqualTo(MAX_ID);
        assertThat(captor.getValue().titel()).isEqualTo("Überschneidung am 28.10.2026");
        assertThat(captor.getValue().text()).contains("→ jetzt 09:00–10:00");
    }

    @Test
    void zeitraumUeberMitternachtNenntDasDatum() {
        assertThat(ZeitbuchungUeberschneidungService.zeitraum(um(22, 0), um(22, 0).plusHours(3)))
                .isEqualTo("22:00–29.10.2026 01:00");
        assertThat(ZeitbuchungUeberschneidungService.zeitraum(null, null)).isEmpty();
    }

    @Test
    void buchungenFuerBueroUndHandyKommenAusDemRepository() {
        when(ueberschneidungRepository.findByMitarbeiterIdAndMitarbeiterGesehenAmIsNullOrderByGeaendertAmDesc(MAX_ID))
                .thenReturn(List.of(new ZeitbuchungUeberschneidung()));
        when(ueberschneidungRepository.findGeaendertAb(any())).thenReturn(List.of());

        assertThat(service.ungeleseneFuerMitarbeiter(MAX_ID)).hasSize(1);
        assertThat(service.fuerBuero()).isEmpty();
    }
}
