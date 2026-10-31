package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailPostfachZuordnung;
import org.example.kalkulationsprogramm.domain.ServerSpamStatus;
import org.example.kalkulationsprogramm.dto.Email.ServerSpamKandidat;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EmailPostfachZuordnungRepository extends JpaRepository<EmailPostfachZuordnung, Long> {

    boolean existsByPostfachId(Long postfachId);

    java.util.List<EmailPostfachZuordnung> findByEmailId(Long emailId);

    /**
     * Spam, der in diesem Postfach auf dem Server noch im Ordner {@code ordner} (Posteingang)
     * liegt und dort weg soll. Mails im ERP-Papierkorb und Mails mit Projekt, Anfrage oder
     * Lieferant bleiben, wo sie sind.
     */
    @Query("SELECT new org.example.kalkulationsprogramm.dto.Email.ServerSpamKandidat("
            + "z.id, z.imapOrdner, z.imapUid, e.messageId, e.fromAddress, e.subject) "
            + "FROM EmailPostfachZuordnung z JOIN z.email e "
            + "WHERE z.postfach.id = :postfachId AND z.imapOrdner = :ordner AND z.serverSpamStatus IS NULL "
            + "AND e.isSpam = true AND e.deletedAt IS NULL AND e.direction = :richtung "
            + "AND e.projekt IS NULL AND e.anfrage IS NULL AND e.lieferant IS NULL "
            + "ORDER BY z.id ASC")
    java.util.List<ServerSpamKandidat> findSpamZumVerschieben(@Param("postfachId") Long postfachId,
            @Param("ordner") String ordner, @Param("richtung") EmailDirection richtung, Limit limit);

    /** Vom ERP in den Spam-Ordner verschobene Mails, die inzwischen „Kein Spam“ sind. */
    @Query("SELECT new org.example.kalkulationsprogramm.dto.Email.ServerSpamKandidat("
            + "z.id, z.imapOrdner, z.imapUid, e.messageId, e.fromAddress, e.subject) "
            + "FROM EmailPostfachZuordnung z JOIN z.email e "
            + "WHERE z.postfach.id = :postfachId AND z.serverSpamStatus = :status "
            + "AND e.isSpam = false AND e.deletedAt IS NULL "
            + "ORDER BY z.id ASC")
    java.util.List<ServerSpamKandidat> findZurueckZuHolen(@Param("postfachId") Long postfachId,
            @Param("status") ServerSpamStatus status, Limit limit);

    /** Neuer Ort der Mail auf dem Server nach dem Verschieben. */
    @Transactional
    @Modifying
    @Query("UPDATE EmailPostfachZuordnung z SET z.imapOrdner = :ordner, z.imapUid = :uid, "
            + "z.serverSpamStatus = :status WHERE z.id = :id")
    int merkeServerOrt(@Param("id") Long id, @Param("ordner") String ordner, @Param("uid") Long uid,
            @Param("status") ServerSpamStatus status);

    @Transactional
    @Modifying
    @Query("UPDATE EmailPostfachZuordnung z SET z.serverSpamStatus = :status WHERE z.id = :id")
    int merkeServerSpamStatus(@Param("id") Long id, @Param("status") ServerSpamStatus status);

    /**
     * Ausgangsmails ohne Postfach (z. B. aus Mahnlauf, Rechnungsversand, Anfrage-Bestätigung)
     * kommen in das Postfach mit ihrer Absender-Adresse – sonst trügen Rechnungs-Mails nach dem
     * Nachtrag das Schild des Hauptpostfachs und würden auch von dort beantwortet.
     *
     * @return Anzahl neu zugeordneter Mails
     */
    @Modifying
    @NativeQuery("INSERT INTO email_postfach_zuordnung (email_id, postfach_id, imap_ordner, imap_uid) "
            + "SELECT e.id, p.id, e.imap_folder, e.imap_uid FROM email e "
            + "JOIN email_absender p ON LOWER(TRIM(e.from_address)) = LOWER(p.email_adresse) "
            + "WHERE e.direction = 'OUT' "
            + "AND NOT EXISTS (SELECT 1 FROM email_postfach_zuordnung z WHERE z.email_id = e.id)")
    int ordneAusgangsmailsNachAbsenderZu();

    /**
     * Legt alle Mails, die noch in keinem Postfach liegen, in das angegebene Postfach.
     * Ordner und UID werden aus der Mail übernommen. Idempotent: Mails mit Zuordnung
     * bleiben unberührt.
     *
     * @return Anzahl neu zugeordneter Mails
     */
    @Modifying
    @NativeQuery("INSERT INTO email_postfach_zuordnung (email_id, postfach_id, imap_ordner, imap_uid) "
            + "SELECT e.id, :postfachId, e.imap_folder, e.imap_uid FROM email e "
            + "WHERE NOT EXISTS (SELECT 1 FROM email_postfach_zuordnung z WHERE z.email_id = e.id)")
    int ordneOhnePostfachZu(@Param("postfachId") Long postfachId);

    /**
     * Mails, die nur in Postfächern außerhalb von {@code sichtbarePostfachIds} liegen – für den
     * Benutzer also unsichtbar. Mails ohne jede Zuordnung gelten als Hauptpostfach-Mails und
     * tauchen hier nicht auf. {@code sichtbarePostfachIds} darf nicht leer sein (Platzhalter
     * {@code -1} übergeben).
     */
    @Query("SELECT DISTINCT z.email.id FROM EmailPostfachZuordnung z "
            + "WHERE z.postfach.id NOT IN :sichtbarePostfachIds "
            + "AND NOT EXISTS (SELECT 1 FROM EmailPostfachZuordnung s "
            + "WHERE s.email = z.email AND s.postfach.id IN :sichtbarePostfachIds)")
    java.util.Set<Long> findEmailIdsNurInAnderenPostfaechern(
            @Param("sichtbarePostfachIds") java.util.Collection<Long> sichtbarePostfachIds);
}
