package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.BestellQuelle;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachlicher Workflow „Material aus Lager nehmen".
 *
 * <p>Der Chef druckt per {@code StuecklistePdfService} eine Lagerliste fuer
 * das Projekt aus; der Werkstatt- bzw. Lagermitarbeiter harkt auf Papier ab,
 * was vorhanden ist. Zurueck im Buero markiert der Chef die entsprechenden
 * {@link ArtikelInProjekt}-Zeilen als {@link BestellQuelle#AUS_LAGER} — mit
 * Preis entweder manuell oder aus dem gewichteten Durchschnittspreis des
 * Artikels ({@link ArtikelDurchschnittspreisService}).</p>
 *
 * <p>Damit gilt die Bedarfszeile als abgedeckt: sie laeuft weder in eine
 * Preisanfrage noch in eine Bestellung, der {@code preisProStueck} ist
 * bereits ins Projekt einkalkuliert.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LagerAbgleichService {

    private final ArtikelInProjektRepository artikelInProjektRepository;

    /**
     * Markiert die AiP-Zeile als aus dem Lager entnommen und schreibt den
     * Kalkulationspreis.
     *
     * @param aipId                    ID der Bedarfszeile
     * @param preisProStueckManuell    expliziter Preis; wenn {@code null},
     *                                 wird der Durchschnittspreis des
     *                                 Artikels verwendet
     * @param chefSnapshotName         GoBD-Snapshot (Name des erfassenden
     *                                 Chefs, nullable)
     * @throws IllegalArgumentException wenn die AiP nicht existiert
     * @throws IllegalStateException    wenn die AiP bereits bestellt wurde
     *                                  oder kein Preis ermittelbar ist
     */
    @Transactional
    public ArtikelInProjekt markiereAusLager(Long aipId,
                                             BigDecimal preisProStueckManuell,
                                             String chefSnapshotName) {
        ArtikelInProjekt aip = artikelInProjektRepository.findById(aipId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Bedarfsposition nicht gefunden: " + aipId));

        if (aip.getQuelle() == BestellQuelle.BESTELLT) {
            throw new IllegalStateException(
                    "Position wurde bereits bestellt und kann nicht mehr aus dem Lager entnommen werden.");
        }

        BigDecimal preis = preisProStueckManuell;
        if (preis == null || preis.signum() <= 0) {
            preis = ermittleDurchschnittspreis(aip.getArtikel());
        }
        if (preis == null || preis.signum() <= 0) {
            throw new IllegalStateException(
                    "Kein Preis vorhanden: bitte manuell setzen oder Durchschnittspreis im Artikelstamm hinterlegen.");
        }

        aip.setPreisProStueck(preis);
        aip.setQuelle(BestellQuelle.AUS_LAGER);
        aip.setLagerAbgleichAm(LocalDateTime.now());
        aip.setLagerAbgleichDurch(chefSnapshotName);
        log.info("AiP {} als AUS_LAGER markiert, Preis/Stk = {} €, durch = {}",
                aip.getId(), preis, chefSnapshotName);
        return artikelInProjektRepository.save(aip);
    }

    /**
     * Setzt den Lager-Haken zurueck — Position wird wieder zu {@code OFFEN},
     * der Preis bleibt stehen (Chef kann ihn per Bearbeiten-Dialog aendern).
     */
    @Transactional
    public ArtikelInProjekt setzeZurueckAufOffen(Long aipId) {
        ArtikelInProjekt aip = artikelInProjektRepository.findById(aipId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Bedarfsposition nicht gefunden: " + aipId));
        if (aip.getQuelle() != BestellQuelle.AUS_LAGER) {
            return aip;
        }
        aip.setQuelle(BestellQuelle.OFFEN);
        aip.setLagerAbgleichAm(null);
        aip.setLagerAbgleichDurch(null);
        return artikelInProjektRepository.save(aip);
    }

    private BigDecimal ermittleDurchschnittspreis(Artikel artikel) {
        if (artikel == null) {
            return null;
        }
        return artikel.getDurchschnittspreisNetto();
    }
}
