package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.*;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufPositionService;
import org.junit.jupiter.api.Test;

class EinkaufBeschaffungsdetailsTest {
    private final ArtikelRepository artikel = mock(ArtikelRepository.class);
    private final EinkaufPositionService service = new EinkaufPositionService(artikel);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void freiePositionBehaeltDetailsBeiValidierungJsonUndTeilmenge() throws Exception {
        var details = new Beschaffungsdetails(7L, 8L, 9L, 10L, "00012-34");
        var position = position(Positionsart.FREITEXT, details);
        var validated = service.validiere(position, null);
        var reloaded = json.readValue(json.writeValueAsBytes(validated), PositionSnapshot.class);
        assertEquals(details, reloaded.beschaffungsdetails());
        assertEquals("S235", reloaded.werkstoff());
        assertEquals(details, service.mitTeilmenge(reloaded, BigDecimal.ONE).beschaffungsdetails());
    }

    @Test
    void katalogvalidierungErhaeltLieferantennummerNebenInternerNummer() {
        var entry = new Artikel();
        entry.setId(4L);
        entry.setArtikelnummer("INTERN-4");
        entry.setProduktname("Testprofil");
        when(artikel.findById(4L)).thenReturn(Optional.of(entry));
        var details = new Beschaffungsdetails(7L, null, null, null, "00012-34");
        var result = service.validiere(position(Positionsart.ARTIKEL, details), null);
        assertEquals("INTERN-4", result.interneReferenz());
        assertEquals(details, result.beschaffungsdetails());
    }

    @Test
    void verschiedeneLieferantenUndSchnittbilderWerdenNichtZusammengefasst() {
        var group = new Liefergruppe(null, null, null, "Werkstatt");
        var a = position(Positionsart.FREITEXT, new Beschaffungsdetails(7L, 8L, 9L, null, "A"));
        var b = position(Positionsart.FREITEXT, new Beschaffungsdetails(10L, 8L, 9L, null, "A"));
        var c = position(Positionsart.FREITEXT, new Beschaffungsdetails(7L, 8L, 11L, null, "A"));
        assertNotEquals(service.buendelSchluessel(a, group), service.buendelSchluessel(b, group));
        assertNotEquals(service.buendelSchluessel(a, group), service.buendelSchluessel(c, group));
    }

    @Test
    void historischeJsonSnapshotsOhneZusatzfelderBleibenLesbar() throws Exception {
        var result = json.readValue("""
                {"art":"FREITEXT","bezeichnung":"Testprofil","basis":{"menge":2,"einheit":"STUECK"}}
                """, PositionSnapshot.class);
        assertNull(service.validiere(result, null).beschaffungsdetails());
    }

    @Test
    void ungueltigeReferenzenUndZuLangeNummernWerdenAbgelehnt() {
        for (Long id : List.of(-1L, 0L, Long.MAX_VALUE)) {
            for (var details : List.of(new Beschaffungsdetails(id, null, null, null, null),
                    new Beschaffungsdetails(null, id, null, null, null),
                    new Beschaffungsdetails(null, null, id, null, null),
                    new Beschaffungsdetails(null, null, null, id, null))) {
                assertThrows(IllegalArgumentException.class,
                        () -> service.validiere(position(Positionsart.FREITEXT, details), null));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> service.validiere(position(Positionsart.FREITEXT,
                new Beschaffungsdetails(null, null, null, null, "X".repeat(256))), null));
    }

    private PositionSnapshot position(Positionsart art, Beschaffungsdetails details) {
        return new PositionSnapshot(art, art == Positionsart.ARTIKEL ? 4L : null, null, null, null,
                "Testprofil", "S235", null,
                new Mengenbasis(new BigDecimal("2"), Einheit.STUECK, null, null, null, null),
                null, null, null, null, null, List.of(), List.of(), details);
    }
}
