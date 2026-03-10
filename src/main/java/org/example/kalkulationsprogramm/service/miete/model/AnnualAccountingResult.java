package org.example.kalkulationsprogramm.service.miete.model;

import lombok.Builder;
import lombok.Getter;
import org.example.kalkulationsprogramm.domain.miete.Kostenposition;
import org.example.kalkulationsprogramm.domain.miete.Kostenstelle;
import org.example.kalkulationsprogramm.domain.miete.Mietpartei;
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class AnnualAccountingResult {
    private final Long mietobjektId;
    private final String mietobjektName;
    private final String mietobjektStrasse;
    private final String mietobjektPlz;
    private final String mietobjektOrt;
    private final Integer abrechnungsJahr;
    private final BigDecimal gesamtkosten;
    private final BigDecimal gesamtkostenVorjahr;
    private final BigDecimal gesamtkostenDifferenz;
    private final List<KostenstellenResult> kostenstellen;
    private final List<ParteiErgebnis> parteien;
    private final List<Verbrauchsvergleich> verbrauchsvergleiche;

    @Getter
    @Builder
    public static class KostenstellenResult {
        private final Kostenstelle kostenstelle;
        private final BigDecimal gesamtkosten;
        private final BigDecimal gesamtkostenVorjahr;
        private final List<Kostenposition> positionen;
        private final List<Parteianteil> parteianteile;
    }

    @Getter
    @Builder
    public static class Parteianteil {
        private final Mietpartei mietpartei;
        private final BigDecimal betrag;
    }

    @Getter
    @Builder
    public static class ParteiErgebnis {
        private final Mietpartei mietpartei;
        private final BigDecimal betrag;
        private final BigDecimal betragVorjahr;
        private final BigDecimal differenz;
        private final BigDecimal vorauszahlungMonatlich;
        private final BigDecimal vorauszahlungJahr;
        private final BigDecimal saldo;
    }

    @Getter
    @Builder
    public static class Verbrauchsvergleich {
        private final Verbrauchsgegenstand verbrauchsgegenstand;
        private final String raumName;
        private final BigDecimal verbrauchJahr;
        private final BigDecimal verbrauchVorjahr;
        private final BigDecimal differenz;
    }
}
