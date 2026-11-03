package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.AENDERN;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.ANSEHEN;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.KEINE;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Sichtbarkeit des Ordnerbaums im Speicher: Rechte hängen am obersten Ordner, Unterordner erben. */
class FirmendokumentSichtbarkeitTest {

    private static FirmendokumentOrdner ordner(long id, String name, FirmendokumentOrdner parent, boolean eingeschraenkt) {
        FirmendokumentOrdner o = new FirmendokumentOrdner();
        o.setId(id);
        o.setName(name);
        o.setParent(parent);
        o.setRechteEingeschraenkt(eingeschraenkt);
        return o;
    }

    private final FirmendokumentOrdner pruefungen = ordner(1, "Prüfungen", null, false);
    private final FirmendokumentOrdner tore = ordner(2, "Tore", pruefungen, false);
    private final FirmendokumentOrdner versicherungen = ordner(3, "Versicherungen", null, true);
    private final FirmendokumentOrdner haftpflicht = ordner(4, "Haftpflicht", versicherungen, false);
    private final FirmendokumentOrdner jahr2026 = ordner(5, "2026", haftpflicht, false);
    private final FirmendokumentOrdner geheim = ordner(6, "Geheim", null, true);
    private final List<FirmendokumentOrdner> alle = List.of(pruefungen, tore, versicherungen, haftpflicht, jahr2026, geheim);

    @Test
    @DisplayName("Alles offen: jeder Ordner sichtbar und änderbar, nichts eingeschränkt")
    void alles() {
        FirmendokumentSichtbarkeit s = FirmendokumentSichtbarkeit.alles(List.of(pruefungen, tore));

        assertThat(s.allesSichtbar()).isTrue();
        assertThat(s.stufe(2L)).isEqualTo(AENDERN);
        assertThat(s.stufe(999L)).isEqualTo(AENDERN);
        assertThat(s.eingeschraenkt(2L)).isFalse();
        assertThat(s.sichtbareOrdner()).containsExactly(pruefungen, tore);
    }

    @Test
    @DisplayName("Admin sieht und ändert alles, auch eingeschränkte Ordner – das Schloss wird trotzdem angezeigt")
    void admin() {
        FirmendokumentSichtbarkeit s = FirmendokumentSichtbarkeit.berechne(alle, true, Map.of(), true);

        assertThat(s.allesSichtbar()).isTrue();
        assertThat(s.stufe(6L)).isEqualTo(AENDERN);
        assertThat(s.eingeschraenkt(6L)).isTrue();
        assertThat(s.eingeschraenkt(5L)).isTrue();
        assertThat(s.eingeschraenkt(2L)).isFalse();
        assertThat(s.sichtbareOrdner()).hasSize(6);
    }

    @Test
    @DisplayName("Werkstatt darf ansehen: eingeschränkter Zweig nur lesend, bis in tiefe Unterordner; ohne Eintrag unsichtbar")
    void ansehenErbt() {
        FirmendokumentSichtbarkeit s = FirmendokumentSichtbarkeit.berechne(alle, false, Map.of(3L, ANSEHEN), true);

        assertThat(s.stufe(1L)).isEqualTo(AENDERN);
        assertThat(s.stufe(2L)).isEqualTo(AENDERN);
        assertThat(s.stufe(3L)).isEqualTo(ANSEHEN);
        assertThat(s.stufe(4L)).isEqualTo(ANSEHEN);
        assertThat(s.stufe(5L)).isEqualTo(ANSEHEN);
        assertThat(s.stufe(6L)).isEqualTo(KEINE);
        assertThat(s.allesSichtbar()).isFalse();
        assertThat(s.sichtbareOrdner()).containsExactly(pruefungen, tore, versicherungen, haftpflicht, jahr2026);
        assertThat(s.sichtbareOrdnerIds()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);
        assertThat(s.stufe(999L)).isEqualTo(KEINE);
    }

    @Test
    @DisplayName("Büro darf ändern: eingeschränkter Zweig voll bearbeitbar")
    void aendern() {
        FirmendokumentSichtbarkeit s = FirmendokumentSichtbarkeit.berechne(alle, false, Map.of(3L, AENDERN), true);

        assertThat(s.stufe(5L)).isEqualTo(AENDERN);
        assertThat(s.stufe(6L)).isEqualTo(KEINE);
    }

    @Test
    @DisplayName("Sind alle Ordner sichtbar, gilt „alles sichtbar“ – aber nicht, wenn die Liste abgeschnitten ist")
    void allesSichtbarNurVollstaendig() {
        Map<Long, org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe> stufen =
                Map.of(3L, ANSEHEN, 6L, AENDERN);
        assertThat(FirmendokumentSichtbarkeit.berechne(alle, false, stufen, true).allesSichtbar()).isTrue();
        assertThat(FirmendokumentSichtbarkeit.berechne(alle, false, stufen, false).allesSichtbar()).isFalse();
    }

    @Test
    @DisplayName("Kaputte Daten: Zyklus oder fehlender Elternordner → für Nicht-Admins unsichtbar, keine Endlosschleife")
    void kaputteDaten() {
        FirmendokumentOrdner a = ordner(10, "A", null, false);
        FirmendokumentOrdner b = ordner(11, "B", a, false);
        a.setParent(b);
        FirmendokumentOrdner fremderParent = ordner(99, "nicht geladen", null, false);
        FirmendokumentOrdner waise = ordner(12, "Waise", fremderParent, false);

        FirmendokumentSichtbarkeit s = FirmendokumentSichtbarkeit.berechne(List.of(a, b, waise), false, Map.of(), false);

        assertThat(s.stufe(10L)).isEqualTo(KEINE);
        assertThat(s.stufe(11L)).isEqualTo(KEINE);
        assertThat(s.stufe(12L)).isEqualTo(KEINE);
        assertThat(FirmendokumentSichtbarkeit.berechne(List.of(a, b, waise), true, Map.of(), false).stufe(12L))
                .isEqualTo(AENDERN);
    }
}
