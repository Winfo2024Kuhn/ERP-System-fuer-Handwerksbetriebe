package org.example.kalkulationsprogramm.repository;

import java.util.Collection;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Email;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Abfragen der E-Mail-Suche: Treffer-IDs ({@link EmailSucheRepositoryErweiterung}), die Mails einer
 * Trefferseite und die Nachpflege des Suchtexts für den Altbestand.
 */
public interface EmailSucheRepository extends Repository<Email, Long>, EmailSucheRepositoryErweiterung {

    /**
     * Mails einer Trefferseite mit Projekt, Anfrage, Lieferant und Ursprungsmail in einer Abfrage.
     * Anhänge, Postfach-Zuordnungen und Antworten lädt Hibernate gebündelt ({@code @BatchSize}).
     */
    @Query("SELECT e FROM Email e LEFT JOIN FETCH e.projekt LEFT JOIN FETCH e.anfrage "
            + "LEFT JOIN FETCH e.lieferant LEFT JOIN FETCH e.parentEmail WHERE e.id IN :ids")
    List<Email> findSeite(@Param("ids") Collection<Long> ids);

    /** Felder für den Suchtext der nächsten Mails ohne Suchtext (Nachpflege), kleinste ID zuerst. */
    @Query("SELECT new org.example.kalkulationsprogramm.repository.EmailSuchQuellen(e.id, e.fromAddress, "
            + "e.replyToAddress, e.recipient, e.cc, e.subject, e.body, e.htmlBody) "
            + "FROM Email e WHERE e.suchtext IS NULL ORDER BY e.id")
    List<EmailSuchQuellen> findOhneSuchtext(Pageable seite);

    /** Anhang-Dateinamen der Mails ({@code [emailId, dateiname]}). */
    @Query("SELECT a.email.id, a.originalFilename FROM EmailAttachment a WHERE a.email.id IN :ids")
    List<Object[]> findDateinamen(@Param("ids") Collection<Long> ids);

    /** Setzt den Suchtext, wenn ihn niemand inzwischen beim Speichern gesetzt hat. */
    @Modifying
    @Query("UPDATE Email e SET e.suchtext = :suchtext WHERE e.id = :id AND e.suchtext IS NULL")
    int setzeSuchtext(@Param("id") Long id, @Param("suchtext") String suchtext);
}
