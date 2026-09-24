package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufAnlageVersion;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Liefergruppe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.AnfrageRevisionRepository;
import org.example.kalkulationsprogramm.repository.BestellungRevisionRepository;
import org.example.kalkulationsprogramm.repository.EinkaufAnlageVersionRepository;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.repository.EinkaufLagerentnahmeRepository;
import org.example.kalkulationsprogramm.repository.EinkaufMengenbuchungRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAuditService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBedarfLoeschService;
import org.example.kalkulationsprogramm.service.einkauf.HiCadImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EinkaufBedarfLoeschServiceTest {
    @Mock EinkaufBedarfRepository bedarfe;
    @Mock AnfrageRevisionRepository anfragen;
    @Mock BestellungRevisionRepository bestellungen;
    @Mock EinkaufMengenbuchungRepository mengenbuchungen;
    @Mock EinkaufLagerentnahmeRepository lagerentnahmen;
    @Mock EinkaufAnlageVersionRepository anlagen;
    @Mock HiCadImportService hicad;
    @Mock EinkaufAuditService audit;
    private EinkaufBedarfLoeschService service;
    private EinkaufBedarf bedarf;

    @BeforeEach
    void setUp() {
        service = new EinkaufBedarfLoeschService(bedarfe, anfragen, bestellungen, mengenbuchungen, lagerentnahmen, anlagen, hicad, audit,
                new ObjectMapper().findAndRegisterModules());
        PositionSnapshot position = new PositionSnapshot(Positionsart.FREITEXT, null, null, null, null, "Dummy Konsole",
                "S235", null, new Mengenbasis(new BigDecimal("4"), Einheit.STUECK, new BigDecimal("4"), null, null, null),
                null, null, null, null, null, List.of(), List.of(), null, "1200");
        bedarf = new EinkaufBedarf(position, new Liefergruppe(null, null, 17L, null), 17L, null, false);
        bedarf.setId(5L);
        bedarf.setVersion(3L);
        when(bedarfe.findByIdForUpdate(5L)).thenReturn(Optional.of(bedarf));
        when(bestellungen.bestellNummernMitBedarf(5L)).thenReturn(List.of());
        when(anfragen.anfrageNummernMitBedarf(5L)).thenReturn(List.of());
        when(anlagen.findByBedarfIdOrderByIdAsc(5L)).thenReturn(List.of());
        when(hicad.gibUebernahmeFrei(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void loeschtFreienBedarfSamtAnlagenGibtHicadZeileFreiUndProtokolliert() {
        EinkaufAnlageVersion anlage = mock(EinkaufAnlageVersion.class);
        when(anlage.getId()).thenReturn(40L);
        when(anlage.getRevision()).thenReturn("HiCAD-Z3-B1");
        when(anlagen.findByBedarfIdOrderByIdAsc(5L)).thenReturn(List.of(anlage));
        when(hicad.gibUebernahmeFrei(17L, 5L, bedarf.getPosition()))
                .thenReturn(List.of(new HiCadImportService.ImportFreigabe(9L, 3, new BigDecimal("4"))));

        service.loeschen(5L, 3L, 7L);

        InOrder reihenfolge = inOrder(hicad, anlagen, bedarfe, audit);
        reihenfolge.verify(hicad).gibUebernahmeFrei(17L, 5L, bedarf.getPosition());
        reihenfolge.verify(anlagen).deleteAll(List.of(anlage));
        reihenfolge.verify(bedarfe).delete(bedarf);
        ArgumentCaptor<JsonNode> vorher = ArgumentCaptor.forClass(JsonNode.class);
        reihenfolge.verify(audit).protokolliere(eq("BEDARF"), eq(5L), eq("BEDARF_GELOESCHT"), eq(7L),
                vorher.capture(), isNull(), any());
        assertEquals("Dummy Konsole", vorher.getValue().path("bezeichnung").asText());
        assertEquals("1200", vorher.getValue().path("position").path("positionsnummer").asText());
        assertEquals(40L, vorher.getValue().path("anlagen").get(0).path("id").asLong());
        assertEquals(3, vorher.getValue().path("hicadFreigaben").get(0).path("zeilennummer").asInt());
    }

    @Test
    void versionskonfliktLiefert409OhneLoeschen() {
        var fehler = assertThrows(ResponseStatusException.class, () -> service.loeschen(5L, 2L, 7L));
        assertEquals(409, fehler.getStatusCode().value());
        assertTrue(fehler.getReason().contains("zwischenzeitlich geändert"));
        verify(bedarfe, never()).delete(any());
        verifyNoInteractions(audit, hicad);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void ungueltigeIdsWerdenAbgelehnt(long id) {
        assertThrows(IllegalArgumentException.class, () -> service.loeschen(id, 0L, 7L));
        verify(bedarfe, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void negativeVersionUndFehlenderAkteurWerdenAbgelehnt() {
        assertThrows(IllegalArgumentException.class, () -> service.loeschen(5L, -1L, 7L));
        assertThrows(IllegalArgumentException.class, () -> service.loeschen(5L, 3L, null));
        verify(bedarfe, never()).delete(any());
    }

    @Test
    void unbekannterBedarfLiefertNichtGefunden() {
        when(bedarfe.findByIdForUpdate(Long.MAX_VALUE)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.loeschen(Long.MAX_VALUE, 0L, 7L));
    }

    @Test
    void bestellungNenntDieBestellnummer() {
        when(bestellungen.bestellNummernMitBedarf(5L)).thenReturn(List.of("B-2026-0001"));
        pruefeKonflikt(b -> { }, "Bestellung B-2026-0001");
    }

    @Test
    void anfrageNenntDieNummernUndKuerztLangeListen() {
        when(anfragen.anfrageNummernMitBedarf(5L)).thenReturn(List.of("PA-1", "PA-2", "PA-3", "PA-4", "PA-5"));
        pruefeKonflikt(b -> { }, "Preisanfragen PA-1, PA-2, PA-3 und 2 weiteren");
    }

    @Test
    void projektmaterialSperrt() {
        pruefeKonflikt(b -> b.setArtikelInProjektId(99L), "Material des Projekts");
    }

    @Test
    void lagerentnahmeSperrt() {
        when(lagerentnahmen.existsByBedarfId(5L)).thenReturn(true);
        pruefeKonflikt(b -> { }, "aus dem Lager entnommen");
    }

    @Test
    void reservierungSperrt() {
        pruefeKonflikt(b -> b.setReserviert(BigDecimal.ONE), "reserviert");
    }

    @Test
    void lieferungSperrt() {
        pruefeKonflikt(b -> b.setGeliefert(BigDecimal.ONE), "bestellt oder geliefert");
    }

    @Test
    void eingetrageneVorhandeneMengeSperrt() {
        pruefeKonflikt(b -> b.setLagergedeckt(BigDecimal.ONE), "„Vorhanden“");
    }

    @Test
    void fruehereMengenbuchungSperrt() {
        when(mengenbuchungen.existsByBedarf_Id(5L)).thenReturn(true);
        pruefeKonflikt(b -> { }, "Mengen gebucht");
    }

    @Test
    void versendeteZeichnungSperrt() {
        EinkaufAnlageVersion anlage = mock(EinkaufAnlageVersion.class);
        when(anlage.isVersendet()).thenReturn(true);
        when(anlagen.findByBedarfIdOrderByIdAsc(5L)).thenReturn(List.of(anlage));
        pruefeKonflikt(b -> { }, "verschickt");
    }

    private void pruefeKonflikt(Consumer<EinkaufBedarf> vorbereitung, String erwarteterText) {
        vorbereitung.accept(bedarf);
        var fehler = assertThrows(ResponseStatusException.class, () -> service.loeschen(5L, 3L, 7L));
        assertEquals(409, fehler.getStatusCode().value());
        assertTrue(fehler.getReason().contains(erwarteterText), fehler.getReason());
        verify(bedarfe, never()).delete(any());
        verify(hicad, never()).gibUebernahmeFrei(any(), any(), any());
    }
}
