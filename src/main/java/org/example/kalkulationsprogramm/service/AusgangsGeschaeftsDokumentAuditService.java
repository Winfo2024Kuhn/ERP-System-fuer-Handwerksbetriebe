package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.AuditChainState;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAuditAktion;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.AuditChainStateRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GoBD-konforme Audit-Protokollierung für AusgangsGeschaeftsDokumente.
 *
 * <p>Zentrale Stelle für: Erstellen, Ändern, Buchen, Versenden, Stornieren, Löschen.
 * Bei Löschung und Änderung ist eine Begründung Pflicht.</p>
 *
 * <p>Jeder Eintrag wird in eine fortlaufende Hash-Kette eingehängt — siehe
 * {@link #appendToChain}. Der Kettenkopf liegt in {@code audit_chain_state}
 * (Singleton-Row, id=1) und wird beim Anhängen pessimistisch gelockt, damit
 * zwei parallele Aktionen niemals den gleichen previousHash bekommen.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AusgangsGeschaeftsDokumentAuditService {

    private final AusgangsGeschaeftsDokumentAuditRepository auditRepository;
    private final AuditChainStateRepository chainStateRepository;

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
        appendToChain(audit);
        log.info("Audit-Eintrag #{} (chain={}): {} für Dokument {} (Nr: {}) durch {} – {}",
                audit.getId(), audit.getChainIndex(), aktion, dokument.getId(), dokument.getDokumentNummer(),
                bearbeiter != null ? bearbeiter.getId() : "system", grund);
    }

    /**
     * Hängt einen Audit-Eintrag atomar an die globale Hash-Kette an.
     *
     * <ol>
     *   <li>Lockt die Singleton-Row in {@code audit_chain_state} (FOR UPDATE).</li>
     *   <li>Liest {@code lastChainIndex} und {@code lastEntryHash}.</li>
     *   <li>Setzt {@code chainIndex = last+1} und {@code previousHash = lastEntryHash}.</li>
     *   <li>Berechnet {@code entryHash} aus der kanonischen Form + previousHash.</li>
     *   <li>Persistiert den Eintrag und schreibt den neuen Kopf in den State.</li>
     * </ol>
     *
     * <p>{@link Propagation#REQUIRED} stellt sicher, dass das Anhängen Teil der
     * aufrufenden Transaktion ist — andernfalls wäre der Lock beim Commit nutzlos.</p>
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AusgangsGeschaeftsDokumentAudit appendToChain(AusgangsGeschaeftsDokumentAudit audit) {
        AuditChainState state = chainStateRepository.lockState();
        if (state == null) {
            // Erst-Initialisierung (sollte über V255-Migration bereits passiert sein).
            state = new AuditChainState();
            state.setId(1);
            state.setLastChainIndex(-1L);
            state.setLastEntryHash(null);
            state.setUpdatedAt(LocalDateTime.now());
            state = chainStateRepository.saveAndFlush(state);
        }

        long nextIndex = state.getLastChainIndex() + 1;
        audit.setChainIndex(nextIndex);
        audit.setPreviousHash(state.getLastEntryHash());
        audit.setEntryHash(audit.computeEntryHash());

        AusgangsGeschaeftsDokumentAudit saved = auditRepository.saveAndFlush(audit);

        state.setLastChainIndex(nextIndex);
        state.setLastEntryHash(saved.getEntryHash());
        state.setUpdatedAt(LocalDateTime.now());
        chainStateRepository.saveAndFlush(state);

        return saved;
    }

    private Map<String, Object> toMap(AusgangsGeschaeftsDokumentAudit a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", a.getId());
        map.put("chainIndex", a.getChainIndex());
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
        map.put("previousHash", a.getPreviousHash());
        map.put("entryHash", a.getEntryHash());
        map.put("geaendertVon", a.getGeaendertVon() != null ? a.getGeaendertVon().getDisplayName() : null);
        map.put("geaendertVonId", a.getGeaendertVon() != null ? a.getGeaendertVon().getId() : null);
        map.put("geaendertAm", a.getGeaendertAm() != null ? a.getGeaendertAm().toString() : null);
        map.put("aenderungsgrund", a.getAenderungsgrund());
        map.put("ipAdresse", a.getIpAdresse());
        return map;
    }
}
