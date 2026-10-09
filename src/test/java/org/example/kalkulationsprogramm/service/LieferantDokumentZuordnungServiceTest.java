package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentPosition;
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.PositionsArt;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Bestellung.DokumentPositionenDto;
import org.example.kalkulationsprogramm.dto.Bestellung.DokumentPositionenDto.PositionsZiel;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentPositionRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LieferantDokumentZuordnungServiceTest {

    @Mock private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    @Mock private LieferantDokumentProjektAnteilRepository projektAnteilRepository;
    @Mock private LieferantDokumentPositionRepository positionRepository;
    @Mock private ProjektRepository projektRepository;
    @Mock private KostenstelleRepository kostenstelleRepository;
    @Mock private ProjektDokumentRepository projektDokumentRepository;
    @Mock private FrontendUserProfileRepository frontendUserProfileRepository;

    private LieferantDokumentZuordnungService service;
    private LieferantGeschaeftsdokument gd;
    private Projekt projektA;
    private Projekt projektB;
    private Kostenstelle lager;

    @BeforeEach
    void setUp() {
        service = new LieferantDokumentZuordnungService(geschaeftsdokumentRepository, projektAnteilRepository,
                positionRepository, projektRepository, kostenstelleRepository, projektDokumentRepository,
                frontendUserProfileRepository);

        LieferantDokument dokument = new LieferantDokument();
        dokument.setId(10L);
        dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
        gd = new LieferantGeschaeftsdokument();
        gd.setId(10L);
        gd.setDokument(dokument);
        gd.setDokumentNummer("RE-4711");
        gd.setDokumentDatum(LocalDate.of(2026, 9, 1));
        gd.setBetragNetto(new BigDecimal("1100.00"));
        gd.setBetragBrutto(new BigDecimal("1309.00"));
        when(geschaeftsdokumentRepository.findById(10L)).thenReturn(Optional.of(gd));

        projektA = projekt(1L, "BV Mustermann");
        projektB = projekt(2L, "BV Musterfrau");
        lager = new Kostenstelle();
        lager.setId(9L);
        lager.setBezeichnung("Lager");
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projektA));
        when(projektRepository.findById(2L)).thenReturn(Optional.of(projektB));
        when(kostenstelleRepository.findById(9L)).thenReturn(Optional.of(lager));
        when(projektRepository.findById(99L)).thenReturn(Optional.empty());
        when(projektAnteilRepository.findByDokumentId(10L)).thenReturn(List.of());
    }

    private static Projekt projekt(Long id, String name) {
        Projekt p = new Projekt();
        p.setId(id);
        p.setBauvorhaben(name);
        return p;
    }

    private LieferantDokumentPosition position(long id, PositionsArt art, String betrag) {
        LieferantDokumentPosition p = new LieferantDokumentPosition();
        p.setId(id);
        p.setGeschaeftsdokument(gd);
        p.setPositionNr((int) id);
        p.setPositionsArt(art);
        p.setBezeichnung("Position " + id);
        p.setGesamtpreisNetto(betrag != null ? new BigDecimal(betrag) : null);
        return p;
    }

    private List<LieferantDokumentPosition> rechnungMitFracht() {
        // 700 + 300 Ware, 100 Fracht = 1100 netto
        List<LieferantDokumentPosition> positionen = List.of(
                position(1, PositionsArt.WARE, "700"),
                position(2, PositionsArt.WARE, "300"),
                position(3, PositionsArt.NEBENKOSTEN, "100"));
        when(positionRepository.findByGeschaeftsdokumentId(10L)).thenReturn(positionen);
        return positionen;
    }

    private static DokumentPositionenDto.AufteilungRequest request(PositionsZiel... ziele) {
        return new DokumentPositionenDto.AufteilungRequest(List.of(ziele), List.of());
    }

    @Nested
    class NachPositionen {

        @SuppressWarnings("unchecked")
        @Test
        void speichertBetraegeUndMerktZiele() {
            List<LieferantDokumentPosition> positionen = rechnungMitFracht();

            int anzahl = service.speichereNachPositionen(10L, new DokumentPositionenDto.AufteilungRequest(
                    List.of(new PositionsZiel(1L, 1L, null), new PositionsZiel(2L, null, 9L)),
                    List.of(new DokumentPositionenDto.ZielDetail(null, 9L, "Lagerware", 3))), null);

            assertThat(anzahl).isEqualTo(2);
            assertThat(positionen.get(0).getProjekt()).isSameAs(projektA);
            assertThat(positionen.get(1).getKostenstelle()).isSameAs(lager);
            assertThat(positionen.get(2).getProjekt()).isNull();

            ArgumentCaptor<List<LieferantDokumentProjektAnteil>> captor = ArgumentCaptor.forClass(List.class);
            verify(projektAnteilRepository).saveAll(captor.capture());
            List<LieferantDokumentProjektAnteil> anteile = captor.getValue();
            // Projekt brutto: 70 % von 1309
            assertThat(anteile.get(0).getBerechneterBetrag()).isEqualByComparingTo("916.30");
            // Kostenstelle netto: 30 % von 1100, mit Streckung und Beschreibung
            assertThat(anteile.get(1).getBerechneterBetrag()).isEqualByComparingTo("330.00");
            assertThat(anteile.get(1).getStreckungJahre()).isEqualTo(3);
            assertThat(anteile.get(1).getBeschreibung()).isEqualTo("Lagerware");
            // Positionsaufteilung darf nicht wieder gelöscht werden
            verify(positionRepository, never()).entferneZuordnungen(anyLong());
        }

        @Test
        void offenePositionVerhindertSpeichern() {
            rechnungMitFracht();

            assertThatThrownBy(() -> service.speichereNachPositionen(10L,
                    request(new PositionsZiel(1L, 1L, null)), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Eine Position hat noch kein Projekt.");
            verify(projektAnteilRepository, never()).saveAll(anyList());
        }

        @Test
        void fremdePositionWirdAbgelehnt() {
            rechnungMitFracht();

            assertThatThrownBy(() -> service.speichereNachPositionen(10L,
                    request(new PositionsZiel(1L, 1L, null), new PositionsZiel(2L, 1L, null),
                            new PositionsZiel(555L, 1L, null)), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("gehört nicht zu diesem Dokument");
        }

        @Test
        void projektUndKostenstelleZugleichAbgelehnt() {
            rechnungMitFracht();

            assertThatThrownBy(() -> service.speichereNachPositionen(10L,
                    request(new PositionsZiel(1L, 1L, 9L), new PositionsZiel(2L, 1L, null)), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void unbekanntesProjekt() {
            rechnungMitFracht();

            assertThatThrownBy(() -> service.speichereNachPositionen(10L,
                    request(new PositionsZiel(1L, 99L, null), new PositionsZiel(2L, 1L, null)), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Projekt nicht gefunden");
            verify(projektAnteilRepository, never()).saveAll(anyList());
        }

        @Test
        void ohnePreiseNichtSpeicherbar() {
            when(positionRepository.findByGeschaeftsdokumentId(10L))
                    .thenReturn(List.of(position(1, PositionsArt.WARE, null)));

            assertThatThrownBy(() -> service.speichereNachPositionen(10L,
                    request(new PositionsZiel(1L, 1L, null)), null))
                    .hasMessageContaining("keine Preise");
        }

        @Test
        void gutschriftMitNegativenBetraegenNichtSpeicherbar() {
            gd.setBetragNetto(new BigDecimal("-100"));
            gd.setBetragBrutto(new BigDecimal("-119"));
            when(positionRepository.findByGeschaeftsdokumentId(10L))
                    .thenReturn(List.of(position(1, PositionsArt.WARE, "100")));

            assertThatThrownBy(() -> service.speichereNachPositionen(10L,
                    request(new PositionsZiel(1L, 1L, null)), null))
                    .hasMessageContaining("Gutschriften");
        }

        @Test
        void ohnePositionenNichtSpeicherbar() {
            when(positionRepository.findByGeschaeftsdokumentId(10L)).thenReturn(List.of());

            assertThatThrownBy(() -> service.speichereNachPositionen(10L, request(), null))
                    .hasMessageContaining("noch keine Positionen");
        }

        @Test
        void vorschauOhneSpeichern() {
            rechnungMitFracht();

            var vorschau = service.vorschau(10L, List.of(new PositionsZiel(1L, 1L, null)));

            assertThat(vorschau.nichtZugeordnet()).isEqualTo(1);
            assertThat(vorschau.speicherbar()).isFalse();
            assertThat(vorschau.hinweis()).contains("noch kein Projekt");
            assertThat(vorschau.nebenkosten()).isEqualByComparingTo("100");
            assertThat(vorschau.ziele()).singleElement()
                    .satisfies(z -> assertThat(z.anteilProzent()).isEqualByComparingTo("100.00"));
            verify(projektAnteilRepository, never()).saveAll(anyList());
        }

        @Test
        void uebersichtMitAbweichungUndZiel() {
            List<LieferantDokumentPosition> positionen = rechnungMitFracht();
            positionen.get(0).setProjekt(projektA);
            gd.setBetragNetto(new BigDecimal("1200.00"));

            var uebersicht = service.positionsUebersicht(10L);

            assertThat(uebersicht.auslesbar()).isTrue();
            assertThat(uebersicht.summePositionen()).isEqualByComparingTo("1100");
            assertThat(uebersicht.abweichung()).isEqualByComparingTo("100.00");
            assertThat(uebersicht.abweichungAuffaellig()).isTrue();
            assertThat(uebersicht.nachPositionenAufgeteilt()).isTrue();
            assertThat(uebersicht.positionen().getFirst().projektName()).isEqualTo("BV Mustermann");
        }

        @Test
        void unbekanntesDokument() {
            assertThatThrownBy(() -> service.positionsUebersicht(12345L)).isInstanceOf(NoSuchElementException.class);
            assertThatThrownBy(() -> service.positionsUebersicht(-1L)).isInstanceOf(NoSuchElementException.class);
            assertThatThrownBy(() -> service.positionsUebersicht(null)).isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    class ProzentUndBetrag {

        @Test
        void hebtPositionsaufteilungAuf() {
            int anzahl = service.speichereAnteile(10L, List.of(
                    new LieferantDokumentZuordnungService.Anteil(1L, null, null, new BigDecimal("60"), null, null),
                    new LieferantDokumentZuordnungService.Anteil(2L, null, null, new BigDecimal("40"), null, null)),
                    null);

            assertThat(anzahl).isEqualTo(2);
            verify(positionRepository).entferneZuordnungen(10L);
        }

        @Test
        void pruefungVorDemLoeschen() {
            assertThatThrownBy(() -> service.speichereAnteile(10L, List.of(
                    new LieferantDokumentZuordnungService.Anteil(99L, null, new BigDecimal("10"), null, null, null)),
                    null)).hasMessageContaining("Projekt nicht gefunden");
            verify(projektAnteilRepository, never()).deleteAll(anyList());
        }

        @Test
        void ungueltigeAnteile() {
            assertThatThrownBy(() -> service.speichereAnteile(10L, List.of(
                    new LieferantDokumentZuordnungService.Anteil(1L, null, BigDecimal.TEN, BigDecimal.TEN, null, null)),
                    null)).hasMessage("Nur Prozent oder Betrag angeben");
            assertThatThrownBy(() -> service.speichereAnteile(10L, List.of(
                    new LieferantDokumentZuordnungService.Anteil(1L, null, null, new BigDecimal("101"), null, null)),
                    null)).hasMessageContaining("zwischen 0 und 100");
            assertThatThrownBy(() -> service.speichereAnteile(10L, List.of(
                    new LieferantDokumentZuordnungService.Anteil(1L, null, new BigDecimal("2000"), null, null, null)),
                    null)).hasMessageContaining("Rechnungsbetrag");
            assertThatThrownBy(() -> service.speichereAnteile(10L, List.of(
                    new LieferantDokumentZuordnungService.Anteil(null, 9L, null, BigDecimal.TEN, null, 21)),
                    null)).hasMessageContaining("Streckung");
        }

        @Test
        void aufhebenLoeschtAuchPositionsziele() {
            service.hebeZuordnungAuf(10L);

            verify(positionRepository).entferneZuordnungen(10L);
            verify(geschaeftsdokumentRepository).save(gd);
            assertThat(gd.getLagerbestellung()).isFalse();
        }
    }

    @Test
    void pdfKopieVerlaesstNieDasAnhangVerzeichnis() throws Exception {
        java.nio.file.Path basis = java.nio.file.Files.createTempDirectory("zuordnung-test");
        try {
            setzeFeld("attachmentDir", basis.resolve("anhaenge").toString());
            setzeFeld("uploadDir", basis.resolve("projekte").toString());
            // Datei außerhalb des Anhang-Verzeichnisses, auf die ein manipulierter Name zeigt
            java.nio.file.Files.writeString(basis.resolve("geheim.pdf"), "%PDF");
            var lieferant = new org.example.kalkulationsprogramm.domain.Lieferanten();
            lieferant.setId(4L);
            var email = new org.example.kalkulationsprogramm.domain.Email();
            email.setLieferant(lieferant);
            var anhang = new org.example.kalkulationsprogramm.domain.EmailAttachment();
            anhang.setEmail(email);
            anhang.setStoredFilename("../../geheim.pdf");
            gd.getDokument().setAttachment(anhang);

            service.speichereAnteile(10L, List.of(
                    new LieferantDokumentZuordnungService.Anteil(1L, null, null, new BigDecimal("100"), null, null)),
                    null);

            verify(projektDokumentRepository, never()).save(org.mockito.ArgumentMatchers.any());
        } finally {
            try (var dateien = java.nio.file.Files.walk(basis)) {
                dateien.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private void setzeFeld(String name, Object wert) throws Exception {
        var feld = LieferantDokumentZuordnungService.class.getDeclaredField(name);
        feld.setAccessible(true);
        feld.set(service, wert);
    }

    @Test
    void auffaelligeAbweichung() {
        assertThat(LieferantDokumentZuordnungService.istAuffaellig(new BigDecimal("0.80"), new BigDecimal("10"))).isFalse();
        assertThat(LieferantDokumentZuordnungService.istAuffaellig(new BigDecimal("5"), new BigDecimal("1000"))).isFalse();
        assertThat(LieferantDokumentZuordnungService.istAuffaellig(new BigDecimal("15"), new BigDecimal("1000"))).isTrue();
        assertThat(LieferantDokumentZuordnungService.istAuffaellig(null, null)).isFalse();
    }
}
