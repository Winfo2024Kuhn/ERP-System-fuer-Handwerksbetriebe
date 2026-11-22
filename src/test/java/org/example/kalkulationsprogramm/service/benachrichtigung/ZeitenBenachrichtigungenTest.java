package org.example.kalkulationsprogramm.service.benachrichtigung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.UeberschneidungErgebnis;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidungHandyZeit;
import org.example.kalkulationsprogramm.dto.Benachrichtigung.BenachrichtigungEintragDto;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.service.ZeitbuchungUeberschneidungService;
import org.example.kalkulationsprogramm.service.ueberschneidung.UeberschneidungStand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Glocke „Zeiten prüfen“: Überschneidungen Handy/Büro und Zeiten ohne Feierabend. */
@ExtendWith(MockitoExtension.class)
class ZeitenBenachrichtigungenTest {

    private static final LocalDateTime TAG = LocalDateTime.of(2026, 11, 18, 0, 0);

    @Mock private ZeitbuchungRepository zeitbuchungRepository;
    @Mock private ZeitbuchungUeberschneidungService ueberschneidungService;

    @InjectMocks private ZeitenBenachrichtigungen zeiten;

    private Mitarbeiter max;

    @BeforeEach
    void setUp() {
        max = new Mitarbeiter();
        max.setId(7L);
        max.setVorname("Max");
        max.setNachname("Mustermann");
    }

    private static LocalDateTime um(int stunde, int minute) {
        return TAG.withHour(stunde).withMinute(minute);
    }

    private Zeitbuchung buchung(long id, LocalDateTime start, LocalDateTime ende) {
        Zeitbuchung z = new Zeitbuchung();
        z.setId(id);
        z.setMitarbeiter(max);
        z.setStartZeit(start);
        z.setEndeZeit(ende);
        return z;
    }

    private static <T> BenachrichtigungEintragDto anzeige(BenachrichtigungService.Quelle<T> quelle) {
        return quelle.anzeige().apply(quelle.eintraege().getFirst());
    }

    @SuppressWarnings("unchecked")
    private static <T> BenachrichtigungService.Quelle<T> alsQuelle(BenachrichtigungService.Quelle<?> quelle) {
        return (BenachrichtigungService.Quelle<T>) quelle;
    }

    @Test
    @DisplayName("Ohne Feierabend: Link zum Tag, markiert die Buchung und was sie überlappt")
    void ohneFeierabendFuehrtZumTagUndNenntUeberschneidungen() {
        Zeitbuchung auto = buchung(1903L, um(16, 48), um(20, 0));
        auto.setAutomatischBeendet(true);
        Zeitbuchung buero = buchung(1910L, um(17, 0), um(18, 0));
        Zeitbuchung abends = buchung(1911L, um(19, 30), um(21, 0));
        given(zeitbuchungRepository.findByAutomatischBeendetTrueAndStartZeitAfterOrderByStartZeitDesc(
                any(LocalDateTime.class))).willReturn(List.of(auto));
        given(zeitbuchungRepository.findUeberlappendeVonMitarbeitern(List.of(7L), um(16, 48), um(20, 0)))
                .willReturn(List.of(auto, buero, abends));

        BenachrichtigungService.Quelle<Zeitbuchung> quelle = alsQuelle(zeiten.automatischBeendet());
        BenachrichtigungEintragDto eintrag = anzeige(quelle);

        assertThat(quelle.typ()).isEqualTo("ZEITEN_AUTO_BEENDET");
        assertThat(quelle.label()).isEqualTo("Zeiten ohne Feierabend");
        assertThat(quelle.link().apply(List.of(auto))).isEqualTo("/zeitbuchungen");
        assertThat(eintrag.id()).isEqualTo("ZEIT_AUTO_BEENDET:1903");
        assertThat(eintrag.title()).isEqualTo("Max Mustermann am 18.11.2026");
        assertThat(eintrag.subtitle()).isEqualTo("Kein Feierabend gestempelt, automatisch auf 20:00 Uhr gesetzt. "
                + "Überschneidet sich mit 2 anderen Einträgen.");
        assertThat(eintrag.link()).isEqualTo("/zeitbuchungen?mitarbeiterId=7&jahr=2026&monat=11&tag=2026-11-18"
                + "&anlass=ohne-feierabend&markiert=1903,1910,1911");
    }

