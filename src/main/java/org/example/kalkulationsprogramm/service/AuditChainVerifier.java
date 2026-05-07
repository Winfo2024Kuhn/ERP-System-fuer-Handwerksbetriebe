package org.example.kalkulationsprogramm.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Prüft die Hash-Kette des Audit-Logs auf Manipulation.
 *
 * <p>Algorithmus:
 * <ol>
 *   <li>Alle Einträge in chain_index-Reihenfolge laden.</li>
 *   <li>Erwarten: chain_index lückenlos beginnend bei 0.</li>
 *   <li>Erwarten: previousHash[i] == entryHash[i-1] (oder NULL bei i=0).</li>
 *   <li>Erwarten: entryHash[i] == sha256(canonicalForm(eintrag) | previousHash).</li>
 * </ol>
 *
 * <p>Findet einer dieser Schritte einen Bruch, ist der Audit-Log <strong>nachweislich
 * manipuliert worden</strong>. Ein intakter Bericht ist der GoBD-Nachweis, dass die
 * Buchhaltung seit Inbetriebnahme unverändert ist.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditChainVerifier {

    private final AusgangsGeschaeftsDokumentAuditRepository auditRepository;

    @Transactional(readOnly = true)
    public Bericht verify() {
        List<AusgangsGeschaeftsDokumentAudit> alle = auditRepository.findAllByOrderByChainIndexAsc();
        Bericht bericht = new Bericht();
        bericht.gesamtAnzahl = alle.size();

        if (alle.isEmpty()) {
            bericht.intakt = true;
            return bericht;
        }

        String erwarteterPreviousHash = null;
        long erwarteterIndex = 0;

        for (AusgangsGeschaeftsDokumentAudit a : alle) {
            // 1. chain_index lückenlos?
            if (a.getChainIndex() == null || a.getChainIndex() != erwarteterIndex) {
                bericht.fehler.add(new Fehler(
                        a.getId(), a.getChainIndex(), a.getDokumentNummer(),
                        "INDEX_LUECKE",
                        "Erwartet chain_index=" + erwarteterIndex + ", gefunden " + a.getChainIndex()));
                bericht.intakt = false;
                return bericht;
            }

            // 2. previous_hash zeigt auf entryHash des vorherigen Eintrags?
            if (!java.util.Objects.equals(a.getPreviousHash(), erwarteterPreviousHash)) {
                bericht.fehler.add(new Fehler(
                        a.getId(), a.getChainIndex(), a.getDokumentNummer(),
                        "KETTE_GEBROCHEN",
                        "previous_hash passt nicht zum entry_hash des Vorgängers"));
                bericht.intakt = false;
                return bericht;
            }

            // 3. entry_hash neu berechnen und vergleichen.
            String berechnet = a.computeEntryHash();
            if (!java.util.Objects.equals(berechnet, a.getEntryHash())) {
                bericht.fehler.add(new Fehler(
                        a.getId(), a.getChainIndex(), a.getDokumentNummer(),
                        "EINTRAG_MANIPULIERT",
                        "Inhalt des Eintrags wurde nachträglich geändert (Hash stimmt nicht)"));
                bericht.intakt = false;
                return bericht;
            }

            erwarteterPreviousHash = a.getEntryHash();
            erwarteterIndex++;
        }

        bericht.intakt = true;
        bericht.letzterEntryHash = erwarteterPreviousHash;
        bericht.letzterChainIndex = erwarteterIndex - 1;
        return bericht;
    }

    @Getter
    public static class Bericht {
        private boolean intakt;
        private int gesamtAnzahl;
        private Long letzterChainIndex;
        private String letzterEntryHash;
        private final List<Fehler> fehler = new ArrayList<>();

        public boolean isIntakt() { return intakt; }
    }

    public record Fehler(
            Long auditId,
            Long chainIndex,
            String dokumentNummer,
            String typ,
            String beschreibung
    ) {}
}
