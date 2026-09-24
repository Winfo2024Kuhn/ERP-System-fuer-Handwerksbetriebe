package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.*;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.service.einkauf.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EinkaufWerkstattServiceTest {
    private final EinkaufBedarfRepository repository = mock(EinkaufBedarfRepository.class);
    private final EinkaufAuditService audit = mock(EinkaufAuditService.class);
    private final EinkaufWerkstattService service = new EinkaufWerkstattService(repository, audit, new ObjectMapper());

    @Test void vorhandeneMengeWirdAbsolutGespeichertUndKannKorrigiertWerden() {
        var need = bedarf(1L);
        when(repository.findeAlleFuerUpdate(List.of(1L))).thenReturn(List.of(need));
        service.pruefen(request(1L, 0, "4"), 7L);
        assertEquals(0, need.disponierbar().compareTo(new BigDecimal("6")));
        service.pruefen(request(1L, 0, "3"), 7L);
        assertEquals(0, need.disponierbar().compareTo(new BigDecimal("7")));
        service.pruefen(request(1L, 0, "0"), 7L);
        assertEquals(0, need.disponierbar().compareTo(new BigDecimal("10")));
        verify(audit, times(3)).protokolliere(eq("BEDARF"), eq(1L), eq("WERKSTATTPRUEFUNG"), eq(7L), any(), any(), any());
    }

    @Test void veralteteVersionVerwirftGesamtesPaketVorJederAenderung() {
        var first = bedarf(1L); var second = bedarf(2L); second.setVersion(1L);
        when(repository.findeAlleFuerUpdate(List.of(1L, 2L))).thenReturn(List.of(first, second));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.pruefen(
                new Werkstattpruefung(List.of(new Werkstattposition(1L, 0L, new BigDecimal("4")),
                        new Werkstattposition(2L, 0L, new BigDecimal("4")))), 7L));
        assertEquals(0, first.getLagergedeckt().signum());
        verifyNoInteractions(audit);
    }

    @Test void ueberhoehteZweiteMengeLaesstErstePositionUnveraendert() {
        var first=bedarf(1L); var second=bedarf(2L);
        when(repository.findeAlleFuerUpdate(List.of(1L,2L))).thenReturn(List.of(first,second));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.pruefen(
                new Werkstattpruefung(List.of(new Werkstattposition(1L,0L,new BigDecimal("4")),
                        new Werkstattposition(2L,0L,new BigDecimal("11")))),7L));
        assertEquals(0,first.getLagergedeckt().signum());
        verifyNoInteractions(audit);
    }

    @Test void fehlendeVersionUndUngueltigeIdsWerdenNichtAlsNullversionUebernommen() {
        for (Long id : List.of(0L,-1L))
            assertThrows(IllegalArgumentException.class,()->service.pruefen(request(id,0,"0"),7L));
        assertThrows(IllegalArgumentException.class,()->service.pruefen(new Werkstattpruefung(List.of(
                new Werkstattposition(1L,null,BigDecimal.ZERO))),7L));
        verifyNoInteractions(repository);
    }

    @Test void reservierteUndBestellteMengenBleibenGeschuetzt() {
        var need = bedarf(1L); need.setBestellt(new BigDecimal("2")); need.setReserviert(new BigDecimal("3"));
        when(repository.findeAlleFuerUpdate(List.of(1L))).thenReturn(List.of(need));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.pruefen(request(1L, 0, "6"), 7L));
        service.pruefen(request(1L, 0, "5"), 7L);
        assertEquals(0, need.disponierbar().signum());
    }

    @Test void ungueltigeMengenUndDoppelteBedarfeWerdenAbgelehnt() {
        for (String value : List.of("-1", "0.1234567", "10000000000000"))
            assertThrows(IllegalArgumentException.class, () -> service.pruefen(request(1L, 0, value), 7L));
        assertThrows(IllegalArgumentException.class, () -> service.pruefen(new Werkstattpruefung(List.of(
                new Werkstattposition(1L,0L,BigDecimal.ONE), new Werkstattposition(1L,0L,BigDecimal.ONE))), 7L));
        verifyNoInteractions(repository);
    }

    private Werkstattpruefung request(Long id, long version, String quantity) {
        return new Werkstattpruefung(List.of(new Werkstattposition(id, version, new BigDecimal(quantity))));
    }
    private EinkaufBedarf bedarf(Long id) {
        var position = new PositionSnapshot(Positionsart.ARTIKEL, 1L, "A-1", null, null, "Schraube", null, null,
                new Mengenbasis(new BigDecimal("10"), Einheit.STUECK, null, null, null, null), null, null, null, null, null, List.of(), List.of());
        var need = new EinkaufBedarf(position, new Liefergruppe(null,null,null,"Werkstatt"),null,null,false);
        need.setId(id); need.setVersion(0L); return need;
    }
}
