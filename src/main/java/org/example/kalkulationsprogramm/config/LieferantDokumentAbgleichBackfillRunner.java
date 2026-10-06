package org.example.kalkulationsprogramm.config;

import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * Verknüpft nach einer Änderung am Abgleich einmal alle Lieferanten-Dokumente
 * neu – sonst profitierten nur neu eingehende Belege von den besseren Regeln,
 * und alte Lieferscheine stünden weiter unter „Rechnung fehlt“.
 *
 * <p>Läuft je {@link #ABGLEICH_VERSION} genau einmal: Die erledigte Version
 * steht in den System-Einstellungen. Nur ergänzend – bestehende und von Hand
 * gesetzte Verknüpfungen bleiben, von Hand gelöste Paare bleiben gelöst.
 *
 * <p>Startet nach dem Hochfahren im Hintergrund und blockiert den Start nicht.
 * Fehler werden nur protokolliert; beim nächsten Start wird es erneut versucht.
 * Von Hand bleibt {@code POST /api/lieferant-dokumente/relink-all}.
 *
 * <p>Abschaltbar mit {@code lieferant.abgleich.backfill-on-start=false} (Tests).
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "lieferant.abgleich.backfill-on-start", havingValue = "true", matchIfMissing = true)
public class LieferantDokumentAbgleichBackfillRunner {

    /** Erhöhen, wenn der Abgleich so geändert wurde, dass alte Belege neu verknüpft werden sollen. */
    public static final int ABGLEICH_VERSION = 2;
    static final String EINSTELLUNG = "lieferant.abgleich.version";

    private final GeminiDokumentAnalyseService analyseService;
    private final SystemSettingsService einstellungen;
    private final TaskExecutor taskExecutor;

    public LieferantDokumentAbgleichBackfillRunner(GeminiDokumentAnalyseService analyseService,
            SystemSettingsService einstellungen, @Qualifier("taskExecutor") TaskExecutor taskExecutor) {
        this.analyseService = analyseService;
        this.einstellungen = einstellungen;
        this.taskExecutor = taskExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(200) // nach den übrigen Start-Aufgaben
    public void nachDemStart() {
        taskExecutor.execute(this::verknuepfeFallsNoetig);
    }

    /**
     * @return {@code true}, wenn neu verknüpft wurde
     */
    public boolean verknuepfeFallsNoetig() {
        try {
            int erledigt = erledigteVersion();
            if (erledigt >= ABGLEICH_VERSION) {
                log.debug("[AbgleichBackfill] Version {} schon erledigt – nichts zu tun.", erledigt);
                return false;
            }
            log.info("[AbgleichBackfill] Verknüpfe Lieferanten-Dokumente neu (Abgleich-Version {} -> {})...",
                    erledigt, ABGLEICH_VERSION);
            int neu = analyseService.relinkAlleDokumente();
            einstellungen.save(EINSTELLUNG, String.valueOf(ABGLEICH_VERSION),
                    "Stand des automatischen Neu-Verknüpfens der Lieferanten-Dokumente");
            log.info("[AbgleichBackfill] Fertig: {} neue Verknüpfungen.", neu);
            return true;
        } catch (Exception e) {
            // Darf den Betrieb nicht stören – beim nächsten Start neuer Versuch.
            log.error("[AbgleichBackfill] Neu-Verknüpfen fehlgeschlagen: {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    private int erledigteVersion() {
        String wert = einstellungen.get(EINSTELLUNG, "0");
        try {
            return Integer.parseInt(wert == null ? "0" : wert.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
