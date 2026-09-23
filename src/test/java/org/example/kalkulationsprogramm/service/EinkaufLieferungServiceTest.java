package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufLieferungDto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.einkauf.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class EinkaufLieferungServiceTest {
    @Test
    void idempotenzschluesselMitAnderemLieferscheininhaltWirdAbgewiesen() {
        var deliveries = mock(EinkaufLieferungRepository.class);
        var old = mock(EinkaufLieferung.class);
        var order = mock(EinkaufBestellung.class);
        var key = UUID.randomUUID();
        when(deliveries.findByIdempotenzKey(key)).thenReturn(Optional.of(old));
        when(old.getBestellung()).thenReturn(order);
        when(order.getId()).thenReturn(5L);
        when(old.getLieferscheinId()).thenReturn(9L);
        when(old.getEingang()).thenReturn(java.time.Instant.parse("2026-09-22T09:00:00Z"));
        when(old.getPositionen()).thenReturn(List.of());
        var amounts = mock(EinkaufMengenService.class);
        var service = new EinkaufLieferungService(mock(EinkaufBestellungRepository.class),
                mock(BestellungRevisionRepository.class), deliveries, mock(EinkaufBedarfRepository.class),
                amounts, mock(LieferantDokumentRepository.class),
                mock(jakarta.persistence.EntityManager.class), mock(EinkaufAuditService.class), new ObjectMapper());
        var request = new Annahme(3, 9L, java.time.Instant.parse("2026-09-22T09:00:00Z"),
                List.of(new Lieferanteil(12L, new BigDecimal("2"), "Charge-A", null, List.of())), key);

        assertThrows(ResponseStatusException.class, () -> service.annehmen(5L, request, 4L));
        verifyNoInteractions(amounts);
    }

    @Test
    void zweiChargenWerdenZusammenGegenBestellmengeGeprueft() {
        var orders = mock(EinkaufBestellungRepository.class);
        var revisions = mock(BestellungRevisionRepository.class);
        var deliveries = mock(EinkaufLieferungRepository.class);
        var needs = mock(EinkaufBedarfRepository.class);
        var amounts = mock(EinkaufMengenService.class);
        var documents = mock(LieferantDokumentRepository.class);
        var order = mock(EinkaufBestellung.class);
        var revision = mock(BestellungRevision.class);
        var line = mock(BestellungPosition.class);
        var document = mock(LieferantDokument.class);
        var supplier = mock(Lieferanten.class);
        when(deliveries.findByIdempotenzKey(any())).thenReturn(Optional.empty());
        when(deliveries.findByBestellung_IdOrderByEingangAsc(5L)).thenReturn(List.of());
        when(orders.findeFuerUpdate(5L)).thenReturn(Optional.of(order));
        when(order.getVersion()).thenReturn(3L);
        when(order.getStatus()).thenReturn(BestellungStatus.BESTELLT);
        when(order.getLieferantId()).thenReturn(7L);
        when(documents.findById(9L)).thenReturn(Optional.of(document));
        when(document.getTyp()).thenReturn(LieferantDokumentTyp.LIEFERSCHEIN);
        when(document.getLieferant()).thenReturn(supplier);
        when(supplier.getId()).thenReturn(7L);
        when(revisions.findFirstByBestellung_IdOrderByNummerDesc(5L)).thenReturn(Optional.of(revision));
        when(revision.getPositionen()).thenReturn(List.of(line));
        when(line.getId()).thenReturn(12L);
        when(line.getMenge()).thenReturn(new BigDecimal("4"));
        when(line.getHerkuenfte()).thenReturn(List.of());
        var service = new EinkaufLieferungService(orders, revisions, deliveries, needs, amounts, documents,
                mock(jakarta.persistence.EntityManager.class), mock(EinkaufAuditService.class), new ObjectMapper());
        var request = new Annahme(3, 9L, java.time.Instant.now(), List.of(
                new Lieferanteil(12L, new BigDecimal("2"), "Charge-A", null, List.of()),
                new Lieferanteil(12L, new BigDecimal("3"), "Charge-B", null, List.of())), UUID.randomUUID());

        assertThrows(ResponseStatusException.class, () -> service.annehmen(5L, request, 4L));
        verifyNoInteractions(amounts);
        verify(deliveries, never()).saveAndFlush(any());
    }
}
