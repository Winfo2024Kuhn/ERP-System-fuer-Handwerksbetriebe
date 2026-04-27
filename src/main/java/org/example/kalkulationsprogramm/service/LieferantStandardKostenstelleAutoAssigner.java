package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Erstellt automatisch eine 100%-Zuordnung auf die Standard-Kostenstelle eines Lieferanten,
 * sobald ein neues RECHNUNGS-Dokument hereinkommt. Dadurch verschwindet das Dokument
 * aus der "Einkauf > Bestellungen > Abgeschlossen"-Liste der noch zuzuordnenden Belege.
 *
 * Wird aufgerufen aus allen Pfaden, die neue Lieferanten-Dokumente erzeugen
 * (manueller Upload, manueller Importer, automatische E-Mail-Anhang-Analyse).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LieferantStandardKostenstelleAutoAssigner {

    private final LieferantDokumentProjektAnteilRepository anteilRepository;

    /**
     * Legt — falls passend — einen 100%-Anteil auf die Standard-Kostenstelle des Lieferanten an.
     * Idempotent: existieren bereits Anteile für das Dokument, passiert nichts.
     */
    @Transactional
    public void applyIfApplicable(LieferantDokument dokument) {
        if (dokument == null || dokument.getId() == null) {
            return;
        }
        if (dokument.getTyp() != LieferantDokumentTyp.RECHNUNG) {
            return;
        }
        Lieferanten lieferant = dokument.getLieferant();
        if (lieferant == null) {
            return;
        }
        Kostenstelle standard = lieferant.getStandardKostenstelle();
        if (standard == null) {
            return;
        }
        if (!anteilRepository.findByDokumentId(dokument.getId()).isEmpty()) {
            return;
        }

        LieferantGeschaeftsdokument gd = dokument.getGeschaeftsdaten();
        BigDecimal betragBrutto = gd != null ? gd.getBetragBrutto() : null;

        LieferantDokumentProjektAnteil anteil = new LieferantDokumentProjektAnteil();
        anteil.setDokument(dokument);
        anteil.setKostenstelle(standard);
        anteil.setProzent(100);
        if (betragBrutto != null) {
            anteil.berechneAnteil(betragBrutto);
        }
        anteilRepository.save(anteil);

        log.info("Auto-Zuweisung: Dokument {} -> Standard-Kostenstelle '{}' (Lieferant {})",
                dokument.getId(), standard.getBezeichnung(), lieferant.getId());
    }
}
