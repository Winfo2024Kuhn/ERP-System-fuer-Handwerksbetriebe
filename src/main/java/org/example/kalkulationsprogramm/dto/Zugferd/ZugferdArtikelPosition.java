package org.example.kalkulationsprogramm.dto.Zugferd;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO für eine Artikelposition aus ZUGFeRD-XML.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ZugferdArtikelPosition {
    private String externeArtikelnummer; // SellerAssignedID / BuyerAssignedID
    private String bezeichnung; // Name des Produkts
    private BigDecimal menge; // BilledQuantity
    private String mengeneinheit; // UnitCode (kg, Stk, etc.)
    private BigDecimal einzelpreis; // ChargeAmount / NetPriceProductTradePrice
    private String preiseinheit; // BasisQuantity Unit (t, 100 kg, kg)
}
