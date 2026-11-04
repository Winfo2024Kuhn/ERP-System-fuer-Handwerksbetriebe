package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailBezug;
import org.springframework.data.repository.Repository;

/**
 * Abfragen des E-Mail-Reiters in Projekt, Anfrage, Kunde und Lieferant ({@link EmailReiterRepositoryErweiterung}).
 * Die Detailseiten holen hier nur die Zahl für die Reiter-Beschriftung – aus derselben Abfrage wie
 * die Liste.
 */
public interface EmailReiterRepository extends Repository<Email, Long>, EmailReiterRepositoryErweiterung {

    /**
     * Zahl der Verläufe ohne Suche und Filter, z. B. „E-Mails (140)“. Ohne Postfach-Grenze: Mails eines
     * Bereichs sind zugeordnet und dürfen von jedem geöffnet werden ({@code PostfachSichtbarkeit.darfLesen}),
     * die Zahl ist also für alle Benutzer gleich und gleich {@code gesamt} der ungefilterten Liste.
     */
    default long anzahlVerlaeufe(EmailBezug bezug, Long id) {
        return id == null ? 0L : zaehleVerlaeufe(EmailReiterAbfrage.ohneFilter(bezug, id));
    }
}
