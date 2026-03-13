package org.example.kalkulationsprogramm.dto.Anfrage;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class AnfrageErstellenDto {
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
    private BigDecimal betrag;
}
