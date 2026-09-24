package org.example.kalkulationsprogramm.dto.Einkauf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;

public final class EinkaufsanfrageDto {
    private EinkaufsanfrageDto() {}
    public record Create(List<Herkunft> positionen, List<Snapshot> empfaenger, LocalDate antwortfrist,
            LocalDate liefertermin, Long zustaendigId, UUID idempotenzKey) {
        public Create { positionen = positionen == null ? List.of() : List.copyOf(positionen); empfaenger = empfaenger == null ? List.of() : List.copyOf(empfaenger); }
    }
    public record RevisionRequest(long version, Create inhalt) {}
    public record Kopf(Long id, long version, String paNummer, Long zustaendigId, Long aktuelleRevisionId,
            int revisionsNummer, String status, LocalDate antwortfrist, LocalDate liefertermin,
            List<Long> projektIds, long antworten, long lieferantenAnzahl) {
        public Kopf { projektIds = List.copyOf(projektIds); }
        public Kopf(Long id, long version, String paNummer, Long zustaendigId, Long aktuelleRevisionId,
                int revisionsNummer, String status, LocalDate antwortfrist, LocalDate liefertermin) {
            this(id, version, paNummer, zustaendigId, aktuelleRevisionId, revisionsNummer, status,
                    antwortfrist, liefertermin, List.of(), 0, 0);
        }
    }
    public record Revisionsinfo(Long id, int nummer, String status, LocalDate antwortfrist, LocalDate liefertermin) {}
    public record Positionszeile(Long id, PositionSnapshot snapshot, List<Herkunft> herkuenfte) {
        public Positionszeile { herkuenfte = List.copyOf(herkuenfte); }
    }
    public record Lieferantenbeteiligung(Long id, Long lieferantId, String lieferantenname, String status, long version, Snapshot kontakt) {
        public Lieferantenbeteiligung(Long id, Long lieferantId, String lieferantenname, String status, long version) {
            this(id, lieferantId, lieferantenname, status, version, null);
        }
    }
    public record LieferantenstatusRequest(long version, String status) {}
    public record Detail(Kopf kopf, List<Positionszeile> positionen, List<Lieferantenbeteiligung> lieferanten,
            Long angezeigteRevisionId, boolean historisch) {
        public Detail(Kopf kopf, List<Positionszeile> positionen, List<Lieferantenbeteiligung> lieferanten) {
            this(kopf, positionen, lieferanten, kopf.aktuelleRevisionId(), false);
        }
        public Detail { positionen = List.copyOf(positionen); lieferanten = List.copyOf(lieferanten); }
    }
}
