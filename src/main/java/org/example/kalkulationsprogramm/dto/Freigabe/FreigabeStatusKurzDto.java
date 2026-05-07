package org.example.kalkulationsprogramm.dto.Freigabe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Kompaktes Status-DTO für Anfrage-/Projekt-Karten in der Übersicht.
 * Zeigt den jüngsten relevanten Freigabe-Status pro Container an.
 */
@Getter
@Builder
@AllArgsConstructor
public class FreigabeStatusKurzDto
{
    private String status;
    private String dokumentArt;
    private String dokumentNummer;
    private LocalDateTime akzeptiertAm;
    private LocalDateTime ablaufDatum;
    private LocalDateTime erstelltAm;
}
