package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für ProjektAuswertungPdfService.
 *
 * Testet die reinen Logik-Methoden ohne PDF-Generierung:
 * - buildKategoriePfad: vollständiger Kategoriepfad
 * - resolveGroupKey:    dynamische Gruppierung
 * - groupByLabel:       Anzeigename der Gruppierung
 * - buildComparator:    Sortier-Comparator
 * - generatePdf:        Fehlverhalten bei leerem Ergebnis
 */
@ExtendWith(MockitoExtension.class)
class ProjektAuswertungPdfServiceTest {

    @Mock
    private ZeitbuchungRepository zeitbuchungRepository;

    @InjectMocks
    private ProjektAuswertungPdfService service;

    // ─── Hilfs-Factories ─────────────────────────────────────────────────────

    private Produktkategorie kategorie(String bezeichnung, Produktkategorie parent) {
        Produktkategorie k = new Produktkategorie();
        k.setBezeichnung(bezeichnung);
        k.setUebergeordneteKategorie(parent);
        k.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        return k;
    }

    private Mitarbeiter mitarbeiter(String vorname, String nachname, Qualifikation qualifikation) {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname(vorname);
        m.setNachname(nachname);
        m.setQualifikation(qualifikation);
        return m;
    }

    private Zeitbuchung buchungMitMitarbeiter(Mitarbeiter m) {
        Zeitbuchung b = new Zeitbuchung();
        b.setMitarbeiter(m);
        b.setStartZeit(LocalDateTime.of(2026, 4, 7, 8, 0));
        b.setEndeZeit(LocalDateTime.of(2026, 4, 7, 12, 0));
        return b;
    }

    // ─── buildKategoriePfad ───────────────────────────────────────────────────

    @Nested
    @DisplayName("buildKategoriePfad")
    class BuildKategoriePfadTests {

        @Test
        @DisplayName("null → Fallback-Strich")
        void nullKategorie() {
            assertThat(service.buildKategoriePfad(null)).isEqualTo("–");
        }

        @Test
        @DisplayName("Einstufige Kategorie → nur Bezeichnung")
        void einstufig() {
            Produktkategorie k = kategorie("Geländer", null);
            assertThat(service.buildKategoriePfad(k)).isEqualTo("Geländer");
        }

        @Test
        @DisplayName("Zweistufige Kategorie → Parent/Child")
        void zweistufig() {
            Produktkategorie root = kategorie("Geländer", null);
            Produktkategorie kind = kategorie("Edelstahl", root);
            assertThat(service.buildKategoriePfad(kind)).isEqualTo("Geländer/Edelstahl");
        }

        @Test
        @DisplayName("Dreistufige Kategorie → vollständiger Pfad")
        void dreistufig() {
            Produktkategorie root = kategorie("Geländer", null);
            Produktkategorie mitte = kategorie("Edelstahl", root);
            Produktkategorie blatt = kategorie("Stabfüllung", mitte);
            assertThat(service.buildKategoriePfad(blatt))
                    .isEqualTo("Geländer/Edelstahl/Stabfüllung");
        }
    }

    // ─── resolveGroupKey ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolveGroupKey")
    class ResolveGroupKeyTests {

        private Zeitbuchung buchungMitAllem;

        @BeforeEach
        void setUp() {
            Mitarbeiter m = mitarbeiter("Max", "Mustermann", Qualifikation.MEISTER);
            buchungMitAllem = buchungMitMitarbeiter(m);

            Arbeitsgang ag = new Arbeitsgang();
            ag.setBeschreibung("Montage");
            buchungMitAllem.setArbeitsgang(ag);
        }

        @Test
        @DisplayName("groupBy=arbeitsgang → Arbeitsgang-Beschreibung")
        void gruppierungArbeitsgang() {
            assertThat(service.resolveGroupKey(buchungMitAllem, "arbeitsgang"))
                    .isEqualTo("Montage");
        }

        @Test
        @DisplayName("groupBy=mitarbeiter → Vor- und Nachname")
        void gruppierungMitarbeiter() {
            assertThat(service.resolveGroupKey(buchungMitAllem, "mitarbeiter"))
                    .isEqualTo("Max Mustermann");
        }

