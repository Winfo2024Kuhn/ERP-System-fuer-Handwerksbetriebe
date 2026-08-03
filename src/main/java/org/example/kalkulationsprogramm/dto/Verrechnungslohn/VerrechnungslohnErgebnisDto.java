package org.example.kalkulationsprogramm.dto.Verrechnungslohn;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class VerrechnungslohnErgebnisDto {

    public enum Modus {
        RUECKWIRKEND,
        HOCHRECHNUNG
    }

    public enum LohnQuelle {
        LOHNABRECHNUNG,
        KALKULATORISCH,
        STUNDENLOHN_HOCHRECHNUNG,
        STAMMSTUNDENLOHN
    }

    private int jahr;
    private Modus modus;

    private List<MitarbeiterLohnZeile> lohnzeilen = new ArrayList<>();
    private List<MitarbeiterStundenZeile> stundenzeilen = new ArrayList<>();
    private List<KostenstelleAnteil> kostenstellen = new ArrayList<>();
    private List<AbteilungVorschlag> abteilungen = new ArrayList<>();
    private List<DatenLuecke> datenLuecken = new ArrayList<>();

    private BigDecimal lohnsummeGesamt = BigDecimal.ZERO;
    private BigDecimal verkaeuflicheStundenGesamt = BigDecimal.ZERO;
    private BigDecimal gemeinkostenGesamt = BigDecimal.ZERO;
    private BigDecimal selbstkostenProStunde = BigDecimal.ZERO;

    /**
     * Anteil der Arbeitszeit, der fuer interne Arbeiten (Werkstatt, Wartung,
     * Buero) angesetzt wurde -- in Prozent. Die Oberflaeche spiegelt diesen
     * Wert zurueck, damit Regler und Rechenergebnis nie auseinanderlaufen.
     * Nur im Modus HOCHRECHNUNG relevant.
     */
    private int interneQuoteProzent;

    @Getter
    @Setter
    public static class MitarbeiterLohnZeile {
        private Long mitarbeiterId;
        private String name;
        private boolean istGeschaeftsfuehrer;
        private String beschaeftigungsart;
        private BigDecimal bruttoJahr = BigDecimal.ZERO;
        private BigDecimal agAnteilSv = BigDecimal.ZERO;
        private BigDecimal bgBeitrag = BigDecimal.ZERO;
        private BigDecimal geldwerterVorteilJahr = BigDecimal.ZERO;
        private BigDecimal gesamtkosten = BigDecimal.ZERO;
        private LohnQuelle quelle;
        private boolean bruttoIstDefault;
    }

    @Getter
    @Setter
    public static class MitarbeiterStundenZeile {
        private Long mitarbeiterId;
        private String name;
        private boolean istGeschaeftsfuehrer;
        private BigDecimal sollstunden = BigDecimal.ZERO;
        private BigDecimal urlaubsstunden = BigDecimal.ZERO;
        private BigDecimal krankheitsstunden = BigDecimal.ZERO;
        private BigDecimal interneStunden = BigDecimal.ZERO;
        private BigDecimal feiertagsstunden = BigDecimal.ZERO;
        private BigDecimal verkaeuflicheStunden = BigDecimal.ZERO;
        private boolean urlaubIstDefault;
        private boolean krankheitIstDefault;
        private boolean interneIstDefault;
        /** true, wenn kein Zeitkonto hinterlegt ist und mit 8 h/Werktag gerechnet wurde. */
        private boolean sollIstDefault;
    }

    @Getter
    @Setter
    public static class KostenstelleAnteil {
        private Long kostenstelleId;
        private String bezeichnung;
        private BigDecimal jahresbetrag = BigDecimal.ZERO;
        private boolean gestreckt;
    }

    @Getter
    @Setter
    public static class AbteilungVorschlag {
        private Long abteilungId;
        private String name;
        private BigDecimal aufschlagEuro = BigDecimal.ZERO;
    }

    @Getter
    @Setter
    public static class DatenLuecke {
        private Long mitarbeiterId;
        private String mitarbeiterName;
        private String problem;
    }
}
