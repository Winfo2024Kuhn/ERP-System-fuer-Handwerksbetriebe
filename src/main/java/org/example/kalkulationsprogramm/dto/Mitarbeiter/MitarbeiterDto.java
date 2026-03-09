package org.example.kalkulationsprogramm.dto.Mitarbeiter;

import lombok.Data;
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
    private List<Long> abteilungIds;
    private String abteilungNames; // Komma-separierte Namen für Anzeige
    private String loginToken;
}
