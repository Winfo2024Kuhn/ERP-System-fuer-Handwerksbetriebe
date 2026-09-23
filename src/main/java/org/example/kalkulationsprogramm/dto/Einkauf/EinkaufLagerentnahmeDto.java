package org.example.kalkulationsprogramm.dto.Einkauf;

import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EinkaufLagerentnahmeDto {
    private EinkaufLagerentnahmeDto() {}

    public record EntnahmeRequest(Herkunft anteil, BigDecimal preisJeEinheit, String preisQuelle,
            Instant entnommenAm, UUID idempotenzKey) {}

    public record BewertungRequest(BigDecimal preisJeEinheit, String preisQuelle) {}

    public record BewertungDto(BigDecimal preisJeEinheit, String preisQuelle, Instant bewertetAm,
            Long akteurId, Long mitarbeiterId) {}

    public record EntnahmeDto(Long id, Long projektId, Long bedarfId, long bedarfVersion,
            BigDecimal menge, Einheit einheit, BigDecimal preisJeEinheit, String preisQuelle,
            BigDecimal bewerteterBetrag, boolean bewertungOffen, BigDecimal offenerBedarf,
            Instant entnommenAm, Long akteurId, Long mitarbeiterId, List<BewertungDto> bewertungen) {}
}
