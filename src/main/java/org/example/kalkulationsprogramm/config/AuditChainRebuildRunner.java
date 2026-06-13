package org.example.kalkulationsprogramm.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.service.AuditChainRepairService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Einmaliger Neuaufbau der Audit-Hash-Kette beim Start — NUR wenn explizit
 * {@code audit.chain.rebuild-on-start=true} gesetzt ist.
 *
 * <p>Einsatz: Nach dem Hash-Roundtrip-Fix (entry_hash wurde vor dem Fix über den
 * In-Memory-Zustand statt über die gespeicherte DB-Form berechnet) muss die
 * Bestandskette einmalig neu verkettet werden. Vorgehen:</p>
 *
 * <ol>
 *   <li>Vorher den Audit-CSV-Export ziehen und archivieren (Beweissicherung).</li>
 *   <li>{@code audit.chain.rebuild-on-start=true} in die lokalen Properties eintragen.</li>
 *   <li>Backend einmal starten, Log-Meldung "Audit-Ketten-Reparatur abgeschlossen" abwarten.</li>
 *   <li>Flag wieder ENTFERNEN — sonst würde jeder Start eine manipulierte Kette
 *       stillschweigend "heilen" und die GoBD-Nachweisfunktion entwerten.</li>
 * </ol>
 *
 * <p>Reihenfolge: {@code @Order(20)} stellt sicher, dass dieser Runner NACH dem
 * {@code AuditChainBackfillRunner} ({@code @Order(10)}) läuft. Zusätzlich bricht
 * {@link AuditChainRepairService#rebuildChain()} ab, falls noch Einträge ohne
 * {@code chain_index} existieren — der Rebuild verkettet also niemals eine
 * unvollständig indizierte Tabelle.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "audit.chain.rebuild-on-start", havingValue = "true")
public class AuditChainRebuildRunner {

    private final AuditChainRepairService repairService;

    @Bean
    @Order(20) // Nach dem AuditChainBackfillRunner (@Order(10)).
    public ApplicationRunner auditChainRebuild() {
        return args -> {
            log.warn("audit.chain.rebuild-on-start=true gesetzt — Audit-Hash-Kette wird "
                    + "einmalig neu aufgebaut. Flag nach diesem Start wieder entfernen!");
            int anzahl = repairService.rebuildChain();
            log.warn("Audit-Hash-Kette neu aufgebaut ({} Einträge). "
                    + "Bitte audit.chain.rebuild-on-start jetzt wieder entfernen.", anzahl);
        };
    }
}
