package org.example.kalkulationsprogramm.dto.Zugferd;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO für eine Position einer E-Rechnung – gelesen aus dem ZUGFeRD-XML oder für den Export
 * aus einer Rechnungsposition gebaut.
 */
@Getter
@Setter
@NoArgsConstructor
public class ZugferdArtikelPosition {
    private String externeArtikelnummer; // SellerAssignedID / BuyerAssignedID
    private String bezeichnung; // Name des Produkts
    private BigDecimal menge; // BilledQuantity
    private String mengeneinheit; // UnitCode (kg, Stk, etc.)
    private BigDecimal einzelpreis; // ChargeAmount / NetPriceProductTradePrice
    private String preiseinheit; // BasisQuantity Unit (t, 100 kg, kg)
    private BigDecimal gesamtpreisNetto; // LineTotalAmount / LineExtensionAmount

    // Nur beim Erstellen einer E-Rechnung:
    /** Langtext der Position (BT-154), ohne HTML. */
    private String beschreibung;
    /** Steuersatz dieser Position, in Prozent (19) oder als Anteil (0.19); {@code null} = Satz der Rechnung. */
    private BigDecimal mwstSatz;
    /** Positionsrabatt in Prozent – nur für den Text des Abzugs; der Betrag folgt aus {@link #gesamtpreisNetto}. */
    private BigDecimal rabattProzent;

    public ZugferdArtikelPosition(String externeArtikelnummer, String bezeichnung, BigDecimal menge,
            String mengeneinheit, BigDecimal einzelpreis, String preiseinheit, BigDecimal gesamtpreisNetto) {
        this.externeArtikelnummer = externeArtikelnummer;
        this.bezeichnung = bezeichnung;
        this.menge = menge;
        this.mengeneinheit = mengeneinheit;
        this.einzelpreis = einzelpreis;
        this.preiseinheit = preiseinheit;
        this.gesamtpreisNetto = gesamtpreisNetto;
    }
}
