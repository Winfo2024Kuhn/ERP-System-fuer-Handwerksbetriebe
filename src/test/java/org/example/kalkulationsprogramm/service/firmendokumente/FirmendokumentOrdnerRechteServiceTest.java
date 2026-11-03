package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.AENDERN;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.ANSEHEN;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.KEINE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdner;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdnerRecht;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentAbteilungsRechtDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerRechteDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerRechteSpeichernDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerRechteSpeichernDto.Eintrag;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRechtRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

/** Rechte am obersten Ordner laden, speichern und beim Verschieben/Löschen mitnehmen. Dummy-Abteilungen. */
@ExtendWith(MockitoExtension.class)
class FirmendokumentOrdnerRechteServiceTest {

    @Mock FirmendokumentOrdnerRepository ordnerRepository;
    @Mock FirmendokumentOrdnerRechtRepository rechtRepository;
    @Mock AbteilungRepository abteilungRepository;

    private FirmendokumentOrdnerRechteService service;

    private final Abteilung buero = abteilung(10, "Büro");
    private final Abteilung werkstatt = abteilung(11, "Werkstatt");
    private final Abteilung montage = abteilung(12, "Montage");
    private final FirmendokumentOrdner versicherungen = ordner(3, "Versicherungen", null, true);
    private final FirmendokumentOrdner haftpflicht = ordner(4, "Haftpflicht", versicherungen, false);

    @BeforeEach
    void setUp() {
        service = new FirmendokumentOrdnerRechteService(ordnerRepository, rechtRepository, abteilungRepository);
    }

    private static Abteilung abteilung(long id, String name) {
        Abteilung a = new Abteilung();
        a.setId(id);
        a.setName(name);
        return a;
    }

    private static FirmendokumentOrdner ordner(long id, String name, FirmendokumentOrdner parent, boolean eingeschraenkt) {
        FirmendokumentOrdner o = new FirmendokumentOrdner();
        o.setId(id);
        o.setName(name);
        o.setParent(parent);
        o.setRechteEingeschraenkt(eingeschraenkt);
        return o;
    }

    private static FirmendokumentOrdnerRecht recht(FirmendokumentOrdner o, Abteilung a, FirmendokumentZugriffsStufe s) {
        FirmendokumentOrdnerRecht r = new FirmendokumentOrdnerRecht();
        r.setOrdner(o);
        r.setAbteilung(a);
        r.setStufe(s);
        return r;
    }

    private static int status(Throwable t) {
        return ((ResponseStatusException) t).getStatusCode().value();
    }

    private void alleAbteilungen() {
        given(abteilungRepository.findeSortiert(PageRequest.of(0, FirmendokumentOrdnerRechteService.MAX_ABTEILUNGEN)))
                .willReturn(List.of(buero, montage, werkstatt));
    }

    @Test
    @DisplayName("Laden: alle Abteilungen nach Name, ohne Eintrag KEINE")
    void laden() {
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        alleAbteilungen();
        given(rechtRepository.findeFuerOrdner(3L)).willReturn(List.of(recht(versicherungen, buero, AENDERN),
                recht(versicherungen, werkstatt, ANSEHEN)));

        FirmendokumentOrdnerRechteDto dto = service.laden(3L);

        assertThat(dto).isEqualTo(new FirmendokumentOrdnerRechteDto(3L, "Versicherungen", true, List.of(
                new FirmendokumentAbteilungsRechtDto(10L, "Büro", AENDERN),
                new FirmendokumentAbteilungsRechtDto(12L, "Montage", KEINE),
                new FirmendokumentAbteilungsRechtDto(11L, "Werkstatt", ANSEHEN))));
    }

