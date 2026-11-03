package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.AENDERN;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.ANSEHEN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdner;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdnerRecht;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerAnlegenDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerZaehler;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRechtRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentRepository;
import org.example.kalkulationsprogramm.service.AbteilungsRechtPruefung;
import org.example.kalkulationsprogramm.service.AbteilungsRechtPruefung.Zugehoerigkeit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ordnerbaum mit echter Rechteprüfung: „Versicherungen“ (3) ist eingeschränkt, Max Mustermann aus der
 * Werkstatt (Abteilung 11) darf dort je nach Test ansehen oder gar nichts.
 */
@ExtendWith(MockitoExtension.class)
class FirmendokumentOrdnerServiceTest {

    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 2);
    private static final String VERBOTEN = "Du darfst in diesem Ordner nichts ändern.";

    @Mock FirmendokumentOrdnerRepository ordnerRepository;
    @Mock FirmendokumentRepository dokumentRepository;
    @Mock FirmendokumentOrdnerRechtRepository rechtRepository;
    @Mock AbteilungsRechtPruefung rechtPruefung;
    @Mock FirmendokumentOrdnerRechteService rechteService;

    private final Authentication max = mock(Authentication.class);
    private FirmendokumentOrdnerService service;
    private final FirmendokumentOrdner versicherungen = ordner(3, "Versicherungen", null);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T08:00:00Z"), ZoneId.of("Europe/Berlin"));
        FirmendokumentZugriff zugriff = new FirmendokumentZugriff(rechtPruefung, ordnerRepository, rechtRepository);
        service = new FirmendokumentOrdnerService(ordnerRepository, dokumentRepository, zugriff, rechteService, clock);
        versicherungen.setRechteEingeschraenkt(true);
    }

    private static FirmendokumentOrdner ordner(long id, String name, FirmendokumentOrdner parent) {
        FirmendokumentOrdner o = new FirmendokumentOrdner();
        o.setId(id);
        o.setName(name);
        o.setParent(parent);
        return o;
    }

    private static int status(Throwable t) {
        return ((ResponseStatusException) t).getStatusCode().value();
    }

    private FirmendokumentOrdnerRecht recht(FirmendokumentZugriffsStufe stufe) {
        Abteilung werkstatt = new Abteilung();
        werkstatt.setId(11L);
        FirmendokumentOrdnerRecht r = new FirmendokumentOrdnerRecht();
        r.setOrdner(versicherungen);
        r.setAbteilung(werkstatt);
        r.setStufe(stufe);
        return r;
    }

    private void werkstattHatAnVersicherungen(FirmendokumentZugriffsStufe... stufen) {
        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(false, Set.of(11L)));
        List<FirmendokumentOrdnerRecht> rechte = java.util.Arrays.stream(stufen).map(this::recht).toList();
        given(rechtRepository.findeFuerOrdnerUndAbteilungen(3L, Set.of(11L))).willReturn(rechte);
    }

    @Test
    @DisplayName("Liste: alle Ordner mit Zählern; Ordner ohne Dokumente haben 0, Obergrenze 2000, offen = alle dürfen ändern")
    void liste() {
        FirmendokumentOrdner pruefungen = ordner(1, "Prüfungen", null);
        FirmendokumentOrdner tore = ordner(2, "Tore", pruefungen);
        given(ordnerRepository.findeSortiert(PageRequest.of(0, FirmendokumentZugriff.MAX_ORDNER)))
                .willReturn(List.of(pruefungen, tore));
        given(dokumentRepository.zaehleJeOrdner(HEUTE, HEUTE.plusDays(30)))
                .willReturn(List.of(new FirmendokumentOrdnerZaehler(2L, 3L, 1L, null)));

        List<FirmendokumentOrdnerDto> liste = service.liste(max);

        assertThat(liste).containsExactly(
                new FirmendokumentOrdnerDto(1L, "Prüfungen", null, 0, 0, 0, true, false),
                new FirmendokumentOrdnerDto(2L, "Tore", 1L, 3, 1, 0, true, false));
    }

    @Test
    @DisplayName("Liste: eingeschränkte Zweige nur mit Recht sichtbar, mit Schloss; ohne Eintrag gar nicht")
    void listeEingeschraenkt() {
        FirmendokumentOrdner pruefungen = ordner(1, "Prüfungen", null);
        FirmendokumentOrdner haftpflicht = ordner(4, "Haftpflicht", versicherungen);
        FirmendokumentOrdner geheim = ordner(6, "Geheim", null);
        geheim.setRechteEingeschraenkt(true);
        given(ordnerRepository.findeSortiert(PageRequest.of(0, FirmendokumentZugriff.MAX_ORDNER)))
                .willReturn(List.of(geheim, haftpflicht, pruefungen, versicherungen));
        given(dokumentRepository.zaehleJeOrdner(any(), any())).willReturn(List.of());
        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(false, Set.of(11L)));
        given(rechtRepository.findeFuerAbteilungen(Set.of(11L))).willReturn(List.of(recht(ANSEHEN)));

        List<FirmendokumentOrdnerDto> liste = service.liste(max);

        assertThat(liste).containsExactly(
                new FirmendokumentOrdnerDto(4L, "Haftpflicht", 3L, 0, 0, 0, false, true),
                new FirmendokumentOrdnerDto(1L, "Prüfungen", null, 0, 0, 0, true, false),
                new FirmendokumentOrdnerDto(3L, "Versicherungen", null, 0, 0, 0, false, true));
    }

    @Test
    @DisplayName("Anlegen: Name wird getrimmt, Unterordner bekommt den Eltern-Ordner")
    void anlegen() {
        FirmendokumentOrdner pruefungen = ordner(1, "Prüfungen", null);
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(pruefungen));
        given(ordnerRepository.save(any())).willAnswer(a -> {
            FirmendokumentOrdner o = a.getArgument(0);
            o.setId(9L);
            return o;
        });

        FirmendokumentOrdnerDto dto = service.anlegen(new FirmendokumentOrdnerAnlegenDto("  Tore  ", 1L), max);

        assertThat(dto).isEqualTo(new FirmendokumentOrdnerDto(9L, "Tore", 1L, 0, 0, 0, true, false));
    }

    @Test
    @DisplayName("Anlegen auf oberster Ebene: jeder darf, nicht eingeschränkt; XSS- und SQL-Text bleibt reiner Text")
    void anlegenObersteEbene() {
        given(ordnerRepository.save(any())).willAnswer(a -> a.getArgument(0));
        String boese = "<script>alert(1)</script>'; DROP TABLE x; --";

        FirmendokumentOrdnerDto dto = service.anlegen(new FirmendokumentOrdnerAnlegenDto(boese, null), max);

        assertThat(dto.name()).isEqualTo(boese);
        assertThat(dto.parentId()).isNull();
        assertThat(dto.darfAendern()).isTrue();
        assertThat(dto.eingeschraenkt()).isFalse();
        verify(ordnerRepository).findByParentIsNullAndNameIgnoreCase(boese);
        verify(rechtPruefung, never()).zugehoerigkeit(any());
    }

    @Test
    @DisplayName("Anlegen im eingeschränkten Ordner: mit Ändern-Recht erlaubt (Schloss), nur ansehen → 403, unsichtbar → 400")
    void anlegenRechte() {
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        given(ordnerRepository.save(any())).willAnswer(a -> a.getArgument(0));
        werkstattHatAnVersicherungen(AENDERN);

        assertThat(service.anlegen(new FirmendokumentOrdnerAnlegenDto("2026", 3L), max).eingeschraenkt()).isTrue();

        werkstattHatAnVersicherungen(ANSEHEN);
        assertThatThrownBy(() -> service.anlegen(new FirmendokumentOrdnerAnlegenDto("2027", 3L), max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(403)).hasMessageContaining(VERBOTEN);

        werkstattHatAnVersicherungen();
        assertThatThrownBy(() -> service.anlegen(new FirmendokumentOrdnerAnlegenDto("2027", 3L), max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400))
                .hasMessageContaining("Den übergeordneten Ordner gibt es nicht mehr.");
        verify(ordnerRepository).save(any());
    }

    @Test
    @DisplayName("Anlegen: leerer, zu langer oder doppelter Name und fehlender Eltern-Ordner → 400")
    void anlegenFehler() {
        assertThatThrownBy(() -> service.anlegen(new FirmendokumentOrdnerAnlegenDto("  ", null), max))
                .hasMessageContaining("Bitte einen Namen für den Ordner eingeben.");
        assertThatThrownBy(() -> service.anlegen(new FirmendokumentOrdnerAnlegenDto(null, null), max))
                .hasMessageContaining("Bitte einen Namen");
        assertThatThrownBy(() -> service.anlegen(new FirmendokumentOrdnerAnlegenDto("x".repeat(10_001), null), max))
                .hasMessageContaining("höchstens 200 Zeichen");
        given(ordnerRepository.findById(Long.MAX_VALUE)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.anlegen(new FirmendokumentOrdnerAnlegenDto("Tore", Long.MAX_VALUE), max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400))
                .hasMessageContaining("übergeordneten Ordner gibt es nicht mehr");
        given(ordnerRepository.findByParentIsNullAndNameIgnoreCase("prüfungen"))
                .willReturn(List.of(ordner(1, "Prüfungen", null)));
        assertThatThrownBy(() -> service.anlegen(new FirmendokumentOrdnerAnlegenDto("prüfungen", null), max))
                .hasMessageContaining("Hier gibt es schon einen Ordner „prüfungen“.");
        verify(ordnerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Umbenennen: gleicher Name beim selben Ordner ist erlaubt, Zähler kommen mit")
    void umbenennen() {
        FirmendokumentOrdner pruefungen = ordner(1, "Prüfungen", null);
        FirmendokumentOrdner tore = ordner(2, "Tore", pruefungen);
        given(ordnerRepository.findById(2L)).willReturn(Optional.of(tore));
        given(ordnerRepository.findByParentIdAndNameIgnoreCase(1L, "TORE")).willReturn(List.of(tore));
        given(dokumentRepository.zaehleJeOrdner(any(), any()))
                .willReturn(List.of(new FirmendokumentOrdnerZaehler(2L, 4L, 0L, 2L)));

        FirmendokumentOrdnerDto dto = service.umbenennen(2L, "TORE", max);

        assertThat(dto).isEqualTo(new FirmendokumentOrdnerDto(2L, "TORE", 1L, 4, 0, 2, true, false));
    }

    @Test
    @DisplayName("Umbenennen: unbekannte, negative oder 0-ID → 404")
    void umbenennenNichtGefunden() {
        for (long id : new long[] { -1L, 0L, Long.MAX_VALUE }) {
            given(ordnerRepository.findById(id)).willReturn(Optional.empty());
            assertThatThrownBy(() -> service.umbenennen(id, "x", max))
                    .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.NOT_FOUND.value()));
        }
    }

    @Test
    @DisplayName("Umbenennen: nur ansehen → 403, unsichtbar → 404; Name bleibt")
    void umbenennenRechte() {
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        werkstattHatAnVersicherungen(ANSEHEN);
        assertThatThrownBy(() -> service.umbenennen(3L, "Neu", max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(403)).hasMessageContaining(VERBOTEN);

        werkstattHatAnVersicherungen();
        assertThatThrownBy(() -> service.umbenennen(3L, "Neu", max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(404)).hasMessageContaining("Den Ordner gibt es nicht mehr.");
        assertThat(versicherungen.getName()).isEqualTo("Versicherungen");
    }

    @Test
    @DisplayName("Verschieben in einen anderen Zweig und auf die oberste Ebene; Rechte werden vorher übertragen")
    void verschieben() {
        FirmendokumentOrdner arbeitsschutz = ordner(1, "Arbeitsschutz", null);
        FirmendokumentOrdner pruefungen = ordner(2, "Prüfungen", null);
        FirmendokumentOrdner tore = ordner(3, "Tore", pruefungen);
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(tore));
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(arbeitsschutz));
        AtomicReference<FirmendokumentOrdner> parentBeimUebertragen = new AtomicReference<>();
        willAnswer(i -> {
            parentBeimUebertragen.set(tore.getParent());
            return null;
        }).given(rechteService).beimVerschieben(eq(tore), any());

        assertThat(service.verschieben(3L, 1L, max).parentId()).isEqualTo(1L);
        assertThat(tore.getParent()).isSameAs(arbeitsschutz);
        assertThat(parentBeimUebertragen.get()).isSameAs(pruefungen);

        assertThat(service.verschieben(3L, null, max).parentId()).isNull();
        assertThat(tore.getParent()).isNull();
        verify(rechteService).beimVerschieben(tore, null);
    }

    @Test
    @DisplayName("Verschieben: Ordner nur ansehen → 403, unsichtbar → 404; Ziel nur ansehen → 403, Ziel unsichtbar → 400")
    void verschiebenRechte() {
        FirmendokumentOrdner pruefungen = ordner(1, "Prüfungen", null);
        FirmendokumentOrdner haftpflicht = ordner(4, "Haftpflicht", versicherungen);
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(pruefungen));
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        given(ordnerRepository.findById(4L)).willReturn(Optional.of(haftpflicht));

        werkstattHatAnVersicherungen(ANSEHEN);
        assertThatThrownBy(() -> service.verschieben(4L, null, max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(403)).hasMessageContaining(VERBOTEN);
        assertThatThrownBy(() -> service.verschieben(1L, 3L, max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(403)).hasMessageContaining(VERBOTEN);

        werkstattHatAnVersicherungen();
        assertThatThrownBy(() -> service.verschieben(4L, null, max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(404));
        assertThatThrownBy(() -> service.verschieben(1L, 3L, max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400)).hasMessageContaining("Den Zielordner gibt es nicht mehr.");

        assertThat(pruefungen.getParent()).isNull();
        assertThat(haftpflicht.getParent()).isSameAs(versicherungen);
        verify(rechteService, never()).beimVerschieben(any(), any());
    }

    @Test
    @DisplayName("Verschieben: Nicht-Admin mit Ändern-Recht darf eingeschränkten Zweig nicht in einen anderen Hauptordner legen")
    void verschiebenAusEingeschraenktemZweigNurAdmin() {
        FirmendokumentOrdner pruefungen = ordner(1, "Prüfungen", null);
        FirmendokumentOrdner haftpflicht = ordner(4, "Haftpflicht", versicherungen);
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(pruefungen));
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        given(ordnerRepository.findById(4L)).willReturn(Optional.of(haftpflicht));
        werkstattHatAnVersicherungen(AENDERN);
        String meldung = "Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben.";

        assertThatThrownBy(() -> service.verschieben(3L, 1L, max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(403)).hasMessageContaining(meldung);
        assertThatThrownBy(() -> service.verschieben(4L, 1L, max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(403)).hasMessageContaining(meldung);
        assertThat(versicherungen.getParent()).isNull();
        assertThat(haftpflicht.getParent()).isSameAs(versicherungen);
        verify(rechteService, never()).beimVerschieben(any(), any());

        assertThat(service.verschieben(4L, null, max).parentId()).isNull();
        verify(rechteService).beimVerschieben(haftpflicht, null);
    }

    @Test
    @DisplayName("Verschieben: Admin darf einen eingeschränkten obersten Ordner in einen anderen Zweig legen")
    void verschiebenEingeschraenktAlsAdmin() {
        FirmendokumentOrdner pruefungen = ordner(1, "Prüfungen", null);
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(pruefungen));
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(true, Set.of()));

        assertThat(service.verschieben(3L, 1L, max).parentId()).isEqualTo(1L);
        verify(rechteService).beimVerschieben(versicherungen, pruefungen);
    }

    @Test
    @DisplayName("Anlegen: sind schon 2000 Ordner da, kommt 400 statt dass Ordner still aus der Liste fallen")
    void anlegenObergrenze() {
        given(ordnerRepository.count()).willReturn((long) FirmendokumentZugriff.MAX_ORDNER);

        assertThatThrownBy(() -> service.anlegen(new FirmendokumentOrdnerAnlegenDto("Neu", null), max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400))
                .hasMessageContaining("Es gibt schon 2000 Ordner. Bitte zuerst leere Ordner löschen.");
        verify(ordnerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Verschieben in sich selbst, in einen Unterordner oder ins Leere → 400")
    void verschiebenFehler() {
        FirmendokumentOrdner pruefungen = ordner(1, "Prüfungen", null);
        FirmendokumentOrdner tore = ordner(2, "Tore", pruefungen);
        FirmendokumentOrdner halle = ordner(3, "Halle 2", tore);
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(pruefungen));
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(halle));
        given(ordnerRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.verschieben(1L, 1L, max)).hasMessageContaining("nicht in sich selbst");
        assertThatThrownBy(() -> service.verschieben(1L, 3L, max)).hasMessageContaining("nicht in sich selbst");
        assertThatThrownBy(() -> service.verschieben(1L, 99L, max)).hasMessageContaining("Zielordner gibt es nicht mehr");
        assertThat(pruefungen.getParent()).isNull();
        verify(rechteService, never()).beimVerschieben(any(), any());
    }

    @Test
    @DisplayName("Verschieben: kaputte (zyklische) Daten führen nicht in eine Endlosschleife; Admins dürfen trotzdem")
    void verschiebenZyklus() {
        FirmendokumentOrdner a = ordner(1, "A", null);
        FirmendokumentOrdner b = ordner(2, "B", a);
        a.setParent(b);
        FirmendokumentOrdner c = ordner(3, "C", null);
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(c));
        given(ordnerRepository.findById(2L)).willReturn(Optional.of(b));
        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(false, Set.of(11L)));

        assertThatThrownBy(() -> service.verschieben(3L, 2L, max)).hasMessageContaining("Zielordner gibt es nicht mehr");

        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(true, Set.of()));
        assertThat(service.verschieben(3L, 2L, max).parentId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Verschieben: im Ziel gibt es schon einen gleichnamigen Ordner → 400")
    void verschiebenNamensdoppel() {
        FirmendokumentOrdner arbeitsschutz = ordner(1, "Arbeitsschutz", null);
        FirmendokumentOrdner tore = ordner(2, "Tore", null);
        given(ordnerRepository.findById(2L)).willReturn(Optional.of(tore));
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(arbeitsschutz));
        given(ordnerRepository.findByParentIdAndNameIgnoreCase(1L, "Tore"))
                .willReturn(List.of(ordner(5, "tore", arbeitsschutz)));

        assertThatThrownBy(() -> service.verschieben(2L, 1L, max)).hasMessageContaining("schon einen Ordner");
    }

    @Test
    @DisplayName("Löschen nur bei leerem Ordner, seine Rechte-Einträge zuerst; mit Unterordner oder Dokument → 400")
    void loeschen() {
        FirmendokumentOrdner leer = ordner(1, "Leer", null);
        FirmendokumentOrdner mitUnterordner = ordner(2, "Mit Unterordner", null);
        FirmendokumentOrdner mitDokument = ordner(4, "Mit Dokument", null);
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(leer));
        given(ordnerRepository.findById(2L)).willReturn(Optional.of(mitUnterordner));
        given(ordnerRepository.findById(4L)).willReturn(Optional.of(mitDokument));
        given(ordnerRepository.existsByParentId(anyLong())).willAnswer(i -> i.<Long>getArgument(0) == 2L);
        given(dokumentRepository.existsByOrdnerId(anyLong())).willAnswer(i -> i.<Long>getArgument(0) == 4L);

        service.loeschen(1L, max);
        InOrder reihenfolge = inOrder(rechteService, ordnerRepository);
        reihenfolge.verify(rechteService).loescheEintraege(1L);
        reihenfolge.verify(ordnerRepository).delete(leer);

        assertThatThrownBy(() -> service.loeschen(2L, max)).hasMessageContaining("Der Ordner ist nicht leer.");
        assertThatThrownBy(() -> service.loeschen(4L, max)).hasMessageContaining("Der Ordner ist nicht leer.");
        verify(ordnerRepository, never()).delete(eq(mitUnterordner));
        verify(ordnerRepository, never()).delete(eq(mitDokument));
        verify(rechteService, never()).loescheEintraege(2L);
    }

    @Test
    @DisplayName("Löschen: nur ansehen → 403, unsichtbar → 404; nichts wird gelöscht")
    void loeschenRechte() {
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        werkstattHatAnVersicherungen(ANSEHEN);
        assertThatThrownBy(() -> service.loeschen(3L, max)).satisfies(t -> assertThat(status(t)).isEqualTo(403));

        werkstattHatAnVersicherungen();
        assertThatThrownBy(() -> service.loeschen(3L, max)).satisfies(t -> assertThat(status(t)).isEqualTo(404));
        verify(ordnerRepository, never()).delete(any());
        verify(rechteService, never()).loescheEintraege(anyLong());
    }
}
