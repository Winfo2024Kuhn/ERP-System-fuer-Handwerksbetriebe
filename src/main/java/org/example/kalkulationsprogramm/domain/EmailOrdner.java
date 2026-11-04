package org.example.kalkulationsprogramm.domain;

import java.util.Optional;

/**
 * Ordner des E-Mail-Centers. Der {@link #schluessel()} ist der Pfad der Ordnerliste
 * ({@code GET /api/emails/{schluessel}}), der Wert des Felds {@code folder} im
 * {@code UnifiedEmailDto} und der Parameter {@code ordner} der Suche.
 *
 * <p>{@link #ALLE} gibt es nur in der Suche (alle sichtbaren Mails samt Papierkorb, Spam,
 * Newsletter und Gesendet). {@link #STARRED}, {@link #UNASSIGNED} und {@link #ALLE} liefert
 * {@link #von(Email)} nie: Markiert und „Nicht zugeordnet“ sind Ansichten quer über die Ordner.</p>
 */
public enum EmailOrdner {
    ALLE("alle"),
    INBOX("inbox"),
    SENT("sent"),
    TRASH("trash"),
    SPAM("spam"),
    NEWSLETTER("newsletter"),
    STARRED("starred"),
    PROJECTS("projects"),
    OFFERS("offers"),
    SUPPLIERS("suppliers"),
    TAX_ADVISORS("tax-advisors"),
    UNASSIGNED("unassigned"),
    INQUIRIES("inquiries");

    private final String schluessel;

    EmailOrdner(String schluessel) {
        this.schluessel = schluessel;
    }

    public String schluessel() {
        return schluessel;
    }

    /** Ordner zum Schlüssel (genau so geschrieben); leer bei unbekanntem Schlüssel. */
    public static Optional<EmailOrdner> vonSchluessel(String schluessel) {
        for (EmailOrdner ordner : values()) {
            if (ordner.schluessel.equals(schluessel)) {
                return Optional.of(ordner);
            }
        }
        return Optional.empty();
    }

    /**
     * In welchem Ordner liegt die Mail? Reihenfolge: Papierkorb, Spam, Newsletter, Gesendet,
     * Zuordnung (Projekt, Anfrage, Lieferant, Steuerberater), mögliche Anfrage, sonst Posteingang.
     * Steuerberater-Zuordnung setzt nur {@code SteuerberaterEmailProcessingService}, wenn der Absender
     * ein gepflegter Steuerberater ist – solche Mails stehen im Ordner „Steuerberater“, nie im
     * Posteingang (der verlangt Zuordnung KEINE).
     */
    public static EmailOrdner von(Email email) {
        if (email.getDeletedAt() != null) return TRASH;
        if (email.isSpam()) return SPAM;
        if (email.isNewsletter()) return NEWSLETTER;
        if (email.getDirection() == EmailDirection.OUT) return SENT;
        EmailZuordnungTyp typ = email.getZuordnungTyp();
        if (typ != null) {
            switch (typ) {
                case PROJEKT: return PROJECTS;
                case ANFRAGE: return OFFERS;
                case LIEFERANT: return SUPPLIERS;
                case STEUERBERATER: return TAX_ADVISORS;
                default: break;
            }
        }
        if (email.isPotentialInquiry()) return INQUIRIES;
        return INBOX;
    }
}
