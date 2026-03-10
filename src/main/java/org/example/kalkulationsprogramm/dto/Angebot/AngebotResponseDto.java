package org.example.kalkulationsprogramm.dto.Angebot;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class AngebotResponseDto {
    private Long id;
    private Long kundenId;
    private String kundenName;
    private String bauvorhaben;
    private String kundennummer;
    private String angebotsnummer;
    private BigDecimal betrag;
    private List<String> kundenEmails;
    private LocalDate emailVersandDatum;
    private Long projektId;
    private LocalDate anlegedatum;
    private String bildUrl;
    private String projektStrasse;
    private String projektPlz;
    private String projektOrt;
    private String kurzbeschreibung;
    private boolean abgeschlossen;
    private java.time.LocalDateTime createdAt;
    // Erweiterte Kundendaten
    private String kundenStrasse;
    private String kundenPlz;
    private String kundenOrt;
    private String kundenTelefon;
    private String kundenMobiltelefon;
    private String kundenAnsprechpartner;
    private String kundenAnrede;
}
