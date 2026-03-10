package org.example.kalkulationsprogramm.dto.Kunde;

import lombok.Data;
import java.util.List;

@Data
public class KundeResponseDto {
    private Long id;
    private String kundennummer;
    private String name;
    private String anrede;
    private String ansprechspartner;
    private String strasse;
    private String plz;
    private String ort;
    private String telefon;
    private String mobiltelefon;
    private List<String> kundenEmails;
}
