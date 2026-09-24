package org.example.kalkulationsprogramm.dto.Einkauf;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Persistierte Bestellmengen und Lieferbezüge für die Bestellseiten. */
public final class EinkaufBestellstatusDto {
    private EinkaufBestellstatusDto() {}
    public record Mengenstand(Long bedarfId, BigDecimal reserviert, BigDecimal bestellt,
            BigDecimal geliefert, BigDecimal storniert, BigDecimal offen) {}
    public record Charge(Long id, String kennung, String schmelznummer) {}
    public record Lieferposition(Long id, Long bestellPositionId, BigDecimal menge, String charge,
            String schmelznummer, List<EinkaufBestellungDto.Herkunft> projektAnteile, List<Charge> chargen) {
        public Lieferposition { projektAnteile = List.copyOf(projektAnteile); chargen = List.copyOf(chargen); }
    }
    public record Lieferung(Long id, Long bestellungId, Long revisionId, Long lieferscheinId,
            Instant eingang, List<Lieferposition> positionen) {
        public Lieferung { positionen = List.copyOf(positionen); }
    }
}
