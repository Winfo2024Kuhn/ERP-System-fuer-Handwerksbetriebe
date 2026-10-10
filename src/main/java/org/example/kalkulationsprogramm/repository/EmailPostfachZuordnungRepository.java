package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.EmailPostfachZuordnung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailPostfachZuordnungRepository extends JpaRepository<EmailPostfachZuordnung, Long> {

    boolean existsByPostfachId(Long postfachId);

    java.util.List<EmailPostfachZuordnung> findByEmailId(Long emailId);

    /**
     * Ausgangsmails ohne Postfach (z. B. aus Mahnlauf, Rechnungsversand, Anfrage-Bestätigung)
     * kommen in das Postfach mit ihrer Absender-Adresse – sonst trügen Rechnungs-Mails nach dem
     * Nachtrag das Schild des Hauptpostfachs und würden auch von dort beantwortet.
     *
     * @return Anzahl neu zugeordneter Mails
     */
    @Modifying
    @Query(value = "INSERT INTO email_postfach_zuordnung (email_id, postfach_id, imap_ordner, imap_uid) "
            + "SELECT e.id, p.id, e.imap_folder, e.imap_uid FROM email e "
            + "JOIN email_absender p ON LOWER(TRIM(e.from_address)) = LOWER(p.email_adresse) "
            + "WHERE e.direction = 'OUT' "
            + "AND NOT EXISTS (SELECT 1 FROM email_postfach_zuordnung z WHERE z.email_id = e.id)",
            nativeQuery = true)
    int ordneAusgangsmailsNachAbsenderZu();

    /**
     * Legt alle Mails, die noch in keinem Postfach liegen, in das angegebene Postfach.
     * Ordner und UID werden aus der Mail übernommen. Idempotent: Mails mit Zuordnung
     * bleiben unberührt.
     *
     * @return Anzahl neu zugeordneter Mails
     */
    @Modifying
    @Query(value = "INSERT INTO email_postfach_zuordnung (email_id, postfach_id, imap_ordner, imap_uid) "
            + "SELECT e.id, :postfachId, e.imap_folder, e.imap_uid FROM email e "
            + "WHERE NOT EXISTS (SELECT 1 FROM email_postfach_zuordnung z WHERE z.email_id = e.id)",
            nativeQuery = true)
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
