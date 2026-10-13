package org.example.kalkulationsprogramm.config;

import org.example.kalkulationsprogramm.service.LieferantDokumentPositionNachtrag;
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
 * Trägt einmal die Positionen der Lieferanten-Dokumente nach, die per E-Mail kamen
 * und deshalb ohne Positionen gespeichert wurden (siehe
 * {@link LieferantDokumentPositionNachtrag}).
 *
 * <p>Läuft je {@link #NACHTRAG_VERSION} genau einmal: Die erledigte Version steht
 * in den System-Einstellungen. Startet nach dem Hochfahren im Hintergrund und
 * blockiert den Start nicht. Fehler werden nur protokolliert; beim nächsten Start
 * wird es erneut versucht.
 *
 * <p>Abschaltbar mit {@code lieferant.positionen.nachtrag-on-start=false} (Tests).
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "lieferant.positionen.nachtrag-on-start", havingValue = "true", matchIfMissing = true)
public class LieferantDokumentPositionNachtragRunner {

    /** Erhöhen, wenn fehlende Positionen erneut aus den KI-Antworten nachgetragen werden sollen. */
    public static final int NACHTRAG_VERSION = 1;
    static final String EINSTELLUNG = "lieferant.positionen.nachtrag.version";

    private final LieferantDokumentPositionNachtrag nachtrag;
    private final SystemSettingsService einstellungen;
    private final TaskExecutor taskExecutor;

    public LieferantDokumentPositionNachtragRunner(LieferantDokumentPositionNachtrag nachtrag,
            SystemSettingsService einstellungen, @Qualifier("taskExecutor") TaskExecutor taskExecutor) {
        this.nachtrag = nachtrag;
        this.einstellungen = einstellungen;
        this.taskExecutor = taskExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(190) // nur die Reihenfolge der Übergabe an den Hintergrund-Thread-Pool
    public void nachDemStart() {
        taskExecutor.execute(this::tragNachFallsNoetig);
    }

    /**
     * @return {@code true}, wenn nachgetragen wurde
     */
    public boolean tragNachFallsNoetig() {
        try {
            int erledigt = erledigteVersion();
            if (erledigt >= NACHTRAG_VERSION) {
                log.debug("[PositionenNachtrag] Version {} schon erledigt – nichts zu tun.", erledigt);
                return false;
            }
            int ergaenzt = nachtrag.nachtragen();
            einstellungen.save(EINSTELLUNG, String.valueOf(NACHTRAG_VERSION),
                    "Stand des Nachtrags fehlender Positionen der Lieferanten-Dokumente");
            log.info("[PositionenNachtrag] Fertig: {} Dokumente ergänzt.", ergaenzt);
            return true;
        } catch (Exception e) {
            // Darf den Betrieb nicht stören – beim nächsten Start neuer Versuch.
            log.error("[PositionenNachtrag] Nachtrag fehlgeschlagen: {}: {}",
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
