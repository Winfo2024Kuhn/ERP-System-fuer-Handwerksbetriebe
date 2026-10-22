package org.example.kalkulationsprogramm.service.telefon.telefonbuch;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonbuchAbgleichErgebnisDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wann abgeglichen wird:
 * <ul>
 *   <li><b>Nach Änderungen:</b> {@link #aenderungGemeldet()} plant einen Lauf in
 *       {@value #VERZOEGERUNG_SEKUNDEN} s. Weitere Meldungen bis dahin hängen sich an
 *       diesen Lauf an (viele Änderungen hintereinander → ein Lauf). Meldungen
 *       während eines Laufs planen danach einen weiteren.</li>
 *   <li><b>Sofort:</b> {@link #sofort()} – Knopf „Jetzt abgleichen“ und Umlegen des
 *       Schalters. Der Aufrufer wartet nicht; den Fortschritt zeigt der Status.</li>
 *   <li><b>Beim Start des Servers:</b> einmal ein Vollabgleich, {@value #START_VERZOEGERUNG_SEKUNDEN} s
 *       nachdem die Anwendung bereit ist.</li>
 *   <li><b>Nachts:</b> Vollabgleich, der auch den Ist-Stand der Box liest.</li>
 * </ul>
 * Start- und Nachtlauf holen auch eine offene Löschung nach dem Ausschalten nach.
 * <p>
 * Alle Läufe laufen auf einem <b>eigenen Thread</b> – ein langer erster Abgleich
 * blockiert so keinen der gemeinsamen Scheduler-Threads (E-Mail-Import, Abholung …).
 * Sie gehen durch {@link TelefonbuchAbgleichService#abgleichen(boolean)} und laufen
 * damit nie parallel. Fehler werden hier abgefangen und nur protokolliert: Ein Lauf
 * darf weder das Speichern im ERP noch den Start stören.
 */
@Slf4j
@Component
public class TelefonbuchAbgleichPlaner {

    static final long VERZOEGERUNG_SEKUNDEN = 10;
    static final long START_VERZOEGERUNG_SEKUNDEN = 45;

    private final TaskScheduler scheduler;
    private final ThreadPoolTaskScheduler eigenerScheduler;
    private final TelefonbuchAbgleichService abgleich;
    private final Clock clock;
    private final Duration verzoegerung;
    private final Duration startVerzoegerung;
    private final AtomicBoolean geplant = new AtomicBoolean();
    private final AtomicBoolean vollGewuenscht = new AtomicBoolean();

    @Autowired
    public TelefonbuchAbgleichPlaner(TelefonbuchAbgleichService abgleich, Clock clock) {
        this(eigenerThread(), abgleich, clock, Duration.ofSeconds(VERZOEGERUNG_SEKUNDEN),
                Duration.ofSeconds(START_VERZOEGERUNG_SEKUNDEN));
    }

    TelefonbuchAbgleichPlaner(TaskScheduler scheduler, TelefonbuchAbgleichService abgleich, Clock clock,
                              Duration verzoegerung, Duration startVerzoegerung) {
        this.scheduler = scheduler;
        this.eigenerScheduler = scheduler instanceof ThreadPoolTaskScheduler t ? t : null;
        this.abgleich = abgleich;
        this.clock = clock;
        this.verzoegerung = verzoegerung;
        this.startVerzoegerung = startVerzoegerung;
    }

    /** Ein Thread nur für das Telefonbuch. */
    private static ThreadPoolTaskScheduler eigenerThread() {
        ThreadPoolTaskScheduler t = new ThreadPoolTaskScheduler();
        t.setPoolSize(1);
        t.setThreadNamePrefix("telefonbuch-");
        t.setWaitForTasksToCompleteOnShutdown(false);
        t.initialize();
        return t;
    }

    @PreDestroy
    void beenden() {
        if (eigenerScheduler != null) {
            eigenerScheduler.shutdown();
        }
    }

    /** Ein Kontakt wurde gespeichert oder gelöscht. Wirft nie. */
    public void aenderungGemeldet() {
        plane(false);
    }

    /** Wie {@link #aenderungGemeldet()}, aber mit Vollabgleich. Wirft nie. */
    public void vollabgleichGewuenscht() {
        plane(true);
    }

    /**
     * Vollabgleich (bzw. offene Löschung) sofort im Hintergrund starten. Der Status
     * zeigt ab jetzt „läuft“.
     *
     * @return {@code false}, wenn der Lauf nicht gestartet werden konnte
     */
    public boolean sofort() {
        abgleich.markiereAngefordert();
        try {
            scheduler.schedule(() -> fuehreAus(true), clock.instant());
            return true;
        } catch (RuntimeException e) {
            abgleich.verwerfeAnforderung();
            log.warn("Telefonbuch-Lauf konnte nicht gestartet werden: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /** Server ist gestartet: Vollabgleich mit Verzögerung planen – der Start wartet nicht. */
    @EventListener(ApplicationReadyEvent.class)
    public void serverGestartet() {
        try {
            scheduler.schedule(this::vollabgleichWennBereit, clock.instant().plus(startVerzoegerung));
        } catch (RuntimeException e) {
            log.warn("Telefonbuch-Lauf nach dem Start konnte nicht geplant werden: {}", e.getClass().getSimpleName());
        }
    }

    /** Nächtlicher Vollabgleich (Standard 03:40 Uhr) – läuft auf dem eigenen Thread. */
    @Scheduled(cron = "${telefon.telefonbuch.vollabgleich.cron:0 40 3 * * *}", zone = "Europe/Berlin")
    public void naechtlicherVollabgleich() {
        try {
            scheduler.schedule(this::vollabgleichWennBereit, clock.instant());
        } catch (RuntimeException e) {
            log.warn("Nächtlicher Telefonbuch-Lauf konnte nicht geplant werden: {}", e.getClass().getSimpleName());
        }
    }

    /** Vollabgleich bzw. offene Löschung, sofern es etwas zu tun gibt. */
    void vollabgleichWennBereit() {
        try {
            if (abgleich.istBereit()) {
                fuehreAus(true);
            }
        } catch (RuntimeException e) {
            log.warn("Telefonbuch-Vollabgleich fehlgeschlagen: {}", e.getClass().getSimpleName());
        }
    }

    private void plane(boolean voll) {
        try {
            if (voll) {
                vollGewuenscht.set(true);
            }
            if (geplant.compareAndSet(false, true)) {
                try {
                    scheduler.schedule(this::geplanterLauf, clock.instant().plus(verzoegerung));
                } catch (RuntimeException e) {
                    geplant.set(false);
                    throw e;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Telefonbuch-Abgleich konnte nicht geplant werden: {}", e.getClass().getSimpleName());
        }
    }

    void geplanterLauf() {
        geplant.set(false);
        fuehreAus(vollGewuenscht.getAndSet(false));
    }

    /** Ein Lauf; lief gerade schon einer, wird danach noch einmal geplant. */
    private void fuehreAus(boolean voll) {
        try {
            TelefonbuchAbgleichErgebnisDto ergebnis = abgleich.abgleichen(voll);
            if (ergebnis.laeuftSchon()) {
                // Der laufende Lauf hat die Anforderung schon zurückgesetzt – ohne neue
                // Markierung zeigte der Status bis zum Nachholen „läuft nicht“.
                abgleich.markiereAngefordert();
                plane(voll);
            }
        } catch (RuntimeException e) {
            log.warn("Telefonbuch-Lauf fehlgeschlagen: {}", e.getClass().getSimpleName());
        }
    }
}
