package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KalenderService {

    private final KalenderEintragRepository kalenderEintragRepository;
    private final ProjektRepository projektRepository;
    private final KundeRepository kundeRepository;
    private final LieferantenRepository lieferantenRepository;
    private final AngebotRepository angebotRepository;
    private final MitarbeiterRepository mitarbeiterRepository;

    /**
     * Lädt alle Kalendereinträge für einen bestimmten Monat (PC-Ansicht, alle Einträge).
     */
    public List<KalenderEintrag> getEintraegeForMonat(int jahr, int monat) {
        YearMonth yearMonth = YearMonth.of(jahr, monat);
        LocalDate von = yearMonth.atDay(1);
        LocalDate bis = yearMonth.atEndOfMonth();
        return kalenderEintragRepository.findByDatumBetween(von, bis);
    }

    /**
     * Lädt alle Kalendereinträge für einen Mitarbeiter in einem Monat.
     * Zeigt Termine wo Mitarbeiter Ersteller oder Teilnehmer ist, plus Firmenkalender-Einträge.
     */
    public List<KalenderEintrag> getEintraegeForMitarbeiter(Long mitarbeiterId, int jahr, int monat) {
        YearMonth yearMonth = YearMonth.of(jahr, monat);
        LocalDate von = yearMonth.atDay(1);
        LocalDate bis = yearMonth.atEndOfMonth();
        return kalenderEintragRepository.findByMitarbeiterAndDatumBetween(mitarbeiterId, von, bis);
    }

    /**
     * Lädt alle Kalendereinträge für einen Mitarbeiter an einem bestimmten Tag.
     */
    public List<KalenderEintrag> getEintraegeForMitarbeiterTag(Long mitarbeiterId, LocalDate datum) {
        return kalenderEintragRepository.findByMitarbeiterAndDatum(mitarbeiterId, datum);
    }

    /**
     * Lädt alle Kalendereinträge für einen Datumsbereich.
     */
    public List<KalenderEintrag> getEintraegeForRange(LocalDate von, LocalDate bis) {
        return kalenderEintragRepository.findByDatumBetween(von, bis);
    }

    /**
     * Lädt einen einzelnen Eintrag.
     */
    public KalenderEintrag getEintrag(Long id) {
        return kalenderEintragRepository.findById(id).orElse(null);
    }

    /**
     * Lädt einen einzelnen Eintrag mit Teilnehmern.
     */
    public KalenderEintrag getEintragWithTeilnehmer(Long id) {
        return kalenderEintragRepository.findByIdWithTeilnehmer(id);
    }

    /**
     * Speichert einen Kalendereintrag (neu oder aktualisiert).
     * Unterstützt jetzt auch Ersteller und Teilnehmer.
     */
    @Transactional
    public KalenderEintrag saveEintrag(KalenderEintrag eintrag, Long projektId, Long kundeId, Long lieferantId,
            Long angebotId, Long erstellerId, List<Long> teilnehmerIds) {
        // Verknüpfungen setzen
        if (projektId != null) {
            eintrag.setProjekt(projektRepository.findById(projektId).orElse(null));
        } else {
            eintrag.setProjekt(null);
        }

        if (kundeId != null) {
            eintrag.setKunde(kundeRepository.findById(kundeId).orElse(null));
        } else {
            eintrag.setKunde(null);
        }

        if (lieferantId != null) {
            eintrag.setLieferant(lieferantenRepository.findById(lieferantId).orElse(null));
        } else {
            eintrag.setLieferant(null);
        }

        if (angebotId != null) {
            eintrag.setAngebot(angebotRepository.findById(angebotId).orElse(null));
        } else {
            eintrag.setAngebot(null);
        }

        // Ersteller setzen (nur bei neuem Eintrag, wenn noch kein Ersteller)
        if (erstellerId != null && eintrag.getErsteller() == null) {
            eintrag.setErsteller(mitarbeiterRepository.findById(erstellerId).orElse(null));
        }

        // Teilnehmer setzen
        if (teilnehmerIds != null) {
            Set<Mitarbeiter> teilnehmer = new HashSet<>();
            for (Long teilnehmerId : teilnehmerIds) {
                mitarbeiterRepository.findById(teilnehmerId).ifPresent(teilnehmer::add);
            }
            eintrag.setTeilnehmer(teilnehmer);
        }

        return kalenderEintragRepository.save(eintrag);
    }

    /**
     * Legacy-Methode für Kompatibilität ohne Ersteller/Teilnehmer.
     */
    @Transactional
    public KalenderEintrag saveEintrag(KalenderEintrag eintrag, Long projektId, Long kundeId, Long lieferantId,
            Long angebotId) {
        return saveEintrag(eintrag, projektId, kundeId, lieferantId, angebotId, null, null);
    }

    /**
     * Löscht einen Kalendereintrag.
     */
    @Transactional
    public void deleteEintrag(Long id) {
        kalenderEintragRepository.deleteById(id);
    }

    /**
     * Lädt Einträge für ein bestimmtes Projekt.
     */
    public List<KalenderEintrag> getEintraegeForProjekt(Long projektId) {
        return kalenderEintragRepository.findByProjektIdOrderByDatumDesc(projektId);
    }
}
