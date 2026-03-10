package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LieferantSearchResponseDto {
    private List<LieferantListItemDto> lieferanten;
    private long gesamt;
    private int seite;
    private int seitenGroesse;
}
