package org.example.kalkulationsprogramm.domain;

import java.util.List;

import org.example.kalkulationsprogramm.util.suche.EmailSuchtext;
import org.hibernate.Hibernate;

import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * Pflegt {@code email.suchtext} beim Speichern – für jede neue oder geänderte Mail, egal über
 * welchen Weg (Import, Versand, Archiv). Entity-Listener an {@link Email} und {@link EmailAttachment}.
 *
 * <ul>
 *   <li>Neue Mail: Suchtext samt Anhang-Dateinamen berechnen.</li>
 *   <li>Neuer Anhang an einer schon gespeicherten Mail (IMAP-Import): Dateiname nachtragen.</li>
 *   <li>Geänderte Mail: nur neu berechnen, wenn sich ein durchsuchbares Feld geändert hat
 *       (Vergleich der beim Laden gemerkten Referenzen – kostet nichts). Sind die Anhänge nicht
 *       geladen, wird nichts nachgeladen (im Flush verboten): der Suchtext wird geleert und von
 *       {@code EmailSuchtextNachpflege} neu berechnet.</li>
 * </ul>
 */
public class EmailSuchtextPflege {

    @PostLoad
    void geladen(Object entitaet) {
        if (entitaet instanceof Email email) {
            email.setSuchQuellenStand(quellen(email));
        }
    }

    @PrePersist
    void vorDemAnlegen(Object entitaet) {
        if (entitaet instanceof Email email) {
            berechnen(email);
        } else if (entitaet instanceof EmailAttachment anhang && anhang.getEmail() != null) {
            Email email = anhang.getEmail();
            email.setSuchtext(EmailSuchtext.mitDateiname(email.getSuchtext(), anhang.getOriginalFilename()));
        }
    }

    @PreUpdate
    void vorDerAenderung(Object entitaet) {
        if (!(entitaet instanceof Email email)) {
            return;
        }
        if (email.getSuchtext() != null && !geaendert(email)) {
            return;
        }
        if (Hibernate.isInitialized(email.getAttachments())) {
            berechnen(email);
        } else {
            email.setSuchtext(null);
            email.setSuchQuellenStand(quellen(email));
        }
    }

    private static void berechnen(Email email) {
        List<String> dateinamen = email.getAttachments() == null ? List.of()
                : email.getAttachments().stream().map(EmailAttachment::getOriginalFilename).toList();
        email.setSuchtext(EmailSuchtext.aus(new EmailSuchtext.Quellen(email.getFromAddress(),
                email.getReplyToAddress(), email.getRecipient(), email.getCc(), email.getSubject(), email.getBody(),
                email.getHtmlBody(), dateinamen)));
        email.setSuchQuellenStand(quellen(email));
    }

    private static Object[] quellen(Email email) {
        return new Object[] { email.getFromAddress(), email.getReplyToAddress(), email.getRecipient(),
                email.getCc(), email.getSubject(), email.getBody(), email.getHtmlBody() };
    }

    private static boolean geaendert(Email email) {
        Object[] stand = email.getSuchQuellenStand();
        Object[] jetzt = quellen(email);
        if (stand == null || stand.length != jetzt.length) {
            return true;
        }
        for (int i = 0; i < jetzt.length; i++) {
            if (jetzt[i] != stand[i]) {
                return true;
            }
        }
        return false;
    }
}
