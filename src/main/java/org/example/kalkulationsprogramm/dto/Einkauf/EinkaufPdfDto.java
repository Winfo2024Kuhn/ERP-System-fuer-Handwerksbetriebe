package org.example.kalkulationsprogramm.dto.Einkauf;

import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Liefergruppe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class EinkaufPdfDto {
    private EinkaufPdfDto() {}

    public record Beleg(String typ, String nummer, int revision, Snapshot empfaenger,
            List<PdfPosition> positionen, List<PdfKosten> kopfkosten, List<Liefergruppe> liefergruppen,
            LocalDate antwortfrist, LocalDate liefertermin, String bedingungen, BigDecimal nettoSumme,
            boolean entwurf) {
        public Beleg {
            positionen = positionen == null ? List.of() : List.copyOf(positionen);
            kopfkosten = kopfkosten == null ? List.of() : List.copyOf(kopfkosten);
            liefergruppen = liefergruppen == null ? List.of() : List.copyOf(liefergruppen);
        }
    }

    public record PdfPosition(String positionsnummer, PositionSnapshot technik, List<PdfHerkunft> herkuenfte,
            List<PdfKosten> kosten, BigDecimal nettoSumme) {
        public PdfPosition {
            herkuenfte = herkuenfte == null ? List.of() : List.copyOf(herkuenfte);
            kosten = kosten == null ? List.of() : List.copyOf(kosten);
        }
    }

    public record PdfHerkunft(Long bedarfId, String projektNummer, BigDecimal menge, Einheit einheit) {}

    public record PdfKosten(String bezeichnung, BigDecimal betrag, String basis, BigDecimal basisMenge,
            boolean enthalten, String rechenweg) {}
}
