package org.example.kalkulationsprogramm.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Event das ausgelöst wird, wenn E-Mail-Adressen einer Entität geändert werden.
 * Wird verwendet um rückwirkend E-Mails zuzuordnen.
 */
@Getter
@AllArgsConstructor
public class EmailAddressChangedEvent {

    public enum EntityType {
        KUNDE, LIEFERANT, ANFRAGE, PROJEKT, ANGEBOT
    }

    /**
     * Typ der Entität, deren E-Mail-Adressen geändert wurden.
     */
    private final EntityType entityType;

    /**
     * ID der Entität.
     */
    private final Long entityId;

    /**
     * Neu hinzugefügte E-Mail-Adressen (für gezieltes Backfill).
     */
    private final List<String> newAddresses;

    /**
     * Alle aktuellen E-Mail-Adressen der Entität.
     */
    private final List<String> allAddresses;

    /**
     * True wenn die Entität gerade neu angelegt wurde.
     */
    private final boolean newEntity;

    /**
     * Factory-Methode für neu erstellte Entitäten.
     */
    public static EmailAddressChangedEvent forNewEntity(EntityType type, Long id, List<String> addresses) {
        return new EmailAddressChangedEvent(type, id, addresses, addresses, true);
    }

    /**
     * Factory-Methode für Adress-Änderungen an bestehenden Entitäten.
     */
    public static EmailAddressChangedEvent forAddressChange(EntityType type, Long id, 
            List<String> newAddresses, List<String> allAddresses) {
        return new EmailAddressChangedEvent(type, id, newAddresses, allAddresses, false);
    }
}
