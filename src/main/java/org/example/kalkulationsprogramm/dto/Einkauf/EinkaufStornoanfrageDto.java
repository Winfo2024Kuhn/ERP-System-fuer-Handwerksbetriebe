package org.example.kalkulationsprogramm.dto.Einkauf;

import java.util.List;
import java.util.UUID;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellungDto.Herkunft;

public final class EinkaufStornoanfrageDto {
    private EinkaufStornoanfrageDto() {}
    public record Entwurf(long version, List<Herkunft> anteile, String grund) {}
    public record Vorschau(long version, Long vorschauId, String vorschauHash, String subject,
            String htmlBody, String empfaenger, Long revisionId) {}
    public record Freigabe(long version, Long vorschauId, String vorschauHash, UUID idempotenzKey) {}
}
