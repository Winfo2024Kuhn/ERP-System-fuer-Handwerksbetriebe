package org.example.kalkulationsprogramm.dto.Kunde;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KundeSearchResponseDto {
    private List<KundeListItemDto> kunden;
    private long gesamt;
    private int seite;
    private int seitenGroesse;
}
