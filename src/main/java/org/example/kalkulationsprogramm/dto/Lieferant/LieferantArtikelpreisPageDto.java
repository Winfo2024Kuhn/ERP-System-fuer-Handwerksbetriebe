package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LieferantArtikelpreisPageDto {
    private List<LieferantArtikelpreisDto> artikelpreise;
    private long gesamt;
    private int seite;
    private int seitenGroesse;
}
