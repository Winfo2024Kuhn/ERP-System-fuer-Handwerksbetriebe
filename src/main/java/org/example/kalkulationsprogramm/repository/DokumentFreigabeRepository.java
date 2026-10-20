package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DokumentFreigabeRepository extends JpaRepository<DokumentFreigabe, Long>
{
    Optional<DokumentFreigabe> findByUuid(String uuid);

    /**
     * Lädt die Freigabe mit Schreibsperre auf der Zeile (SELECT … FOR UPDATE). Code-Versand
     * und Annahme laufen darüber, damit zwei gleichzeitige Klicks nacheinander statt
     * gegeneinander arbeiten: der zweite sieht den Zähler bzw. Status des ersten.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM DokumentFreigabe f WHERE f.uuid = :uuid")
    Optional<DokumentFreigabe> findByUuidZumSperren(@Param("uuid") String uuid);

    /**
     * Wie {@link #findByUuidZumSperren}, wartet aber nicht (FOR UPDATE NOWAIT): Hält gerade ein
     * anderer Aufruf die Zeile, scheitert dieser sofort mit
     * {@link org.springframework.dao.PessimisticLockingFailureException}. Für den Code-Versand –
     * dort hält der Halter die Sperre während des SMTP-Versands, und ein zweiter Klick soll
     * keine Datenbankverbindung blockieren, sondern gleich "bitte kurz warten" hören.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("SELECT f FROM DokumentFreigabe f WHERE f.uuid = :uuid")
    Optional<DokumentFreigabe> findByUuidZumSperrenOhneWarten(@Param("uuid") String uuid);

    @Query("""
            SELECT f FROM DokumentFreigabe f
            WHERE f.quellTyp = :quellTyp AND f.quellDokumentId IN :ids
            """)
    List<DokumentFreigabe> findByQuelle(@Param("quellTyp") FreigabeQuellTyp quellTyp,
                                        @Param("ids") List<Long> ids);

    /** Freigaben zu Geschäftsdokumenten dieser Projekte (Kunde anonymisieren). */
    @Query("""
            SELECT f FROM DokumentFreigabe f
            WHERE f.quellTyp = org.example.kalkulationsprogramm.domain.FreigabeQuellTyp.PROJEKT
              AND f.quellDokumentId IN (
                  SELECT d.id FROM ProjektGeschaeftsdokument d WHERE d.projekt.id IN :projektIds)
            """)
    List<DokumentFreigabe> findFuerProjekte(@Param("projektIds") List<Long> projektIds);

    /** Freigaben zu Geschäftsdokumenten dieser Anfragen (Kunde anonymisieren). */
    @Query("""
            SELECT f FROM DokumentFreigabe f
            WHERE f.quellTyp = org.example.kalkulationsprogramm.domain.FreigabeQuellTyp.ANFRAGE
              AND f.quellDokumentId IN (
                  SELECT d.id FROM AnfrageGeschaeftsdokument d WHERE d.anfrage.id IN :anfrageIds)
            """)
    List<DokumentFreigabe> findFuerAnfragen(@Param("anfrageIds") List<Long> anfrageIds);

    /**
     * Liefert alle Freigaben, die seit dem angegebenen Zeitpunkt akzeptiert wurden,
     * neueste zuerst. Wird vom NotificationController genutzt.
     */
    @Query("""
            SELECT f FROM DokumentFreigabe f
            WHERE f.status = org.example.kalkulationsprogramm.domain.FreigabeStatus.ACCEPTED
              AND f.akzeptiertAm IS NOT NULL
              AND f.akzeptiertAm >= :seit
            ORDER BY f.akzeptiertAm DESC
            """)
    List<DokumentFreigabe> findKuerzlichAkzeptiert(@Param("seit") LocalDateTime seit);
}
