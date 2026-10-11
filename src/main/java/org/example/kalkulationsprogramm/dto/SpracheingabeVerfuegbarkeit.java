package org.example.kalkulationsprogramm.dto;

/**
 * Sagt der mobilen Zeiterfassung, ob sie den Mikrofon-Knopf zeigen soll.
 *
 * @param verfuegbar true, wenn ein KI-Schluessel hinterlegt ist; ohne ihn ist
 *                   die Spracheingabe aus
 */
public record SpracheingabeVerfuegbarkeit(boolean verfuegbar) {}
