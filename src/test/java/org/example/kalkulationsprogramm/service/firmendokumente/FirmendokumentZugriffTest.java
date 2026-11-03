package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.AENDERN;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.ANSEHEN;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.KEINE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdner;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdnerRecht;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRechtRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRepository;
import org.example.kalkulationsprogramm.service.AbteilungsRechtPruefung;
import org.example.kalkulationsprogramm.service.AbteilungsRechtPruefung.Zugehoerigkeit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/** Wer darf welchen Ordner sehen bzw. ändern? Dummy-Abteilungen „Büro“ (10) und „Werkstatt“ (11). */
@ExtendWith(MockitoExtension.class)
class FirmendokumentZugriffTest {

    @Mock AbteilungsRechtPruefung rechtPruefung;
    @Mock FirmendokumentOrdnerRepository ordnerRepository;
    @Mock FirmendokumentOrdnerRechtRepository rechtRepository;

    private final Authentication max = mock(Authentication.class);
    private FirmendokumentZugriff zugriff;

    private final FirmendokumentOrdner pruefungen = ordner(1, null, false);
    private final FirmendokumentOrdner versicherungen = ordner(3, null, true);
    private final FirmendokumentOrdner haftpflicht = ordner(4, versicherungen, false);
    private final FirmendokumentOrdner jahr2026 = ordner(5, haftpflicht, false);

    @BeforeEach
    void setUp() {
        zugriff = new FirmendokumentZugriff(rechtPruefung, ordnerRepository, rechtRepository);
    }

    private static FirmendokumentOrdner ordner(long id, FirmendokumentOrdner parent, boolean eingeschraenkt) {
        FirmendokumentOrdner o = new FirmendokumentOrdner();
        o.setId(id);
        o.setName("Ordner " + id);
        o.setParent(parent);
        o.setRechteEingeschraenkt(eingeschraenkt);
        return o;
    }

    private static FirmendokumentOrdnerRecht recht(FirmendokumentOrdner ordner, long abteilungId,
            FirmendokumentZugriffsStufe stufe) {
        Abteilung a = new Abteilung();
        a.setId(abteilungId);
        FirmendokumentOrdnerRecht r = new FirmendokumentOrdnerRecht();
        r.setOrdner(ordner);
        r.setAbteilung(a);
        r.setStufe(stufe);
        return r;
    }

