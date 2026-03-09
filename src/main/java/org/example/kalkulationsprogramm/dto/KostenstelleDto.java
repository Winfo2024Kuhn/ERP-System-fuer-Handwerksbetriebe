package org.example.kalkulationsprogramm.dto;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.KostenstellenTyp;

@Data
public class KostenstelleDto {
    private Long id;
    private String bezeichnung;
    private KostenstellenTyp typ;
    private String beschreibung;
    private boolean istFixkosten;
    private boolean istInvestition;
    private boolean aktiv;
    private Integer sortierung;
}
