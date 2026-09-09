package org.example.kalkulationsprogramm.dto.Mitarbeiter;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class MitarbeiterErstellenDto {
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
    /** Null: bei Neuanlage true, bei Update bisherigen Wert beibehalten.
     * Änderungen bestehender Kontoführung erfordern eine atomare Versionsoperation. */
    private Boolean fuehrtZeitkonto;
    private List<Long> abteilungIds;
    private String beschaeftigungsart;
    private Long krankenkasseId;
    private Boolean kinderlos;
    private Boolean istGeschaeftsfuehrer;
    private BigDecimal kalkulatorischerLohnMonat;
    private BigDecimal geldwertVorteilMonat;
}
