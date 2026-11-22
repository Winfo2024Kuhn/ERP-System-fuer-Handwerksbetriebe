package org.example.kalkulationsprogramm.service.benachrichtigung;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.junit.jupiter.api.Test;

/** Link aus der Glocke direkt zum Tag im Zeitkalender, betroffene Buchungen markiert. */
class ZeitkalenderLinkTest {

    private static final LocalDate TAG = LocalDate.of(2026, 11, 18);

    private static Zeitbuchung buchung(Long id, int stunde, int minute) {
        Zeitbuchung z = new Zeitbuchung();
        z.setId(id);
        z.setStartZeit(TAG.atTime(stunde, minute));
        return z;
    }

    @Test
    void fuehrtZumTagUndMarkiertNachStartzeit() {
        String link = ZeitkalenderLink.zumTag(7L, TAG, ZeitkalenderLink.ANLASS_UEBERSCHNEIDUNG,
                List.of(buchung(30L, 13, 35), buchung(12L, 10, 30), buchung(31L, 11, 27)));

        assertThat(link).isEqualTo("/zeitbuchungen?mitarbeiterId=7&jahr=2026&monat=11&tag=2026-11-18"
                + "&anlass=ueberschneidung&markiert=12,31,30");
    }

    @Test
    void monatOhneFuehrendeNullUndOhneMarkierteFehltDerParameter() {
        String link = ZeitkalenderLink.zumTag(7L, LocalDate.of(2026, 3, 5), ZeitkalenderLink.ANLASS_OHNE_FEIERABEND,
                List.of());

        assertThat(link).isEqualTo("/zeitbuchungen?mitarbeiterId=7&jahr=2026&monat=3&tag=2026-03-05"
                + "&anlass=ohne-feierabend");
    }

    @Test
    void hoechstensZehnGueltigeIdsOhneDoppelte() {
        List<Zeitbuchung> viele = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            viele.add(buchung((long) i, 6 + i, 0));
        }
        viele.add(buchung(3L, 9, 0));
        viele.add(buchung(0L, 5, 0));
        viele.add(buchung(-4L, 5, 0));
        viele.add(buchung(null, 5, 0));
        viele.add(null);

        String link = ZeitkalenderLink.zumTag(7L, TAG, ZeitkalenderLink.ANLASS_UEBERSCHNEIDUNG, viele);

        assertThat(link).endsWith("&markiert=1,2,3,4,5,6,7,8,9,10");
    }

    @Test
    void gleicheStartzeitNachIdUndOhneStartzeitZuletzt() {
        Zeitbuchung ohneStart = buchung(2L, 0, 0);
        ohneStart.setStartZeit(null);

        String link = ZeitkalenderLink.zumTag(7L, TAG, ZeitkalenderLink.ANLASS_UEBERSCHNEIDUNG,
                List.of(ohneStart, buchung(9L, 8, 0), buchung(5L, 8, 0)));

        assertThat(link).endsWith("&markiert=5,9,2");
    }

    @Test
    void ohneMitarbeiterFehltNurDieserParameter() {
        assertThat(ZeitkalenderLink.zumTag(null, TAG, ZeitkalenderLink.ANLASS_OHNE_FEIERABEND, null))
                .isEqualTo("/zeitbuchungen?jahr=2026&monat=11&tag=2026-11-18&anlass=ohne-feierabend");
    }

    @Test
    void ohneTagNurDerMitarbeiterWieBisher() {
        assertThat(ZeitkalenderLink.zumTag(7L, null, ZeitkalenderLink.ANLASS_UEBERSCHNEIDUNG,
                List.of(buchung(1L, 8, 0)))).isEqualTo("/zeitbuchungen?mitarbeiterId=7");
    }
}