        @Test
        @DisplayName("groupBy=qualifikation → Qualifikations-Bezeichnung")
        void gruppierungQualifikation() {
            assertThat(service.resolveGroupKey(buchungMitAllem, "qualifikation"))
                    .isEqualTo("Meister");
        }

        @Test
        @DisplayName("groupBy=datum → Datum im deutschen Format")
        void gruppierungDatum() {
            assertThat(service.resolveGroupKey(buchungMitAllem, "datum"))
                    .isEqualTo("07.04.2026");
        }

        @Test
        @DisplayName("kein Mitarbeiter + groupBy=mitarbeiter → Fallback")
        void keinMitarbeiter() {
            Zeitbuchung b = new Zeitbuchung();
            b.setStartZeit(LocalDateTime.of(2026, 4, 7, 8, 0));
            assertThat(service.resolveGroupKey(b, "mitarbeiter"))
                    .isEqualTo("Unbekannter Mitarbeiter");
        }

        @Test
        @DisplayName("kein Mitarbeiter + groupBy=qualifikation → Fallback")
        void keineQualifikation() {
            Zeitbuchung b = new Zeitbuchung();
            b.setStartZeit(LocalDateTime.of(2026, 4, 7, 8, 0));
            assertThat(service.resolveGroupKey(b, "qualifikation"))
                    .isEqualTo("Keine Qualifikation");
        }

        @Test
        @DisplayName("kein Datum + groupBy=datum → Fallback")
        void keinDatum() {
            Zeitbuchung b = new Zeitbuchung();
            assertThat(service.resolveGroupKey(b, "datum"))
                    .isEqualTo("Kein Datum");
        }

        @Test
        @DisplayName("kein Arbeitsgang + groupBy=arbeitsgang → Sonstiges")
        void keinArbeitsgang() {
            Zeitbuchung b = new Zeitbuchung();
            assertThat(service.resolveGroupKey(b, "arbeitsgang"))
                    .isEqualTo("Sonstiges");
        }

        @Test
        @DisplayName("unbekannter groupBy-Wert → arbeitsgang-Fallback")
        void unbekannterGroupBy() {
            Zeitbuchung b = new Zeitbuchung();
            assertThat(service.resolveGroupKey(b, "unbekannt"))
                    .isEqualTo("Sonstiges");
        }

        @Test
        @DisplayName("groupBy=produktkategorie mit Kategorie → Kategoriepfad")
        void gruppierungProduktkategorieVorhanden() {
            Produktkategorie root = kategorie("Geländer", null);
            Produktkategorie kind = kategorie("Edelstahl", root);

            ProjektProduktkategorie ppk = new ProjektProduktkategorie();
            ppk.setProduktkategorie(kind);

            Zeitbuchung b = new Zeitbuchung();
            b.setProjektProduktkategorie(ppk);

            assertThat(service.resolveGroupKey(b, "produktkategorie"))
                    .isEqualTo("Geländer/Edelstahl");
        }

        @Test
        @DisplayName("groupBy=produktkategorie ohne Kategorie → Fallback")
        void gruppierungProduktkategorieFehlend() {
            Zeitbuchung b = new Zeitbuchung();
            assertThat(service.resolveGroupKey(b, "produktkategorie"))
                    .isEqualTo("Keine Kategorie");
        }
    }

    // ─── groupByLabel ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("groupByLabel")
    class GroupByLabelTests {

        @Test void arbeitsgang()      { assertThat(service.groupByLabel("arbeitsgang")).isEqualTo("Arbeitsgang"); }
        @Test void qualifikation()    { assertThat(service.groupByLabel("qualifikation")).isEqualTo("Qualifikation"); }
        @Test void mitarbeiter()      { assertThat(service.groupByLabel("mitarbeiter")).isEqualTo("Mitarbeiter"); }
        @Test void datum()            { assertThat(service.groupByLabel("datum")).isEqualTo("Datum"); }
        @Test void produktkategorie() { assertThat(service.groupByLabel("produktkategorie")).isEqualTo("Produktkategorie"); }
        @Test void fallback()         { assertThat(service.groupByLabel("xyz")).isEqualTo("Arbeitsgang"); }
    }

    // ─── buildComparator ────────────────────────────────────────────────────

