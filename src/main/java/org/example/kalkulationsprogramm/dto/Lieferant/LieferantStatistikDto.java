package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class LieferantStatistikDto {
    private int artikelAnzahl;
    private long emailAnzahl;
    private LocalDateTime letzteEmail;
    private List<String> emailDomains;
    private int bestellungAnzahl; // Anzahl der Auftragsbestätigungen
    private Integer lieferzeit; // Durchschnittliche Lieferzeit in Tagen
    private Double gesamtKosten; // Summe aller Rechnungen (Netto)
}
