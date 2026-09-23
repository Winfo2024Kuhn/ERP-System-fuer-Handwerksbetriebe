package org.example.kalkulationsprogramm.dto.Einkauf;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.example.kalkulationsprogramm.domain.einkauf.Dokumentart;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufZeugnisErwartung.Status;

public final class EinkaufZeugnisDto {
    private EinkaufZeugnisDto() {}
    public record DokumentSoll(Dokumentart art, String grundlage, String grundlageVersion, boolean fachlichBestaetigt) {}
    public record Zuordnung(Long dokumentId, List<Long> erwartungIds, List<Long> lieferPositionIds,
            List<Long> chargeIds, String schmelznummer) {
        public Zuordnung { erwartungIds=copy(erwartungIds); lieferPositionIds=copy(lieferPositionIds); chargeIds=copy(chargeIds); }
    }
    public record Pruefung(long version, String ergebnis, String begruendung, String grundlageVersion) {}
    public record Vorlage(Long artikelId, Long projektId, Dokumentart art, String grundlage) {}
    public record ChargeStatusDto(Long chargeId, Status status, long version, boolean materialFreigegeben) {}
    public record ErwartungDto(Long id, long version, Long revisionId, Long bestellPositionId, Dokumentart art,
            String grundlage, String grundlageVersion, LocalDate frist, Status status, List<Long> dateiIds,
            List<Long> lieferPositionIds, List<Long> chargeIds, boolean materialFreigegeben,List<ChargeStatusDto> chargeStaende) {
        public ErwartungDto { dateiIds=copy(dateiIds);lieferPositionIds=copy(lieferPositionIds);chargeIds=copy(chargeIds);chargeStaende=copy(chargeStaende); }
    }
    public record PruefungDto(Long id, Long erwartungId, String ergebnis, String begruendung,
            String grundlageVersion, Long akteurId, Instant geprueftAm, boolean materialFreigegeben) {}
    public record ChargeZuordnungDto(Long zuordnungId, Long erwartungId, Long chargeId, Status status,
            long version, boolean materialFreigegeben) {}
    public record ZuordnungDto(List<ErwartungDto> erwartungen, boolean klaerungNoetig, List<ChargeZuordnungDto> chargen) {
        public ZuordnungDto { erwartungen=copy(erwartungen); chargen=copy(chargen); }
    }
    public record VorlageDto(Long id, Long artikelId, Long projektId, Dokumentart art, String grundlage,
            String grundlageVersion, boolean fachlichBestaetigt) {}
    private static <T> List<T> copy(List<T> list){return list==null?List.of():List.copyOf(list);}
}
