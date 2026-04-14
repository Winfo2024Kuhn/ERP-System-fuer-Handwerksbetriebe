package org.example.kalkulationsprogramm.dto;

import lombok.Data;

@Data
public class En1090RolleDto {
    private Long id;
    private String kurztext;
    private String beschreibung;
    private Integer sortierung;
    private Boolean aktiv;
}
