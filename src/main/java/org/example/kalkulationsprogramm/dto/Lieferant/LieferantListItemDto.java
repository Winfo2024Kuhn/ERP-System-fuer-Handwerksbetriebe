package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LieferantListItemDto {
    private Long id;
    private String lieferantenname;
    private String lieferantenTyp;
    private String vertreter;
    private String strasse;
    private String plz;
    private String ort;
    private String telefon;
    private String mobiltelefon;
    private Boolean istAktiv;
    private List<String> kundenEmails;
    private Integer lieferzeit; // Durchschnittliche Lieferzeit in Tagen (berechnet aus ABs)
    private Integer bestellungen; // Anzahl Auftragsbestätigungen (berechnet)
    private Long standardKostenstelleId;
    private String standardKostenstelleName;
}
