package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellfreigabeDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellungDto.Herkunft;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.einkauf.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EinkaufBestellfreigabeRegressionTest {
    @Test void externerVersandOhneExistierendenBelegBuchtKeineMenge() {
        var f = fixture(BestellungStatus.ENTWURF);
        when(f.files.pruefeExternenVersandbeleg(999L,7L,1L)).thenThrow(new org.example.kalkulationsprogramm.exception.NotFoundException("Beleg fehlt"));
        assertThrows(org.example.kalkulationsprogramm.exception.NotFoundException.class, () -> f.service.externGesendet(1L,
                new ExternerNachweis(0, Instant.now(), 999L, "Dummy-Übermittlung", UUID.randomUUID()), 9L));
        verify(f.amounts, never()).buche(any(), any(), any(), any(), any());
        assertNull(f.revision.getVersandId());
    }
    @Test void teilstornoLaesstRestbestellungLieferbar() {
        var f = fixture(BestellungStatus.BESTELLT);
        f.service.stornoBestaetigen(1L, new Storno(0, List.of(new Herkunft(11L, 0, new BigDecimal("2"))),
                99L, "Zwei Stück entfallen", UUID.randomUUID()), 9L);
        assertEquals(BestellungStatus.BESTELLT, f.order.getStatus());
    }
    @Test void stornoDarfNichtOffeneMengeEinerAnderenBestellungVerbrauchen() {
        var f = fixture(BestellungStatus.BESTELLT);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> f.service.stornoBestaetigen(1L,
                new Storno(0, List.of(new Herkunft(11L, 0, new BigDecimal("11"))), 99L, "zu viel", UUID.randomUUID()), 9L));
        verify(f.amounts, never()).buche(any(), any(), any(), any(), any());
    }
    private Fixture fixture(BestellungStatus status) {
        var orders = mock(EinkaufBestellungRepository.class);
        var revisions = mock(BestellungRevisionRepository.class);
        var needs = mock(EinkaufBedarfRepository.class);
        var amounts = mock(EinkaufMengenService.class);
        var documents = mock(LieferantDokumentRepository.class);
        var files = mock(EinkaufDateiService.class);
        var order = new EinkaufBestellung("B-DUMMY", 7L, null, null,
                new Snapshot(7L, 8L, "Dummy", "test@example.com", "Max Mustermann", null, null), UUID.randomUUID(), "a".repeat(64), 9L);
        ReflectionTestUtils.setField(order, "id", 1L); ReflectionTestUtils.setField(order, "version", 0L); order.setStatus(status);
        var revision = new BestellungRevision(order, 1, Map.of("typ", "DIREKT", "bedingungen", "frei Haus"), "b".repeat(64), 9L);
        ReflectionTestUtils.setField(revision, "id", 2L);
        var snapshot = new PositionSnapshot(Positionsart.ARTIKEL, 21L, "DUMMY", null, null, "Profil", null, null,
                new Mengenbasis(BigDecimal.TEN, Einheit.STUECK, BigDecimal.TEN, null, null, null), null, null, null, null, null, List.of(), List.of());
        var position = new BestellungPosition(revision, snapshot, BigDecimal.TEN, BigDecimal.ONE, "EUR", List.of(), Map.of("lieferadresse", "Musterstraße 1"));
        position.addHerkunft(new BestellungHerkunft(position, 11L, 0, BigDecimal.TEN)); revision.addPosition(position); order.addRevision(revision);
        when(orders.findeFuerUpdate(1L)).thenReturn(Optional.of(order));
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        when(revisions.findFirstByBestellung_IdOrderByNummerDesc(1L)).thenReturn(Optional.of(revision));
        when(revisions.findByBestellung_IdOrderByNummerAsc(1L)).thenReturn(List.of(revision));
        var doc = new LieferantDokument(); doc.setId(99L); doc.setTyp(LieferantDokumentTyp.GUTSCHRIFT);
        var supplier = new Lieferanten(); supplier.setId(7L); doc.setLieferant(supplier);
        when(documents.sperreEinkaufsbeleg(99L)).thenReturn(Optional.of(doc));
        if(status!=BestellungStatus.ENTWURF)revision.angenommen(Instant.now());
        var need = mock(EinkaufBedarf.class);when(need.getId()).thenReturn(11L);when(need.getVersion()).thenReturn(0L);
        when(needs.findeAlleFuerUpdate(any())).thenReturn(List.of(need));
        when(amounts.standFuerVorgang("BESTELLUNG:1")).thenReturn(
                Map.of(11L,new EinkaufMengenService.Vorgangsmenge(BigDecimal.ZERO,BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO)),
                Map.of(11L,new EinkaufMengenService.Vorgangsmenge(BigDecimal.ZERO,new BigDecimal("8"),BigDecimal.ZERO,new BigDecimal("2"))));
        var service = new EinkaufBestellfreigabeService(orders, revisions, mock(AngebotVersionRepository.class),
                mock(EinkaufKommunikationVorschauRepository.class), mock(EinkaufVorlagenService.class), mock(EinkaufPdfService.class), files,
                mock(EinkaufOutboxService.class), mock(EinkaufVersandWorker.class), mock(EinkaufVersandauftragRepository.class),
                amounts, needs, documents, mock(EinkaufAuditService.class), new ObjectMapper().findAndRegisterModules(),
                mock(EinkaufZeugnisService.class));
        return new Fixture(service, order, revision, amounts, files);
    }
    record Fixture(EinkaufBestellfreigabeService service, EinkaufBestellung order, BestellungRevision revision,
            EinkaufMengenService amounts, EinkaufDateiService files) {}
}
