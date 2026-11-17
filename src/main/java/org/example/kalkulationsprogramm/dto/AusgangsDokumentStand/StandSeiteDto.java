package org.example.kalkulationsprogramm.dto.AusgangsDokumentStand;

import java.util.List;

/**
 * Eine Seite des Verlaufs, neueste Stände zuerst.
 *
 * @param offeneAenderungen Änderungen des aktuellen Inhalts gegenüber dem neuesten Stand
 *                          ({@code []} wenn gleich, {@code null} wenn es noch keinen Stand gibt)
 * @param dokumentBearbeitbar ob das aktuelle Dokument noch geändert werden darf (Wiederherstellen möglich)
 */
public record StandSeiteDto(List<StandEintragDto> staende, long gesamt, int seite, int seitenGroesse,
        boolean hatMehr, List<String> offeneAenderungen, boolean dokumentBearbeitbar) {
}
