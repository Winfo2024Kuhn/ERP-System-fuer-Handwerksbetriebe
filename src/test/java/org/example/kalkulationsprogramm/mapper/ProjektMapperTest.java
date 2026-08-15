package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektResponseDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ProjektMapperTest {

    private final ProjektMapper mapper = new ProjektMapper(new ProduktkategorieMapper(),
            new AnfrageMapper(), mock(KundeMapper.class));

    @Test
    void mapsKilogrammOnArtikel() {
        Projekt projekt = new Projekt();
        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setKilogramm(new BigDecimal("5.5"));
        projekt.getArtikelInProjekt().add(aip);

        ProjektResponseDto dto = mapper.toProjektResponseDto(projekt);
        assertEquals(1, dto.getArtikel().size());
        assertEquals(0, dto.getArtikel().getFirst().getKilogramm().compareTo(new BigDecimal("5.5")));
    }

    @Test
    void updatesPreisProStueckWithSupplierPrice() {
        Projekt projekt = new Projekt();
        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setArtikel(artikel);
        aip.setStueckzahl(2);
        aip.setPreisProStueck(new BigDecimal("5"));

        LieferantenArtikelPreise lap = new LieferantenArtikelPreise();
        lap.setArtikel(artikel);
        lap.setPreis(new BigDecimal("7"));
        aip.setLieferantenArtikelPreis(lap);

        projekt.getArtikelInProjekt().add(aip);

        ProjektResponseDto dto = mapper.toProjektResponseDto(projekt);
        assertEquals(0, dto.getArtikel().getFirst().getPreisProStueck().compareTo(new BigDecimal("14")));
    }

    /**
     * Die Nachkalkulation summiert {@code gesamtpreis}. Solange ein Preisstand
     * haengt, muss er live gerechnet werden - sonst stuende bei Menge 2 nur der
     * halbe Betrag im Projekt.
     */
    @Test
    void berechnetGesamtpreisAusPreisstandUndMenge() {
        Projekt projekt = new Projekt();
        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setArtikel(artikel);
        aip.setMeter(new BigDecimal("3"));

        LieferantenArtikelPreise lap = new LieferantenArtikelPreise();
        lap.setArtikel(artikel);
        lap.setPreis(new BigDecimal("11.80"));
        aip.setLieferantenArtikelPreis(lap);

        projekt.getArtikelInProjekt().add(aip);

        ProjektResponseDto dto = mapper.toProjektResponseDto(projekt);
        assertEquals(0, dto.getArtikel().getFirst().getGesamtpreis().compareTo(new BigDecimal("35.40")));
    }

    /**
     * Beim Abhaken einer Bestellung loest {@code BestellungService} den
     * Preisstand und friert den Gesamtpreis in {@code preisProStueck} ein.
     * Ohne Preisstand muss der Mapper diesen eingefrorenen Wert nehmen und
     * darf ihn nicht ein zweites Mal mit der Menge multiplizieren.
     */
    @Test
    void nimmtEingefrorenenGesamtpreisWennKeinPreisstandMehrHaengt() {
        Projekt projekt = new Projekt();
        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setArtikel(artikel);
        aip.setStueckzahl(4);
        aip.setPreisProStueck(new BigDecimal("60"));
        aip.setBestellt(true);

        projekt.getArtikelInProjekt().add(aip);

        ProjektResponseDto dto = mapper.toProjektResponseDto(projekt);
        assertEquals(0, dto.getArtikel().getFirst().getGesamtpreis().compareTo(new BigDecimal("60")));
    }

    /**
     * V339 hat den Preisbezug nachtraeglich auch an laengst abgehakte
     * Bestellungen zurueckgehaengt. Deren {@code preisProStueck} blieb die
     * eingefrorene Summe - wer hier am haengenden Preisstand entscheidet,
     * rechnet die Position mit dem heutigen Preis neu und widerspricht damit
     * dem historischen Beleg.
     */
    @Test
    void rechnetAbgehakteBestellungNichtMitDemHeutigenPreisstandNeu() {
        Projekt projekt = new Projekt();
        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setArtikel(artikel);
        aip.setStueckzahl(4);
        // Beim Abhaken eingefroren: 4 Stueck zu je 15.
        aip.setPreisProStueck(new BigDecimal("60"));
        aip.setBestellt(true);
        aip.setAusLager(false);

        // Der Preis ist seither gestiegen - und der Bezug haengt wieder dran.
        LieferantenArtikelPreise heute = new LieferantenArtikelPreise();
        heute.setArtikel(artikel);
        heute.setPreis(new BigDecimal("25"));
        aip.setLieferantenArtikelPreis(heute);

        projekt.getArtikelInProjekt().add(aip);

        ProjektResponseDto dto = mapper.toProjektResponseDto(projekt);
        assertEquals(0, dto.getArtikel().getFirst().getGesamtpreis().compareTo(new BigDecimal("60")),
                "Die abgehakte Bestellung muss bei ihrer eingefrorenen Summe bleiben");
    }

    /**
     * Liess sich der Preisstand beim Anlegen nicht aufloesen (er galt nicht
     * mehr als aktuell), haelt {@code fuegeArtikelMaterialkosten} nur den
     * Einzelpreis fest. Wer den ungerechnet als Gesamtpreis durchreicht,
     * meldet die Lagerposition um den Faktor Menge zu billig.
     */
    @Test
    void rechnetLagerwareOhnePreisstandUeberDieMenge() {
        Projekt projekt = new Projekt();
        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setArtikel(artikel);
        aip.setStueckzahl(3);
        aip.setPreisProStueck(new BigDecimal("12.50"));
        // Lagerware ist "bestellt", damit sie aus der offenen Liste faellt.
        aip.setBestellt(true);
        aip.setAusLager(true);

        projekt.getArtikelInProjekt().add(aip);

        ProjektResponseDto dto = mapper.toProjektResponseDto(projekt);
        assertEquals(0, dto.getArtikel().getFirst().getGesamtpreis().compareTo(new BigDecimal("37.50")));
    }

    /**
     * Lagerware ist bereits bezahlt und zaehlt sofort als Materialkosten,
     * bestellte Ware kommt erst per Lieferantenrechnung. Die Oberflaeche
     * unterscheidet das an diesem Feld - es muss also durchgereicht werden.
     */
    @Test
    void reichtAusLagerDurch() {
        Projekt projekt = new Projekt();
        ArtikelInProjekt lagerware = new ArtikelInProjekt();
        lagerware.setAusLager(true);
        ArtikelInProjekt bestellung = new ArtikelInProjekt();
        bestellung.setAusLager(false);
        projekt.getArtikelInProjekt().add(lagerware);
        projekt.getArtikelInProjekt().add(bestellung);

        ProjektResponseDto dto = mapper.toProjektResponseDto(projekt);
        assertEquals(true, dto.getArtikel().get(0).isAusLager());
        assertEquals(false, dto.getArtikel().get(1).isAusLager());
    }

    @Test
    void mapsAbgeschlossenField() {
        Projekt projekt = new Projekt();
        projekt.setAbgeschlossen(false);

        ProjektResponseDto dto1 = mapper.toProjektResponseDto(projekt);
        assertEquals(false, dto1.isAbgeschlossen(), "Projekt should not be closed initially");

        projekt.setAbgeschlossen(true);
        ProjektResponseDto dto2 = mapper.toProjektResponseDto(projekt);
        assertEquals(true, dto2.isAbgeschlossen(), "Projekt should be closed after setting abgeschlossen=true");
    }
}
