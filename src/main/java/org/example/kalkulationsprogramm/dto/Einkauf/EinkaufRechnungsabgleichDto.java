package org.example.kalkulationsprogramm.dto.Einkauf;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;

public final class EinkaufRechnungsabgleichDto {
    private EinkaufRechnungsabgleichDto() {}
    public record Abgleich(Long bestellungId, List<PositionAbgleich> positionen, List<Long> unbelegteDokumentIds) {}
    public record PositionAbgleich(Long positionId, String bezeichnung, Einheit einheit, BigDecimal vereinbart,
            BigDecimal bestaetigt, BigDecimal geliefert, BigDecimal kumuliertAbgerechnet, BigDecimal offen,
            List<Abweichung> abweichungen, boolean pruefen, List<Quelle> quellen) {}
    public record BelegZuordnung(long version,String art,Long bezugsDokumentId,List<BelegPosition> positionen,UUID idempotenzKey) {}
    public record BelegPosition(String originalPositionsnummer,Long bestellPositionId,BigDecimal menge,Einheit einheit,
            List<Kosten> kosten,List<Quelle> quellen) {}
    public record Kosten(String schluessel,String art,BigDecimal betrag,String basis,BigDecimal basisMenge,boolean enthalten,String quelle) {}
    public record Quelle(String typ,Long id,String bezeichnung,BigDecimal betrag) {}
    public record Abweichung(Long positionId,String feld,BigDecimal vereinbart,BigDecimal abgerechnet,BigDecimal differenz,String rechenweg,List<Quelle> quellen) {}
    public record Mengenstand(BigDecimal offen,BigDecimal differenz) {}
}
