package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdner;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerAnlegenDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerZaehler;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FirmendokumentOrdnerServiceTest {

    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 2);

    @Mock FirmendokumentOrdnerRepository ordnerRepository;
    @Mock FirmendokumentRepository dokumentRepository;

    private FirmendokumentOrdnerService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T08:00:00Z"), ZoneId.of("Europe/Berlin"));
        service = new FirmendokumentOrdnerService(ordnerRepository, dokumentRepository, clock);
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

    @Test
    @DisplayName("Liste: alle Ordner mit Zählern; Ordner ohne Dokumente haben 0, Obergrenze 2000")
    void liste() {
        FirmendokumentOrdner pruefungen = ordner(1, "Prüfungen", null);
        FirmendokumentOrdner tore = ordner(2, "Tore", pruefungen);
        given(ordnerRepository.findeSortiert(PageRequest.of(0, FirmendokumentOrdnerService.MAX_ORDNER)))
                .willReturn(List.of(pruefungen, tore));
        given(dokumentRepository.zaehleJeOrdner(HEUTE, HEUTE.plusDays(30)))
                .willReturn(List.of(new FirmendokumentOrdnerZaehler(2L, 3L, 1L, null)));

        List<FirmendokumentOrdnerDto> liste = service.liste();

        assertThat(liste).containsExactly(
                new FirmendokumentOrdnerDto(1L, "Prüfungen", null, 0, 0, 0),
                new FirmendokumentOrdnerDto(2L, "Tore", 1L, 3, 1, 0));
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

        FirmendokumentOrdnerDto dto = service.anlegen(new FirmendokumentOrdnerAnlegenDto("  Tore  ", 1L));

        assertThat(dto).isEqualTo(new FirmendokumentOrdnerDto(9L, "Tore", 1L, 0, 0, 0));
    }

    @Test
    @DisplayName("Anlegen auf oberster Ebene; XSS- und SQL-Text wird unverändert als Text gespeichert")
    void anlegenObersteEbene() {
        given(ordnerRepository.save(any())).willAnswer(a -> a.getArgument(0));
        String boese = "<script>alert(1)</script>'; DROP TABLE x; --";

        FirmendokumentOrdnerDto dto = service.anlegen(new FirmendokumentOrdnerAnlegenDto(boese, null));

        assertThat(dto.name()).isEqualTo(boese);
        assertThat(dto.parentId()).isNull();
        verify(ordnerRepository).findByParentIsNullAndNameIgnoreCase(boese);
    }

    @Test
    @DisplayName("Anlegen: leerer, zu langer oder doppelter Name und fehlender Eltern-Ordner → 400")
    void anlegenFehler() {
        assertThatThrownBy(() -> service.anlegen(new FirmendokumentOrdnerAnlegenDto("  ", null)))
                .hasMessageContaining("Bitte einen Namen für den Ordner eingeben.");
        assertThatThrownBy(() -> service.anlegen(new FirmendokumentOrdnerAnlegenDto(null, null)))
                .hasMessageContaining("Bitte einen Namen");
        assertThatThrownBy(() -> service.anlegen(new FirmendokumentOrdnerAnlegenDto("x".repeat(10_001), null)))
                .hasMessageContaining("höchstens 200 Zeichen");
        given(ordnerRepository.findById(Long.MAX_VALUE)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.anlegen(new FirmendokumentOrdnerAnlegenDto("Tore", Long.MAX_VALUE)))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400))
                .hasMessageContaining("übergeordneten Ordner gibt es nicht mehr");
        given(ordnerRepository.findByParentIsNullAndNameIgnoreCase("prüfungen"))
                .willReturn(List.of(ordner(1, "Prüfungen", null)));
        assertThatThrownBy(() -> service.anlegen(new FirmendokumentOrdnerAnlegenDto("prüfungen", null)))
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

        FirmendokumentOrdnerDto dto = service.umbenennen(2L, "TORE");

        assertThat(dto).isEqualTo(new FirmendokumentOrdnerDto(2L, "TORE", 1L, 4, 0, 2));
    }

    @Test
    @DisplayName("Umbenennen: unbekannte, negative oder 0-ID → 404")
    void umbenennenNichtGefunden() {
        for (long id : new long[] { -1L, 0L, Long.MAX_VALUE }) {
            given(ordnerRepository.findById(id)).willReturn(Optional.empty());
            assertThatThrownBy(() -> service.umbenennen(id, "x"))
                    .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.NOT_FOUND.value()));
        }
    }

    @Test
    @DisplayName("Verschieben in einen anderen Zweig und auf die oberste Ebene")
    void verschieben() {
        FirmendokumentOrdner arbeitsschutz = ordner(1, "Arbeitsschutz", null);
        FirmendokumentOrdner pruefungen = ordner(2, "Prüfungen", null);
        FirmendokumentOrdner tore = ordner(3, "Tore", pruefungen);
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(tore));
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(arbeitsschutz));

        assertThat(service.verschieben(3L, 1L).parentId()).isEqualTo(1L);
        assertThat(tore.getParent()).isSameAs(arbeitsschutz);

        assertThat(service.verschieben(3L, null).parentId()).isNull();
        assertThat(tore.getParent()).isNull();
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

        assertThatThrownBy(() -> service.verschieben(1L, 1L)).hasMessageContaining("nicht in sich selbst");
        assertThatThrownBy(() -> service.verschieben(1L, 3L)).hasMessageContaining("nicht in sich selbst");
        assertThatThrownBy(() -> service.verschieben(1L, 99L)).hasMessageContaining("Zielordner gibt es nicht mehr");
        assertThat(pruefungen.getParent()).isNull();
    }

    @Test
    @DisplayName("Verschieben: kaputte (zyklische) Daten führen nicht in eine Endlosschleife")
    void verschiebenZyklus() {
        FirmendokumentOrdner a = ordner(1, "A", null);
        FirmendokumentOrdner b = ordner(2, "B", a);
        a.setParent(b);
        FirmendokumentOrdner c = ordner(3, "C", null);
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(c));
        given(ordnerRepository.findById(2L)).willReturn(Optional.of(b));

        assertThat(service.verschieben(3L, 2L).parentId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Verschieben: im Ziel gibt es schon einen gleichnamigen Ordner → 400")
    void verschiebenNamensdoppel() {
        FirmendokumentOrdner arbeitsschutz = ordner(1, "Arbeitsschutz", null);
        FirmendokumentOrdner tore = ordner(2, "Tore", null);
        given(ordnerRepository.findById(2L)).willReturn(Optional.of(tore));
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(arbeitsschutz));
        given(ordnerRepository.findByParentIdAndNameIgnoreCase(1L, "Tore")).willReturn(List.of(ordner(5, "tore", arbeitsschutz)));

        assertThatThrownBy(() -> service.verschieben(2L, 1L)).hasMessageContaining("schon einen Ordner");
    }

    @Test
    @DisplayName("Löschen nur bei leerem Ordner; mit Unterordner oder Dokument → 400")
    void loeschen() {
        FirmendokumentOrdner leer = ordner(1, "Leer", null);
        FirmendokumentOrdner mitUnterordner = ordner(2, "Mit Unterordner", null);
        FirmendokumentOrdner mitDokument = ordner(3, "Mit Dokument", null);
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(leer));
        given(ordnerRepository.findById(2L)).willReturn(Optional.of(mitUnterordner));
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(mitDokument));
        given(ordnerRepository.existsByParentId(anyLong())).willAnswer(i -> i.<Long>getArgument(0) == 2L);
        given(dokumentRepository.existsByOrdnerId(anyLong())).willAnswer(i -> i.<Long>getArgument(0) == 3L);

        service.loeschen(1L);
        verify(ordnerRepository).delete(leer);

        assertThatThrownBy(() -> service.loeschen(2L)).hasMessageContaining("Der Ordner ist nicht leer.");
        assertThatThrownBy(() -> service.loeschen(3L)).hasMessageContaining("Der Ordner ist nicht leer.");
        verify(ordnerRepository, never()).delete(eq(mitUnterordner));
        verify(ordnerRepository, never()).delete(eq(mitDokument));
    }
}
