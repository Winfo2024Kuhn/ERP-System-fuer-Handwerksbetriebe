package org.example.kalkulationsprogramm.service.ids;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.ids.IdsProtokoll;
import org.example.kalkulationsprogramm.domain.ids.IdsVorgangStatus;
import org.example.kalkulationsprogramm.domain.ids.IdsZugang;
import org.example.kalkulationsprogramm.dto.Ids.IdsArtikelstammAnfrageDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsVorgangDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.IdsZugangRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Der ganze Shop-Besuch gegen eine echte Datenbank und ohne umschließende Test-Transaktion – so wie
 * im Betrieb (Open-in-View ist aus). Fängt nachgeladene Daten außerhalb der Transaktion ab, die
 * gemockte Tests nicht sehen.
 */
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "file.mail-attachment-dir=attachments" })
class IdsShopBesuchIntegrationTest {

    @Autowired private IdsShopBesuchService shopBesuch;
    @Autowired private IdsArtikelstammAbgleich abgleich;
    @Autowired private SystemSettingsService settings;
    @Autowired private LieferantenRepository lieferanten;
    @Autowired private IdsZugangRepository zugaenge;
    @Autowired private LieferantenArtikelPreiseRepository preise;
    @Autowired private ArtikelRepository artikel;

    @Test
    @DisplayName("Starten → Warenkorb zurück → abholen → Artikel anlegen; zweiter Rücksprung wird abgewiesen")
    void ganzerAblauf() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Muster Großhandel " + UUID.randomUUID());
        lieferant = lieferanten.save(lieferant);
        IdsZugang zugang = new IdsZugang();
        zugang.setLieferant(lieferant);
        zugang.setAktiv(true);
        zugang.setProtokoll(IdsProtokoll.IDS_CONNECT_2_5);
        zugang.setShopUrl("http://localhost:9000/ids");
        zugang.setKundennummer("4711");
        zugaenge.save(zugang);
        settings.save("ids.aktiv", "true", "Test");

        IdsShopBesuchService.Gestartet gestartet = shopBesuch.starte(lieferant.getId(), 70L, "http://localhost:8080");
        String hook = gestartet.formular().felder().get("hookurl");
        String schluessel = hook.substring(hook.lastIndexOf('/') + 1);
        String nummer = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        byte[] xml = ("\n<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"x.dtd\">\n"
                + "<Warenkorb><WarenkorbInfo><RueckgabeKZ>Warenkorbrückgabe</RueckgabeKZ></WarenkorbInfo><Order>"
                + "<OrderItem><ArtNo>" + nummer + "</ArtNo><Qty>5</Qty><QU>PCE</QU><Kurztext>Silikon transparent</Kurztext>"
                + "<NetPrice>8.40</NetPrice></OrderItem></Order></Warenkorb>").getBytes(StandardCharsets.UTF_8);

        assertThat(shopBesuch.nimmRueckgabeAn(schluessel, new IdsRueckgabe(xml, null)).ergebnis())
                .isEqualTo(IdsShopBesuchService.Ergebnis.OK);
        assertThat(shopBesuch.nimmRueckgabeAn(schluessel, new IdsRueckgabe(xml, null)).ergebnis())
                .isEqualTo(IdsShopBesuchService.Ergebnis.UNGUELTIG);

        IdsVorgangDto ansicht = abgleich.ansicht(gestartet.vorgangId(), 70L);
        assertThat(ansicht.status()).isEqualTo(IdsVorgangStatus.EMPFANGEN);
        assertThat(ansicht.lieferantName()).startsWith("Muster Großhandel");
        assertThat(ansicht.positionen()).singleElement().satisfies(p -> {
            assertThat(p.einkaufspreis()).isEqualByComparingTo("1.68");
            assertThat(p.stamm()).isNull();
        });

        var ergebnis = abgleich.uebernimm(gestartet.vorgangId(), 70L, new IdsArtikelstammAnfrageDto(List.of(),
                List.of(new IdsArtikelstammAnfrageDto.NeuAnlegen(0, new BigDecimal("1.68"), "Silikon transparent"))));
        assertThat(ergebnis.angelegt()).isEqualTo(1);
        Long artikelId = ergebnis.artikelIds().get(0);
        assertThat(artikel.findById(artikelId)).isPresent();
        assertThat(preise.findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikelId, lieferant.getId()))
                .hasValueSatisfying(p -> assertThat(p.getQuelle()).isEqualTo(PreisQuelle.IDS_SHOP));

        // Jetzt ist der Artikel bekannt: die Ansicht zeigt ihn als Treffer.
        assertThat(abgleich.ansicht(gestartet.vorgangId(), 70L).positionen().get(0).stamm().artikelId()).isEqualTo(artikelId);
        settings.save("ids.aktiv", "false", "Test");
    }
}
