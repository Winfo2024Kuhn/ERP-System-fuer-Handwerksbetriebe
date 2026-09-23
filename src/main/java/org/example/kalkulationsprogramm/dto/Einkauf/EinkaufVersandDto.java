package org.example.kalkulationsprogramm.dto.Einkauf;

import java.time.Instant;

public final class EinkaufVersandDto {
    private EinkaufVersandDto() {}

    public record KontoZugangReferenz(String kontoId) {}

    public record VersandSnapshot(String typ, Long vorgangId, Long revisionId, Long beteiligungId,
            KontoZugangReferenz konto, MailTransportDto.Nachricht nachricht, String freigabeHash) {
        public VersandSnapshot {
            if (typ == null || typ.isBlank() || vorgangId == null || konto == null
                    || nachricht == null || freigabeHash == null || freigabeHash.isBlank()) {
                throw new IllegalArgumentException("Der freigegebene Versand-Snapshot ist unvollständig.");
            }
            if (!"EINKAUF".equals(konto.kontoId())) {
                throw new IllegalArgumentException("Einkaufsnachrichten müssen das Einkaufspostfach verwenden.");
            }
        }
    }

    public record VersandDto(Long id, long version, String typ, Long vorgangId, Long revisionId,
            String status, String fehlerCode, Instant erstelltAm, Instant angenommenAm,
            boolean archiviert, String messageId) {}

    public record EinkaufVersandAngenommen(Long versandId, String typ, Long vorgangId,
            Long revisionId, Instant zeit) {}

    public record Klaerung(long version, Entscheidung entscheidung, String beleg) {}

    public enum Entscheidung { BEREITS_ANGENOMMEN, NACHWEISLICH_NICHT_GESENDET }
}
