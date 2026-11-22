package org.example.kalkulationsprogramm.service.ueberschneidung;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.UeberschneidungErgebnis;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidungHandyZeit;
import org.junit.jupiter.api.Test;

/**
 * Texte der Überschneidungs-Meldungen. Echter Fall (mit Dummy-Namen): Büro trägt den 18.11.
 * von Hand ein, das Handy schickt am Folgetag Arbeit 11:27–12:00 und Pause 12:00–13:34.
 */
class UeberschneidungAnzeigeTest {

    private static final LocalDateTime TAG = LocalDateTime.of(2026, 11, 18, 0, 0);

    private final Map<Long, List<ZeitbuchungUeberschneidungHandyZeit>> handyZeiten = new HashMap<>();
    private final Map<Long, Zeitbuchung> buchungen = new HashMap<>();

    private static LocalDateTime um(int stunde, int minute) {
        return TAG.withHour(stunde).withMinute(minute);
    }

    private static ZeitbuchungUeberschneidung meldung(String bezeichnung, LocalDateTime von, LocalDateTime bis,
            UeberschneidungErgebnis ergebnis) {
        Mitarbeiter max = new Mitarbeiter();
        max.setId(7L);
        max.setVorname("Max");
        max.setNachname("Mustermann");
        ZeitbuchungUeberschneidung u = new ZeitbuchungUeberschneidung();
        u.setId(1L);
        u.setMitarbeiter(max);
        u.setBueroBuchungId(10L);
        u.setBueroVon(von);
        u.setBueroBis(bis);
        u.setBueroBezeichnung(bezeichnung);
        u.setErgebnis(ergebnis);
        u.setHandyVon(um(11, 27));
        u.setHandyBis(um(13, 34));
        return u;
    }

    private void handy(long id, BuchungsTyp art, LocalDateTime von, LocalDateTime bis) {
        ZeitbuchungUeberschneidungHandyZeit h = new ZeitbuchungUeberschneidungHandyZeit();
        h.setHandyBuchungId(id);
        h.setArt(art);
        h.setVon(von);
        h.setBis(bis);
        handyZeiten.computeIfAbsent(1L, k -> new java.util.ArrayList<>()).add(h);
    }

    private void jetzt(long id, LocalDateTime von, LocalDateTime bis) {
        Zeitbuchung z = new Zeitbuchung();
        z.setId(id);
        z.setStartZeit(von);
        z.setEndeZeit(bis);
        buchungen.put(id, z);
    }

    private UeberschneidungStand stand() {
        return new UeberschneidungStand(handyZeiten, buchungen);
    }

    @Test
    void bueroSiehtJedeHandyZeitEinzelnUndDassDiePauseEntferntWurde() {
        ZeitbuchungUeberschneidung u = meldung("Pause", um(12, 0), um(13, 0), UeberschneidungErgebnis.ENTFERNT);
        handy(31, BuchungsTyp.PAUSE, um(12, 0), um(13, 34));
        handy(30, BuchungsTyp.ARBEIT, um(11, 27), um(12, 0));

        UeberschneidungAnzeige.Anzeige anzeige = UeberschneidungAnzeige.fuerBuero(u, stand());

        assertThat(anzeige.titel()).isEqualTo("Max Mustermann am 18.11.2026");
        assertThat(anzeige.text()).isEqualTo("Vom Handy: Arbeit 11:27–12:00, Pause 12:00–13:34. "
                + "Büro-Eintrag „Pause“ 12:00–13:00 wurde deshalb entfernt.");
    }

    @Test
    void gekuerzterEintragNenntDenNeuenStand() {
        ZeitbuchungUeberschneidung u = meldung("Musterhaus · Montage", um(10, 30), um(12, 0),
                UeberschneidungErgebnis.GEKUERZT);
        handy(30, BuchungsTyp.ARBEIT, um(11, 27), um(12, 0));
        jetzt(10, um(10, 30), um(11, 27));

        assertThat(UeberschneidungAnzeige.fuerBuero(u, stand()).text()).isEqualTo("Vom Handy: Arbeit 11:27–12:00. "
                + "Büro-Eintrag „Musterhaus · Montage“ 10:30–12:00 → jetzt 10:30–11:27.");
    }

    @Test
    void handySiehtDeineHandyZeitenUndGeteiltenStand() {
        ZeitbuchungUeberschneidung u = meldung("Musterhaus · Montage", um(7, 0), um(16, 0),
                UeberschneidungErgebnis.GETEILT);
        u.setRestBuchungId(11L);
        handy(30, BuchungsTyp.ARBEIT, um(10, 0), um(12, 0));
        jetzt(10, um(7, 0), um(10, 0));
        jetzt(11, um(12, 0), um(16, 0));

        UeberschneidungAnzeige.Anzeige anzeige = UeberschneidungAnzeige.fuerMitarbeiter(u, stand());

        assertThat(anzeige.titel()).isEqualTo("Überschneidung am 18.11.2026");
        assertThat(anzeige.text()).isEqualTo("Deine Handy-Zeiten: Arbeit 10:00–12:00. Büro-Eintrag "
                + "„Musterhaus · Montage“ 07:00–16:00 → jetzt 07:00–10:00 und 12:00–16:00.");
    }

