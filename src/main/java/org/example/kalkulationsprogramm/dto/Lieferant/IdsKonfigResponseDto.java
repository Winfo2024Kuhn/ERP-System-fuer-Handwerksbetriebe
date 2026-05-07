package org.example.kalkulationsprogramm.dto.Lieferant;

import org.example.kalkulationsprogramm.domain.IdsProtokoll;

/**
 * Ausgabe-DTO der IDS-Connect-Konfiguration. Das Passwort wird hier
 * NIEMALS im Klartext zurückgegeben – stattdessen ein Platzhalter
 * ("********"), falls eines hinterlegt ist, sonst {@code null}.
 */
public record IdsKonfigResponseDto(
        boolean aktiviert,
        IdsProtokoll protokoll,
        String punchoutUrl,
        String kundennummer,
        String loginName,
        String passwort,
        String notizen) {
}
