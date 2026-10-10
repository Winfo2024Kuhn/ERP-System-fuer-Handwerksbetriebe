package org.example.kalkulationsprogramm.config;

import org.example.kalkulationsprogramm.service.LieferantDokumentDuplikatService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import lombok.extern.slf4j.Slf4j;

/**
 * Löscht rückwirkend einmal die Lieferanten-Dokumente, die dieselbe gespeicherte Datei
 * doppelt zeigen (siehe {@link LieferantDokumentDuplikatService}). Neue Duplikate
 * verhindert der Mail-Import selbst.
 *
 * <p>Läuft je {@link #BEREINIGUNG_VERSION}, bis nichts mehr übrig ist: Die erledigte
 * Version steht in den System-Einstellungen und wird erst gespeichert, wenn keine Gruppe
 * übersprungen wurde – sonst neuer Versuch beim nächsten Start (und erneuter Hinweis im Log).
 *
 * <p>Läuft direkt im Start-Ereignis, nicht im Hintergrund: So ist die Bereinigung sicher
 * fertig, bevor der {@link LieferantDokumentAbgleichBackfillRunner} (später eingereiht)
 * neu verknüpft – beide parallel könnten sich gegenseitig Dokumente wegziehen. Der Lauf
 * dauert Sekunden; danach bleibt nur das Lesen der Version. Fehler werden nur protokolliert.
 *
 * <p>Abschaltbar mit {@code lieferant.duplikate.backfill-on-start=false} (Tests).
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "lieferant.duplikate.backfill-on-start", havingValue = "true", matchIfMissing = true)
public class LieferantDokumentDuplikatBackfillRunner {

    /** Erhöhen, wenn die Bereinigung noch einmal über alle Dokumente laufen soll. */
    public static final int BEREINIGUNG_VERSION = 1;
    static final String EINSTELLUNG = "lieferant.duplikate.version";

    private final LieferantDokumentDuplikatService duplikatService;
    private final SystemSettingsService einstellungen;

    public LieferantDokumentDuplikatBackfillRunner(LieferantDokumentDuplikatService duplikatService,
            SystemSettingsService einstellungen) {
        this.duplikatService = duplikatService;
        this.einstellungen = einstellungen;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(150) // vor dem Abgleich-Backfill (200), der seine Arbeit erst danach einreiht
    public void nachDemStart() {
        bereinigeFallsNoetig();
    }

    /**
     * @return {@code true}, wenn bereinigt wurde
     */
    public boolean bereinigeFallsNoetig() {
        try {
            int erledigt = erledigteVersion();
            if (erledigt >= BEREINIGUNG_VERSION) {
                log.debug("[DuplikatBackfill] Version {} schon erledigt – nichts zu tun.", erledigt);
                return false;
            }
            log.info("[DuplikatBackfill] Lösche doppelte Lieferanten-Dokumente (Version {} -> {})...",
                    erledigt, BEREINIGUNG_VERSION);
            LieferantDokumentDuplikatService.Ergebnis ergebnis = duplikatService.bereinigeDateiDuplikate();
            if (ergebnis.uebersprungen() > 0) {
                log.warn("[DuplikatBackfill] {} Duplikate gelöscht, {} Gruppen übersprungen (siehe [Duplikate] im Log)"
                        + " – neuer Versuch beim nächsten Start.", ergebnis.geloescht(), ergebnis.uebersprungen());
                return true;
            }
            einstellungen.save(EINSTELLUNG, String.valueOf(BEREINIGUNG_VERSION),
                    "Stand der automatischen Bereinigung doppelter Lieferanten-Dokumente");
            log.info("[DuplikatBackfill] Fertig: {} Duplikate gelöscht.", ergebnis.geloescht());
            return true;
        } catch (Exception e) {
            // Darf den Betrieb nicht stören – beim nächsten Start neuer Versuch.
            log.error("[DuplikatBackfill] Bereinigung fehlgeschlagen: {}: {}",
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
