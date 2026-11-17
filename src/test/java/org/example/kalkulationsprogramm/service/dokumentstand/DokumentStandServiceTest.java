package org.example.kalkulationsprogramm.service.dokumentstand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentOffeneBearbeitung;
import org.example.kalkulationsprogramm.domain.AusgangsDokumentStand;
import org.example.kalkulationsprogramm.domain.AusgangsDokumentStandArt;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.repository.AusgangsDokumentOffeneBearbeitungRepository;
import org.example.kalkulationsprogramm.repository.AusgangsDokumentStandRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DokumentStandServiceTest {

    @Mock AusgangsDokumentStandRepository standRepository;
    @Mock AusgangsDokumentOffeneBearbeitungRepository bearbeitungRepository;
    @Mock AusgangsGeschaeftsDokumentRepository dokumentRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T09:30:00Z"), ZoneId.of("Europe/Berlin"));
    private DokumentStandService service;

    @BeforeEach
    void setUp() {
        service = new DokumentStandService(standRepository, bearbeitungRepository, dokumentRepository, clock);
    }

    private static AusgangsGeschaeftsDokument dokument() {
        AusgangsGeschaeftsDokument d = new AusgangsGeschaeftsDokument();
        d.setId(7L);
        d.setBetreff("Geländer Max Mustermann");
        d.setDatum(LocalDate.of(2026, 11, 17));
        d.setBetragNetto(new BigDecimal("100.00"));
        d.setMwstSatz(new BigDecimal("0.19"));
        d.setPositionenJson("{\"blocks\":[{\"id\":\"a\",\"type\":\"SERVICE\",\"title\":\"Tor\",\"price\":100,\"quantity\":1}]}");
        d.setHtmlInhalt("<p>Tor</p>");
        return d;
    }

    private static AusgangsDokumentStand standVon(AusgangsGeschaeftsDokument d, int nummer) {
        AusgangsDokumentStand s = new AusgangsDokumentStand();
        s.setId(100L + nummer);
        s.setDokumentId(d.getId());
        s.setNummer(nummer);
        s.setBetreff(d.getBetreff());
        s.setBetragNetto(d.getBetragNetto());
        s.setMwstSatz(d.getMwstSatz());
        s.setPositionenJson(d.getPositionenJson());
        s.setInhaltFingerabdruck(DokumentStandInhalt.von(d).fingerabdruck());
        return s;
    }

    @Test
    @DisplayName("Erster Export: Stand 1 „Erste Fassung“ mit Inhalt, Bearbeitern und PDF")
    void ersterStand() {
        AusgangsGeschaeftsDokument d = dokument();
        when(dokumentRepository.sperreZumFesthalten(7L)).thenReturn(Optional.of(d));
        when(standRepository.findFirstByDokumentIdOrderByNummerDesc(7L)).thenReturn(Optional.empty());
        AusgangsDokumentOffeneBearbeitung b = new AusgangsDokumentOffeneBearbeitung();
        b.setBenutzerId(3L);
        b.setSeit(LocalDateTime.now(clock));
        when(bearbeitungRepository.findByDokumentIdOrderBySeitAscIdAsc(7L)).thenReturn(List.of(b));
        when(standRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AusgangsDokumentStand stand = service.festhaltenNachExport(7L, 42L, "abc.pdf").orElseThrow();

        assertThat(stand.getNummer()).isEqualTo(1);
        assertThat(stand.getArt()).isEqualTo(AusgangsDokumentStandArt.EXPORT);
        assertThat(stand.getFestgehaltenVonId()).isEqualTo(42L);
        assertThat(stand.getFestgehaltenAm()).isEqualTo(LocalDateTime.of(2026, 11, 17, 10, 30));
        assertThat(DokumentStandJson.leseSaetze(stand.getAenderungen())).containsExactly("Erste Fassung");
        assertThat(DokumentStandJson.leseIds(stand.getBearbeitetVonIds())).containsExactly(3L);
        assertThat(stand.getPdfDateiname()).isEqualTo("abc.pdf");
        assertThat(stand.getHtmlInhalt()).isEqualTo("<p>Tor</p>");
        assertThat(stand.getDatum()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(stand.getWiederhergestelltAusNummer()).isNull();
        verify(bearbeitungRepository).loescheFuerDokument(7L);
    }

    @Test
    @DisplayName("Gleicher Inhalt wie der neueste Stand: kein neuer Stand, offene Bearbeitung bleibt")
    void gleicherInhaltKeinStand() {
        AusgangsGeschaeftsDokument d = dokument();
        when(dokumentRepository.sperreZumFesthalten(7L)).thenReturn(Optional.of(d));
        AusgangsDokumentStand mitPdf = standVon(d, 2);
        mitPdf.setPdfDateiname("alt.pdf");
        when(standRepository.findFirstByDokumentIdOrderByNummerDesc(7L)).thenReturn(Optional.of(mitPdf));

        assertThat(service.festhaltenNachExport(7L, 42L, "x.pdf")).isEmpty();
        verify(standRepository, never()).save(any());
        verify(bearbeitungRepository, never()).loescheFuerDokument(any());
    }

    @Test
    @DisplayName("Gleicher Inhalt, neuester Stand ohne PDF: PDF wird nachgetragen, kein neuer Stand")
    void pdfNachtragen() {
        AusgangsGeschaeftsDokument d = dokument();
        AusgangsDokumentStand ohnePdf = standVon(d, 2);
        when(dokumentRepository.sperreZumFesthalten(7L)).thenReturn(Optional.of(d));
        when(standRepository.findFirstByDokumentIdOrderByNummerDesc(7L)).thenReturn(Optional.of(ohnePdf));

        assertThat(service.festhaltenNachExport(7L, 42L, "neu.pdf")).isEmpty();
        assertThat(ohnePdf.getPdfDateiname()).isEqualTo("neu.pdf");
        verify(standRepository).save(ohnePdf);

        // Ein vorhandenes PDF wird nie ersetzt.
        assertThat(service.festhaltenNachExport(7L, 42L, "noch-eins.pdf")).isEmpty();
        assertThat(ohnePdf.getPdfDateiname()).isEqualTo("neu.pdf");
    }

    @Test
    @DisplayName("Festhalten sperrt die Dokumentzeile vor der Nummernvergabe")
    void sperrtDokumentzeile() {
        AusgangsGeschaeftsDokument d = dokument();
        when(dokumentRepository.sperreZumFesthalten(7L)).thenReturn(Optional.of(d));
        when(standRepository.findFirstByDokumentIdOrderByNummerDesc(7L)).thenReturn(Optional.empty());
        when(standRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.festhalten(d, AusgangsDokumentStandArt.VOR_WIEDERHERSTELLEN, 42L, null);

        org.mockito.InOrder reihenfolge = org.mockito.Mockito.inOrder(dokumentRepository, standRepository);
        reihenfolge.verify(dokumentRepository).sperreZumFesthalten(7L);
        reihenfolge.verify(standRepository).findFirstByDokumentIdOrderByNummerDesc(7L);
    }

    @Test
    @DisplayName("Nur das Datum geändert: kein neuer Stand")
    void nurDatumKeinStand() {
        AusgangsGeschaeftsDokument d = dokument();
        AusgangsDokumentStand alt = standVon(d, 1);
        d.setDatum(LocalDate.of(2026, 12, 1));
        when(dokumentRepository.sperreZumFesthalten(7L)).thenReturn(Optional.of(d));
        when(standRepository.findFirstByDokumentIdOrderByNummerDesc(7L)).thenReturn(Optional.of(alt));

        assertThat(service.festhaltenNachExport(7L, 42L, null)).isEmpty();
    }

    @Test
    @DisplayName("Geänderter Inhalt: nächste Nummer, Änderungssätze, zuletzt zurückgeholte Nummer")
    void folgestand() {
        AusgangsGeschaeftsDokument d = dokument();
        AusgangsDokumentStand alt = standVon(d, 3);
        d.setBetreff("Neuer Betreff");
        when(standRepository.findFirstByDokumentIdOrderByNummerDesc(7L)).thenReturn(Optional.of(alt));
        AusgangsDokumentOffeneBearbeitung frueher = new AusgangsDokumentOffeneBearbeitung();
        frueher.setBenutzerId(3L);
        frueher.setWiederhergestelltAusNummer(1);
        frueher.setWiederhergestelltAm(LocalDateTime.of(2026, 11, 1, 8, 0));
        AusgangsDokumentOffeneBearbeitung spaeter = new AusgangsDokumentOffeneBearbeitung();
        spaeter.setBenutzerId(4L);
        spaeter.setWiederhergestelltAusNummer(2);
        spaeter.setWiederhergestelltAm(LocalDateTime.of(2026, 11, 2, 8, 0));
        when(bearbeitungRepository.findByDokumentIdOrderBySeitAscIdAsc(7L)).thenReturn(List.of(frueher, spaeter));
        when(standRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AusgangsDokumentStand stand = service.festhalten(d, AusgangsDokumentStandArt.VOR_WIEDERHERSTELLEN, 42L, null)
                .orElseThrow();

        assertThat(stand.getNummer()).isEqualTo(4);
        assertThat(stand.getArt()).isEqualTo(AusgangsDokumentStandArt.VOR_WIEDERHERSTELLEN);
        assertThat(DokumentStandJson.leseSaetze(stand.getAenderungen()))
                .containsExactly("Betreff: „Geländer Max Mustermann“ → „Neuer Betreff“");
        assertThat(DokumentStandJson.leseIds(stand.getBearbeitetVonIds())).containsExactly(3L, 4L);
        assertThat(stand.getWiederhergestelltAusNummer()).isEqualTo(2);
    }

    @Test
    @DisplayName("Unbekanntes Dokument beim Export: 404")
    void unbekanntesDokument() {
        when(dokumentRepository.sperreZumFesthalten(9L)).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.festhaltenNachExport(9L, 1L, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("Bearbeitung merken: neuer Eintrag, vorhandener bleibt, ohne Benutzer nichts")
    void bearbeitungMerken() {
        when(bearbeitungRepository.findByDokumentIdAndBenutzerId(7L, 42L)).thenReturn(Optional.empty());
        when(bearbeitungRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.merkeBearbeitung(7L, 42L);
        ArgumentCaptor<AusgangsDokumentOffeneBearbeitung> captor = ArgumentCaptor.forClass(AusgangsDokumentOffeneBearbeitung.class);
        verify(bearbeitungRepository).save(captor.capture());
        assertThat(captor.getValue().getDokumentId()).isEqualTo(7L);
        assertThat(captor.getValue().getBenutzerId()).isEqualTo(42L);

        service.merkeBearbeitung(7L, null);
        verify(bearbeitungRepository).findByDokumentIdAndBenutzerId(7L, 42L);
    }

    @Test
    @DisplayName("Wiederherstellung merken: Nummer und Zeitpunkt am Eintrag des Benutzers")
    void wiederherstellungMerken() {
        AusgangsDokumentOffeneBearbeitung vorhanden = new AusgangsDokumentOffeneBearbeitung();
        vorhanden.setDokumentId(7L);
        vorhanden.setBenutzerId(42L);
        when(bearbeitungRepository.findByDokumentIdAndBenutzerId(7L, 42L)).thenReturn(Optional.of(vorhanden));
        service.merkeWiederherstellung(7L, 42L, 2);
        assertThat(vorhanden.getWiederhergestelltAusNummer()).isEqualTo(2);
        assertThat(vorhanden.getWiederhergestelltAm()).isEqualTo(LocalDateTime.of(2026, 11, 17, 10, 30));
        verify(bearbeitungRepository).save(vorhanden);
    }
}
