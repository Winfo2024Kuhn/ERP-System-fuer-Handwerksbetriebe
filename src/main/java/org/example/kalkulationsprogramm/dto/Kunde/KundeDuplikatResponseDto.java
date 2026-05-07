package org.example.kalkulationsprogramm.dto.Kunde;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Antwort auf einen Duplikat-Check (Live-Hinweis sowie 409-Body beim POST).
 */
@Getter
@Setter
public class KundeDuplikatResponseDto {
    private List<KundeDuplikatTrefferDto> duplikate;
    /** Gibt an, ob mindestens ein "harter" Treffer (E-Mail/Tel.) dabei ist. */
    private boolean harterTreffer;

    public KundeDuplikatResponseDto() {}

    public KundeDuplikatResponseDto(List<KundeDuplikatTrefferDto> duplikate, boolean harterTreffer) {
        this.duplikate = duplikate;
        this.harterTreffer = harterTreffer;
    }
}
