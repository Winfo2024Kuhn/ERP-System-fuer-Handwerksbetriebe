package org.example.kalkulationsprogramm.dto.Einkauf;

import java.util.List;
import java.util.UUID;

public final class EinkaufKommunikationDto {
    private EinkaufKommunikationDto() {}
    public record Vorschau(long version, String vorschauHash, String subject, String htmlBody,
            String empfaenger, Long pdfDateiId, List<Long> anlageVersionIds) {
        public Vorschau { anlageVersionIds = anlageVersionIds == null ? List.of() : List.copyOf(anlageVersionIds); }
    }
    public record Freigabe(long version, String vorschauHash, UUID idempotenzKey) {}
    public record ZuordnungRequest(String typ, Long vorgangId, Long beteiligungId, Long revisionId, String begruendung) {}
    public record Zuordnungsergebnis(Long emailId, String typ, Long vorgangId, Long beteiligungId,
            Long revisionId, String status, String quelle, boolean bestaetigt) {}
    public record NachrichtDto(Long emailId, String messageId, String subject, String fromAddress,
            java.time.LocalDateTime sentAt, String typ, Long vorgangId, Long beteiligungId, Long revisionId,
            String status, String quelle) {}
    public record VersandErgebnis(Long beteiligungId, String status, String fehlerCode, String messageId) {}
}
