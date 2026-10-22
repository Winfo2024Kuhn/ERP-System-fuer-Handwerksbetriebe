package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.service.AufschlagService.Quelle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AufschlagServiceTest {

    private final SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    private final KategorieRepository kategorien = mock(KategorieRepository.class);
    private final Map<String, String> gespeichert = new HashMap<>();
    private AufschlagService service;

    private static Kategorie kategorie(int id, String name, Kategorie eltern, String prozent) {
        Kategorie k = new Kategorie();
        k.setId(id);
        k.setBeschreibung(name);
        k.setParentKategorie(eltern);
        k.setVerkaufsaufschlagProzent(prozent == null ? null : new BigDecimal(prozent));
        return k;
    }

    private static Artikel artikel(Kategorie k, String prozent) {
        Artikel a = new Artikel();
        a.setKategorie(k);
        a.setVerkaufsaufschlagProzent(prozent == null ? null : new BigDecimal(prozent));
        return a;
    }

    @BeforeEach
    void setUp() {
        when(einstellungen.get(anyString(), any())).thenAnswer(a -> gespeichert.getOrDefault(a.<String>getArgument(0), a.getArgument(1)));
        org.mockito.Mockito.doAnswer(a -> { gespeichert.put(a.getArgument(0), a.getArgument(1)); return null; }).when(einstellungen).save(anyString(), any(), any());
        when(kategorien.save(any(Kategorie.class))).thenAnswer(a -> a.getArgument(0));
        service = new AufschlagService(einstellungen, kategorien);
    }

    @Test
    void dasGenauereGewinnt_ArtikelVorKategorieVorAllgemein() {
        Kategorie rohre = kategorie(1, "Rohre", null, "30");
        gespeichert.put(AufschlagService.ALLGEMEIN_SCHLUESSEL, "20.00");

        var amArtikel = service.wirksamer(artikel(rohre, "45"));
        var ausKategorie = service.wirksamer(artikel(rohre, null));
        var allgemein = service.wirksamer(artikel(kategorie(2, "Bleche", null, null), null));

        assertThat(amArtikel.quelle()).isEqualTo(Quelle.ARTIKEL);
        assertThat(amArtikel.prozent()).isEqualByComparingTo("45");
        assertThat(ausKategorie.quelle()).isEqualTo(Quelle.KATEGORIE);
        assertThat(ausKategorie.prozent()).isEqualByComparingTo("30");
        assertThat(ausKategorie.quellenName()).isEqualTo("Rohre");
        assertThat(allgemein.quelle()).isEqualTo(Quelle.ALLGEMEIN);
        assertThat(allgemein.prozent()).isEqualByComparingTo("20");
    }

    @Test
    void eineUnterkategorieErbtVonDerNaechstenOberkategorieMitAufschlag() {
        Kategorie stahl = kategorie(1, "Stahl", null, "25");
        Kategorie rohre = kategorie(2, "Rohre", stahl, null);
        Kategorie quadrat = kategorie(3, "Quadratrohre", rohre, null);
        Kategorie eigener = kategorie(4, "Sonderrohre", rohre, "0");

        var geerbt = service.wirksamer(artikel(quadrat, null));
        var nullProzent = service.wirksamer(artikel(eigener, null));

        assertThat(geerbt.quelle()).isEqualTo(Quelle.KATEGORIE);
        assertThat(geerbt.quellenName()).as("kommt von Stahl, nicht von Rohre").isEqualTo("Stahl");
        assertThat(nullProzent.prozent()).as("0 Prozent ist ein eigener Wert und kein „nicht eingestellt“").isEqualByComparingTo("0");
        assertThat(nullProzent.quellenName()).isEqualTo("Sonderrohre");
    }

    @Test
    void ohneJedenAufschlagGibtEsKeinenUndEinZyklusHaengtNicht() {
        assertThat(service.wirksamer(artikel(null, null)).quelle()).isEqualTo(Quelle.KEINER);
        assertThat(service.wirksamer(null).quelle()).isEqualTo(Quelle.KEINER);
        gespeichert.put(AufschlagService.ALLGEMEIN_SCHLUESSEL, "kaputt");
        Kategorie a = kategorie(1, "A", null, null);
        Kategorie b = kategorie(2, "B", a, null);
        a.setParentKategorie(b);

        assertThat(service.wirksamer(artikel(a, null)).quelle()).as("kaputter Wert und Zyklus: kein Aufschlag, keine Endlosschleife").isEqualTo(Quelle.KEINER);
    }

    @Test
    void uebersichtZeigtPfadEigenenUndGeltendenAufschlag() {
        Kategorie stahl = kategorie(1, "Stahl", null, "25");
        Kategorie rohre = kategorie(2, "Rohre", stahl, null);
        Kategorie alu = kategorie(3, "Alu", null, null);
        when(kategorien.findAll()).thenReturn(List.of(rohre, alu, stahl));
        gespeichert.put(AufschlagService.ALLGEMEIN_SCHLUESSEL, "15.00");

        var u = service.uebersicht();

        assertThat(u.allgemein()).isEqualByComparingTo("15");
        assertThat(u.kategorien()).extracting(z -> z.pfad()).containsExactly("Alu", "Stahl", "Stahl › Rohre");
        var zeileRohre = u.kategorien().get(2);
        assertThat(zeileRohre.prozent()).isNull();
        assertThat(zeileRohre.wirksamProzent()).isEqualByComparingTo("25");
        assertThat(zeileRohre.wirksamVon()).isEqualTo("Stahl");
        assertThat(u.kategorien().get(0).wirksamQuelle()).isEqualTo(Quelle.ALLGEMEIN);
    }

    @Test
    void speichernPrueftBereichUndLeerBedeutetKeinAufschlag() {
        service.setzeAllgemein(new BigDecimal("12.5"));
        assertThat(gespeichert.get(AufschlagService.ALLGEMEIN_SCHLUESSEL)).isEqualTo("12.50");
        service.setzeAllgemein(null);
        assertThat(service.allgemein()).isNull();

        Kategorie rohre = kategorie(2, "Rohre", null, "30");
        when(kategorien.findById(2)).thenReturn(Optional.of(rohre));
        when(kategorien.findAll()).thenReturn(List.of(rohre));
        service.setzeKategorie(2, null);
        assertThat(rohre.getVerkaufsaufschlagProzent()).isNull();
        verify(kategorien).save(rohre);

        assertThatThrownBy(() -> service.setzeAllgemein(new BigDecimal("-1"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("0 und 999,99");
        assertThatThrownBy(() -> service.setzeKategorie(2, new BigDecimal("1000"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("0 und 999,99");
        assertThatThrownBy(() -> service.setzeKategorie(99, BigDecimal.ONE)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Kategorie");
        assertThat(rohre.getVerkaufsaufschlagProzent()).as("ein abgelehnter Wert ändert nichts").isNull();
    }

    @Test
    void dieGrenzenGeltenWieBeimAufschlagAmArtikel() {
        service.setzeAllgemein(new BigDecimal("999.99"));
        service.setzeAllgemein(BigDecimal.ZERO);
        assertThat(service.allgemein()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> service.setzeAllgemein(new BigDecimal("999.991"))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void dasSpeichernWirdBeiAbgelehnterKategorieNichtAusgefuehrt() {
        assertThatThrownBy(() -> service.setzeKategorie(0, new BigDecimal("-5"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.setzeKategorie(Integer.MAX_VALUE, BigDecimal.TEN)).isInstanceOf(ResponseStatusException.class);
        org.mockito.Mockito.verify(kategorien, org.mockito.Mockito.never()).save(any());
    }
}