    @Nested
    @DisplayName("buildComparator")
    class BuildComparatorTests {

        private Zeitbuchung frueh;
        private Zeitbuchung spaet;

        @BeforeEach
        void setUp() {
            frueh = new Zeitbuchung();
            frueh.setStartZeit(LocalDateTime.of(2026, 4, 1, 8, 0));
            frueh.setEndeZeit(LocalDateTime.of(2026, 4, 1, 10, 0)); // 2h

            spaet = new Zeitbuchung();
            spaet.setStartZeit(LocalDateTime.of(2026, 4, 2, 8, 0));
            spaet.setEndeZeit(LocalDateTime.of(2026, 4, 2, 16, 0)); // 8h

            frueh.setMitarbeiter(mitarbeiter("Anna", "Müller", Qualifikation.FACHARBEITER));
            spaet.setMitarbeiter(mitarbeiter("Zara", "Zahn", Qualifikation.AUSZUBILDENDER));
        }

        @Test
        @DisplayName("sortField=datum → frühere Buchung kommt zuerst")
        void sortByDatum() {
            Comparator<Zeitbuchung> comp = service.buildComparator("datum");
            assertThat(comp.compare(frueh, spaet)).isNegative();
            assertThat(comp.compare(spaet, frueh)).isPositive();
        }

        @Test
        @DisplayName("sortField=null → wie datum")
        void sortByNull() {
            Comparator<Zeitbuchung> comp = service.buildComparator(null);
            assertThat(comp.compare(frueh, spaet)).isNegative();
        }

        @Test
        @DisplayName("sortField=dauer → kürzere Buchung kommt zuerst")
        void sortByDauer() {
            Comparator<Zeitbuchung> comp = service.buildComparator("dauer");
            assertThat(comp.compare(frueh, spaet)).isNegative(); // 2h < 8h
        }

        @Test
        @DisplayName("sortField=mitarbeiter → alphabetisch nach Nachname")
        void sortByMitarbeiter() {
            Comparator<Zeitbuchung> comp = service.buildComparator("mitarbeiter");
            // Müller < Zahn alphabetisch
            assertThat(comp.compare(frueh, spaet)).isNegative();
        }

        @Test
        @DisplayName("sortField=arbeitsgang → alphabetisch nach Arbeitsgang")
        void sortByArbeitsgang() {
            Arbeitsgang ag1 = new Arbeitsgang(); ag1.setBeschreibung("Abdichten");
            Arbeitsgang ag2 = new Arbeitsgang(); ag2.setBeschreibung("Montage");
            frueh.setArbeitsgang(ag1);
            spaet.setArbeitsgang(ag2);

            Comparator<Zeitbuchung> comp = service.buildComparator("arbeitsgang");
            assertThat(comp.compare(frueh, spaet)).isNegative(); // Abdichten < Montage
        }

        @Test
        @DisplayName("sortField=produktkategorie → alphabetisch nach Kategoriepfad")
        void sortByProduktkategorie() {
            Produktkategorie kat1 = kategorie("Balkone", null);
            Produktkategorie kat2 = kategorie("Zäune", null);

            ProjektProduktkategorie ppk1 = new ProjektProduktkategorie();
            ppk1.setProduktkategorie(kat1);
            ProjektProduktkategorie ppk2 = new ProjektProduktkategorie();
            ppk2.setProduktkategorie(kat2);

            frueh.setProjektProduktkategorie(ppk1);
            spaet.setProjektProduktkategorie(ppk2);

            Comparator<Zeitbuchung> comp = service.buildComparator("produktkategorie");
            assertThat(comp.compare(frueh, spaet)).isNegative(); // Balkone < Zäune
        }
    }

    // ─── generatePdf – Fehlverhalten ─────────────────────────────────────────

    @Nested
    @DisplayName("generatePdf – Validierung")
    class GeneratePdfValidierungTests {

        @Test
        @DisplayName("Keine Buchungen im Zeitraum → RuntimeException")
        void keineBuchungen() {
            when(zeitbuchungRepository.findByProjektId(1L)).thenReturn(List.of());

            assertThatThrownBy(() -> service.generatePdf(1L, null, null, "datum", "asc", "arbeitsgang"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Keine Buchungen");
        }
    }
}
