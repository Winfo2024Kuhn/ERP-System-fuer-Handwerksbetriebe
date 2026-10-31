package org.example.kalkulationsprogramm.dto.Ids;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.ids.IdsProtokoll;

/** Shop-Zugang eines Lieferanten; das Passwort selbst verlässt den Server nie. */
public record IdsZugangDto(Long lieferantId, String lieferantName, boolean aktiv, IdsProtokoll protokoll,
        String shopUrl, String kundennummer, String benutzername, boolean passwortGesetzt, LocalDateTime geaendertAm) {
}
