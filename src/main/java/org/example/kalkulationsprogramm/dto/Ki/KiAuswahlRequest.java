package org.example.kalkulationsprogramm.dto.Ki;

/**
 * Standard-Anbieter wählen.
 *
 * @param ersatzErlaubt {@code null} = unverändert lassen
 */
public record KiAuswahlRequest(String anbieter, Boolean ersatzErlaubt) {
}
