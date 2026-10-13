package org.example.kalkulationsprogramm.dto.Artikel;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Fertigungszustand;
import org.example.kalkulationsprogramm.domain.Herstellverfahren;
import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;

import lombok.Getter;
import lombok.Setter;

/**
 * Alle Stammdaten eines Artikels zum Ändern. Es ist ein vollständiges Ersetzen: Was fehlt, wird geleert, nur der Produktname ist Pflicht.
 * Die Abmessungen gelten nur für Halbzeug ({@code ArtikelWerkstoffe}), bei anderen Artikeln werden sie ignoriert. Texte für Angebote,
 * Aufschlag, Lieferanten, Preise und Gewicht haben eigene Endpunkte bzw. bleiben unberührt.
 */
@Getter
@Setter
public class ArtikelStammdatenRequest {

    /** Stand des Artikels beim Laden (Pflicht). Wurde er inzwischen von jemand anderem geändert, wird das Speichern abgelehnt. */
    private Long version;

    private String produktname;
    private String produktlinie;
    private String produkttext;
    private String artikelnummer;
    private String ean;
    private Long verpackungseinheit;
    private String preiseinheit;
    private Verrechnungseinheit verrechnungseinheit;
    private Long kategorieId;
    private Long werkstoffId;

    private Profilform profilform;
    private Herstellverfahren herstellverfahren;
    private Fertigungszustand fertigungszustand;
    private String massnorm;
    private String werkstoffnorm;

    /** Eignung zum Feuerverzinken nur für diesen Artikel festlegen; leer = vom Werkstoff übernehmen. */
    private Boolean verzinkungsgeeignet;
    /** Eignung zum Pulverbeschichten nur für diesen Artikel festlegen; leer = vom Werkstoff übernehmen. */
    private Boolean pulverbeschichtungsgeeignet;

    private BigDecimal durchmesser;
    private BigDecimal hoehe;
    private BigDecimal breite;
    private BigDecimal wandstaerke;
    private BigDecimal stegdicke;
    private BigDecimal flanschdicke;
    private Integer standardlaenge;
}
