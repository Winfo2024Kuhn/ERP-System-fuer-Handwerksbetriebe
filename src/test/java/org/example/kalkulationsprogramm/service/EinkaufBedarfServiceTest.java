package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Liefergruppe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBedarfService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufPositionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class EinkaufBedarfServiceTest {
    @Mock EinkaufBedarfRepository bedarfRepository;
    @Mock ArtikelInProjektRepository artikelInProjektRepository;
    @Mock ProjektRepository projektRepository;
    @Mock EinkaufPositionService positionService;

    @Test
    void freierBedarfOhneArtikelnummerIstVollstaendig() {
        var input = new PositionSnapshot(Positionsart.FREITEXT, null, null, null, null, "Schweißdraht", null, null,
                new Mengenbasis(new BigDecimal("10"), Einheit.KILOGRAMM, null, null, null, null), null,null,null,null,null,List.of(),List.of());
        when(positionService.validiere(input, null)).thenReturn(input);
        when(bedarfRepository.save(any())).thenAnswer(i -> {var b=i.<EinkaufBedarf>getArgument(0);b.setId(1L);b.setVersion(0L);return b;});
        var service=new EinkaufBedarfService(bedarfRepository,artikelInProjektRepository,projektRepository,positionService,new ObjectMapper());
        var result=service.anlegen(new EinkaufBedarfDto.Create(input,new Liefergruppe(null,null,null,"Werkstatt"),null),7L);
        assertFalse(result.nachpflegeErforderlich());
    }

    @Test
    void filterOhneProjektIstGetrenntUndSchliesstProjektIdAus() {
        var page=PageRequest.of(0,20);
        when(bedarfRepository.sucheOhneProjekt(null,page)).thenReturn(new PageImpl<>(List.of()));
        var service=new EinkaufBedarfService(bedarfRepository,artikelInProjektRepository,projektRepository,positionService,new ObjectMapper());
        assertTrue(service.suche(null,null,true,page).isEmpty());
        assertThrows(IllegalArgumentException.class,()->service.suche(null,1L,true,page));
        verify(bedarfRepository).sucheOhneProjekt(null,page);
    }

    @Test
    void neuerBedarfStartetMitVollstaendigUngedeckterMenge() {
        PositionSnapshot input = new PositionSnapshot(Positionsart.ARTIKEL, 17L, "A-17", null, null,
                "Schraube", null, null, new Mengenbasis(new BigDecimal("10"), Einheit.STUECK,
                        new BigDecimal("10"), null, null, null), null, null, null, null, null, null, null);
        when(positionService.validiere(input, null)).thenReturn(input);
        when(bedarfRepository.save(any())).thenAnswer(invocation -> {
            var bedarf = invocation.<org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf>getArgument(0);
            bedarf.setId(1L);
            bedarf.setVersion(0L);
            return bedarf;
        });

        var service = new EinkaufBedarfService(bedarfRepository, artikelInProjektRepository,
                projektRepository, positionService, new ObjectMapper());
        EinkaufBedarfDto.Response response = service.anlegen(
                new EinkaufBedarfDto.Create(input, new Liefergruppe(null, null, null, "Werkstatt"), null), 5L);

        assertEquals(0, response.mengen().bedarf().compareTo(new BigDecimal("10")));
        assertEquals(0, response.mengen().ungedeckt().compareTo(new BigDecimal("10")));
        assertEquals(0, response.mengen().disponierbar().compareTo(new BigDecimal("10")));
    }

    @Test
    void angefragteMengeKommtAusBatchProviderUndReduziertUngedecktNicht() {
        PositionSnapshot input = new PositionSnapshot(Positionsart.ARTIKEL, 17L, "A-17", null, null,
                "Schraube", null, null, new Mengenbasis(new BigDecimal("10"), Einheit.STUECK,
                        new BigDecimal("10"), null, null, null), null, null, null, null, null, null, null);
        EinkaufBedarf bedarf = new EinkaufBedarf(input, new Liefergruppe(null, null, null, "Werkstatt"),
                null, null, false);
        bedarf.setId(21L);
        bedarf.setVersion(0L);
        when(bedarfRepository.suche(null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(bedarf)));
        var service = new EinkaufBedarfService(bedarfRepository, artikelInProjektRepository,
                projektRepository, positionService, new ObjectMapper());
        service.setAngefragtMengenProvider(ids -> Map.of(21L, new BigDecimal("4")));

        var result = service.suche(null, null, PageRequest.of(0, 20));

        assertEquals(0, result.getContent().getFirst().mengen().angefragt().compareTo(new BigDecimal("4")));
        assertEquals(0, result.getContent().getFirst().mengen().ungedeckt().compareTo(new BigDecimal("10")));
        verify(bedarfRepository).suche(null, null, PageRequest.of(0, 20));
    }

    @Test
    void manuellAngelegterArtikelOhneInterneArtikelnummerWirdZurNachpflegeMarkiert() {
        PositionSnapshot input = new PositionSnapshot(Positionsart.ARTIKEL, 17L, null, null, null,
                "Schraube", null, null, new Mengenbasis(new BigDecimal("10"), Einheit.STUECK,
                        new BigDecimal("10"), null, null, null), null, null, null, null, null, null, null);
        when(positionService.validiere(input, null)).thenReturn(input);
        when(bedarfRepository.save(any())).thenAnswer(invocation -> {
            var bedarf = invocation.<EinkaufBedarf>getArgument(0);
            bedarf.setId(44L);
            bedarf.setVersion(0L);
            return bedarf;
        });
        var service = new EinkaufBedarfService(bedarfRepository, artikelInProjektRepository,
                projektRepository, positionService, new ObjectMapper());

        var response = service.anlegen(new EinkaufBedarfDto.Create(input,
                new Liefergruppe(null, null, null, "Werkstatt"), null), 5L);

        assertTrue(response.nachpflegeErforderlich());
    }

    @Test
    void synchronisierenAktualisiertVorhandenenBedarfUndLaesstDeckungUnveraendert() {
        Projekt projekt = new Projekt();
        projekt.setId(9L);
        Artikel artikel = new Artikel();
        artikel.setId(17L);
        artikel.setProduktname("Schraube neu");
        artikel.setArtikelnummer("A-NEU");
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setId(99L);
        aip.setProjekt(projekt);
        aip.setArtikel(artikel);
        aip.setStueckzahl(7);
        PositionSnapshot alt = new PositionSnapshot(Positionsart.ARTIKEL, 17L, "A-NEU", null, null,
                "Schraube neu", null, null, new Mengenbasis(new BigDecimal("5"), Einheit.STUECK,
                        new BigDecimal("5"), null, null, null), null, null, null, null, null, null, null);
        EinkaufBedarf bedarf = new EinkaufBedarf(alt, new Liefergruppe(null, null, 9L, null), 9L, 99L, false);
        bedarf.setId(21L);
        bedarf.setVersion(0L);
        bedarf.setBestellt(new BigDecimal("2"));
        when(bedarfRepository.findByArtikelInProjektIdForUpdate(99L)).thenReturn(java.util.Optional.of(bedarf));
        when(bedarfRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new EinkaufBedarfService(bedarfRepository, artikelInProjektRepository,
                projektRepository, positionService, new ObjectMapper());

        service.synchronisiereProjektposition(aip);

        assertEquals(0, bedarf.getBedarfMenge().compareTo(new BigDecimal("7")));
        assertEquals(0, bedarf.getBestellt().compareTo(new BigDecimal("2")));
        assertEquals(0, bedarf.ungedeckt().compareTo(new BigDecimal("5")));
        assertEquals("A-NEU", bedarf.getPosition().interneReferenz());
        assertEquals("Schraube neu", bedarf.getBezeichnung());
    }

    @Test
    void synchronisierenLehntReduktionUnterReservierungAbUndLaesstStandUnveraendert() {
        Projekt projekt = new Projekt();
        projekt.setId(9L);
        Artikel artikel = new Artikel();
        artikel.setId(17L);
        artikel.setProduktname("Schraube");
        artikel.setArtikelnummer("A-17");
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setId(99L);
        aip.setProjekt(projekt);
        aip.setArtikel(artikel);
        aip.setStueckzahl(1);
        PositionSnapshot alt = new PositionSnapshot(Positionsart.ARTIKEL, 17L, "A-17", null, null,
                "Schraube", null, null, new Mengenbasis(new BigDecimal("5"), Einheit.STUECK,
                        new BigDecimal("5"), null, null, null), null, null, null, null, null, null, null);
        EinkaufBedarf bedarf = new EinkaufBedarf(alt, new Liefergruppe(null, null, 9L, null), 9L, 99L, false);
        bedarf.setId(21L);
        bedarf.setVersion(0L);
        bedarf.setReserviert(new BigDecimal("2"));
        when(bedarfRepository.findByArtikelInProjektIdForUpdate(99L)).thenReturn(java.util.Optional.of(bedarf));
        var service = new EinkaufBedarfService(bedarfRepository, artikelInProjektRepository,
                projektRepository, positionService, new ObjectMapper());

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.synchronisiereProjektposition(aip));

        assertEquals(0, bedarf.getBedarfMenge().compareTo(new BigDecimal("5")));
        assertEquals(0, bedarf.getReserviert().compareTo(new BigDecimal("2")));
        verify(bedarfRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void aktualisierenPflegtProjektkennungUndBezeichnungAusZeichnungsteilSnapshot() {
        Projekt projekt = new Projekt();
        projekt.setId(9L);
        PositionSnapshot alt = new PositionSnapshot(Positionsart.ZEICHNUNGSTEIL, null, "T-ALT", "Z-1",
                "A", "Träger alt", "S355", "100x10", new Mengenbasis(new BigDecimal("3"), Einheit.STUECK,
                        new BigDecimal("3"), null, null, null), null, null, null, null, null, null, null);
        PositionSnapshot neu = new PositionSnapshot(Positionsart.ZEICHNUNGSTEIL, null, "T-NEU", "Z-1",
                "A", "Träger neu", "S355", "100x10", new Mengenbasis(new BigDecimal("3"), Einheit.STUECK,
                        new BigDecimal("3"), null, null, null), null, null, null, null, null, null, null);
        EinkaufBedarf bedarf = new EinkaufBedarf(alt, new Liefergruppe(null, null, 9L, null), 9L, null, false);
        bedarf.setId(21L);
        bedarf.setVersion(3L);
        when(bedarfRepository.findByIdForUpdate(21L)).thenReturn(java.util.Optional.of(bedarf));
        when(positionService.validiere(neu, 9L)).thenReturn(neu);
        when(projektRepository.findById(9L)).thenReturn(java.util.Optional.of(projekt));
        when(bedarfRepository.existsByProjektIdAndInterneKennungAndIdNot(9L, "T-NEU", 21L))
                .thenReturn(false);
        when(bedarfRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            var gespeichert = invocation.<EinkaufBedarf>getArgument(0);
            gespeichert.setVersion(4L);
            return gespeichert;
        });
        var service = new EinkaufBedarfService(bedarfRepository, artikelInProjektRepository,
                projektRepository, positionService, new ObjectMapper());

        service.aktualisieren(21L, new EinkaufBedarfDto.Update(3L, neu,
                new Liefergruppe(null, null, 9L, null)), 5L);

        assertEquals("T-NEU", bedarf.getInterneKennung());
        assertEquals("Träger neu", bedarf.getBezeichnung());
        verify(bedarfRepository).existsByProjektIdAndInterneKennungAndIdNot(9L, "T-NEU", 21L);
    }

    @Test
    void aktualisierenBerechnetNachpflegeAusSnapshotUndEntferntSieNachBehebung() {
        PositionSnapshot unvollstaendig = new PositionSnapshot(Positionsart.ARTIKEL, 17L, null, null, null,
                "Schraube", null, null, new Mengenbasis(new BigDecimal("10"), Einheit.STUECK,
                        new BigDecimal("10"), null, null, null), null, null, null, null, null, null, null);
        PositionSnapshot vervollstaendigt = new PositionSnapshot(Positionsart.ARTIKEL, 17L, "A-17", null, null,
                "Schraube", null, null, new Mengenbasis(new BigDecimal("9"), Einheit.STUECK,
                        new BigDecimal("9"), null, null, null), null, null, null, null, null, null, null);
        Liefergruppe liefergruppe = new Liefergruppe(null, null, null, "Werkstatt");
        EinkaufBedarf bedarf = new EinkaufBedarf(unvollstaendig, liefergruppe, null, null, false);
        bedarf.setId(55L);
        bedarf.setVersion(0L);
        when(bedarfRepository.findByIdForUpdate(55L)).thenReturn(java.util.Optional.of(bedarf));
        when(positionService.validiere(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bedarfRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            var gespeichert = invocation.<EinkaufBedarf>getArgument(0);
            gespeichert.setVersion(gespeichert.getVersion() + 1L);
            return gespeichert;
        });
        var service = new EinkaufBedarfService(bedarfRepository, artikelInProjektRepository,
                projektRepository, positionService, new ObjectMapper());

        var unvollstaendigeAntwort = service.aktualisieren(55L,
                new EinkaufBedarfDto.Update(0L, unvollstaendig, liefergruppe), 5L);
        assertTrue(unvollstaendigeAntwort.nachpflegeErforderlich());

        var behobeneAntwort = service.aktualisieren(55L,
                new EinkaufBedarfDto.Update(unvollstaendigeAntwort.version(), vervollstaendigt, liefergruppe), 5L);
        assertFalse(behobeneAntwort.nachpflegeErforderlich());
    }
}
