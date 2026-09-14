package org.example.kalkulationsprogramm.cli;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.example.kalkulationsprogramm.service.LocalRagService;

/**
 * CLI Runner to manually trigger RAG indexing without starting the full application.
 *
 * Usage:
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--rag-index"
 * Or with custom settings:
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--rag-index --ai.rag.enabled=true"
 */
@Slf4j
@Component
public class RagIndexingRunner implements ApplicationRunner {

    private final LocalRagService localRagService;

    @Value("${ai.rag.enabled:false}")
    private boolean ragEnabled;

    public RagIndexingRunner(LocalRagService localRagService) {
        this.localRagService = localRagService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (args.containsOption("rag-index")) {
            log.info("╔════════════════════════════════════════════════════════════╗");
            log.info("║  RAG INDEXIERUNG GESTARTET (manuelle CLI-Ausführung)       ║");
            log.info("╚════════════════════════════════════════════════════════════╝");

            if (!ragEnabled) {
                log.warn("⚠️  RAG ist deaktiviert (ai.rag.enabled=false in application.properties)");
                log.info("   Aktiviere es mit: --ai.rag.enabled=true");
                return;
            }

            // Wait for LocalRagService to complete indexing
            int maxWaitSec = 300; // 5 minutes
            long startTime = System.currentTimeMillis();

            while (!localRagService.isReady() && System.currentTimeMillis() - startTime < maxWaitSec * 1000) {
                Thread.sleep(1000);
                log.info("⏳ Indexierung in Fortschritt... ({}s)",
                    (System.currentTimeMillis() - startTime) / 1000);
            }

            if (localRagService.isReady()) {
                log.info("✅ RAG-Indexierung erfolgreich abgeschlossen!");
                log.info("   Status: {} Chunks indexiert und bereit",
                    localRagService.isAvailable() ? "✓" : "✗");
                System.exit(0);
            } else {
                log.error("❌ RAG-Indexierung Timeout nach {}s", maxWaitSec);
                System.exit(1);
            }
        }
    }
}
