package org.example.kalkulationsprogramm.dto.Einkauf;

import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Liefergruppe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;

import java.math.BigDecimal;

public final class EinkaufBedarfDto {
    private EinkaufBedarfDto() {}

    public record Create(PositionSnapshot position, Liefergruppe liefergruppe, Long artikelInProjektId) {}
    public record Update(long version, PositionSnapshot position, Liefergruppe liefergruppe) {}
    public record Werkstattposition(Long bedarfId, Long version, BigDecimal vorhanden) {}
    public record Werkstattpruefung(java.util.List<Werkstattposition> positionen) {}
    public record Mengenstand(BigDecimal bedarf, BigDecimal lagergedeckt, BigDecimal angefragt,
            BigDecimal reserviert, BigDecimal bestellt, BigDecimal geliefert, BigDecimal storniert,
            BigDecimal ungedeckt, BigDecimal disponierbar) {}
    public record Response(Long id, long version, PositionSnapshot position, Liefergruppe liefergruppe,
            Mengenstand mengen, boolean nachpflegeErforderlich, String historischerHinweis) {}
}
