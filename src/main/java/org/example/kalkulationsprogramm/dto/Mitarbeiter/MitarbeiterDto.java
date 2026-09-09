package org.example.kalkulationsprogramm.dto.Mitarbeiter;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class MitarbeiterDto {
    private Long id;
    private String vorname;
    private String nachname;
    private String strasse;
    private String plz;
    private String ort;
    private String email;
    private String telefon;
    private String festnetz;
    private String qualifikation;
    private BigDecimal stundenlohn;
    private Integer jahresUrlaub;
    private LocalDate geburtstag;
    private LocalDate eintrittsdatum;
    private Boolean aktiv;
    private MitarbeiterArt art;
    private Boolean fuehrtZeitkonto;
    private List<Long> abteilungIds;
    private String abteilungNames; // Komma-separierte Namen für Anzeige
    private String loginToken;

    // Lohn-/Sozialversicherungs-Felder (siehe V297__mitarbeiter_sv_felder.sql).
    private String beschaeftigungsart;
    private String beschaeftigungsartLabel;
    private Long krankenkasseId;
    private String krankenkasseName;
    private Boolean kinderlos;

    // Geschaeftsfuehrer-Felder (siehe V300__mitarbeiter_geschaeftsfuehrer.sql).
    // Wenn istGeschaeftsfuehrer=true, fliesst kalkulatorischerLohnMonat statt
    // echtes Brutto in die Lohnsumme des Verrechnungslohn-Rechners ein.
    private Boolean istGeschaeftsfuehrer;
    private BigDecimal kalkulatorischerLohnMonat;
    private BigDecimal geldwertVorteilMonat;
}