    @Test
    @DisplayName("Laden/Speichern: unbekannter Ordner → 404, Unterordner → 400 mit Hinweis auf den obersten Ordner")
    void ladenFehler() {
        for (long id : new long[] { 0L, -1L, Long.MAX_VALUE }) {
            given(ordnerRepository.findById(id)).willReturn(Optional.empty());
            assertThatThrownBy(() -> service.laden(id)).satisfies(t -> assertThat(status(t)).isEqualTo(404))
                    .hasMessageContaining("Den Ordner gibt es nicht mehr.");
        }
        given(ordnerRepository.findById(4L)).willReturn(Optional.of(haftpflicht));
        assertThatThrownBy(() -> service.laden(4L)).satisfies(t -> assertThat(status(t)).isEqualTo(400))
                .hasMessageContaining("Rechte werden am obersten Ordner eingestellt. Sie gelten auch für alle Unterordner.");
        assertThatThrownBy(() -> service.speichern(4L, new FirmendokumentOrdnerRechteSpeichernDto(true, List.of())))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400));
    }

    @Test
    @DisplayName("Speichern: bestehenden Eintrag ändern, neuen anlegen, KEINE bzw. fehlende Abteilung löschen")
    void speichern() {
        FirmendokumentOrdner pruefungen = ordner(1, "Prüfungen", null, false);
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(pruefungen));
        FirmendokumentOrdnerRecht bueroAlt = recht(pruefungen, buero, ANSEHEN);
        FirmendokumentOrdnerRecht montageAlt = recht(pruefungen, montage, AENDERN);
        given(rechtRepository.findeFuerOrdner(1L)).willReturn(List.of(bueroAlt, montageAlt));
        given(abteilungRepository.findAllById(any())).willAnswer(i -> {
            List<Abteilung> treffer = new ArrayList<>();
            for (Long id : i.<Collection<Long>>getArgument(0)) {
                List.of(buero, werkstatt, montage).stream().filter(a -> a.getId().equals(id)).forEach(treffer::add);
            }
            return treffer;
        });
        alleAbteilungen();

        service.speichern(1L, new FirmendokumentOrdnerRechteSpeichernDto(true, List.of(
                new Eintrag(10L, "AENDERN"), new Eintrag(11L, "ANSEHEN"), new Eintrag(12L, "KEINE"))));

        assertThat(pruefungen.isRechteEingeschraenkt()).isTrue();
        assertThat(bueroAlt.getStufe()).isEqualTo(AENDERN);
        verify(rechtRepository).delete(montageAlt);
        ArgumentCaptor<FirmendokumentOrdnerRecht> neu = ArgumentCaptor.forClass(FirmendokumentOrdnerRecht.class);
        verify(rechtRepository).save(neu.capture());
        assertThat(neu.getValue().getAbteilung()).isSameAs(werkstatt);
        assertThat(neu.getValue().getOrdner()).isSameAs(pruefungen);
        assertThat(neu.getValue().getStufe()).isEqualTo(ANSEHEN);
    }

    @Test
    @DisplayName("Speichern mit eingeschraenkt=false: Einträge bleiben für später erhalten, wirken aber nicht")
    void speichernAusgeschaltet() {
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        FirmendokumentOrdnerRecht bueroAlt = recht(versicherungen, buero, AENDERN);
        given(rechtRepository.findeFuerOrdner(3L)).willReturn(List.of(bueroAlt));
        given(abteilungRepository.findAllById(any())).willReturn(List.of(buero));
        alleAbteilungen();

        FirmendokumentOrdnerRechteDto dto = service.speichern(3L,
                new FirmendokumentOrdnerRechteSpeichernDto(false, List.of(new Eintrag(10L, "AENDERN"))));

        assertThat(versicherungen.isRechteEingeschraenkt()).isFalse();
        assertThat(dto.eingeschraenkt()).isFalse();
        verify(rechtRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Speichern: unbekannte oder fehlende Abteilung, falsche Stufe, doppelte Abteilung, fehlendes Flag → 400")
    void speichernFehler() {
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        given(abteilungRepository.findAllById(any())).willReturn(List.of());

        assertThatThrownBy(() -> service.speichern(3L, new FirmendokumentOrdnerRechteSpeichernDto(true,
                List.of(new Eintrag(Long.MAX_VALUE, "ANSEHEN")))))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400)).hasMessageContaining("Die Abteilung gibt es nicht mehr.");
        assertThatThrownBy(() -> service.speichern(3L, new FirmendokumentOrdnerRechteSpeichernDto(true,
                List.of(new Eintrag(null, "ANSEHEN"))))).hasMessageContaining("Die Abteilung gibt es nicht mehr.");
        List<Eintrag> mitNull = new ArrayList<>();
        mitNull.add(null);
        assertThatThrownBy(() -> service.speichern(3L, new FirmendokumentOrdnerRechteSpeichernDto(true, mitNull)))
                .hasMessageContaining("Die Abteilung gibt es nicht mehr.");
        for (String stufe : new String[] { "LESEN", "'; DROP TABLE abteilung; --", "<script>alert(1)</script>", "", null }) {
            assertThatThrownBy(() -> service.speichern(3L, new FirmendokumentOrdnerRechteSpeichernDto(true,
                    List.of(new Eintrag(10L, stufe)))))
                    .satisfies(t -> assertThat(status(t)).isEqualTo(400))
                    .hasMessageContaining("Bitte für jede Abteilung „Kein Zugriff“, „Ansehen“ oder „Ändern“ wählen.");
        }
        assertThatThrownBy(() -> service.speichern(3L, new FirmendokumentOrdnerRechteSpeichernDto(true,
                List.of(new Eintrag(10L, "ANSEHEN"), new Eintrag(10L, "AENDERN")))))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400))
                .hasMessageContaining("Jede Abteilung darf nur einmal vorkommen.");
        assertThatThrownBy(() -> service.speichern(3L, new FirmendokumentOrdnerRechteSpeichernDto(null, List.of())))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400))
                .hasMessageContaining("Bitte angeben, ob der Ordner eingeschränkt ist.");
        assertThatThrownBy(() -> service.speichern(3L, null)).satisfies(t -> assertThat(status(t)).isEqualTo(400));
        verify(rechtRepository, never()).save(any());
        assertThat(versicherungen.isRechteEingeschraenkt()).isTrue();
    }

    @Test
    @DisplayName("Speichern gleichzeitig mit jemand anderem (doppelter Eintrag in der Datenbank) → 409 mit Meldung")
    void speichernGleichzeitig() {
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        given(rechtRepository.findeFuerOrdner(3L)).willReturn(List.of());
        given(abteilungRepository.findAllById(any())).willReturn(List.of(buero));
        org.mockito.BDDMockito.willThrow(new org.springframework.dao.DataIntegrityViolationException("uk_firmendokument_ordner_recht"))
                .given(rechtRepository).flush();

        assertThatThrownBy(() -> service.speichern(3L, new FirmendokumentOrdnerRechteSpeichernDto(true,
                List.of(new Eintrag(10L, "AENDERN")))))
                .satisfies(t -> assertThat(status(t)).isEqualTo(409))
                .hasMessageContaining("Jemand anders hat die Rechte gerade gespeichert. Bitte neu laden.");
    }

    @Test
    @DisplayName("Speichern ohne Abteilungsliste: alle Einträge weg, eingeschränkt = nur noch Admins")
    void speichernOhneListe() {
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        FirmendokumentOrdnerRecht bueroAlt = recht(versicherungen, buero, AENDERN);
        given(rechtRepository.findeFuerOrdner(3L)).willReturn(List.of(bueroAlt));
        alleAbteilungen();

        service.speichern(3L, new FirmendokumentOrdnerRechteSpeichernDto(true, null));

        verify(rechtRepository).delete(bueroAlt);
        assertThat(versicherungen.isRechteEingeschraenkt()).isTrue();
    }

    @Test
    @DisplayName("Speichern: mehr Einträge als Abteilungen möglich → 400, ohne die Datenbank zu fragen")
    void speichernZuViele() {
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        List<Eintrag> viele = new ArrayList<>();
        for (long id = 1; id <= FirmendokumentOrdnerRechteService.MAX_ABTEILUNGEN + 1; id++) {
            viele.add(new Eintrag(id, "ANSEHEN"));
        }

        assertThatThrownBy(() -> service.speichern(3L, new FirmendokumentOrdnerRechteSpeichernDto(true, viele)))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400));
        verify(abteilungRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("Verschieben: Unterordner auf die oberste Ebene übernimmt Flag und Einträge seines alten obersten Ordners")
    void unterordnerNachOben() {
        given(rechtRepository.findeFuerOrdner(4L)).willReturn(List.of());
        given(rechtRepository.findeFuerOrdner(3L)).willReturn(List.of(recht(versicherungen, buero, AENDERN),
                recht(versicherungen, werkstatt, ANSEHEN)));
        FirmendokumentOrdner jahr = ordner(5, "2026", haftpflicht, false);

        service.beimVerschieben(haftpflicht, null);

        assertThat(haftpflicht.isRechteEingeschraenkt()).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FirmendokumentOrdnerRecht>> kopie = ArgumentCaptor.forClass(List.class);
        verify(rechtRepository).saveAll(kopie.capture());
        assertThat(kopie.getValue()).extracting(FirmendokumentOrdnerRecht::getOrdner).containsOnly(haftpflicht);
        assertThat(kopie.getValue()).extracting(r -> r.getAbteilung().getName(), FirmendokumentOrdnerRecht::getStufe)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Büro", AENDERN),
                        org.assertj.core.groups.Tuple.tuple("Werkstatt", ANSEHEN));
        assertThat(jahr.isRechteEingeschraenkt()).isFalse();
    }

    @Test
    @DisplayName("Verschieben (nur Admins kommen bis hierher, siehe FirmendokumentZugriff.verlangeVerschieben): "
            + "oberster Ordner in einen anderen Zweig verliert eigene Einträge und Flag")
    void obersterOrdnerNachUnten() {
        FirmendokumentOrdner pruefungen = ordner(1, "Prüfungen", null, false);
        FirmendokumentOrdnerRecht bueroAlt = recht(versicherungen, buero, AENDERN);
        given(rechtRepository.findeFuerOrdner(3L)).willReturn(List.of(bueroAlt));

        service.beimVerschieben(versicherungen, pruefungen);

        assertThat(versicherungen.isRechteEingeschraenkt()).isFalse();
        verify(rechtRepository).deleteAll(List.of(bueroAlt));
    }

    @Test
    @DisplayName("Verschieben innerhalb der Unterordner oder oberster Ordner bleibt oben: an den Rechten ändert sich nichts")
    void verschiebenOhneRechteaenderung() {
        FirmendokumentOrdner pruefungen = ordner(1, "Prüfungen", null, false);

        service.beimVerschieben(haftpflicht, pruefungen);
        service.beimVerschieben(versicherungen, null);

        verify(rechtRepository, never()).findeFuerOrdner(any());
        assertThat(versicherungen.isRechteEingeschraenkt()).isTrue();
    }

    @Test
    @DisplayName("Ordner löschen: seine Rechte-Einträge werden mitgelöscht")
    void loescheEintraege() {
        FirmendokumentOrdnerRecht bueroAlt = recht(versicherungen, buero, AENDERN);
        given(rechtRepository.findeFuerOrdner(3L)).willReturn(List.of(bueroAlt));

        service.loescheEintraege(3L);

        verify(rechtRepository).deleteAll(List.of(bueroAlt));
    }
}
