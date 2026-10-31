package org.example.kalkulationsprogramm.service.ids;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.ids.IdsVorgang;
import org.example.kalkulationsprogramm.domain.ids.IdsVorgangStatus;
import org.example.kalkulationsprogramm.domain.ids.IdsWarenkorbPosition;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsArtikelstammAnfrageDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsArtikelstammAnfrageDto.Aktualisieren;
import org.example.kalkulationsprogramm.dto.Ids.IdsArtikelstammAnfrageDto.NeuAnlegen;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.service.ArtikelServiceContract;
import org.example.kalkulationsprogramm.service.AufschlagService;
import org.example.kalkulationsprogramm.service.LieferantArtikelpreisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdsArtikelstammAbgleichTest {

    @Mock private LieferantenArtikelPreiseRepository preiseRepository;
    @Mock private LieferantArtikelpreisService preisService;
    @Mock private ArtikelServiceContract artikelService;
    @Mock private AufschlagService aufschlagService;
    @Mock private IdsShopBesuchService shopBesuch;
    @InjectMocks private IdsArtikelstammAbgleich abgleich;

    private Lieferanten wuerth;
    private IdsVorgang vorgang;
    private Artikel silikon;

    @BeforeEach
    void setUp() {
        wuerth = new Lieferanten();
        wuerth.setId(7L);
        wuerth.setLieferantenname("Würth");
        vorgang = new IdsVorgang();
        vorgang.setId(42L);
        vorgang.setLieferant(wuerth);
        vorgang.setStatus(IdsVorgangStatus.EMPFANGEN);
        vorgang.addPosition(position(0, "0892 161", "Silikon", "Stk", "8.40"));
        vorgang.addPosition(position(1, "0903-100", "Schraube", "m", null));

        silikon = new Artikel();
        silikon.setId(500L);
        silikon.setProduktname("Silikon transparent");
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(silikon);
        preis.setLieferant(wuerth);
        preis.setExterneArtikelnummer("0892 161");
        preis.setPreis(new BigDecimal("7.90"));
        when(preiseRepository.findByLieferant_IdAndAktuellTrueAndExterneArtikelnummerIn(eq(7L), anyCollection()))
                .thenReturn(List.of(preis));
    }

    private static IdsWarenkorbPosition position(int index, String nummer, String bez, String einheit, String preis) {
        IdsWarenkorbPosition p = new IdsWarenkorbPosition();
        p.setPositionIndex(index);
        p.setArtikelnummer(nummer);
        p.setBezeichnung(bez);
        p.setMenge(BigDecimal.ONE);
        p.setEinheit(einheit);
        p.setEinkaufspreis(preis == null ? null : new BigDecimal(preis));
        return p;
    }

    @Test
    @DisplayName("Ansicht: bekannter Artikel mit seinem Aufschlag, unbekannter mit dem allgemeinen")
    void ansicht() {
        when(aufschlagService.allgemein()).thenReturn(new BigDecimal("20"));
        Function<Artikel, AufschlagService.Wirksam> fuerListe =
                a -> new AufschlagService.Wirksam(new BigDecimal("35"), AufschlagService.Quelle.KATEGORIE, "Dichtstoffe");
        when(aufschlagService.fuerListe()).thenReturn(fuerListe);

        var dto = abgleich.ansicht(vorgang);

        assertThat(dto.standardAufschlagProzent()).isEqualByComparingTo("20");
        assertThat(dto.lieferantName()).isEqualTo("Würth");
        var bekannt = dto.positionen().get(0);
        assertThat(bekannt.stamm().artikelId()).isEqualTo(500L);
        assertThat(bekannt.stamm().bezeichnung()).isEqualTo("Silikon transparent");
        assertThat(bekannt.stamm().aktuellerPreis()).isEqualByComparingTo("7.90");
        assertThat(bekannt.aufschlagProzent()).isEqualByComparingTo("35");
        assertThat(bekannt.aufschlagQuelle()).isEqualTo(AufschlagService.Quelle.KATEGORIE);
        var unbekannt = dto.positionen().get(1);
        assertThat(unbekannt.stamm()).isNull();
        assertThat(unbekannt.aufschlagQuelle()).isEqualTo(AufschlagService.Quelle.ALLGEMEIN);

        when(aufschlagService.allgemein()).thenReturn(null);
        assertThat(abgleich.ansicht(vorgang).positionen().get(1).aufschlagQuelle()).isEqualTo(AufschlagService.Quelle.KEINER);
    }

    @Test
    @DisplayName("Übernehmen: Preis aktualisieren mit Quelle IDS_SHOP, unbekannten Artikel neu anlegen")
    void uebernimm() {
        Artikel neu = new Artikel();
        neu.setId(600L);
        when(artikelService.erstelleArtikel(any(ArtikelCreateDto.class), eq(PreisQuelle.IDS_SHOP))).thenReturn(neu);

        var ergebnis = abgleich.uebernimm(vorgang, new IdsArtikelstammAnfrageDto(
                List.of(new Aktualisieren(0, new BigDecimal("8.40"))),
                List.of(new NeuAnlegen(1, null, "<b>Schraube</b> 6x100"))));

        assertThat(ergebnis.aktualisiert()).isEqualTo(1);
        assertThat(ergebnis.angelegt()).isEqualTo(1);
        assertThat(ergebnis.artikelIds()).containsEntry(0, 500L).containsEntry(1, 600L);
        verify(preisService).schreibePreisstand(eq(7L), eq(500L), eq(new BigDecimal("8.40")), eq("0892 161"),
                eq(PreisQuelle.IDS_SHOP), any());
        ArgumentCaptor<ArtikelCreateDto> dto = ArgumentCaptor.forClass(ArtikelCreateDto.class);
        verify(artikelService).erstelleArtikel(dto.capture(), eq(PreisQuelle.IDS_SHOP));
        assertThat(dto.getValue().getProduktname()).isEqualTo("Schraube 6x100");
        assertThat(dto.getValue().getExterneArtikelnummer()).isEqualTo("0903-100");
        assertThat(dto.getValue().getLieferantId()).isEqualTo(7L);
        assertThat(dto.getValue().getVerrechnungseinheit()).isEqualTo(Verrechnungseinheit.LAUFENDE_METER);
    }

    @Test
    @DisplayName("Im Dialog geänderter Einkaufspreis wird als „von Hand“ gespeichert, nicht als Shop-Preis")
    void geaenderterPreisIstManuell() {
        abgleich.uebernimm(vorgang, new IdsArtikelstammAnfrageDto(List.of(new Aktualisieren(0, new BigDecimal("9.10"))), List.of()));
        verify(preisService).schreibePreisstand(eq(7L), eq(500L), eq(new BigDecimal("9.10")), eq("0892 161"),
                eq(PreisQuelle.MANUELL), any());
    }

    @Test
    @DisplayName("Gleicher Preis, „Neu anlegen“ für schon bekannten Artikel und unbekannter „Aktualisieren“: nichts doppelt")
    void nichtsDoppelt() {
        var ergebnis = abgleich.uebernimm(vorgang, new IdsArtikelstammAnfrageDto(
                List.of(new Aktualisieren(1, new BigDecimal("1.00"))),
                List.of(new NeuAnlegen(0, new BigDecimal("7.90"), "Silikon"))));

        assertThat(ergebnis.aktualisiert()).isZero();
        assertThat(ergebnis.angelegt()).isZero();
        assertThat(ergebnis.unveraendert()).isEqualTo(2);
        assertThat(ergebnis.artikelIds()).containsOnlyKeys(0);
        verify(preisService, never()).schreibePreisstand(any(), any(), any(), any(), any(), any());
        verify(artikelService, never()).erstelleArtikel(any(), any());
    }

    @Test
    @DisplayName("Ungültige Anfragen: kein Warenkorb, fremde Zeile, negativer Preis, leere oder lange Bezeichnung")
    void ungueltig() {
        assertThatThrownBy(() -> abgleich.uebernimm(vorgang, null)).hasMessageContaining("Bitte angeben");
        assertThatThrownBy(() -> abgleich.uebernimm(vorgang, anfrageAktualisieren(5, "1"))).hasMessageContaining("gibt es im Warenkorb nicht");
        assertThatThrownBy(() -> abgleich.uebernimm(vorgang, anfrageAktualisieren(-1, "1"))).hasMessageContaining("gibt es im Warenkorb nicht");
        assertThatThrownBy(() -> abgleich.uebernimm(vorgang, anfrageAktualisieren(0, "-1"))).hasMessageContaining("ungültig");
        assertThatThrownBy(() -> abgleich.uebernimm(vorgang, anfrageAktualisieren(0, "1000000000"))).hasMessageContaining("ungültig");
        assertThatThrownBy(() -> abgleich.uebernimm(vorgang,
                new IdsArtikelstammAnfrageDto(List.of(new Aktualisieren(0, null)), null))).hasMessageContaining("Einkaufspreis angeben");
        assertThatThrownBy(() -> abgleich.uebernimm(vorgang,
                new IdsArtikelstammAnfrageDto(null, List.of(new NeuAnlegen(1, null, "<script></script>"))))).hasMessageContaining("Bezeichnung angeben");
        assertThatThrownBy(() -> abgleich.uebernimm(vorgang,
                new IdsArtikelstammAnfrageDto(null, List.of(new NeuAnlegen(1, null, "x".repeat(256)))))).hasMessageContaining("höchstens 255");

        vorgang.setStatus(IdsVorgangStatus.WARTET);
        assertThatThrownBy(() -> abgleich.uebernimm(vorgang, anfrageAktualisieren(0, "1"))).hasMessageContaining("kein Warenkorb");
    }

    private static IdsArtikelstammAnfrageDto anfrageAktualisieren(Integer index, String preis) {
        return new IdsArtikelstammAnfrageDto(List.of(new Aktualisieren(index, new BigDecimal(preis))), List.of());
    }

    @Test
    @DisplayName("Über die Vorgangs-ID: lädt den Besuch des Benutzers in derselben Transaktion")
    void ueberVorgangsId() {
        when(shopBesuch.lade(42L, 70L)).thenReturn(vorgang);
        when(aufschlagService.fuerListe()).thenReturn(a -> new AufschlagService.Wirksam(null, AufschlagService.Quelle.KEINER, null));
        assertThat(abgleich.ansicht(42L, 70L).vorgangId()).isEqualTo(42L);
        assertThat(abgleich.uebernimm(42L, 70L, new IdsArtikelstammAnfrageDto(List.of(), List.of())).aktualisiert()).isZero();
    }

    @Test
    @DisplayName("Treffer: Groß-/Kleinschreibung egal; bei doppeltem Altbestand gewinnt der kleinere Artikel")
    void treffer() {
        Artikel alt = new Artikel();
        alt.setId(400L);
        alt.setKurzbeschreibung("Silikon alt");
        LieferantenArtikelPreise zweiter = new LieferantenArtikelPreise();
        zweiter.setArtikel(alt);
        zweiter.setExterneArtikelnummer("0892 161");
        LieferantenArtikelPreise erster = new LieferantenArtikelPreise();
        erster.setArtikel(silikon);
        erster.setExterneArtikelnummer("0892 161");
        when(preiseRepository.findByLieferant_IdAndAktuellTrueAndExterneArtikelnummerIn(eq(7L), anyCollection()))
                .thenReturn(List.of(erster, zweiter));

        var treffer = abgleich.stammTreffer(7L, List.of(" 0892 161 ", "", "abc"));
        assertThat(treffer.get("0892 161").getArtikel().getId()).isEqualTo(400L);
        assertThat(abgleich.stammTreffer(7L, Set.of())).isEmpty();
        assertThat(IdsArtikelstammAbgleich.schluessel("ab-1")).isEqualTo("AB-1");
        assertThat(IdsArtikelstammAbgleich.schluessel(" 032225   500 ")).isEqualTo("032225 500");
        assertThat(IdsArtikelstammAbgleich.schluessel(null)).isNull();
    }
}
