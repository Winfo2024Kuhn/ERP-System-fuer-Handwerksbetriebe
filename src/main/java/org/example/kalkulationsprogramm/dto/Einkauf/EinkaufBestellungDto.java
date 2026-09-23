package org.example.kalkulationsprogramm.dto.Einkauf;

import java.math.BigDecimal; import java.time.LocalDate; import java.util.*;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;

public final class EinkaufBestellungDto {
 private EinkaufBestellungDto() {}
 public record Herkunft(Long bedarfId,long version,BigDecimal menge) {}
 public record Direktpreis(Long bedarfId,BigDecimal preis,Einheit einheit,BigDecimal basisMenge,Long preisHistorieId,LocalDate bestaetigtAm,LocalDate gueltigBis,String bestaetigungsbeleg) {}
 public record AusAngebot(Long angebotVersionId,List<Herkunft> paket,String entscheidungsgrund,UUID idempotenzKey) { public AusAngebot { paket=paket==null?List.of():List.copyOf(paket); } }
 public record Direkt(Long lieferantId,Snapshot empfaenger,List<Herkunft> paket,List<Direktpreis> preise,LocalDate liefertermin,LocalDate bestaetigungsfrist,String bedingungen,UUID idempotenzKey) { public Direkt { paket=paket==null?List.of():List.copyOf(paket); preise=preise==null?List.of():List.copyOf(preise); } }
 public record Aenderung(long version,Direkt inhalt,String grund) {}
 public record Position(Long id,PositionSnapshot snapshot,BigDecimal menge,BigDecimal nettoEinzelpreis,List<Herkunft> herkuenfte) { public Position { herkuenfte=List.copyOf(herkuenfte); } }
 public record Revision(Long id,int nummer,long version,Map<String,Object> snapshot,String sha256,Long versandId,boolean verworfen,List<Position> positionen) { public Revision { snapshot=Map.copyOf(snapshot); positionen=List.copyOf(positionen); } }
 public record Detail(Long id,long version,String nummer,Long lieferantId,Long angebotsversionId,Long anfrageRevisionId,Snapshot empfaenger,BestellungStatus status,LieferantenBestellstatus lieferantenStatus,List<Revision> revisionen) { public Detail { revisionen=List.copyOf(revisionen); } }
 public record Uebersicht(Long id,String nummer,Long lieferantId,BestellungStatus status,LieferantenBestellstatus lieferantenStatus,LocalDate angelegtAm) {}
}
