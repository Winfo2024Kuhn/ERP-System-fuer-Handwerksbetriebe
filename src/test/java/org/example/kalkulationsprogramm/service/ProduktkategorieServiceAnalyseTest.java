package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieAnalyseDto;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests für die Analyse-Logik in ProduktkategorieService:
 * - Nur abgeschlossene Projekte (abgeschlossen=true) fließen ein (via Repository-Query)
 * - Projekte ohne Zeiterfassung werden nicht angezeigt und nicht berechnet
 * - R² und Residual-Standardabweichung werden korrekt berechnet
 */
@ExtendWith(MockitoExtension.class)
class ProduktkategorieServiceAnalyseTest {

    @Mock
    private ProduktkategorieRepository produktkategorieRepository;

    @Mock
    private ProjektRepository projektRepository;

    @Mock
    private DateiSpeicherService dateiSpeicherService;

    private ProduktkategorieService service;

    private Produktkategorie kategorie;

    @BeforeEach
    void setUp() {
        service = new ProduktkategorieService(
                produktkategorieRepository,
                projektRepository,
                new ProduktkategorieMapper(),
                dateiSpeicherService
        );

        kategorie = new Produktkategorie();
        kategorie.setId(1L);
        kategorie.setBezeichnung("Metallfassaden");
        kategorie.setVerrechnungseinheit(Verrechnungseinheit.QUADRATMETER);

        when(produktkategorieRepository.findById(1L)).thenReturn(Optional.of(kategorie));
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private Arbeitsgang arbeitsgang(Long id, String beschreibung) {
        Arbeitsgang ag = new Arbeitsgang();
        ag.setId(id);
        ag.setBeschreibung(beschreibung);
        return ag;
    }

    /** Erstellt ein Projekt mit ProjektProduktkategorie (menge) und optionalen Zeitbuchungen. */
    private Projekt projekt(Long id, String auftragsnummer, double menge, double... stundenJeZeitbuchung) {
        Projekt p = new Projekt();
        p.setId(id);
        p.setBauvorhaben("Projekt " + auftragsnummer);
        p.setAuftragsnummer(auftragsnummer);
        p.setAbgeschlossen(true);
        p.setBruttoPreis(BigDecimal.ZERO);

        ProjektProduktkategorie ppk = new ProjektProduktkategorie();
        ppk.setProjekt(p);
        ppk.setProduktkategorie(kategorie);
        ppk.setMenge(BigDecimal.valueOf(menge));
        p.getProjektProduktkategorien().add(ppk);

        Arbeitsgang ag = arbeitsgang(1L, "Montage");
        for (double h : stundenJeZeitbuchung) {
            Zeitbuchung z = new Zeitbuchung();
            z.setArbeitsgang(ag);
            z.setProjektProduktkategorie(ppk);
            z.setAnzahlInStunden(BigDecimal.valueOf(h));
            p.getZeitbuchungen().add(z);
        }

        return p;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void projekteOhneZeitenWerdenNichtInAnalyseAufgenommen() {
        // Projekt ohne Zeitbuchungen
        Projekt ohneZeit = projekt(1L, "A-001", 50.0 /* keine Stunden */);
        // Projekt mit Zeitbuchungen
        Projekt mitZeit = projekt(2L, "A-002", 100.0, 8.0);

        when(projektRepository.findByProduktkategorieIds(List.of(1L)))
                .thenReturn(List.of(ohneZeit, mitZeit));

        ProduktkategorieAnalyseDto result = service.analysiereKategorie(1L, null);

        assertThat(result.getProjekte())
                .as("Projekt ohne Zeiten darf nicht in Analyse erscheinen")
                .hasSize(1);
        assertThat(result.getProjekte().get(0).getAuftragsnummer()).isEqualTo("A-002");
    }

    @Test
    void projekteOhneZeitenFliessenNichtInDatenpunkteEin() {
        Projekt ohneZeit = projekt(1L, "A-001", 50.0);
        Projekt mitZeit  = projekt(2L, "A-002", 100.0, 10.0);

        when(projektRepository.findByProduktkategorieIds(List.of(1L)))
                .thenReturn(List.of(ohneZeit, mitZeit));

        ProduktkategorieAnalyseDto result = service.analysiereKategorie(1L, null);

        assertThat(result.getDatenpunkte())
                .as("Nur Projekte mit Zeiterfassung zählen als Datenpunkte")
                .isEqualTo(1);
    }

    @Test
    void keineProjekteErgibtLeereAnalyse() {
        when(projektRepository.findByProduktkategorieIds(List.of(1L)))
                .thenReturn(List.of());

        ProduktkategorieAnalyseDto result = service.analysiereKategorie(1L, null);

        assertThat(result.getProjekte()).isEmpty();
        assertThat(result.getDatenpunkte()).isZero();
        assertThat(result.getFixzeit()).isZero();
        assertThat(result.getSteigung()).isZero();
        assertThat(result.getRQuadrat()).isZero();
    }

    @Test
    void nurProjekteMitZeitenFliessen_alleOhneZeiten() {
        // Alle Projekte ohne Zeiten → Analyse komplett leer
        Projekt p1 = projekt(1L, "A-001", 50.0);
        Projekt p2 = projekt(2L, "A-002", 80.0);

        when(projektRepository.findByProduktkategorieIds(List.of(1L)))
                .thenReturn(List.of(p1, p2));

        ProduktkategorieAnalyseDto result = service.analysiereKategorie(1L, null);

        assertThat(result.getProjekte()).isEmpty();
        assertThat(result.getDatenpunkte()).isZero();
    }

    @Test
    void rQuadratIstNull_BeiNurEinemDatenpunkt() {
        // R² lässt sich erst ab n>=2 sinnvoll berechnen
        Projekt einziges = projekt(1L, "A-001", 100.0, 10.0);

        when(projektRepository.findByProduktkategorieIds(List.of(1L)))
                .thenReturn(List.of(einziges));

        ProduktkategorieAnalyseDto result = service.analysiereKategorie(1L, null);

        assertThat(result.getDatenpunkte()).isEqualTo(1);
        assertThat(result.getRQuadrat()).isZero();
        assertThat(result.getResidualStdAbweichung()).isZero();
    }

    @Test
    void rQuadratIstPositiv_BeiMehrerenDatenpunkten() {
        // Drei linear steigende Projekte → R² nahe 1
        Projekt p1 = projekt(1L, "A-001", 10.0, 5.0);
        Projekt p2 = projekt(2L, "A-002", 20.0, 10.0);
        Projekt p3 = projekt(3L, "A-003", 30.0, 15.0);

        when(projektRepository.findByProduktkategorieIds(List.of(1L)))
                .thenReturn(List.of(p1, p2, p3));

        ProduktkategorieAnalyseDto result = service.analysiereKategorie(1L, null);

        assertThat(result.getDatenpunkte()).isEqualTo(3);
        assertThat(result.getRQuadrat()).isGreaterThan(0.9);
        assertThat(result.getResidualStdAbweichung()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void analyseJahrFilterWirdAnRepoWeitergegeben() {
        // Stellt sicher, dass bei Jahr-Angabe die korrekte Repository-Methode aufgerufen wird
        when(projektRepository.findByProduktkategorieIdsAndAbschlussdatumBetween(
                any(), any(), any()))
                .thenReturn(List.of());

        ProduktkategorieAnalyseDto result = service.analysiereKategorie(1L, 2024);

        assertThat(result.getProjekte()).isEmpty();
    }

    @Test
    void zeitGesamt_IstSummeAllerZeitbuchungenDesProjekts() {
        // Projekt mit 3 Zeitbuchungen à 4 h → zeitGesamt = 12
        Projekt p = projekt(1L, "A-001", 100.0, 4.0, 4.0, 4.0);

        when(projektRepository.findByProduktkategorieIds(List.of(1L)))
                .thenReturn(List.of(p));

        ProduktkategorieAnalyseDto result = service.analysiereKategorie(1L, null);

        assertThat(result.getProjekte()).hasSize(1);
        assertThat(result.getProjekte().get(0).getZeitGesamt()).isEqualTo(12.0);
    }

    @Test
    void verrechnungseinheitWirdKorrektUebertragen() {
        Projekt p = projekt(1L, "A-001", 50.0, 5.0);

        when(projektRepository.findByProduktkategorieIds(List.of(1L)))
                .thenReturn(List.of(p));

        ProduktkategorieAnalyseDto result = service.analysiereKategorie(1L, null);

        assertThat(result.getVerrechnungseinheit())
                .isEqualTo(Verrechnungseinheit.QUADRATMETER.getAnzeigename());
    }
}
