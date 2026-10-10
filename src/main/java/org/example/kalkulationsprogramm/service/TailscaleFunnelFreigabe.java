package org.example.kalkulationsprogramm.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Darf die Zeiterfassung über Tailscale Funnel aus dem Internet erreichbar sein?
 *
 * <p>Zwei Wege für die Handys der Mitarbeiter: Entweder sie haben die Tailscale-App
 * und sind damit im privaten Firmennetz (Standard, das ERP bleibt komplett privat),
 * oder der Server gibt die Zeiterfassung per {@code tailscale funnel} öffentlich frei.
 * Den Funnel selbst schaltet man am Server ein; dieser Schalter entscheidet, ob das
 * ERP solche Anfragen annimmt. Ausgeschaltet weist die Netzgrenze jede Funnel-Anfrage
 * ab, auch wenn am Server versehentlich ein Funnel läuft.</p>
 *
 * <p>Die Netzgrenze fragt bei jeder Funnel-Anfrage nach, deshalb wird der Wert
 * zwischengespeichert und beim Umschalten erneuert.</p>
 */
@Service
@Slf4j
public class TailscaleFunnelFreigabe {

    static final String SCHLUESSEL = "zeiterfassung.tailscale-funnel.freigegeben";

    private final SystemSettingsService settings;

    private volatile Boolean freigegeben;
    private volatile Instant letzteAnfrage;
    private volatile boolean letzteAbgewiesen;

    public TailscaleFunnelFreigabe(SystemSettingsService settings) {
        this.settings = settings;
    }

    /** Standard: nicht freigegeben – die Mitarbeiter nutzen die Tailscale-App. */
    public boolean istFreigegeben() {
        Boolean wert = freigegeben;
        if (wert == null) {
            wert = Boolean.parseBoolean(settings.get(SCHLUESSEL, "false"));
            freigegeben = wert;
        }
        return wert;
    }

    public void setzen(boolean neu) {
        settings.save(SCHLUESSEL, String.valueOf(neu),
                "Zeiterfassung über Tailscale Funnel aus dem Internet erreichbar");
        freigegeben = neu;
        log.info("Zeiterfassung über Tailscale Funnel: {}", neu ? "freigegeben" : "gesperrt");
    }

    /** Merkt sich die letzte Funnel-Anfrage – so sieht man in den Einstellungen, ob am Server ein Funnel läuft. */
    public void anfrageGesehen(boolean abgewiesen) {
        letzteAbgewiesen = abgewiesen;
        letzteAnfrage = Instant.now();
    }

    /** Seit dem Start der App; {@code null}, wenn noch keine kam. */
    public Instant letzteAnfrage() {
        return letzteAnfrage;
    }

    public boolean letzteAbgewiesen() {
        return letzteAbgewiesen;
    }
}