    @Test
    @DisplayName("Ohne Feierabend: eine Sammelabfrage für alle Einträge, je Buchung im Speicher gefiltert")
    void ohneFeierabendEineAbfrageFuerAlleEintraege() {
        Mitarbeiter erika = new Mitarbeiter();
        erika.setId(8L);
        erika.setVorname("Erika");
        erika.setNachname("Musterfrau");
        Zeitbuchung autoMax = buchung(1903L, um(16, 48), um(20, 0));
        Zeitbuchung autoErika = buchung(2001L, um(15, 0).minusDays(1), um(20, 0).minusDays(1));
        autoErika.setMitarbeiter(erika);
        // Treffer der Sammelabfrage: überlappt Max (1910), liegt zwischen beiden (1920, Max, überlappt nicht),
        // gehört Erika und überlappt ihre Buchung (2010).
        Zeitbuchung maxBuero = buchung(1910L, um(17, 0), um(18, 0));
        Zeitbuchung maxFrueh = buchung(1920L, um(7, 0), um(12, 0));
        Zeitbuchung erikaBuero = buchung(2010L, um(19, 0).minusDays(1), um(21, 0).minusDays(1));
        erikaBuero.setMitarbeiter(erika);
        given(zeitbuchungRepository.findByAutomatischBeendetTrueAndStartZeitAfterOrderByStartZeitDesc(
                any(LocalDateTime.class))).willReturn(List.of(autoMax, autoErika));
        given(zeitbuchungRepository.findUeberlappendeVonMitarbeitern(anyCollection(),
                org.mockito.ArgumentMatchers.eq(um(15, 0).minusDays(1)), org.mockito.ArgumentMatchers.eq(um(20, 0))))
                .willReturn(List.of(erikaBuero, autoErika, maxFrueh, autoMax, maxBuero));

        BenachrichtigungService.Quelle<Zeitbuchung> quelle = alsQuelle(zeiten.automatischBeendet());
        List<BenachrichtigungEintragDto> eintraege = quelle.eintraege().stream().map(quelle.anzeige()).toList();

        assertThat(eintraege.get(0).subtitle()).endsWith(" Überschneidet sich mit einem anderen Eintrag.");
        assertThat(eintraege.get(0).link()).endsWith("&markiert=1903,1910");
        assertThat(eintraege.get(1).title()).isEqualTo("Erika Musterfrau am 17.11.2026");
        assertThat(eintraege.get(1).subtitle()).endsWith(" Überschneidet sich mit einem anderen Eintrag.");
        assertThat(eintraege.get(1).link()).endsWith("&markiert=2001,2010");
        verify(zeitbuchungRepository, times(1)).findUeberlappendeVonMitarbeitern(anyCollection(), any(), any());
        verify(zeitbuchungRepository, never()).findUeberlappende(any(), any(), any());
    }

    @Test
    void ohneFeierabendOhneUeberschneidungMarkiertNurSich() {
        Zeitbuchung auto = buchung(1903L, um(16, 48), um(20, 0));
        given(zeitbuchungRepository.findByAutomatischBeendetTrueAndStartZeitAfterOrderByStartZeitDesc(
                any(LocalDateTime.class))).willReturn(List.of(auto));
        given(zeitbuchungRepository.findUeberlappendeVonMitarbeitern(anyCollection(), any(), any()))
                .willReturn(List.of(auto));

        BenachrichtigungEintragDto ohne = anzeige(alsQuelle(zeiten.automatischBeendet()));

        assertThat(ohne.subtitle()).isEqualTo("Kein Feierabend gestempelt, automatisch auf 20:00 Uhr gesetzt.");
        assertThat(ohne.link()).endsWith("&anlass=ohne-feierabend&markiert=1903");
    }

    @Test
    void ohneFeierabendOhneEndeOderMitarbeiterBleibtLesbar() {
        Zeitbuchung auto = buchung(1903L, um(16, 48), null);
        auto.setMitarbeiter(null);
        given(zeitbuchungRepository.findByAutomatischBeendetTrueAndStartZeitAfterOrderByStartZeitDesc(
                any(LocalDateTime.class))).willReturn(List.of(auto));

        BenachrichtigungEintragDto eintrag = anzeige(alsQuelle(zeiten.automatischBeendet()));

        assertThat(eintrag.title()).isEqualTo("Mitarbeiter am 18.11.2026");
        assertThat(eintrag.subtitle()).isEqualTo("Kein Feierabend gestempelt, automatisch auf ? Uhr gesetzt.");
        assertThat(eintrag.link()).isEqualTo("/zeitbuchungen?jahr=2026&monat=11&tag=2026-11-18"
                + "&anlass=ohne-feierabend&markiert=1903");
        verify(zeitbuchungRepository, never()).findUeberlappendeVonMitarbeitern(any(), any(), any());
    }

