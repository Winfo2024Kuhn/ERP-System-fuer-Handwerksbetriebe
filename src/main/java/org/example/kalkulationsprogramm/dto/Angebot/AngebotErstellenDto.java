package org.example.kalkulationsprogramm.dto.Angebot;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class AngebotErstellenDto {
    private String bauvorhaben;
    private String kunde;
    private Long kundenId;
    private String kundennummer;
    private List<String> kundenEmails;
    private LocalDate anlegedatum;
    private String projektStrasse;
    private String projektPlz;
    private String projektOrt;
    private String kurzbeschreibung;
    private Boolean abgeschlossen;
}
