package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.En1090Anforderungen;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Diese Tests sind bewusst dicht am Wortlaut der Norm: Das Ergebnis des Service
 * ist die Single Source of Truth fuer alle Folgemodule (Wareneingang, WPS,
 * SAP-Freigabe, ZfP). Eine falsche Logik hier produziert stillschweigend
 * Fehlverhalten in allen abhaengigen Features.
 */
class En1090AnforderungenServiceTest {

    private ProjektRepository projektRepository;
    private En1090AnforderungenService service;

    @BeforeEach
    void setUp() {
        projektRepository = mock(ProjektRepository.class);
        service = new En1090AnforderungenService(projektRepository);
    }

    // ---- fuerExcKlasse ---------------------------------------------------

    @Test
    void fuerExcKlasse_null_istNichtPflichtig() {
        assertThat(service.fuerExcKlasse(null).en1090Pflichtig()).isFalse();
    }

    @Test
    void fuerExcKlasse_leererString_istNichtPflichtig() {
        assertThat(service.fuerExcKlasse("").en1090Pflichtig()).isFalse();
    }

    @Test
    void fuerExcKlasse_whitespace_istNichtPflichtig() {
        assertThat(service.fuerExcKlasse("   ").en1090Pflichtig()).isFalse();
    }

    @Test
    void fuerExcKlasse_exc1_istPflichtig() {
        assertThat(service.fuerExcKlasse("EXC_1").en1090Pflichtig()).isTrue();
    }

    @Test
    void fuerExcKlasse_exc2_istPflichtig() {
        assertThat(service.fuerExcKlasse("EXC_2").en1090Pflichtig()).isTrue();
    }

    @Test
    void fuerExcKlasse_exc1_mitWhitespace_istPflichtig() {
        assertThat(service.fuerExcKlasse("  EXC_1 ").en1090Pflichtig()).isTrue();
    }

    @Test
    void fuerExcKlasse_exc3_wieNull_istNichtPflichtig() {
        // EXC 3 ist im ERP entfernt worden und wird wie null behandelt.
        assertThat(service.fuerExcKlasse("EXC_3").en1090Pflichtig()).isFalse();
    }

    @Test
    void fuerExcKlasse_exc4_wieNull_istNichtPflichtig() {
        assertThat(service.fuerExcKlasse("EXC_4").en1090Pflichtig()).isFalse();
    }

    @Test
    void fuerExcKlasse_unbekannterWert_istNichtPflichtig() {
        assertThat(service.fuerExcKlasse("FOO").en1090Pflichtig()).isFalse();
    }

    @Test
    void fuerExcKlasse_klammeschreibung_nichtPflichtig() {
        // Wir akzeptieren bewusst nur die kanonischen Enum-Strings EXC_1 / EXC_2.
        assertThat(service.fuerExcKlasse("exc_1").en1090Pflichtig()).isFalse();
        assertThat(service.fuerExcKlasse("EXC 1").en1090Pflichtig()).isFalse();
    }

    @Test
    void konstantenSindStabil() {
        // Ein Folgemodul kann bedenkenlos gegen die Konstanten vergleichen.
        assertThat(service.fuerExcKlasse("EXC_1")).isEqualTo(En1090Anforderungen.PFLICHTIG);
        assertThat(service.fuerExcKlasse(null)).isEqualTo(En1090Anforderungen.KEINE);
    }

    // ---- fuerProjekt -----------------------------------------------------

    @Test
    void fuerProjekt_nullId_istKeine() {
        assertThat(service.fuerProjekt(null)).isEqualTo(En1090Anforderungen.KEINE);
    }

    @Test
    void fuerProjekt_nichtGefunden_wirftIllegalArgument() {
        when(projektRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fuerProjekt(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void fuerProjekt_ohneExcKlasse_istKeine() {
        Projekt p = new Projekt();
        p.setId(7L);
        p.setExcKlasse(null);
        when(projektRepository.findById(7L)).thenReturn(Optional.of(p));

        assertThat(service.fuerProjekt(7L).en1090Pflichtig()).isFalse();
    }

    @Test
    void fuerProjekt_exc1_istPflichtig() {
        Projekt p = new Projekt();
        p.setId(7L);
        p.setExcKlasse("EXC_1");
        when(projektRepository.findById(7L)).thenReturn(Optional.of(p));

        assertThat(service.fuerProjekt(7L).en1090Pflichtig()).isTrue();
    }

    @Test
    void fuerProjekt_exc2_istPflichtig() {
        Projekt p = new Projekt();
        p.setId(8L);
        p.setExcKlasse("EXC_2");
        when(projektRepository.findById(8L)).thenReturn(Optional.of(p));

        assertThat(service.fuerProjekt(8L).en1090Pflichtig()).isTrue();
    }

    @Test
    void fuerProjekt_exc3_wieKeinEn1090() {
        Projekt p = new Projekt();
        p.setId(9L);
        p.setExcKlasse("EXC_3");
        when(projektRepository.findById(9L)).thenReturn(Optional.of(p));

        assertThat(service.fuerProjekt(9L).en1090Pflichtig()).isFalse();
    }
}