    @Test
    @DisplayName("Überschneidung: einzelne Handy-Zeiten, Link markiert Büro-Eintrag, Rest und Handy-Buchungen")
    void ueberschneidungNenntHandyZeitenUndMarkiertVorhandeneBuchungen() {
        ZeitbuchungUeberschneidung u = new ZeitbuchungUeberschneidung();
        u.setId(5L);
        u.setMitarbeiter(max);
        u.setBueroBuchungId(10L);
        u.setRestBuchungId(11L);
        u.setBueroVon(um(10, 30));
        u.setBueroBis(um(15, 0));
        u.setBueroBezeichnung("Musterhaus · Montage");
        u.setErgebnis(UeberschneidungErgebnis.GETEILT);
        u.setHandyVon(um(11, 27));
        u.setHandyBis(um(13, 34));
        u.setGeaendertAm(TAG.plusDays(1).withHour(17).withMinute(41));
        ZeitbuchungUeberschneidungHandyZeit arbeit = handyZeit(u, 30L, BuchungsTyp.ARBEIT, um(11, 27), um(12, 0));
        ZeitbuchungUeberschneidungHandyZeit pause = handyZeit(u, 31L, BuchungsTyp.PAUSE, um(12, 0), um(13, 34));
        // Handy-Buchung 31 hat das Büro inzwischen gelöscht – sie wird nicht markiert.
        UeberschneidungStand stand = new UeberschneidungStand(Map.of(5L, List.of(arbeit, pause)), Map.of(
                10L, buchung(10L, um(10, 30), um(11, 27)),
                11L, buchung(11L, um(13, 34), um(15, 0)),
                30L, buchung(30L, um(11, 27), um(12, 0))));
        given(ueberschneidungService.fuerBuero()).willReturn(List.of(u));
        given(ueberschneidungService.stand(anyCollection())).willReturn(stand);

        BenachrichtigungService.Quelle<ZeitbuchungUeberschneidung> quelle = alsQuelle(zeiten.ueberschneidungen());
        BenachrichtigungEintragDto eintrag = anzeige(quelle);

        assertThat(quelle.typ()).isEqualTo("ZEITEN_UEBERSCHNEIDUNG");
        assertThat(quelle.label()).isEqualTo("Überschneidungen Handy/Büro");
        assertThat(eintrag.id()).isEqualTo("ZEIT_UEBERSCHNEIDUNG:5");
        assertThat(eintrag.title()).isEqualTo("Max Mustermann am 18.11.2026");
        assertThat(eintrag.subtitle()).isEqualTo("Vom Handy: Arbeit 11:27–12:00, Pause 12:00–13:34. Büro-Eintrag "
                + "„Musterhaus · Montage“ 10:30–15:00 → jetzt 10:30–11:27 und 13:34–15:00.");
        assertThat(eintrag.timestamp()).isEqualTo("2026-11-19T17:41");
        assertThat(eintrag.link()).isEqualTo("/zeitbuchungen?mitarbeiterId=7&jahr=2026&monat=11&tag=2026-11-18"
                + "&anlass=ueberschneidung&markiert=10,30,11");
        // Stand einmal für die ganze Liste, nicht je Meldung.
        verify(ueberschneidungService, times(1)).stand(anyCollection());
    }

    @Test
    void ueberschneidungAllesEntferntOhneMarkierung() {
        ZeitbuchungUeberschneidung u = new ZeitbuchungUeberschneidung();
        u.setId(6L);
        u.setMitarbeiter(max);
        u.setBueroBuchungId(10L);
        u.setBueroVon(um(12, 0));
        u.setBueroBis(um(13, 0));
        u.setBueroBezeichnung("Pause");
        u.setErgebnis(UeberschneidungErgebnis.ENTFERNT);
        u.setHandyVon(um(12, 0));
        u.setHandyBis(um(13, 34));
        u.setGeaendertAm(TAG);
        given(ueberschneidungService.fuerBuero()).willReturn(List.of(u));
        given(ueberschneidungService.stand(anyCollection())).willReturn(UeberschneidungStand.LEER);

        BenachrichtigungEintragDto eintrag = anzeige(alsQuelle(zeiten.ueberschneidungen()));

        assertThat(eintrag.subtitle()).isEqualTo("Vom Handy: 12:00–13:34. Büro-Eintrag „Pause“ 12:00–13:00 "
                + "wurde deshalb entfernt.");
        assertThat(eintrag.link()).isEqualTo("/zeitbuchungen?mitarbeiterId=7&jahr=2026&monat=11&tag=2026-11-18"
                + "&anlass=ueberschneidung");
    }

    private static ZeitbuchungUeberschneidungHandyZeit handyZeit(ZeitbuchungUeberschneidung u, Long id,
            BuchungsTyp art, LocalDateTime von, LocalDateTime bis) {
        ZeitbuchungUeberschneidungHandyZeit h = new ZeitbuchungUeberschneidungHandyZeit();
        h.setUeberschneidung(u);
        h.setHandyBuchungId(id);
        h.setArt(art);
        h.setVon(von);
        h.setBis(bis);
        return h;
    }
}
