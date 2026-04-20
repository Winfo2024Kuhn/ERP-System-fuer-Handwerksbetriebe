package org.example.kalkulationsprogramm.dto.Preisanfrage;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ausgabe-DTO fuer einen einzelnen Lieferanten-Eintrag einer Preisanfrage.
 * Enthaelt den Token nur fuer angemeldete Nutzer (wird serverseitig geliefert,
 * dient nicht als Zugriffsgeheimnis gegenueber Dritten).
 */
@Getter
@Setter
@NoArgsConstructor
public class PreisanfrageLieferantDto {

    private Long id;
    private Long lieferantId;
    private String lieferantenname;
    private String token;
    private String versendetAn;
    private LocalDateTime versendetAm;
    private LocalDateTime antwortErhaltenAm;
    private Long antwortEmailId;
    private String status;
}
