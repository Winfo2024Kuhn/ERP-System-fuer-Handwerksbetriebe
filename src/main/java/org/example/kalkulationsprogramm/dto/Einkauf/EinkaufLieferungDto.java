package org.example.kalkulationsprogramm.dto.Einkauf;
import java.math.BigDecimal;import java.time.*;import java.util.*;
public final class EinkaufLieferungDto {private EinkaufLieferungDto(){}
 public record Lieferanteil(Long bestellPositionId,BigDecimal menge,String charge,String schmelznummer,List<EinkaufBestellungDto.Herkunft> projektAnteile){public Lieferanteil{projektAnteile=projektAnteile==null?List.of():List.copyOf(projektAnteile);}}
 public record Annahme(long version,Long lieferscheinId,Instant eingang,List<Lieferanteil> positionen,UUID idempotenzKey){public Annahme{positionen=positionen==null?List.of():List.copyOf(positionen);}}
 public record BestaetigtePosition(Long bestellPositionId,BigDecimal menge,BigDecimal nettoPreis,String abweichung){}
 public record Bestaetigung(Long dokumentId,LocalDate datum,LocalDate liefertermin,List<BestaetigtePosition> positionen){public Bestaetigung{positionen=positionen==null?List.of():List.copyOf(positionen);}}
 public record LieferungDto(Long id,Long bestellungId,Long revisionId,Long lieferscheinId,Instant eingang,List<Lieferanteil> positionen){public LieferungDto{positionen=List.copyOf(positionen);}}
 public record BestaetigungDto(Long id,Long dokumentId,LocalDate datum,LocalDate liefertermin,boolean abweichung,List<BestaetigtePosition> positionen){public BestaetigungDto{positionen=List.copyOf(positionen);}}
}
