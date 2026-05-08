package org.example.kalkulationsprogramm.dto;

import lombok.Data;

@Data
public class SteuerberaterAnsprechpartnerDto {
    private Long id;
    private String anrede;
    private String vorname;
    private String nachname;
    private String email;
    private String telefon;
    private Boolean istLohnAnsprechpartner;
    private String notizen;
}