    @Test
    void hoechstensDreiHandyZeitenDannUndWeitere() {
        ZeitbuchungUeberschneidung u = meldung(null, um(6, 0), um(18, 0), UeberschneidungErgebnis.ENTFERNT);
        handy(1, BuchungsTyp.ARBEIT, um(7, 0), um(8, 0));
        handy(2, BuchungsTyp.PAUSE, um(8, 0), um(8, 15));
        handy(3, BuchungsTyp.ARBEIT, um(8, 15), um(12, 0));
        handy(4, BuchungsTyp.PAUSE, um(12, 0), um(12, 30));
        handy(5, BuchungsTyp.ARBEIT, um(12, 30), null);

        assertThat(UeberschneidungAnzeige.fuerBuero(u, stand()).text()).isEqualTo("Vom Handy: Arbeit 07:00–08:00, "
                + "Pause 08:00–08:15, Arbeit 08:15–12:00 und 2 weitere. Büro-Eintrag 06:00–18:00 wurde deshalb entfernt.");
    }

    @Test
    void offenesEndeUndUeberMitternacht() {
        ZeitbuchungUeberschneidung u = meldung(null, um(6, 0), null, UeberschneidungErgebnis.BEENDET);
        handy(1, BuchungsTyp.ARBEIT, um(22, 0), um(22, 0).plusHours(3));
        handy(2, null, um(7, 0), null);
        jetzt(10, um(6, 0), um(7, 0));

        assertThat(UeberschneidungAnzeige.fuerBuero(u, stand()).text()).isEqualTo("Vom Handy: Arbeit ab 07:00, "
                + "Arbeit 22:00–19.11.2026 01:00. Büro-Eintrag ab 06:00 → jetzt 06:00–07:00.");
    }

    @Test
    void altmeldungOhneFestgehalteneZeitenNenntDieSpanne() {
        ZeitbuchungUeberschneidung u = meldung("Pause", um(12, 0), um(13, 0), UeberschneidungErgebnis.ENTFERNT);

        assertThat(UeberschneidungAnzeige.fuerBuero(u, stand()).text()).isEqualTo("Vom Handy: 11:27–13:34. "
                + "Büro-Eintrag „Pause“ 12:00–13:00 wurde deshalb entfernt.");
        u.setHandyBis(null);
        assertThat(UeberschneidungAnzeige.fuerMitarbeiter(u, UeberschneidungStand.LEER).text())
                .startsWith("Deine Handy-Zeiten: ab 11:27. ");
    }

    @Test
    void sonderfaelleBehaltenIhrenWortlaut() {
        ZeitbuchungUeberschneidung abgeschlossen = meldung("Pause", um(12, 0), um(13, 0),
                UeberschneidungErgebnis.NICHT_GEAENDERT);
        ZeitbuchungUeberschneidung belegt = meldung("Pause", um(12, 0), um(13, 0), UeberschneidungErgebnis.ZEIT_BELEGT);
        handy(30, BuchungsTyp.PAUSE, um(12, 0), um(13, 34));

        assertThat(UeberschneidungAnzeige.fuerBuero(abgeschlossen, stand()).text()).isEqualTo(
                "Vom Handy: Pause 12:00–13:34. Büro-Eintrag „Pause“ 12:00–13:00 wurde nicht geändert, "
                        + "weil der Monat schon abgeschlossen ist. Bitte im Büro klären.");
        assertThat(UeberschneidungAnzeige.fuerBuero(belegt, stand()).text()).isEqualTo(
                "Vom Handy: Pause 12:00–13:34. Büro-Eintrag „Pause“ 12:00–13:00 wurde nicht geändert, "
                        + "weil danach schon ein anderer Eintrag beginnt. Bitte im Büro klären.");
    }

    @Test
    void ohneMitarbeiterHeisstEsMitarbeiter() {
        ZeitbuchungUeberschneidung u = meldung("Pause", um(12, 0), um(13, 0), UeberschneidungErgebnis.ENTFERNT);
        u.setMitarbeiter(null);

        assertThat(UeberschneidungAnzeige.fuerBuero(u, stand()).titel()).isEqualTo("Mitarbeiter am 18.11.2026");
    }

    @Test
    void bezeichnungAusProjektUndArbeitsgangOderPause() {
        Zeitbuchung z = new Zeitbuchung();
        z.setTyp(BuchungsTyp.ARBEIT);
        assertThat(UeberschneidungAnzeige.bezeichnung(z)).isNull();
        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Musterhaus");
        z.setProjekt(projekt);
        Arbeitsgang ag = new Arbeitsgang();
        ag.setBeschreibung("Montage");
        z.setArbeitsgang(ag);
        assertThat(UeberschneidungAnzeige.bezeichnung(z)).isEqualTo("Musterhaus · Montage");
        projekt.setBauvorhaben("x".repeat(600));
        assertThat(UeberschneidungAnzeige.bezeichnung(z)).hasSize(500);
        z.setTyp(BuchungsTyp.PAUSE);
        assertThat(UeberschneidungAnzeige.bezeichnung(z)).isEqualTo("Pause");
    }

    @Test
    void zeitraumUeberMitternachtNenntDasDatum() {
        assertThat(UeberschneidungAnzeige.zeitraum(um(22, 0), um(22, 0).plusHours(3)))
                .isEqualTo("22:00–19.11.2026 01:00");
        assertThat(UeberschneidungAnzeige.zeitraum(um(7, 0), null)).isEqualTo("ab 07:00");
        assertThat(UeberschneidungAnzeige.zeitraum(null, null)).isEmpty();
    }
}
