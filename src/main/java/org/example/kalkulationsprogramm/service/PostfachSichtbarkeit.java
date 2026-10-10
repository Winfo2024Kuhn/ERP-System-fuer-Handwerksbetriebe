package org.example.kalkulationsprogramm.service;

import java.util.Objects;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailPostfachZuordnung;

/**
 * Was ein angemeldeter Benutzer im E-Mail-Center sieht: entweder alles (Admin) oder die Mails
 * aus den Postfächern {@link #sichtbarePostfachIds()}. Reiner Wert ohne Datenbankzugriff –
 * ermittelt vom {@link PostfachSichtbarkeitService}.
 *
 * <p>Regeln für Mails:</p>
 * <ul>
 *   <li>Sichtbar, wenn sie in mindestens einem sichtbaren Postfach liegt.</li>
 *   <li>Mails ohne Postfach-Zuordnung gelten als Hauptpostfach-Mails – sichtbar.</li>
 *   <li>Lesen (Detail, Verlauf, Anhänge) ist zusätzlich erlaubt, wenn die Mail einem Projekt,
 *       einer Anfrage oder einem Lieferanten zugeordnet ist: Die Reiter dort sind ungefiltert
 *       und öffnen Mails über das E-Mail-Center.</li>
 * </ul>
 *
 * @param alles                {@code true} für Admins: keine Einschränkung
 * @param sichtbarePostfachIds sichtbare, eingeschaltete Postfächer (bei {@code alles} ohne Bedeutung)
 */
public record PostfachSichtbarkeit(boolean alles, Set<Long> sichtbarePostfachIds) {

    /** Keine Einschränkung (Admin). */
    public static final PostfachSichtbarkeit ALLES = new PostfachSichtbarkeit(true, Set.of());

    public PostfachSichtbarkeit {
        sichtbarePostfachIds = sichtbarePostfachIds == null ? Set.of() : Set.copyOf(sichtbarePostfachIds);
    }

    /** Darf der Benutzer dieses Postfach sehen bzw. darüber senden? */
    public boolean siehtPostfach(Long postfachId) {
        return alles || (postfachId != null && sichtbarePostfachIds.contains(postfachId));
    }

    /** Wie {@link #siehtPostfach(Long)}. */
    public boolean siehtPostfach(EmailAbsender postfach) {
        return postfach != null && siehtPostfach(postfach.getId());
    }

    /** Erscheint die Mail in den Listen des E-Mail-Centers bzw. darf sie bearbeitet werden? */
    public boolean siehtEmail(Email email) {
        if (email == null) {
            return false;
        }
        if (alles || email.getPostfachZuordnungen() == null || email.getPostfachZuordnungen().isEmpty()) {
            return true;
        }
        return email.getPostfachZuordnungen().stream()
                .map(EmailPostfachZuordnung::getPostfach)
                .filter(Objects::nonNull)
                .anyMatch(this::siehtPostfach);
    }

    /** Darf die Mail geöffnet werden (Detail, Verlauf, Anhänge)? */
    public boolean darfLesen(Email email) {
        return siehtEmail(email) || istZugeordnet(email);
    }

    private static boolean istZugeordnet(Email email) {
        return email != null
                && (email.getProjekt() != null || email.getAnfrage() != null || email.getLieferant() != null);
    }
}
