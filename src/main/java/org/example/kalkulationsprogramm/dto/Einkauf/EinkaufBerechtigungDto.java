package org.example.kalkulationsprogramm.dto.Einkauf;

import java.util.Set;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;

public record EinkaufBerechtigungDto(Set<EinkaufBerechtigung> rechte) {
    public EinkaufBerechtigungDto {
        rechte = rechte == null ? Set.of() : Set.copyOf(rechte);
    }
}
