package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.AenderungsgrundKatalogRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * Service für GoBD-konforme Audit-Protokollierung von Zeitbuchungen.
 * 
 * Zentrale Stelle für:
 * - Protokollierung bei Erstellung (Mobile/Desktop)
 * - Protokollierung bei Änderung (mit Pflicht-Änderungsgrund)
 * - Protokollierung bei Stornierung
 * - Abruf der Änderungshistorie
 */
@Service
@RequiredArgsConstructor
public class ZeitbuchungAuditService {

    private final ZeitbuchungAuditRepository auditRepository;
    private final AenderungsgrundKatalogRepository aenderungsgrundRepository;

    /**
     * Protokolliert die initiale Erfassung einer Zeitbuchung.
     * Wird aufgerufen bei: Start am Handy, manuelle Anlage im Büro
     */
    @Transactional
    public void protokolliereErstellung(Zeitbuchung buchung, Mitarbeiter bearbeiter, ErfassungsQuelle quelle) {
        ZeitbuchungAudit audit = ZeitbuchungAudit.fromZeitbuchung(
                buchung,
                AuditAktion.ERSTELLT,
                bearbeiter,
                quelle,
                "Initiale Erfassung");
        auditRepository.save(audit);
    }

    /**
     * Protokolliert eine Änderung an einer Zeitbuchung.
     * WICHTIG: Muss VOR dem Speichern der Änderung aufgerufen werden,
     * um den Vorher-Zustand zu erfassen!
     * 
     * @param buchung         Die Buchung im AKTUELLEN Zustand (vor der Änderung)
     * @param bearbeiter      Wer führt die Änderung durch
     * @param quelle          Über welchen Kanal (MOBILE_APP, DESKTOP, etc.)
     * @param aenderungsgrund Pflicht! Begründung für die Änderung
     */
    @Transactional
    public void protokolliereAenderung(Zeitbuchung buchung, Mitarbeiter bearbeiter,
            ErfassungsQuelle quelle, String aenderungsgrund) {
        if (aenderungsgrund == null || aenderungsgrund.isBlank()) {
            throw new IllegalArgumentException("Änderungsgrund ist ein Pflichtfeld für GoBD-Konformität");
        }

        ZeitbuchungAudit audit = ZeitbuchungAudit.fromZeitbuchung(
                buchung,
                AuditAktion.GEAENDERT,
                bearbeiter,
                quelle,
                aenderungsgrund);
        auditRepository.save(audit);
    }

    /**
     * Protokolliert die Stornierung/Löschung einer Zeitbuchung.
     * Bei GoBD-konformer Buchhaltung: Nicht löschen, sondern stornieren!
     */
    @Transactional
    public void protokolliereStorno(Zeitbuchung buchung, Mitarbeiter bearbeiter,
            ErfassungsQuelle quelle, String grund) {
        if (grund == null || grund.isBlank()) {
            throw new IllegalArgumentException("Stornierungsgrund ist ein Pflichtfeld");
        }

        ZeitbuchungAudit audit = ZeitbuchungAudit.fromZeitbuchung(
                buchung,
                AuditAktion.STORNIERT,
                bearbeiter,
                quelle,
                grund);
        auditRepository.save(audit);
    }

    /**
     * Gibt die vollständige Änderungshistorie einer Zeitbuchung zurück.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistorie(Long zeitbuchungId) {
        return auditRepository.findByZeitbuchungIdOrderByVersionDesc(zeitbuchungId)
                .stream()
                .map(this::auditToMap)
                .collect(Collectors.toList());
    }

    /**
     * Gibt alle verfügbaren Änderungsgründe für das Frontend-Dropdown zurück.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAenderungsgruende() {
        return aenderungsgrundRepository.findAllByOrderByBezeichnungAsc()
                .stream()
                .map(g -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("code", g.getCode());
                    map.put("bezeichnung", g.getBezeichnung());
                    map.put("erfordertFreitext", g.getErfordertFreitext());
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * Prüft ob für eine Zeitbuchung eine Änderungshistorie existiert.
     */
    @Transactional(readOnly = true)
    public boolean hatHistorie(Long zeitbuchungId) {
        return auditRepository.existsByZeitbuchungId(zeitbuchungId);
    }

    private Map<String, Object> auditToMap(ZeitbuchungAudit audit) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", audit.getId());
        map.put("version", audit.getVersion());
        map.put("aktion", audit.getAktion().name());
        map.put("startZeit", audit.getStartZeit() != null ? audit.getStartZeit().toString() : null);
        map.put("endeZeit", audit.getEndeZeit() != null ? audit.getEndeZeit().toString() : null);
        map.put("anzahlInStunden", audit.getAnzahlInStunden());
        map.put("notiz", audit.getNotiz());
        map.put("geaendertVon", audit.getGeaendertVon() != null
                ? audit.getGeaendertVon().getVorname() + " " + audit.getGeaendertVon().getNachname()
                : null);
        map.put("geaendertAm", audit.getGeaendertAm() != null ? audit.getGeaendertAm().toString() : null);
        map.put("geaendertVia", audit.getGeaendertVia() != null ? audit.getGeaendertVia().name() : null);
        map.put("aenderungsgrund", audit.getAenderungsgrund());
        return map;
    }
}
