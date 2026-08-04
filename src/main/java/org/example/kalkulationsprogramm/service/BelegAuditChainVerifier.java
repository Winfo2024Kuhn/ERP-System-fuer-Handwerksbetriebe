package org.example.kalkulationsprogramm.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.BelegAudit;
import org.example.kalkulationsprogramm.repository.BelegAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rechnet die Hash-Kette des Kassenbuch-Protokolls nach.
 *
 * <p>Geprueft wird dreierlei:</p>
 * <ol>
 *   <li>Die Kettenpositionen sind lueckenlos und beginnen bei 0 --
 *       ein fehlender Eintrag faellt damit auf, auch wenn ihn jemand
 *       sauber aus der Tabelle geloescht hat.</li>
 *   <li>Jeder Eintrag verweist auf den Hash seines Vorgaengers.</li>
 *   <li>Jeder Hash laesst sich aus dem Eintrag neu berechnen.</li>
 * </ol>
 *
 * <p>Schlaegt einer der Punkte fehl, ist das Protokoll nachweislich
 * veraendert worden. Ein sauberer Bericht ist umgekehrt der Nachweis, dass
 * am Kassenbuch seit Inbetriebnahme nichts nachtraeglich gedreht wurde --
 * genau das, was ein Pruefer sehen will.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BelegAuditChainVerifier {

    private final BelegAuditRepository auditRepository;

    @Transactional(readOnly = true)
    public Bericht verify() {
        List<BelegAudit> alle = auditRepository.findAllByOrderByChainIndexAsc();
        Bericht bericht = new Bericht();
        bericht.gesamtAnzahl = alle.size();

        if (alle.isEmpty()) {
            bericht.intakt = true;
            return bericht;
        }

        String erwarteterPreviousHash = null;
        long erwarteterIndex = 0;

        for (BelegAudit a : alle) {
            if (a.getChainIndex() == null || a.getChainIndex() != erwarteterIndex) {
                return bericht.abbrechen(a, "LUECKE_IN_DER_KETTE",
                        "Erwartet Position " + erwarteterIndex + ", gefunden " + a.getChainIndex()
                                + " -- es fehlt mindestens ein Protokolleintrag");
            }

            if (!Objects.equals(a.getPreviousHash(), erwarteterPreviousHash)) {
                return bericht.abbrechen(a, "KETTE_GEBROCHEN",
                        "Der Verweis auf den vorherigen Eintrag stimmt nicht");
            }

            if (!Objects.equals(a.computeEntryHash(), a.getEntryHash())) {
                return bericht.abbrechen(a, "EINTRAG_VERAENDERT",
                        "Der Inhalt des Eintrags wurde nachtraeglich geaendert");
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

        public boolean isIntakt() {
            return intakt;
        }

        private Bericht abbrechen(BelegAudit a, String typ, String beschreibung) {
            fehler.add(new Fehler(a.getId(), a.getChainIndex(), a.getBelegId(),
                    a.getLaufendeNummer(), typ, beschreibung));
            intakt = false;
            log.warn("Kassenbuch-Protokoll gebrochen bei Kettenposition {}: {} -- {}",
                    a.getChainIndex(), typ, beschreibung);
            return this;
        }
    }

    public record Fehler(
            Long auditId,
            Long chainIndex,
            Long belegId,
            Long laufendeNummer,
            String typ,
            String beschreibung
    ) {}
}
