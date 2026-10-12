package org.example.kalkulationsprogramm.service;

import java.util.Collections;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;

/**
 * Welche Mails ein Benutzer gelesen hat – reiner Wert ohne Datenbankzugriff, ermittelt vom
 * {@link EmailGelesenService}. Gesendete Mails gelten immer als gelesen.
 *
 * @param gelesen     IDs der Mails, die der Benutzer gelesen hat
 * @param proBenutzer {@code false}, wenn kein Benutzer angemeldet ist: dann zählt das gemeinsame
 *                    Feld der Mail („irgendwer hat sie geöffnet“)
 */
public record GelesenStand(Set<Long> gelesen, boolean proBenutzer) {

    /** Ohne angemeldeten Benutzer: das gemeinsame Feld der Mail. */
    public static final GelesenStand GEMEINSAM = new GelesenStand(Set.of(), false);

    public GelesenStand {
        // Keine Kopie: Die Menge kommt frisch aus der Datenbank und kann groß sein.
        gelesen = gelesen == null ? Set.of() : Collections.unmodifiableSet(gelesen);
    }

    public boolean istGelesen(Email email) {
        if (email == null || email.getDirection() == EmailDirection.OUT) {
            return true;
        }
        if (!proBenutzer) {
            return email.isRead();
        }
        return email.getId() != null && gelesen.contains(email.getId());
    }

    public boolean istUngelesen(Email email) {
        return !istGelesen(email);
    }
}
