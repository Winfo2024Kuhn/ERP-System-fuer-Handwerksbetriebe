package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.SchweisserZertifikat;
import org.example.kalkulationsprogramm.domain.Werkstoffzeugnis;
import org.example.kalkulationsprogramm.domain.Wps;
import org.example.kalkulationsprogramm.repository.BetriebsmittelRepository;
import org.example.kalkulationsprogramm.repository.SchweisserZertifikatRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffzeugnisRepository;
import org.example.kalkulationsprogramm.repository.WpsRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aggregiert EN 1090 EXC 2 Compliance-Status für ein Projekt.
 * Liefert Ampel-Auswertungen für das WPK-Dashboard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class En1090ReportService {

    private final SchweisserZertifikatRepository zertifikatRepository;
    private final WpsRepository wpsRepository;
    private final WerkstoffzeugnisRepository werkstoffzeugnisRepository;
    private final BetriebsmittelRepository betriebsmittelRepository;

    /**
     * Liefert den WPK-Status (Werkseigene Produktionskontrolle) für ein Projekt.
     * Jede Kategorie hat einen Status: OK | WARNUNG | FEHLER
     */
    public WpkStatus getWpkStatus(Long projektId) {
        WpkStatus status = new WpkStatus();
        LocalDate heute = LocalDate.now();
        LocalDate warnFrist = heute.plusDays(60); // 60 Tage Vorwarnung

        // --- Schweißer-Zertifikate ---
        List<SchweisserZertifikat> alleZertifikate = zertifikatRepository.findAll();
        LocalDate frist6Monate = heute.minusMonths(6);
        LocalDate frist5Monate = heute.minusMonths(5);

        long abgelaufen = alleZertifikate.stream()
                .filter(z -> {
                    boolean generalExpired = z.getAblaufdatum() != null && z.getAblaufdatum().isBefore(heute);
                    LocalDate refDate = z.getLetzteVerlaengerung() != null ? z.getLetzteVerlaengerung() : z.getAusstellungsdatum();
                    boolean verlaengerungUeberfaellig = refDate != null && refDate.isBefore(frist6Monate);
                    return generalExpired || verlaengerungUeberfaellig;
                })
                .count();

        long baldAblaufend = alleZertifikate.stream()
                .filter(z -> {
                    boolean generalExpired = z.getAblaufdatum() != null && z.getAblaufdatum().isBefore(heute);
                    LocalDate refDate = z.getLetzteVerlaengerung() != null ? z.getLetzteVerlaengerung() : z.getAusstellungsdatum();
                    boolean verlaengerungUeberfaellig = refDate != null && refDate.isBefore(frist6Monate);
                    if (generalExpired || verlaengerungUeberfaellig) return false;

                    boolean generalBald = z.getAblaufdatum() != null && z.getAblaufdatum().isBefore(warnFrist);
                    boolean verlaengerungBald = refDate != null && refDate.isBefore(frist5Monate);
                    return generalBald || verlaengerungBald;
                })
                .count();

        if (abgelaufen > 0) {
            status.schweisser = "FEHLER";
            status.schweisserHinweis = abgelaufen + " Zertifikat(e) abgelaufen bzw. Verlängerung überfällig";
        } else if (baldAblaufend > 0) {
            status.schweisser = "WARNUNG";
            status.schweisserHinweis = baldAblaufend + " Zertifikat(e) laufen in <60 Tagen ab bzw. benötigen Verlängerung";
        } else if (alleZertifikate.isEmpty()) {
            status.schweisser = "FEHLER";
            status.schweisserHinweis = "Keine Schweißer-Zertifikate hinterlegt";
        } else {
            status.schweisser = "OK";
            status.schweisserHinweis = alleZertifikate.size() + " gültige Zertifikate";
        }

        // --- WPS vorhanden ---
        List<Wps> wpsList = wpsRepository.findByProjektId(projektId);
        if (wpsList.isEmpty()) {
            status.wps = "WARNUNG";
            status.wpsHinweis = "Keine WPS für dieses Projekt hinterlegt";
        } else {
            long abgelaufeneWps = wpsList.stream()
                    .filter(w -> w.getGueltigBis() != null && w.getGueltigBis().isBefore(heute))
                    .count();
            status.wps = abgelaufeneWps > 0 ? "FEHLER" : "OK";
            status.wpsHinweis = wpsList.size() + " WPS" + (abgelaufeneWps > 0 ? " (" + abgelaufeneWps + " abgelaufen)" : "");
        }

        // --- Werkstoffzeugnisse ---
        List<Werkstoffzeugnis> wzList = werkstoffzeugnisRepository.findByProjektId(projektId);
        status.werkstoffzeugnisse = wzList.isEmpty() ? "WARNUNG" : "OK";
        status.werkstoffzeugnisseHinweis = wzList.isEmpty()
                ? "Keine Werkstoffzeugnisse für dieses Projekt zugeordnet"
                : wzList.size() + " Zeugnis(se) vorhanden";

        // --- E-Check (alle Betriebsmittel) ---
        long faelligBm = betriebsmittelRepository.findFaelligBis(heute).size();
        long baldFaelligBm = betriebsmittelRepository.findFaelligBis(warnFrist).size() - faelligBm;
        if (faelligBm > 0) {
            status.echeck = "FEHLER";
            status.echeckHinweis = faelligBm + " Betriebsmittel überfällig";
        } else if (baldFaelligBm > 0) {
            status.echeck = "WARNUNG";
            status.echeckHinweis = baldFaelligBm + " Betriebsmittel in <60 Tagen fällig";
        } else {
            status.echeck = "OK";
            status.echeckHinweis = "Alle E-Checks aktuell";
        }

        return status;
    }

    /** Mutable WPK-Status-Objekt mit Ampel-Feldern je Kategorie. */
    public static class WpkStatus {
        public String schweisser = "OK";
        public String schweisserHinweis = "";
        public String wps = "OK";
        public String wpsHinweis = "";
        public String werkstoffzeugnisse = "OK";
        public String werkstoffzeugnisseHinweis = "";
        public String echeck = "OK";
        public String echeckHinweis = "";
    }
}
