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
