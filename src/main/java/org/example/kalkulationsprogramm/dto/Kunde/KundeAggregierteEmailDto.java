package org.example.kalkulationsprogramm.dto.Kunde;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class KundeAggregierteEmailDto {
    private String email;
    private boolean ausStammdaten;
    private List<KundeEmailQuelleDto> quellen = new ArrayList<>();
}

