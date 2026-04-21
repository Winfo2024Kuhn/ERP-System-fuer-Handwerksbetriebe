package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Wps;
import org.example.kalkulationsprogramm.domain.WpsProjektAutoSource;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.WpsProjektAutoSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ordnet beim Speichern eines Ausgangsdokuments (Angebot/Auftrag) die
 * an den enthaltenen Leistungen hinterlegten Schweißanweisungen automatisch
 * dem Projekt zu – EN 1090 EXC 2 im normalen Kalkulator-Flow.
 *
 * Entfernen tut der Service bewusst nichts: Wenn der Nutzer eine Leistung
 * wieder aus dem Dokument nimmt, bleiben bereits zugeordnete WPS am Projekt
 * bestehen. Manuelles Aufräumen im WPK-Dashboard ist gewollt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeistungWpsAutoAssignService {

    private final LeistungRepository leistungRepository;
    private final ProjektRepository projektRepository;
    private final WpsProjektAutoSourceRepository autoSourceRepository;

    /**
     * Fügt alle WPS, die an den übergebenen Leistungen hängen, dem Projekt hinzu.
     * Bereits zugeordnete WPS werden nicht doppelt eingetragen.
     *
     * @return Anzahl der NEU zugeordneten WPS (für Toast-Anzeige im Frontend).
     */
    @Transactional
    public int autoAssignWpsFromLeistungen(Long projektId, Set<Long> leistungIds) {
        if (projektId == null || leistungIds == null || leistungIds.isEmpty()) {
            return 0;
        }
        Projekt projekt = projektRepository.findById(projektId).orElse(null);
        if (projekt == null) {
            log.warn("Auto-Assign WPS: Projekt {} nicht gefunden – übersprungen", projektId);
            return 0;
        }

        List<Leistung> leistungen = leistungRepository.findAllById(leistungIds);
        int neuZugewiesen = 0;
        List<WpsProjektAutoSource> neueAuditEintraege = new ArrayList<>();

        for (Leistung l : leistungen) {
            for (Wps w : l.getVerknuepfteWps()) {
                boolean warSchonDa = w.getProjekte().stream()
                        .anyMatch(p -> p.getId().equals(projektId));
                if (!warSchonDa) {
                    w.getProjekte().add(projekt);
                    neuZugewiesen++;
                }
                if (!autoSourceRepository.existsByWpsIdAndProjektIdAndLeistungId(
                        w.getId(), projektId, l.getId())) {
                    WpsProjektAutoSource audit = new WpsProjektAutoSource();
                    audit.setWps(w);
                    audit.setProjekt(projekt);
                    audit.setLeistung(l);
                    neueAuditEintraege.add(audit);
                }
            }
        }
        if (!neueAuditEintraege.isEmpty()) {
            autoSourceRepository.saveAll(neueAuditEintraege);
        }
        if (neuZugewiesen > 0) {
            log.info("Auto-Assign WPS: {} neue Schweißanweisung(en) an Projekt {} gehängt",
                    neuZugewiesen, projektId);
        }
        return neuZugewiesen;
    }
}