    private void angemeldetIn(Long... abteilungIds) {
        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(false, Set.of(abteilungIds)));
    }

    @Test
    @DisplayName("Nicht eingeschränkter Zweig: alle dürfen ändern, ohne dass Profil oder Rechte geladen werden")
    void nichtEingeschraenkt() {
        assertThat(zugriff.stufe(pruefungen, max)).isEqualTo(AENDERN);
        verify(rechtPruefung, never()).zugehoerigkeit(any());
        verify(rechtRepository, never()).findeFuerOrdnerUndAbteilungen(anyLong(), any());
    }

    @Test
    @DisplayName("Admin darf in eingeschränkten Ordnern immer ändern")
    void admin() {
        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(true, Set.of()));

        assertThat(zugriff.stufe(jahr2026, max)).isEqualTo(AENDERN);
        verify(rechtRepository, never()).findeFuerOrdnerUndAbteilungen(anyLong(), any());
    }

    @Test
    @DisplayName("Werkstatt mit ANSEHEN: tiefer Unterordner erbt vom obersten Ordner")
    void ansehenErbt() {
        angemeldetIn(11L);
        given(rechtRepository.findeFuerOrdnerUndAbteilungen(3L, Set.of(11L)))
                .willReturn(List.of(recht(versicherungen, 11, ANSEHEN)));

        assertThat(zugriff.stufe(jahr2026, max)).isEqualTo(ANSEHEN);
    }

    @Test
    @DisplayName("In Büro (AENDERN) und Werkstatt (ANSEHEN): die höchste Stufe gilt")
    void mehrereAbteilungen() {
        angemeldetIn(10L, 11L);
        given(rechtRepository.findeFuerOrdnerUndAbteilungen(3L, Set.of(10L, 11L)))
                .willReturn(List.of(recht(versicherungen, 11, ANSEHEN), recht(versicherungen, 10, AENDERN)));

        assertThat(zugriff.stufe(haftpflicht, max)).isEqualTo(AENDERN);
    }

    @Test
    @DisplayName("Abteilung ohne Eintrag sieht nichts; ohne Mitarbeiter/Abteilung wird gar nicht erst gesucht")
    void ohneEintrag() {
        angemeldetIn(12L);
        given(rechtRepository.findeFuerOrdnerUndAbteilungen(3L, Set.of(12L))).willReturn(List.of());
        assertThat(zugriff.stufe(versicherungen, max)).isEqualTo(KEINE);

        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(false, Set.of()));
        assertThat(zugriff.stufe(versicherungen, max)).isEqualTo(KEINE);
        verify(rechtRepository).findeFuerOrdnerUndAbteilungen(anyLong(), any());
    }

    @Test
    @DisplayName("Kaputte Elternkette (Zyklus): keine Endlosschleife, für Nicht-Admins unsichtbar")
    void zyklus() {
        FirmendokumentOrdner a = ordner(20, null, false);
        FirmendokumentOrdner b = ordner(21, a, false);
        a.setParent(b);
        angemeldetIn(10L);

        assertThat(zugriff.stufe(b, max)).isEqualTo(KEINE);
        assertThat(FirmendokumentZugriff.wurzel(b)).isNull();
        assertThat(FirmendokumentZugriff.wurzel(jahr2026)).isSameAs(versicherungen);
    }

    @Test
    @DisplayName("verlangeAnsehen/verlangeAendern: unsichtbar → Fehler des Aufrufers, nur ansehen → 403 mit Meldung")
    void verlangen() {
        angemeldetIn(11L);
        given(rechtRepository.findeFuerOrdnerUndAbteilungen(3L, Set.of(11L)))
                .willReturn(List.of(recht(versicherungen, 11, ANSEHEN)));

        assertThat(zugriff.verlangeAnsehen(haftpflicht, max, () -> FirmendokumentEingaben.nichtGefunden("weg")))
                .isEqualTo(ANSEHEN);
        assertThatThrownBy(() -> zugriff.verlangeAendern(haftpflicht, max, () -> FirmendokumentEingaben.fehler("weg")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(403))
                .hasMessageContaining("Du darfst in diesem Ordner nichts ändern.");

        given(rechtRepository.findeFuerOrdnerUndAbteilungen(3L, Set.of(11L))).willReturn(List.of());
        assertThatThrownBy(() -> zugriff.verlangeAnsehen(haftpflicht, max,
                () -> FirmendokumentEingaben.nichtGefunden("Den Ordner gibt es nicht mehr.")))
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> zugriff.verlangeAendern(haftpflicht, max,
                () -> FirmendokumentEingaben.fehler("Den Ordner gibt es nicht mehr.")))
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    @DisplayName("Verschieben aus eingeschränktem Zweig in einen anderen Hauptordner: nur Admin, sonst 403")
    void verschiebenAusEingeschraenktemZweig() {
        FirmendokumentOrdner geheim = ordner(6, null, true);
        angemeldetIn(10L);
        String meldung = "Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben.";

        assertThatThrownBy(() -> zugriff.verlangeVerschieben(versicherungen, pruefungen, max))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(403))
                .hasMessageContaining(meldung);
        assertThatThrownBy(() -> zugriff.verlangeVerschieben(jahr2026, pruefungen, max)).hasMessageContaining(meldung);
        assertThatThrownBy(() -> zugriff.verlangeVerschieben(haftpflicht, geheim, max)).hasMessageContaining(meldung);

        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(true, Set.of()));
        zugriff.verlangeVerschieben(versicherungen, pruefungen, max);
    }

    @Test
    @DisplayName("Verschieben erlaubt: innerhalb desselben Zweigs, Unterordner nach ganz oben, aus offenem Zweig")
    void verschiebenErlaubt() {
        zugriff.verlangeVerschieben(jahr2026, versicherungen, max);
        zugriff.verlangeVerschieben(jahr2026, haftpflicht, max);
        zugriff.verlangeVerschieben(haftpflicht, null, max);
        zugriff.verlangeVerschieben(pruefungen, versicherungen, max);
        zugriff.verlangeVerschieben(pruefungen, null, max);
        verify(rechtPruefung, never()).zugehoerigkeit(any());
    }

    @Test
    @DisplayName("Sichtbarkeit des Baums: ohne Einschränkung alles sichtbar, ohne Profil- oder Rechte-Abfrage")
    void sichtbarkeitOffen() {
        given(ordnerRepository.findeSortiert(PageRequest.of(0, FirmendokumentZugriff.MAX_ORDNER)))
                .willReturn(List.of(pruefungen));

        FirmendokumentSichtbarkeit s = zugriff.sichtbarkeit(max);

        assertThat(s.allesSichtbar()).isTrue();
        assertThat(s.sichtbareOrdner()).containsExactly(pruefungen);
        verify(rechtPruefung, never()).zugehoerigkeit(any());
    }

    @Test
    @DisplayName("Sichtbarkeit des Baums: Rechte der eigenen Abteilungen werden einmal geladen, höchste Stufe je Wurzel")
    void sichtbarkeitEingeschraenkt() {
        given(ordnerRepository.findeSortiert(PageRequest.of(0, FirmendokumentZugriff.MAX_ORDNER)))
                .willReturn(List.of(pruefungen, versicherungen, haftpflicht, jahr2026));
        angemeldetIn(10L, 11L);
        given(rechtRepository.findeFuerAbteilungen(Set.of(10L, 11L)))
                .willReturn(List.of(recht(versicherungen, 11, ANSEHEN), recht(versicherungen, 10, AENDERN)));

        FirmendokumentSichtbarkeit s = zugriff.sichtbarkeit(max);

        assertThat(s.stufe(5L)).isEqualTo(AENDERN);
        assertThat(s.eingeschraenkt(5L)).isTrue();
        assertThat(s.allesSichtbar()).isTrue();
    }

    @Test
    @DisplayName("Sichtbarkeit: ohne Abteilungen keine Rechte-Abfrage; abgeschnittene Liste gilt nie als „alles sichtbar“")
    void sichtbarkeitOhneAbteilung() {
        List<FirmendokumentOrdner> voll = new ArrayList<>();
        for (long id = 100; id < 100 + FirmendokumentZugriff.MAX_ORDNER; id++) {
            voll.add(ordner(id, null, false));
        }
        given(ordnerRepository.findeSortiert(PageRequest.of(0, FirmendokumentZugriff.MAX_ORDNER))).willReturn(voll);
        angemeldetIn();

        FirmendokumentSichtbarkeit s = zugriff.sichtbarkeit(max);

        assertThat(s.allesSichtbar()).isFalse();
        assertThat(s.stufe(100L)).isEqualTo(AENDERN);
        verify(rechtRepository, never()).findeFuerAbteilungen(any());
    }
}
