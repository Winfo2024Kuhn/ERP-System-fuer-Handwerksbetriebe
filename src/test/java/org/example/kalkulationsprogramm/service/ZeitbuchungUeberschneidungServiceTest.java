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
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidungHandyZeit;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungUeberschneidungHandyZeitRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungUeberschneidungRepository;
import org.example.kalkulationsprogramm.service.ueberschneidung.UeberschneidungAnzeige;
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
    @Mock private ZeitbuchungUeberschneidungHandyZeitRepository handyZeitRepository;

    private ZeitbuchungUeberschneidungService service;
    private Mitarbeiter max;
    private final Map<Long, Zeitbuchung> buchungen = new HashMap<>();
    private final List<ZeitbuchungUeberschneidung> meldungen = new ArrayList<>();
    private final List<ZeitbuchungUeberschneidungHandyZeit> handyZeiten = new ArrayList<>();
    private long naechsteId = 500;

    @BeforeEach
    void setUp() {
        service = new ZeitbuchungUeberschneidungService(zeitbuchungRepository, ueberschneidungRepository,
                auditService, monatsSaldoService, eventPublisher,
                new org.example.kalkulationsprogramm.service.ueberschneidung.UeberschneidungHandyZeiten(
                        handyZeitRepository, zeitbuchungRepository));
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

        when(zeitbuchungRepository.findAllById(any())).thenAnswer(inv -> {
            List<Zeitbuchung> treffer = new ArrayList<>();
            ((Iterable<Long>) inv.getArgument(0)).forEach(id -> {
                if (buchungen.containsKey(id)) {
                    treffer.add(buchungen.get(id));
                }
            });
            return treffer;
        });
        when(handyZeitRepository.save(any(ZeitbuchungUeberschneidungHandyZeit.class))).thenAnswer(inv -> {
            ZeitbuchungUeberschneidungHandyZeit h = inv.getArgument(0);
            if (h.getId() == null) {
                h.setId((long) handyZeiten.size() + 1);
                handyZeiten.add(h);
            }
            return h;
        });
        when(handyZeitRepository.findByUeberschneidungIdAndHandyBuchungId(anyLong(), anyLong())).thenAnswer(inv ->
                handyZeiten.stream()
                        .filter(h -> h.getUeberschneidung().getId().equals(inv.getArgument(0))
                                && h.getHandyBuchungId().equals(inv.getArgument(1)))
                        .findFirst());
        when(handyZeitRepository.findByHandyBuchungId(anyLong())).thenAnswer(inv -> handyZeiten.stream()
                .filter(h -> h.getHandyBuchungId().equals(inv.getArgument(0)))
                .toList());
        when(handyZeitRepository.findZuMeldungen(any())).thenAnswer(inv -> handyZeiten.stream()
                .filter(h -> ((java.util.Collection<?>) inv.getArgument(0)).contains(h.getUeberschneidung().getId()))
                .toList());
    }

    private String textHandy(ZeitbuchungUeberschneidung meldung) {
        return UeberschneidungAnzeige.fuerMitarbeiter(meldung, service.stand(List.of(meldung))).text();
    }

    private String textBuero(ZeitbuchungUeberschneidung meldung) {
        return UeberschneidungAnzeige.fuerBuero(meldung, service.stand(List.of(meldung))).text();
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
        assertThat(textHandy(meldungen.getFirst()))
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
        assertThat(textHandy(meldungen.getFirst()))
                .isEqualTo("Deine Handy-Zeiten: Arbeit 10:00–12:00. Büro-Eintrag „Musterhaus · Montage“ "
                        + "10:30–11:30 wurde deshalb entfernt.");
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

        assertThat(UeberschneidungAnzeige.fuerBuero(meldung, service.stand(List.of(meldung)))).satisfies(a -> {
            assertThat(a.titel()).isEqualTo("Max Mustermann am 28.10.2026");
            assertThat(a.text()).isEqualTo("Vom Handy: Arbeit 10:00–12:00. Büro-Eintrag "
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
        service.handyBuchungZuordnen(vorher, handy(3, um(12, 0), null));
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
        assertThat(textBuero(meldung)).startsWith("Vom Handy: Arbeit 10:00–12:00, Arbeit 12:00–14:00. ");
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
        service.handyBuchungZuordnen(ergebnis, handy(42, um(10, 0), null));
        assertThat(ergebnis.getFirst().getHandyBuchungId()).isEqualTo(42L);
        assertThat(textHandy(ergebnis.getFirst()))
                .endsWith("Büro-Eintrag „Musterhaus · Montage“ ab 08:00 → jetzt 08:00–10:00.");
    }

    @Test
    void beimAnstechenStehtAbStattPlatzhalterUndSpaeterDasEchteEnde() {
        buero(1, um(10, 30), null);

        List<ZeitbuchungUeberschneidung> ergebnis = service.vorNeuerHandyBuchung(max, um(10, 0));
        Zeitbuchung handy = handy(2, um(10, 0), null);
        service.handyBuchungZuordnen(ergebnis, handy);

        ZeitbuchungUeberschneidung meldung = ergebnis.getFirst();
        assertThat(meldung.getErgebnis()).isEqualTo(UeberschneidungErgebnis.ENTFERNT);
        assertThat(meldung.getHandyBis()).isNull();
        assertThat(textHandy(meldung)).startsWith("Deine Handy-Zeiten: Arbeit ab 10:00. ");

        // Feierabend um 12:00: Der Büro-Eintrag ist schon weg, die Meldung bekommt trotzdem das Ende.
        handy.setEndeZeit(um(12, 0));
        service.nachHandyBuchung(handy, max);
        assertThat(meldung.getHandyBis()).isEqualTo(um(12, 0));
        assertThat(textHandy(meldung)).startsWith("Deine Handy-Zeiten: Arbeit 10:00–12:00. ");
        assertThat(handyZeiten).singleElement().satisfies(h -> assertThat(h.getBis()).isEqualTo(um(12, 0)));
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
        assertThat(textHandy(meldung)).contains("Monat schon abgeschlossen");
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
        assertThat(captor.getValue().text()).isEqualTo("Deine Handy-Zeiten: Arbeit 10:00–12:00. Büro-Eintrag "
                + "„Musterhaus · Montage“ 09:00–11:00 → jetzt 09:00–10:00.");
    }

    @Test
    void buchungenFuerBueroUndHandyKommenAusDemRepository() {
        ZeitbuchungUeberschneidung u = new ZeitbuchungUeberschneidung();
        u.setId(9L);
        u.setHandyVon(um(10, 0));
        u.setBueroVon(um(9, 0));
        u.setBueroBis(um(11, 0));
        u.setErgebnis(UeberschneidungErgebnis.ENTFERNT);
        when(ueberschneidungRepository.findByMitarbeiterIdAndMitarbeiterGesehenAmIsNullOrderByGeaendertAmDesc(MAX_ID))
                .thenReturn(List.of(u));
        when(ueberschneidungRepository.findGeaendertAb(any())).thenReturn(List.of());

        assertThat(service.ungeleseneMitAnzeige(MAX_ID)).singleElement().satisfies(m -> {
            assertThat(m.meldung()).isNotNull();
            assertThat(m.anzeige().text()).startsWith("Deine Handy-Zeiten: ");
        });
        assertThat(service.fuerBuero()).isEmpty();
    }

    /**
     * Echter Fall (Dummy-Daten): Das Büro trägt den Tag von Hand ein, am Folgetag schickt das
     * Handy gepuffert Arbeit 11:27–12:00, Pause 12:00–13:34, Arbeit 13:35–17:57 – so, wie
     * {@code ZeiterfassungApiService} die Schritte aufruft. Jede Meldung nennt genau die
     * Handy-Zeiten, die ihren Büro-Eintrag verdrängt haben, keine zusammengezogene Spanne.
     */
    @Test
    void gepufferteHandyBuchungenStehenEinzelnInDerMeldung() {
        buero(1, um(10, 30), um(12, 0));
        Zeitbuchung bueroPause = buero(2, um(12, 0), um(13, 0));
        bueroPause.setTyp(BuchungsTyp.PAUSE);
        buero(3, um(13, 0), um(15, 30));
        buero(4, um(15, 30), um(16, 45));

        service.handyBuchungZuordnen(service.vorNeuerHandyBuchung(max, um(11, 27)), handy(30, um(11, 27), null));
        Zeitbuchung arbeit = buchungen.get(30L);
        arbeit.setEndeZeit(um(12, 0));
        service.nachHandyBuchung(arbeit, max);
        List<ZeitbuchungUeberschneidung> vorPause = service.vorNeuerHandyBuchung(max, um(12, 0));
        Zeitbuchung pause = handy(31, um(12, 0), null);
        pause.setTyp(BuchungsTyp.PAUSE);
        service.handyBuchungZuordnen(vorPause, pause);
        pause.setEndeZeit(um(13, 34));
        service.nachHandyBuchung(pause, max);
        List<ZeitbuchungUeberschneidung> vorArbeit = service.vorNeuerHandyBuchung(max, um(13, 35));
        Zeitbuchung nachmittag = handy(32, um(13, 35), null);
        service.handyBuchungZuordnen(vorArbeit, nachmittag);
        nachmittag.setEndeZeit(um(17, 57));
        service.nachHandyBuchung(nachmittag, max);

        assertThat(meldungen).hasSize(4);
        assertThat(textBuero(meldungZu(1L))).isEqualTo("Vom Handy: Arbeit 11:27–12:00. Büro-Eintrag "
                + "„Musterhaus · Montage“ 10:30–12:00 → jetzt 10:30–11:27.");
        assertThat(textBuero(meldungZu(2L))).isEqualTo("Vom Handy: Pause 12:00–13:34. Büro-Eintrag „Pause“ "
                + "12:00–13:00 wurde deshalb entfernt.");
        assertThat(textBuero(meldungZu(3L))).isEqualTo("Vom Handy: Pause 12:00–13:34, Arbeit 13:35–17:57. "
                + "Büro-Eintrag „Musterhaus · Montage“ 13:00–15:30 wurde deshalb entfernt.");
        assertThat(textBuero(meldungZu(4L))).isEqualTo("Vom Handy: Arbeit 13:35–17:57. Büro-Eintrag "
                + "„Musterhaus · Montage“ 15:30–16:45 wurde deshalb entfernt.");
        // Je Meldung und Handy-Buchung genau eine Zeile, kein vorläufiger Ein-Minuten-Zeitraum.
        assertThat(handyZeiten).hasSize(5)
                .allSatisfy(h -> assertThat(h.getBis()).isNotNull())
                .noneSatisfy(h -> assertThat(java.time.Duration.between(h.getVon(), h.getBis()))
                        .isEqualTo(java.time.Duration.ofMinutes(1)));
        // Gespeichert wird nur, was sich ändert: 5 neue Zeilen, 3 Mal ein nachgetragenes Ende.
        verify(handyZeitRepository, org.mockito.Mockito.times(8)).save(any(ZeitbuchungUeberschneidungHandyZeit.class));
    }

    private ZeitbuchungUeberschneidung meldungZu(Long bueroId) {
        return meldungen.stream().filter(u -> bueroId.equals(u.getBueroBuchungId())).findFirst().orElseThrow();
    }

    @Test
    void festgehalteneHandyZeitBleibtStandVonDamals() {
        buero(1, um(9, 0), um(11, 0));
        Zeitbuchung handy = handy(2, um(10, 0), um(12, 0));
        service.nachHandyBuchung(handy, max);

        // Das Büro ändert die Handy-Buchung später – die Meldung zeigt weiter, was das Handy schickte.
        handy.setStartZeit(um(10, 30));

        assertThat(textBuero(meldungen.getFirst())).startsWith("Vom Handy: Arbeit 10:00–12:00. ");
    }

    @Test
    void altmeldungOhneFestgehalteneZeitenNenntDieSpanne() {
        ZeitbuchungUeberschneidung alt = new ZeitbuchungUeberschneidung();
        alt.setId(99L);
        alt.setMitarbeiter(max);
        alt.setHandyVon(um(11, 27));
        alt.setHandyBis(um(13, 34));
        alt.setBueroVon(um(12, 0));
        alt.setBueroBis(um(13, 0));
        alt.setBueroBezeichnung("Pause");
        alt.setErgebnis(UeberschneidungErgebnis.ENTFERNT);

        assertThat(textBuero(alt)).isEqualTo("Vom Handy: 11:27–13:34. Büro-Eintrag „Pause“ 12:00–13:00 "
                + "wurde deshalb entfernt.");
    }

    /**
     * Tageswechsel: Ein Offline-Anstempeln vom Vortag kommt erst heute an. Der heute im Büro
     * gestartete, noch laufende Eintrag bleibt stehen (ZeiterfassungApiService lehnt die
     * verspätete Buchung dann mit 409 ab), statt still storniert zu werden.
     */
    @Test
    void verspaeteteBuchungVomVortagLaesstHeuteLaufendenBueroEintragStehen() {
        Zeitbuchung heute = buero(1, um(8, 0).plusDays(1), null);

        List<ZeitbuchungUeberschneidung> ergebnis = service.vorNeuerHandyBuchung(max, um(11, 27));

        assertThat(ergebnis).isEmpty();
        assertThat(buchungen).containsKey(1L);
        assertThat(heute.getEndeZeit()).isNull();
        verify(auditService, never()).protokolliereStorno(any(), any(), any(), anyString());
    }

    @Test
    void vorDerHandyBuchungGestarteterLaufenderEintragVomVortagEndetWeiterhin() {
        Zeitbuchung gestern = buero(1, um(6, 0), null);

        service.vorNeuerHandyBuchung(max, um(6, 0).plusDays(1));

        assertThat(gestern.getEndeZeit()).isEqualTo(um(6, 0).plusDays(1));
    }
}
