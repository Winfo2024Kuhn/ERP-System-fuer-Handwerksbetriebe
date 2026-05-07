package org.example.kalkulationsprogramm.dto.Anfrage;

import java.util.List;

/**
 * Antwort für den paginierten Anfragen-Listenendpunkt
 * (analog zu {@code ProjektSearchResponseDto}). Wird ausgeliefert, sobald der
 * Aufrufer einen {@code page}-Query-Parameter setzt.
 */
public record AnfrageSeiteResponseDto(
        List<AnfrageResponseDto> anfragen,
        long gesamt,
        int seite,
        int seitenGroesse) {
}
