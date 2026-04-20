package org.example.kalkulationsprogramm.service;

import java.nio.file.Path;

/**
 * Strategie-Interface fuer die PDF-Erzeugung einer Preisanfrage pro Lieferant.
 * Wird in Etappe 3 vom {@code BestellungPdfService} implementiert; der
 * {@link PreisanfrageService} nutzt hier nur die Abstraktion, um entkoppelt
 * testbar zu bleiben.
 */
@FunctionalInterface
public interface PreisanfragePdfGenerator {

    /**
     * Erzeugt ein PDF fuer den uebergebenen {@code PreisanfrageLieferant}.
     * Das PDF enthaelt Nummer und Token, damit der Lieferant den Code
     * bei der Antwort zurueckgeben kann.
     *
     * @param preisanfrageLieferantId FK auf {@code preisanfrage_lieferant.id}
     * @return Pfad zur erzeugten PDF-Datei
     */
    Path generatePdfForPreisanfrage(Long preisanfrageLieferantId);
}
