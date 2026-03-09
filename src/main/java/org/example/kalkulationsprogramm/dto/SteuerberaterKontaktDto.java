package org.example.kalkulationsprogramm.dto;

import lombok.Data;

@Data
public class SteuerberaterKontaktDto {
    private Long id;
    private String name;
    private String email;
    private String telefon;
    private String ansprechpartner;
    private Boolean autoProcessEmails;
    private Boolean aktiv;
    private String notizen;
    private java.time.LocalDate gueltigAb;
    private java.time.LocalDate gueltigBis;
    private java.util.List<String> weitereEmails;
}
