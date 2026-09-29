package org.example.kalkulationsprogramm.service.telefon;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.dto.Telefon.KontaktKurzDto;
import org.example.kalkulationsprogramm.dto.Telefon.LiveAnrufDto;
import org.example.kalkulationsprogramm.service.telefon.fritzbox.FritzBoxHost;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Hält die Verbindung zum FRITZ!Box-Anrufmonitor (TCP 1012, einmalig mit
 * {@code #96*5*} eingeschaltet) und macht daraus Live-Anrufe für das
 * Anruf-Fenster. Nur Anrufe auf Geschäftsnummern werden weitergegeben.
 * Nach Gesprächsende wird sofort eine Abholung angestoßen.
 */
@Slf4j
@Service
public class TelefonAnrufmonitorService {

    static final int PORT = 1012;
    private static final int VERBINDUNGS_TIMEOUT_MS = 5_000;
    private static final long WARTEN_MIN_MS = 5_000;
    private static final long WARTEN_MAX_MS = 60_000;

    private final TelefonEinstellungenService einstellungen;
    private final RufnummernZuordnungService zuordnung;
    private final TelefonLiveService live;
    private final TelefonAbholService abholService;

    /** Laufende Anrufe auf Geschäftsnummern: Verbindungs-ID → Stand. */
    private final Map<String, LiveAnrufDto> laufend = new ConcurrentHashMap<>();
    private final Object signal = new Object();
    private final ExecutorService abholung = Executors.newSingleThreadExecutor(r -> daemon(r, "telefon-nachabholung"));

    private final int port;
    private volatile boolean aktiv = true;
    private volatile boolean verbunden;
    private volatile Socket socket;
    private Thread thread;

    @org.springframework.beans.factory.annotation.Autowired
    public TelefonAnrufmonitorService(TelefonEinstellungenService einstellungen,
                                      RufnummernZuordnungService zuordnung,
                                      TelefonLiveService live,
                                      TelefonAbholService abholService) {
        this(einstellungen, zuordnung, live, abholService, PORT);
    }

    TelefonAnrufmonitorService(TelefonEinstellungenService einstellungen,
                               RufnummernZuordnungService zuordnung,
                               TelefonLiveService live,
                               TelefonAbholService abholService,
                               int port) {
        this.port = port;
        this.einstellungen = einstellungen;
        this.zuordnung = zuordnung;
        this.live = live;
        this.abholService = abholService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void starte() {
        thread = daemon(this::schleife, "telefon-anrufmonitor");
        thread.start();
    }

    @PreDestroy
    public void stoppe() {
        aktiv = false;
        schliesseSocket();
        synchronized (signal) {
            signal.notifyAll();
        }
        abholung.shutdownNow();
    }

    public boolean istVerbunden() {
        return verbunden;
    }

    /** Nach dem Speichern der Einstellungen neu verbinden (andere Adresse, aus-/eingeschaltet). */
    public void einstellungenGeaendert() {
        schliesseSocket();
        synchronized (signal) {
            signal.notifyAll();
        }
    }

    /** Verarbeitet ein Ereignis des Anrufmonitors (öffentlich für Tests). */
    public void verarbeite(AnrufmonitorEreignis e) {
        switch (e.typ()) {
            case RING -> klingelt(e);
            case CONNECT -> angenommen(e);
            case DISCONNECT -> beendet(e);
            case CALL -> {
                // Ausgehende Anrufe zeigen kein Anruf-Fenster
            }
        }
    }

    private void klingelt(AnrufmonitorEreignis e) {
        if (!istGeschaeftsnummer(e.eigeneNummer())) {
            return;
        }
        String normalisiert = zuordnung.normalisiere(e.nummer());
        RufnummernZuordnungService.Treffer t = zuordnung.verzeichnis().finde(normalisiert);
        KontaktKurzDto kontakt = t.eindeutig() ? t.kontakte().getFirst() : null;
        List<KontaktKurzDto> kandidaten = t.mehrdeutig() ? t.kontakte() : List.of();
        LiveAnrufDto anruf = new LiveAnrufDto(e.verbindungsId(), "KLINGELT", e.nummer(), kontakt, kandidaten, false);
        laufend.put(e.verbindungsId(), anruf);
        live.sende(anruf);
    }

    private void angenommen(AnrufmonitorEreignis e) {
        LiveAnrufDto vorher = laufend.get(e.verbindungsId());
        if (vorher == null) {
            return;
        }
        String status = e.istAnrufbeantworter() ? "ANRUFBEANTWORTER" : "IM_GESPRAECH";
        LiveAnrufDto jetzt = new LiveAnrufDto(vorher.verbindungsId(), status, vorher.nummer(),
                vorher.kontakt(), vorher.kandidaten(), !e.istAnrufbeantworter());
        laufend.put(e.verbindungsId(), jetzt);
        live.sende(jetzt);
    }

    private void beendet(AnrufmonitorEreignis e) {
        LiveAnrufDto vorher = laufend.remove(e.verbindungsId());
        if (vorher == null) {
            return;
        }
        live.sende(new LiveAnrufDto(vorher.verbindungsId(), "BEENDET", vorher.nummer(),
                vorher.kontakt(), vorher.kandidaten(), vorher.angenommen()));
        if (!abholung.isShutdown()) {
            abholung.submit(this::abholenOhneFehler);
        }
    }

    private void abholenOhneFehler() {
        try {
            // Der FRITZ!Box kurz Zeit geben, Anruf und Aufnahme abzuschließen
            TimeUnit.SECONDS.sleep(5);
            abholService.abholen();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ex) {
            log.debug("Abholung nach Gesprächsende fehlgeschlagen", ex);
        }
    }

    private boolean istGeschaeftsnummer(String eigene) {
        return abholService.istGeschaeftsnummer(eigene, einstellungen.geschaeftsnummern());
    }

    private void schleife() {
        long warten = WARTEN_MIN_MS;
        while (aktiv) {
            Optional<TelefonZugang> zugang = einstellungen.istAktiv() ? einstellungen.zugang() : Optional.empty();
            if (zugang.isEmpty() || !FritzBoxHost.istGueltig(zugang.get().host())) {
                warte(WARTEN_MAX_MS);
                continue;
            }
            try (Socket s = new Socket()) {
                socket = s;
                s.connect(new InetSocketAddress(zugang.get().host(), port), VERBINDUNGS_TIMEOUT_MS);
                s.setKeepAlive(true);
                verbunden = true;
                warten = WARTEN_MIN_MS;
                log.info("Anrufmonitor der FRITZ!Box verbunden");
                lese(s);
            } catch (IOException ex) {
                if (verbunden) {
                    log.info("Anrufmonitor-Verbindung getrennt");
                }
            } finally {
                verbunden = false;
                socket = null;
                laufend.clear();
            }
            if (aktiv) {
                warte(warten);
                warten = Math.min(warten * 2, WARTEN_MAX_MS);
            }
        }
    }

    private void lese(Socket s) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        String zeile;
        while (aktiv && (zeile = in.readLine()) != null) {
            AnrufmonitorEreignis e = AnrufmonitorEreignis.parse(zeile);
            if (e != null) {
                try {
                    verarbeite(e);
                } catch (RuntimeException ex) {
                    log.warn("Anrufmonitor-Ereignis konnte nicht verarbeitet werden", ex);
                }
            }
        }
    }

    private void warte(long ms) {
        synchronized (signal) {
            try {
                signal.wait(ms);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                aktiv = false;
            }
        }
    }

    private void schliesseSocket() {
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // schon zu
            }
        }
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }
}
