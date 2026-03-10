package org.example.kalkulationsprogramm.dto;

import lombok.Data;

@Data
public class FirmeninformationDto {
    private Long id;
    private String firmenname;
    private String strasse;
    private String plz;
    private String ort;
    private String telefon;
    private String fax;
    private String email;
    private String website;
    private String steuernummer;
    private String ustIdNr;
    private String handelsregister;
    private String handelsregisterNummer;
    private String bankName;
    private String iban;
    private String bic;
    private String logoDateiname;
    private String geschaeftsfuehrer;
    private String fusszeileText;
}
