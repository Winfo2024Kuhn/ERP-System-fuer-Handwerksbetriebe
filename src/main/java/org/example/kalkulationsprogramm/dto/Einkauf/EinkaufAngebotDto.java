package org.example.kalkulationsprogramm.dto.Einkauf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.example.kalkulationsprogramm.domain.einkauf.Dokumentart;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;

public final class EinkaufAngebotDto {
    private EinkaufAngebotDto() {}
    public record Erfassung(Long anfrageRevisionId, String angebotsnummer, LocalDate datum, LocalDate gueltigBis,
            String waehrung, List<Position> positionen, List<Kosten> kosten, String zahlungsbedingungen,
            BigDecimal skontoProzent, Integer skontoTage, Long emailId, Long originalDateiId) {
        public Erfassung { positionen = positionen == null ? List.of() : List.copyOf(positionen); kosten = kosten == null ? List.of() : List.copyOf(kosten); }
    }
    public record Position(Long anfragePositionId, String originalNummer, String originalText, Mengenbasis angeboten,
            BigDecimal mindestmenge, BigDecimal verpackungseinheit, LocalDate liefertermin, List<String> abweichungen,
            List<ZeugnisZusage> zeugnisse, List<Kosten> kosten) {
        public Position { abweichungen = abweichungen == null ? List.of() : List.copyOf(abweichungen); zeugnisse = zeugnisse == null ? List.of() : List.copyOf(zeugnisse); kosten = kosten == null ? List.of() : List.copyOf(kosten); }
    }
    public record Kosten(String schluessel, String art, BigDecimal betrag, String basis, BigDecimal basisMenge,
            String prozentBasisSchluessel, boolean enthalten, boolean variabel, String quelle) {}
    public record ZeugnisZusage(Dokumentart art, String status, BigDecimal aufpreis) {}
    public record Abweichungsfreigabe(String begruendung) {}
    public record Angebot(Long id, Long beteiligungId, String status, List<VersionDto> versionen) {
        public Angebot { versionen = versionen == null ? List.of() : List.copyOf(versionen); }
    }
    public record VersionDto(Long id, Long angebotId, int nummer, long version, Long anfrageRevisionId,
            String status, String angebotsnummer, LocalDate datum, LocalDate gueltigBis, String waehrung,
            List<Position> positionen, List<Kosten> kosten, String zahlungsbedingungen,
            BigDecimal skontoProzent, Integer skontoTage, Long emailId, Long originalDateiId, Long bestaetigtVon,
            Long abweichungBestaetigtVon, java.time.Instant abweichungBestaetigtAm, String abweichungBestaetigung) {
        public VersionDto { positionen = List.copyOf(positionen); kosten = List.copyOf(kosten); }
    }
}
