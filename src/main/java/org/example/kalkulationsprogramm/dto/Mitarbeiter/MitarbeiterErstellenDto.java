package org.example.kalkulationsprogramm.dto.Mitarbeiter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;

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
    private List<Long> abteilungIds;

    // EN 1090 – Rollenzuweisungen
    private List<Long> en1090RolleIds;
}
