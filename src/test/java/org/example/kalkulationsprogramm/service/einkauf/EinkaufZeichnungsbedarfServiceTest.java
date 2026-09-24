package org.example.kalkulationsprogramm.service.einkauf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufDateiDto.AnlageDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Liefergruppe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class EinkaufZeichnungsbedarfServiceTest {
    @Test
    void legtZeichnungsteilOhneClientAnlagenIdVollstaendigUndFreigegebenAn() {
        EinkaufBedarfService bedarfe = mock(EinkaufBedarfService.class);
        EinkaufDateiService dateien = mock(EinkaufDateiService.class);
        var service = new EinkaufZeichnungsbedarfService(bedarfe, dateien);
        PositionSnapshot position = new PositionSnapshot(Positionsart.ZEICHNUNGSTEIL, null, "ZT-41", "Z-41", "B",
                "Träger", "S355", "IPE 200", new Mengenbasis(new BigDecimal("2"), Einheit.STUECK,
                        new BigDecimal("2"), null, null, null), "GERADE", null, null, "Entgraten", "Verzinkt",
                List.of(), List.of());
        Liefergruppe liefergruppe = new Liefergruppe(null, null, 9L, null);
        var eingabe = new EinkaufBedarfDto.Create(position, liefergruppe, null);
        var datei = new MockMultipartFile("datei", "Z-41.pdf", "application/pdf", "%PDF-1.7\nDummy".getBytes());
        var zwischenstand = new EinkaufBedarfDto.Response(73L, 0L, position, liefergruppe, null, false, null);
        var freigabe = new AnlageDto(815L, 91L, 73L, "B", "Z-41.pdf", "application/pdf", 12, "hash", true, false, null);
        var ergebnis = new EinkaufBedarfDto.Response(73L, 1L,
                new PositionSnapshot(position.art(), position.artikelId(), position.interneReferenz(), position.zeichnungsnummer(),
                        position.zeichnungsrevision(), position.bezeichnung(), position.werkstoff(), position.abmessung(),
                        position.basis(), position.schnittForm(), position.winkelLinks(), position.winkelRechts(),
                        position.bearbeitung(), position.oberflaeche(), position.dokumente(), List.of(815L)),
                liefergruppe, null, false, null);
        when(bedarfe.anlegen(any(EinkaufBedarfDto.Create.class), eq(7L))).thenReturn(zwischenstand);
        when(dateien.hochladen(73L, datei, "B", 7L)).thenReturn(new AnlageDto(815L, 91L, 73L, "B", "Z-41.pdf", "application/pdf", 12, "hash", false, false, null));
        when(dateien.freigeben(815L, 7L)).thenReturn(freigabe);
        when(bedarfe.aktualisieren(eq(73L), any(EinkaufBedarfDto.Update.class), eq(7L))).thenReturn(ergebnis);

        var gespeichert = service.anlegen(eingabe, datei, "B", 7L);

        assertEquals(List.of(815L), gespeichert.position().anlageVersionIds());
        var aufrufreihenfolge = inOrder(dateien, bedarfe);
        aufrufreihenfolge.verify(dateien).validiereUpload(datei, "B");
        aufrufreihenfolge.verify(bedarfe).anlegen(any(EinkaufBedarfDto.Create.class), eq(7L));
        aufrufreihenfolge.verify(dateien).hochladen(73L, datei, "B", 7L);
        aufrufreihenfolge.verify(dateien).freigeben(815L, 7L);
        aufrufreihenfolge.verify(bedarfe).aktualisieren(eq(73L), any(EinkaufBedarfDto.Update.class), eq(7L));
        verify(bedarfe).anlegen(org.mockito.ArgumentMatchers.argThat(create -> create.position().anlageVersionIds().equals(List.of(Long.MAX_VALUE))), eq(7L));
    }

    @Test
    void lehntArtikelpositionVorJederMutationAb() {
        EinkaufBedarfService bedarfe = mock(EinkaufBedarfService.class);
        EinkaufDateiService dateien = mock(EinkaufDateiService.class);
        var service = new EinkaufZeichnungsbedarfService(bedarfe, dateien);
        var eingabe = new EinkaufBedarfDto.Create(new PositionSnapshot(Positionsart.ARTIKEL, 9L, null, null, null,
                "Artikel", null, null, new Mengenbasis(BigDecimal.ONE, Einheit.STUECK, BigDecimal.ONE, null, null, null),
                null, null, null, null, null, List.of(), List.of()), new Liefergruppe(null, null, 9L, null), null);
        assertThrows(IllegalArgumentException.class, () -> service.anlegen(eingabe,
                new MockMultipartFile("datei", "Bild.pdf", "application/pdf", "%PDF-1.7".getBytes()), "B", 7L));
        org.mockito.Mockito.verifyNoInteractions(dateien, bedarfe);
    }
}
