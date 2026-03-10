package org.example.kalkulationsprogramm.dto.Arbeitsgang;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ArbeitsgangErstellenDto {
    private String beschreibung;
    private Long abteilungId;
}
