package org.example.kalkulationsprogramm.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.AuditChainState;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit;
import org.example.kalkulationsprogramm.repository.AuditChainStateRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Beim ersten Start nach der V255-Migration verkettet diese Komponente alle
 * existierenden Audit-Einträge, die noch keinen entry_hash haben.
 *
 * <p>Vorgehen: Einträge in der Reihenfolge {@code geaendert_am ASC, id ASC}
 * laden, fortlaufenden chain_index zuweisen, previous_hash und entry_hash
 * berechnen, am Ende {@code audit_chain_state} aktualisieren.</p>
 *
 * <p>Idempotent: Ist die Kette bereits intakt (keine Einträge mit chain_index=NULL),
 * läuft die Komponente sofort durch ohne etwas zu tun.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AuditChainBackfillRunner {

    private final AusgangsGeschaeftsDokumentAuditRepository auditRepository;
    private final AuditChainStateRepository chainStateRepository;
    private final TransactionTemplate transactionTemplate;

    @Bean
    public ApplicationRunner auditChainBackfill() {
        return args -> transactionTemplate.executeWithoutResult(status -> backfill());
    }

    @Transactional
    public void backfill() {
        List<AusgangsGeschaeftsDokumentAudit> nichtVerkettet =
                auditRepository.findByChainIndexIsNullOrderByGeaendertAmAscIdAsc();

        if (nichtVerkettet.isEmpty()) {
            log.debug("Audit-Hash-Kette: keine offenen Einträge zum Backfill.");
            return;
        }

        // Lockt die Singleton-Row und holt den letzten Stand. Wenn die Kette frisch ist,
        // ist lastChainIndex = -1 und lastEntryHash = null — passt zur ersten Iteration.
        AuditChainState state = chainStateRepository.lockState();
        if (state == null) {
            state = new AuditChainState();
            state.setId(1);
            state.setLastChainIndex(-1L);
            state.setLastEntryHash(null);
            state.setUpdatedAt(LocalDateTime.now());
            state = chainStateRepository.saveAndFlush(state);
        }

        long index = state.getLastChainIndex();
        String previousHash = state.getLastEntryHash();

        log.info("Audit-Hash-Kette: starte Backfill für {} Einträge ab chain_index={}",
                nichtVerkettet.size(), index + 1);

        for (AusgangsGeschaeftsDokumentAudit a : nichtVerkettet) {
            index++;
            a.setChainIndex(index);
            a.setPreviousHash(previousHash);
            a.setEntryHash(a.computeEntryHash());
            previousHash = a.getEntryHash();
        }

        auditRepository.saveAll(nichtVerkettet);

        state.setLastChainIndex(index);
        state.setLastEntryHash(previousHash);
        state.setUpdatedAt(LocalDateTime.now());
        chainStateRepository.saveAndFlush(state);

        log.info("Audit-Hash-Kette: Backfill abgeschlossen, neuer Kopf chain_index={} entry_hash={}",
                index, previousHash);
    }
}
