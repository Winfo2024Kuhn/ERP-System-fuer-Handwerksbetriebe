package org.example.kalkulationsprogramm.dto.Einkauf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.einkauf.Dokumentart;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;

public final class EinkaufPositionDto {
    private EinkaufPositionDto() {
    }

    public record Mengenbasis(BigDecimal menge, Einheit einheit, BigDecimal stueckzahl,
            BigDecimal einzelLaengeMm, BigDecimal kgJeMeter, String faktorQuelle) {
    }

    public record DokumentSoll(Dokumentart art, String grundlage, String grundlageVersion,
            boolean fachlichBestaetigt) {
    }

    public record PositionSnapshot(Positionsart art, Long artikelId, String interneReferenz,
            String zeichnungsnummer, String zeichnungsrevision, String bezeichnung, String werkstoff,
            String abmessung, Mengenbasis basis, String schnittForm, String winkelLinks,
            String winkelRechts, String bearbeitung, String oberflaeche, List<DokumentSoll> dokumente,
            List<Long> anlageVersionIds) {
        public PositionSnapshot {
            dokumente = dokumente == null ? List.of() : List.copyOf(dokumente);
            anlageVersionIds = anlageVersionIds == null ? List.of() : List.copyOf(anlageVersionIds);
        }
    }

    public record Herkunft(Long bedarfId, long version, BigDecimal menge) {
    }

    public record Liefergruppe(String lieferadresse, LocalDate bedarfstermin, Long projektId,
            String lagerzweck) {
    }
}
