package org.example.kalkulationsprogramm.repository;

/**
 * Ein nicht eingebetteter Anhang an einer Mail des Bereichs in einem Verlauf.
 *
 * @param wurzelId Wurzel des Verlaufs
 * @param anhangId ID des Anhangs ({@code email_attachment.id})
 */
public record EmailVerlaufAnhang(Long wurzelId, Long anhangId) {
}
