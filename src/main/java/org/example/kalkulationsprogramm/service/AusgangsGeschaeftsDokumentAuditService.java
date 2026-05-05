package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAuditAktion;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GoBD-konforme Audit-Protokollierung für AusgangsGeschaeftsDokumente.
 *
 * <p>Zentrale Stelle für: Erstellen, Ändern, Buchen, Versenden, Stornieren, Löschen.
 * Bei Löschung und Änderung ist eine Begründung Pflicht.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AusgangsGeschaeftsDokumentAuditService {

    private final AusgangsGeschaeftsDokumentAuditRepository auditRepository;

    @Transactional
    public void protokolliereErstellung(AusgangsGeschaeftsDokument dokument, FrontendUserProfile bearbeiter, String ipAdresse) {
        save(dokument, AusgangsGeschaeftsDokumentAuditAktion.ERSTELLT, bearbeiter, "Initiale Erstellung", ipAdresse);
    }

    @Transactional
    public void protokolliereAenderung(AusgangsGeschaeftsDokument dokument, FrontendUserProfile bearbeiter,
                                       String aenderungsgrund, String ipAdresse) {
        if (aenderungsgrund == null || aenderungsgrund.isBlank()) {
            throw new IllegalArgumentException("Änderungsgrund ist Pflicht (GoBD)");
        }
        save(dokument, AusgangsGeschaeftsDokumentAuditAktion.GEAENDERT, bearbeiter, aenderungsgrund, ipAdresse);
    }

    @Transactional
    public void protokolliereBuchung(AusgangsGeschaeftsDokument dokument, FrontendUserProfile bearbeiter, String ipAdresse) {
        save(dokument, AusgangsGeschaeftsDokumentAuditAktion.GEBUCHT, bearbeiter, "Festschreibung/Buchung", ipAdresse);
    }

    @Transactional
    public void protokolliereVersand(AusgangsGeschaeftsDokument dokument, FrontendUserProfile bearbeiter, String ipAdresse) {
        save(dokument, AusgangsGeschaeftsDokumentAuditAktion.VERSENDET, bearbeiter, "Versand an Kunden", ipAdresse);
    }

    @Transactional
    public void protokolliereStornierung(AusgangsGeschaeftsDokument dokument, FrontendUserProfile bearbeiter,
                                         String grund, String ipAdresse) {
        if (grund == null || grund.isBlank()) {
            throw new IllegalArgumentException("Stornierungsgrund ist Pflicht");
        }
        save(dokument, AusgangsGeschaeftsDokumentAuditAktion.STORNIERT, bearbeiter, grund, ipAdresse);
    }

    /**
     * Protokolliert die Löschung eines Entwurfs. MUSS vor dem Hard-Delete aufgerufen werden,
     * damit der Snapshot noch vollständig ist. Begründung ist Pflicht (GoBD).
     */
    @Transactional
    public void protokolliereLoeschung(AusgangsGeschaeftsDokument dokument, FrontendUserProfile bearbeiter,
                                       String begruendung, String ipAdresse) {
        if (begruendung == null || begruendung.isBlank()) {
            throw new IllegalArgumentException("Begründung für Löschung ist Pflicht (GoBD)");
        }
        save(dokument, AusgangsGeschaeftsDokumentAuditAktion.GELOESCHT, bearbeiter, begruendung, ipAdresse);
    }

    @Transactional
    public void protokolliereDigitaleAnnahme(AusgangsGeschaeftsDokument dokument, String ipAdresse) {
        save(dokument, AusgangsGeschaeftsDokumentAuditAktion.DIGITAL_ANGENOMMEN, null,
                "Digitale Annahme durch Kunden", ipAdresse);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistorie(Long dokumentId) {
        return auditRepository.findByDokumentIdOrderByGeaendertAmDesc(dokumentId)
                .stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistorieByNummer(String dokumentNummer) {
        return auditRepository.findByDokumentNummerOrderByGeaendertAmDesc(dokumentNummer)
                .stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    private void save(AusgangsGeschaeftsDokument dokument, AusgangsGeschaeftsDokumentAuditAktion aktion,
                      FrontendUserProfile bearbeiter, String grund, String ipAdresse) {
        AusgangsGeschaeftsDokumentAudit audit = AusgangsGeschaeftsDokumentAudit.fromDokument(
                dokument, aktion, bearbeiter, grund, ipAdresse);
        auditRepository.save(audit);
        log.info("Audit-Eintrag: {} für Dokument {} (Nr: {}) durch {} – {}",
                aktion, dokument.getId(), dokument.getDokumentNummer(),
                bearbeiter != null ? bearbeiter.getId() : "system", grund);
    }

    private Map<String, Object> toMap(AusgangsGeschaeftsDokumentAudit a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", a.getId());
        map.put("aktion", a.getAktion().name());
        map.put("dokumentId", a.getDokumentId());
        map.put("dokumentNummer", a.getDokumentNummer());
        map.put("typ", a.getTyp().name());
        map.put("betragNetto", a.getBetragNetto());
        map.put("betragBrutto", a.getBetragBrutto());
        map.put("gebucht", a.isGebucht());
        map.put("storniert", a.isStorniert());
        map.put("digitalAngenommen", a.isDigitalAngenommen());
        map.put("inhaltHash", a.getInhaltHash());
        map.put("geaendertVon", a.getGeaendertVon() != null ? a.getGeaendertVon().getDisplayName() : null);
        map.put("geaendertVonId", a.getGeaendertVon() != null ? a.getGeaendertVon().getId() : null);
        map.put("geaendertAm", a.getGeaendertAm() != null ? a.getGeaendertAm().toString() : null);
        map.put("aenderungsgrund", a.getAenderungsgrund());
        map.put("ipAdresse", a.getIpAdresse());
        return map;
    }
}
