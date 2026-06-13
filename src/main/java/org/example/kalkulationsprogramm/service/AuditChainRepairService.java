package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.AuditChainState;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit;
import org.example.kalkulationsprogramm.repository.AuditChainStateRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Einmalige Reparatur der Audit-Hash-Kette.
 *
 * <p>Hintergrund: Bis zum Fix in {@link AusgangsGeschaeftsDokumentAuditService#appendToChain}
 * wurde der entry_hash über den In-Memory-Zustand VOR dem INSERT berechnet. MySQL
 * {@code DATETIME(6)} trunkiert aber die Nanosekunden von {@code LocalDateTime.now()} —
 * der gespeicherte Eintrag weicht damit von der gehashten Form ab und der
 * {@link AuditChainVerifier} meldet fälschlich EINTRAG_MANIPULIERT. Alle vor dem Fix
 * geschriebenen Hashes sind daher nicht reproduzierbar und müssen einmalig über die
 * tatsächlich gespeicherten Werte neu verkettet werden.</p>
 *
 * <p><strong>Bewusst KEIN HTTP-Endpoint:</strong> Wer die Kette neu aufbauen kann,
 * kann Manipulation verschleiern. Der Aufruf erfolgt nur über das Boot-Flag
 * {@code audit.chain.rebuild-on-start=true} (siehe {@code AuditChainRebuildRunner}),
 * das nach der Reparatur wieder entfernt wird.</p>
 *
 * <p>Die Inhalte der Einträge und ihre {@code chain_index}-Werte werden NICHT
 * verändert — nur {@code previous_hash}/{@code entry_hash} werden über die
 * DB-Repräsentation neu berechnet.</p>
 *
 * <p><strong>Geltungsbereich:</strong> Der Rebuild heilt ausschließlich den
 * Truncation-Bug bei intakter Index-Sequenz. Er prüft vorab, dass (a) kein Eintrag
 * ohne {@code chain_index} existiert (Backfill abgeschlossen) und (b) die Indizes
 * lückenlos bei 0 beginnen. Ist eine dieser Bedingungen verletzt — insbesondere bei
 * einer echten Index-Lücke durch gelöschte Zeilen (ein nachzuweisender
 * Manipulationsfall) — bricht der Rebuild mit {@link IllegalStateException} ab und
 * überschreibt nichts. Eine manipulierte Kette wird also nicht still „geheilt".</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditChainRepairService {

    private final AusgangsGeschaeftsDokumentAuditRepository auditRepository;
    private final AuditChainStateRepository chainStateRepository;
    private final AuditChainVerifier auditChainVerifier;

    /**
     * Verkettet alle Audit-Einträge neu (Hashes über den gespeicherten Zustand).
     *
     * @return Anzahl neu verketteter Einträge
     */
    @Transactional
    public int rebuildChain() {
        AuditChainState state = chainStateRepository.lockState();
        if (state == null) {
            throw new IllegalStateException(
                    "audit_chain_state fehlt — V255-Migration noch nicht gelaufen?");
        }

        // Vorbedingung 1: Der Backfill (chain_index-Vergabe für Alt-Einträge) MUSS
        // abgeschlossen sein. Liefe der Rebuild davor, würde er nur die bereits
        // indizierten Einträge verketten und einen Kettenkopf schreiben, den der
        // nachfolgende Backfill inkonsistent fortschreibt.
        long ohneIndex = auditRepository.findByChainIndexIsNullOrderByGeaendertAmAscIdAsc().size();
        if (ohneIndex > 0) {
            throw new IllegalStateException(
                    "Audit-Ketten-Reparatur abgebrochen: " + ohneIndex + " Einträge ohne "
                            + "chain_index. Der Backfill-Runner muss zuerst laufen.");
        }

        List<AusgangsGeschaeftsDokumentAudit> alle =
                auditRepository.findAllByOrderByChainIndexAsc();

        // Vorbedingung 2: chain_index MUSS lückenlos bei 0 beginnen. Eine echte Lücke
        // (gelöschte Zeile) ist ein nachzuweisender Manipulationsfall und wird hier
        // NICHT automatisch geheilt — der Rebuild repariert ausschließlich den
        // Truncation-Bug (falsch berechnete Hashes bei intakter Index-Sequenz).
        for (int i = 0; i < alle.size(); i++) {
            Long idx = alle.get(i).getChainIndex();
            if (idx == null || idx != i) {
                throw new IllegalStateException(
                        "Audit-Ketten-Reparatur abgebrochen: chain_index nicht lückenlos ab 0 "
                                + "(Position " + i + " hat chain_index=" + idx + "). Eine echte "
                                + "Index-Lücke deutet auf gelöschte Einträge hin und wird bewusst "
                                + "nicht überschrieben.");
            }
        }

        // Beweissicherung: Den alten (kaputten) Kettenzustand vollständig ins Log
        // schreiben, bevor er überschrieben wird — so bleibt nachvollziehbar, welche
        // entry_hash-Werte vor der Reparatur gespeichert waren.
        log.warn("Audit-Ketten-Reparatur startet: {} Einträge, alter Kettenkopf index={} hash={}",
                alle.size(), state.getLastChainIndex(), state.getLastEntryHash());
        for (AusgangsGeschaeftsDokumentAudit a : alle) {
            log.warn("  [pre-rebuild] index={} dok={} alt_entry_hash={} alt_previous_hash={}",
                    a.getChainIndex(), a.getDokumentNummer(), a.getEntryHash(), a.getPreviousHash());
        }

        String previousHash = null;
        long letzterIndex = -1;
        for (AusgangsGeschaeftsDokumentAudit a : alle) {
            // Entities stammen frisch aus der DB — ihre Felder sind exakt die Form,
            // die der Verifier später nachrechnet.
            a.setPreviousHash(previousHash);
            a.setEntryHash(a.computeEntryHash());
            previousHash = a.getEntryHash();
            letzterIndex = a.getChainIndex();
        }
        auditRepository.saveAllAndFlush(alle);

        state.setLastChainIndex(letzterIndex);
        state.setLastEntryHash(previousHash);
        state.setUpdatedAt(LocalDateTime.now());
        chainStateRepository.saveAndFlush(state);

        AuditChainVerifier.Bericht bericht = auditChainVerifier.verify();
        if (!bericht.isIntakt()) {
            throw new IllegalStateException(
                    "Audit-Ketten-Reparatur fehlgeschlagen — Kette nach Neuaufbau weiterhin "
                            + "gebrochen: " + bericht.getFehler());
        }

        log.warn("Audit-Ketten-Reparatur abgeschlossen: {} Einträge neu verkettet, "
                + "neuer Kettenkopf index={} hash={}", alle.size(), letzterIndex, previousHash);
        return alle.size();
    }
}
