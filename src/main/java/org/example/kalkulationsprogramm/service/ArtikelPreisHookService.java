package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelPreisHistorie;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.repository.ArtikelPreisHistorieRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Zentraler Einstiegspunkt fuer alle Preis-Aenderungen an
 * {@link org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise}.
 *
 * <p>Wird an jeder {@code setPreis(...)}-Stelle aufgerufen und
 * <ul>
 *   <li>schreibt immer einen {@link ArtikelPreisHistorie}-Eintrag (inkl. Einheit),</li>
 *   <li>triggert bei {@code quelle == RECHNUNG} und {@code einheit == KILOGRAMM}
 *       zusaetzlich das gewichtete Update in
 *       {@link ArtikelDurchschnittspreisService}. Fuer andere Einheiten
 *       (LAUFENDE_METER, QUADRATMETER, STUECK) wird der Durchschnittspreis
 *       nicht aktualisiert, da er sonst semantisch vergiftet waere.</li>
 * </ul>
 *
 * <p>Best-effort: Fehler werden geloggt, nicht geworfen — ein einzelner
 * Historie-Fehler darf keinen ganzen Rechnungs-Import kippen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtikelPreisHookService {

    private final ArtikelPreisHistorieRepository historieRepository;
    private final ArtikelDurchschnittspreisService durchschnittspreisService;

    @Transactional(propagation = Propagation.REQUIRED)
    public void registriere(
            Artikel artikel,
            Lieferanten lieferant,
            BigDecimal preis,
            BigDecimal menge,
            Verrechnungseinheit einheit,
            PreisQuelle quelle,
            String externeNummer,
            String belegReferenz,
            String bemerkung) {
        if (artikel == null || preis == null || quelle == null) {
            log.warn("Preis-Hook uebersprungen: artikel={}, preis={}, quelle={}",
                    artikel != null ? artikel.getId() : null, preis, quelle);
            return;
        }
        if (preis.signum() <= 0) {
            log.warn("Preis-Hook uebersprungen fuer Artikel {}: preis={} (<= 0)",
                    artikel.getId(), preis);
            return;
        }
        // Einheit ist Pflicht in der Historie. Fallback auf KILOGRAMM,
        // weil ~99% der Stahl-Artikel kg sind und die meisten Alt-Callsites
        // das implizit annehmen.
        Verrechnungseinheit effektiveEinheit = einheit != null ? einheit : Verrechnungseinheit.KILOGRAMM;

        try {
            ArtikelPreisHistorie eintrag = new ArtikelPreisHistorie();
            eintrag.setArtikel(artikel);
            eintrag.setLieferant(lieferant);
            eintrag.setPreis(preis);
            eintrag.setMenge(menge);
            eintrag.setEinheit(effektiveEinheit);
            eintrag.setQuelle(quelle);
            eintrag.setExterneNummer(externeNummer);
            eintrag.setBelegReferenz(belegReferenz);
            eintrag.setBemerkung(bemerkung);
            eintrag.setErfasstAm(LocalDateTime.now());
            historieRepository.save(eintrag);
        } catch (Exception e) {
            log.warn("Preis-Historie-Eintrag fehlgeschlagen fuer Artikel {}: {}",
                    artikel.getId(), e.getMessage());
        }

        boolean durchschnittRelevant = quelle == PreisQuelle.RECHNUNG
                && effektiveEinheit == Verrechnungseinheit.KILOGRAMM
                && menge != null
                && menge.signum() > 0;
        if (durchschnittRelevant) {
            try {
                durchschnittspreisService.aktualisiere(artikel, menge, preis);
            } catch (Exception e) {
                log.warn("Durchschnittspreis-Update fehlgeschlagen fuer Artikel {}: {}",
                        artikel.getId(), e.getMessage());
            }
        }
    }

    /** Convenience fuer Nicht-Rechnungs-Quellen ohne Menge/Beleg. */
    public void registriere(Artikel artikel, Lieferanten lieferant, BigDecimal preis,
                            Verrechnungseinheit einheit, PreisQuelle quelle, String externeNummer) {
        registriere(artikel, lieferant, preis, null, einheit, quelle, externeNummer, null, null);
    }
}
