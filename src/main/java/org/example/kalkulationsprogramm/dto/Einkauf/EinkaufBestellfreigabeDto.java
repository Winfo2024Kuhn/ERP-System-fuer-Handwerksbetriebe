package org.example.kalkulationsprogramm.dto.Einkauf;
import java.time.*; import java.util.*; import org.example.kalkulationsprogramm.domain.einkauf.BestellungStatus; import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.Vorschau;
public final class EinkaufBestellfreigabeDto {
 private EinkaufBestellfreigabeDto(){}
 public record Freigabe(long version,String vorschauHash,UUID idempotenzKey){}
 public record VersandErgebnis(Long bestellungId,String status,String fehlerCode,String messageId){}
 public record ExternerNachweis(long version,Instant versendetAm,Long dateiId,String begruendung,UUID idempotenzKey){}
 public record Storno(long version,List<EinkaufBestellungDto.Herkunft> anteile,Long belegDateiId,String grund,UUID idempotenzKey){public Storno{anteile=anteile==null?List.of():List.copyOf(anteile);}}
 public record StornoErgebnis(Long bestellungId,BestellungStatus status,String hinweis){}
 public record VorschauErgebnis(Vorschau vorschau,long revisionsVersion,String sha256){}
}
