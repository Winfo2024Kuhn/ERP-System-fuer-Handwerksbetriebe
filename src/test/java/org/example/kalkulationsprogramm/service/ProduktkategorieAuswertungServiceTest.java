package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieAuswertungZeileDto;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProduktkategorieAuswertungServiceTest {

    @Mock ProduktkategorieRepository produktkategorieRepository;
    @Mock ProjektProduktkategorieRepository projektProduktkategorieRepository;
    @Mock ZeitbuchungRepository zeitbuchungRepository;
    @Mock LeistungRepository leistungRepository;

    ProduktkategorieAuswertungService service;

    // Metallbau(1, Stück) > Geländer(2, Meter) > Edelstahl(3, Meter); Metallbau > Tore(4, Stück); Fliesen(5, m²)
    Produktkategorie metallbau;
    Produktkategorie gelaender;
    Produktkategorie edelstahl;
    Produktkategorie tore;
    Produktkategorie fliesen;

    private static Produktkategorie ordner(long id, String name, Produktkategorie parent, Verrechnungseinheit e) {
        Produktkategorie k = KategorieBaumTest.ordner(id, name, parent);
        k.setVerrechnungseinheit(e);
        return k;
    }

    private static Object[] zeile(long kategorie, long projekt, String wert) {
        return new Object[] { kategorie, projekt, wert != null ? new BigDecimal(wert) : null };
    }

    @BeforeEach
    void setUp() {
        service = new ProduktkategorieAuswertungService(produktkategorieRepository, projektProduktkategorieRepository,
                zeitbuchungRepository, leistungRepository, new ProduktkategorieMapper());
        metallbau = ordner(1, "Metallbau", null, Verrechnungseinheit.STUECK);
        gelaender = ordner(2, "Geländer", metallbau, Verrechnungseinheit.LAUFENDE_METER);
        edelstahl = ordner(3, "Edelstahl", gelaender, Verrechnungseinheit.LAUFENDE_METER);
        tore = ordner(4, "Tore", metallbau, Verrechnungseinheit.STUECK);
        fliesen = ordner(5, "Fliesen", null, Verrechnungseinheit.QUADRATMETER);
        fliesen.setBildUrl("fliesen.png");
        when(produktkategorieRepository.findAllWithParent())
                .thenReturn(List.of(metallbau, gelaender, edelstahl, tore, fliesen));
        // Projekt 10: Geländer 4h/8m + Edelstahl 6h/12m; Projekt 11: Edelstahl 5h, keine Menge;
        // Projekt 12: Tore 3h/2 Stück; Projekt 13: Fliesen nur Menge
        when(zeitbuchungRepository.summiereStundenAbgeschlossenerProjekte(any(), any())).thenReturn(List.of(
                zeile(2, 10, "4"), zeile(3, 10, "6"), zeile(3, 11, "5"), zeile(4, 12, "3"), zeile(4, 12, null)));
        when(projektProduktkategorieRepository.findeMengenAbgeschlossenerProjekte(any(), any())).thenReturn(List.of(
                zeile(2, 10, "8"), zeile(3, 10, "12"), zeile(4, 12, "2"), zeile(5, 13, "40")));
        when(leistungRepository.zaehleJeKategorie()).thenReturn(List.of(
                new Object[] { 3L, 4L }, new Object[] { 4L, 2L }, new Object[] { 5L, 1L }));
    }

    @Test
    void hauptordnerMitAllenUnterordnernNachStundenSortiert() {
        List<KategorieAuswertungZeileDto> zeilen = service.werteAus(null, null);

        assertThat(zeilen).extracting(KategorieAuswertungZeileDto::bezeichnung).containsExactly("Metallbau", "Fliesen");
        KategorieAuswertungZeileDto metall = zeilen.get(0);
        assertThat(metall.stunden()).isEqualTo(18.0);
        assertThat(metall.projektAnzahl()).isEqualTo(3);
        assertThat(metall.leistungAnzahl()).isEqualTo(6);
        assertThat(metall.leaf()).isFalse();
        // Nur Stück-Ordner (Metallbau, Tore) zählen für Menge und Quote.
        assertThat(metall.verrechnungseinheit()).isEqualTo("Stück");
        assertThat(metall.menge()).isEqualTo(2.0);
        assertThat(metall.stundenProEinheit()).isEqualTo(1.5);

        KategorieAuswertungZeileDto fliesenZeile = zeilen.get(1);
        assertThat(fliesenZeile.stunden()).isZero();
        assertThat(fliesenZeile.menge()).isEqualTo(40.0);
        assertThat(fliesenZeile.stundenProEinheit()).isNull();
        assertThat(fliesenZeile.bildUrl()).isEqualTo("/api/images/fliesen.png");
        assertThat(fliesenZeile.leaf()).isTrue();
    }

    @Test
    void unterordnerEinesOrdnersUndQuoteNurAusProjektenMitZeitUndMenge() {
        List<KategorieAuswertungZeileDto> zeilen = service.werteAus(null, 1L);

        assertThat(zeilen).extracting(KategorieAuswertungZeileDto::bezeichnung).containsExactly("Geländer", "Tore");
        KategorieAuswertungZeileDto gel = zeilen.get(0);
        assertThat(gel.stunden()).isEqualTo(15.0);
        assertThat(gel.menge()).isEqualTo(20.0);
        // Projekt 11 hat keine Menge und zählt nicht in die Quote: 10h / 20m
        assertThat(gel.stundenProEinheit()).isEqualTo(0.5);
        assertThat(gel.projektAnzahl()).isEqualTo(2);
    }

    @Test
    void jahrWirdAlsAbschlusszeitraumWeitergegeben() {
        service.werteAus(2026, null);

        verify(zeitbuchungRepository).summiereStundenAbgeschlossenerProjekte(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        verify(projektProduktkategorieRepository).findeMengenAbgeschlossenerProjekte(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
    }

    @Test
    void ordnerOhneUnterordnerLiefertLeereListeOhneAbfragen() {
        assertThat(service.werteAus(null, 5L)).isEmpty();
        verify(zeitbuchungRepository, never()).summiereStundenAbgeschlossenerProjekte(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1999, 2101, -1, 0, Integer.MAX_VALUE })
    void unsinnigesJahrIst400(int jahr) {
        assertThatThrownBy(() -> service.werteAus(jahr, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @ValueSource(longs = { -1L, 0L, Long.MAX_VALUE })
    void unbekannterOrdnerIst404(long parentId) {
        assertThatThrownBy(() -> service.werteAus(null, parentId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
