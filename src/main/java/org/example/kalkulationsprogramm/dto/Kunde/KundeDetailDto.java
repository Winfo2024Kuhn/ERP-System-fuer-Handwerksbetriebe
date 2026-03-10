package org.example.kalkulationsprogramm.dto.Kunde;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KundeDetailDto {
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
    private KundeStatistikDto statistik;
    private List<KundeAggregierteEmailDto> aggregierteEmails;
    private List<KundeProjektKurzDto> projekte;
    private List<KundeAngebotKurzDto> angebote;
    private List<KundeKommunikationDto> kommunikation;
}
