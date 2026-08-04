package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Date;

import org.example.kalkulationsprogramm.domain.Fertigungszustand;
import org.example.kalkulationsprogramm.domain.Herstellverfahren;
import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;

@Getter
@Setter
public class ArtikelResponseDto {
    private Long id;
    private String externeArtikelnummer;
    private String produktlinie;
    private String produktname;
    private String produkttext;
    private Long verpackungseinheit;
    private String preiseinheit;
    private Verrechnungseinheit verrechnungseinheit;
    private String waehrung;
    private BigDecimal preis;
    private BigDecimal kgProMeter;
    private Date preisDatum;
    private Long lieferantId;
    private String lieferantenname;
    private List<LieferantPreisDto> lieferantenpreise;
    private Long kategorieId;
    private String kategoriePfad;
    private Long parentKategorieId;
    private Long rootKategorieId;
    private String rootKategorieName;
    private Long werkstoffId;
    private String werkstoffName;
    private String kommentar;
    private boolean meterware;

    // ------------------------------------------------------------------
    // Guenstigster Preis - die Suche liefert eine Zeile je Artikel
    // ------------------------------------------------------------------
    /** Niedrigster derzeit gueltiger Preis ueber alle Lieferanten. */
    private BigDecimal guenstigsterPreis;
    private Long guenstigsterLieferantId;
    private String guenstigsterLieferantName;
    private Date guenstigsterPreisDatum;
    /** Wie viele Lieferanten den Artikel derzeit mit Preis fuehren. */
    private int anzahlLieferanten;

    // ------------------------------------------------------------------
    // Technische Stammdaten
    // ------------------------------------------------------------------
    private String artikelnummer;
    private Profilform profilform;
    private Herstellverfahren herstellverfahren;
    private Fertigungszustand fertigungszustand;
    private String massnorm;
    private String werkstoffnorm;

    private BigDecimal durchmesser;
    private BigDecimal hoehe;
    private BigDecimal breite;
    private BigDecimal wandstaerke;
    /** Kurzform der Abmessung fuer die Anzeige, z.B. "42,4 x 2". */
    private String abmessung;

    /** Gewicht je Quadratmeter - nur bei Blechen belegt. */
    private BigDecimal kgProQm;
    /** Abzuwickelnde Oberflaeche in m2 je Meter - Basis fuer Beschichtungskosten. */
    private BigDecimal mantelflaeche;
    private Integer standardlaenge;

    // ------------------------------------------------------------------
    // Eignungen fuer die Kalkulation
    // ------------------------------------------------------------------
    /** Feuerverzinken moeglich - Standard vom Werkstoff, am Artikel ueberschreibbar. */
    private boolean verzinkungsgeeignet;
    /** Pulverbeschichten moeglich. */
    private boolean pulverbeschichtungsgeeignet;
    /** Erklaerung, warum eine Beschichtung geht oder nicht. */
    private String beschichtungshinweis;
}

