package org.example.kalkulationsprogramm.dto;

/**
 * Ergebnis der Spracherkennung fuer die mobile Zeiterfassung.
 *
 * @param text der von der KI erkannte, aufgeraeumte Text (reiner Text, kein
 *             Markdown und kein HTML) - siehe SpracheingabeService.SYSTEM_ANWEISUNG
 */
public record SpracheingabeErgebnis(String text) {}
