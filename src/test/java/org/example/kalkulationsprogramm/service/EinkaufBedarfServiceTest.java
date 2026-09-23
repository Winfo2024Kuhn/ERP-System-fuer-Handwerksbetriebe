package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
