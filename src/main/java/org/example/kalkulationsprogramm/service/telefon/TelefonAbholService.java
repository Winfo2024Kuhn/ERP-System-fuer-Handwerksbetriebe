package org.example.kalkulationsprogramm.service.telefon;

import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Sprachnachricht;
import org.example.kalkulationsprogramm.domain.TelefonAnruf;
import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.domain.TelefonZuordnung;
import org.example.kalkulationsprogramm.dto.Telefon.AbholErgebnisDto;
import org.example.kalkulationsprogramm.dto.Telefon.AnrufbeantworterDto;
import org.example.kalkulationsprogramm.repository.SprachnachrichtRepository;
import org.example.kalkulationsprogramm.repository.TelefonAnrufRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holt Anrufliste und Sprachnachrichten von der Telefonanlage ab.
 * <p>
 * Nur Anrufe auf Geschäftsnummern und nur Nachrichten der gewählten
 * Anrufbeantworter werden gespeichert – alles andere wird verworfen, auch
 * nicht geloggt. Mehrfaches Abholen legt nichts doppelt an. Fehler werden nur
 * beim Wechsel geloggt, damit der Log nicht alle zwei Minuten volläuft.
 */
@Slf4j
@Service
public class TelefonAbholService {

    static final int ERSTER_LAUF_TAGE = 30;
    static final int MAX_TAGE = 999;
    private static final Duration VERKNUEPFUNG_TOLERANZ = Duration.ofMinutes(1);

    private final TelefonAnlage anlage;
    private final TelefonEinstellungenService einstellungen;
    private final RufnummernZuordnungService zuordnung;
    private final TelefonAnrufRepository anrufRepository;
    private final SprachnachrichtRepository nachrichtRepository;
    private final SprachnachrichtDateiablage ablage;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ReentrantLock lauf = new ReentrantLock();

    public TelefonAbholService(TelefonAnlage anlage,
                               TelefonEinstellungenService einstellungen,
                               RufnummernZuordnungService zuordnung,
                               TelefonAnrufRepository anrufRepository,
                               SprachnachrichtRepository nachrichtRepository,
                               SprachnachrichtDateiablage ablage,
                               PlatformTransactionManager transactionManager,
                               Clock clock) {
        this.anlage = anlage;
        this.einstellungen = einstellungen;
        this.zuordnung = zuordnung;
        this.anrufRepository = anrufRepository;
        this.nachrichtRepository = nachrichtRepository;
        this.ablage = ablage;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Regelmäßige Abholung alle 2 Minuten – nur wenn aktiv und eingerichtet. */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void geplanteAbholung() {
        if (!einstellungen.istAktiv() || einstellungen.zugang().isEmpty()) {
            return;
        }
        abholen();
    }

    /** "Jetzt abholen": Zeitraum seit der letzten erfolgreichen Abholung. */
    public AbholErgebnisDto abholen() {
        return lauf(tageSeitLetzterAbholung(), false);
    }

    /**
     * Nachholen (Admin): Anrufliste der letzten {@code tage} Tage, alle Nachrichten
     * auf den gewählten Anrufbeantwortern und erneuter Abgleich aller unbekannten Einträge.
     */
    public AbholErgebnisDto nachholen(int tage) {
        if (tage < 1 || tage > MAX_TAGE) {
            throw new IllegalArgumentException("Bitte zwischen 1 und 999 Tagen wählen.");
        }
        return lauf(tage, true);
    }

    private AbholErgebnisDto lauf(int tage, boolean alleOffenenAbgleichen) {
        Optional<TelefonZugang> zugang = einstellungen.zugang();
        if (zugang.isEmpty()) {
            return new AbholErgebnisDto(false, TelefonAnlageException.Grund.NICHT_EINGERICHTET.text(), 0, 0, 0);
        }
        if (!lauf.tryLock()) {
            return new AbholErgebnisDto(false, "Es läuft gerade schon eine Abholung.", 0, 0, 0);
        }
        try {
            LocalDateTime start = LocalDateTime.now(clock);
            RufnummernZuordnungService.Verzeichnis verzeichnis = zuordnung.frischesVerzeichnis();
            int neueAnrufe = holeAnrufe(zugang.get(), tage, verzeichnis);
            int neueNachrichten = holeSprachnachrichten(zugang.get(), verzeichnis);
            int nachtraeglich = gleicheOffeneAb(verzeichnis, alleOffenenAbgleichen);
            String vorherigerFehler = einstellungen.letzterFehler();
            einstellungen.merkeErfolg(start);
            if (vorherigerFehler != null) {
                log.info("Telefon-Abholung wieder erfolgreich");
            }
            return new AbholErgebnisDto(true, "Abgeholt", neueAnrufe, neueNachrichten, nachtraeglich);
        } catch (TelefonAnlageException e) {
            String grund = e.getMessage();
            if (!grund.equals(einstellungen.letzterFehler())) {
                log.warn("Telefon-Abholung fehlgeschlagen: {}", grund);
            }
            einstellungen.merkeFehler(grund);
            return new AbholErgebnisDto(false, grund, 0, 0, 0);
        } finally {
            lauf.unlock();
        }
    }

    private int holeAnrufe(TelefonZugang zugang, int tage, RufnummernZuordnungService.Verzeichnis verzeichnis) {
        List<String> geschaeftlich = einstellungen.geschaeftsnummern();
        if (geschaeftlich.isEmpty()) {
            return 0;
        }
        int neu = 0;
        List<AnlagenAnruf> anrufe = anlage.ladeAnrufe(zugang, tage).stream()
                .sorted(Comparator.comparing(AnlagenAnruf::zeitpunkt))
                .toList();
        for (AnlagenAnruf a : anrufe) {
            if (!istGeschaeftsnummer(a.eigeneNummer(), geschaeftlich)) {
                continue;
            }
            String nummerRoh = kuerze(a.gegenNummer());
            String eigene = kuerze(a.eigeneNummer());
            Boolean angelegt = tx.execute(status -> {
                if (anrufRepository.existsByZeitpunktAndArtAndEigeneNummerAndNummerRoh(
                        a.zeitpunkt(), a.art(), eigene, nummerRoh)) {
                    return false;
                }
                TelefonAnruf anruf = new TelefonAnruf();
                anruf.setZeitpunkt(a.zeitpunkt());
                anruf.setArt(a.art());
                anruf.setAnrufbeantworter(a.anrufbeantworter());
                anruf.setNummerRoh(nummerRoh);
                anruf.setNummerNormalisiert(zuordnung.normalisiere(nummerRoh));
                anruf.setEigeneNummer(eigene);
                anruf.setDauerMinuten(Math.max(0, a.dauerMinuten()));
                anruf.setNameFritzbox(a.name());
                anruf.setAngelegtAm(LocalDateTime.now(clock));
                zuordnung.ordneAutomatischZu(anruf, verzeichnis);
                anrufRepository.save(anruf);
                return true;
            });
            if (Boolean.TRUE.equals(angelegt)) {
                neu++;
            }
        }
        return neu;
    }

    private int holeSprachnachrichten(TelefonZugang zugang, RufnummernZuordnungService.Verzeichnis verzeichnis) {
        int neu = 0;
        for (AnrufbeantworterDto ab : einstellungen.anrufbeantworter()) {
            for (AnlagenSprachnachricht n : anlage.ladeSprachnachrichten(zugang, ab.index())) {
                String nummerRoh = kuerze(n.gegenNummer());
                if (nachrichtRepository.existsByAnrufbeantworterAndZeitpunktAndNummerRoh(
                        ab.index(), n.zeitpunkt(), nummerRoh)) {
                    continue;
                }
                if (speichereNachricht(zugang, ab.index(), n, nummerRoh, verzeichnis)) {
                    neu++;
                }
            }
        }
        return neu;
    }

    /** Lädt die Aufnahme und legt den Datensatz an. Ohne Aufnahme kein Datensatz – nächster Lauf versucht es erneut. */
    private boolean speichereNachricht(TelefonZugang zugang, int abIndex, AnlagenSprachnachricht n,
                                       String nummerRoh, RufnummernZuordnungService.Verzeichnis verzeichnis) {
        SprachnachrichtAudio.Ergebnis audio;
        try {
            audio = SprachnachrichtAudio.aufbereiten(anlage.ladeAudio(zugang, n));
        } catch (IllegalArgumentException e) {
            log.warn("Aufnahme vom Anrufbeantworter {} konnte nicht gelesen werden: {}", abIndex + 1, e.getMessage());
            return false;
        } catch (TelefonAnlageException e) {
            // Box weg oder Anmeldung kaputt → ganzer Lauf bricht ab. Eine einzelne
            // kaputte Aufnahme darf aber nicht jede weitere Abholung blockieren.
            if (e.getGrund() == TelefonAnlageException.Grund.NICHT_ERREICHBAR
                    || e.getGrund() == TelefonAnlageException.Grund.ANMELDUNG_FEHLGESCHLAGEN) {
                throw e;
            }
            log.warn("Aufnahme vom Anrufbeantworter {} konnte nicht geladen werden: {}", abIndex + 1, e.getMessage());
            return false;
        }
        if (audio.warRoh()) {
            log.info("Aufnahme ohne WAV-Kopf – als G.711 A-law gelesen");
        }
        String dateiName = ablage.speichere(audio.wav());
        try {
            tx.executeWithoutResult(status -> {
                Sprachnachricht s = new Sprachnachricht();
                s.setAnrufbeantworter(abIndex);
                s.setZeitpunkt(n.zeitpunkt());
                s.setNummerRoh(nummerRoh);
                s.setNummerNormalisiert(zuordnung.normalisiere(nummerRoh));
                s.setDauerSekunden(audio.dauerSekunden());
                s.setDateiName(dateiName);
                s.setAngelegtAm(LocalDateTime.now(clock));
                TelefonAnruf anruf = passenderAnruf(nummerRoh, n.zeitpunkt(), abIndex);
                s.setAnruf(anruf);
                if (anruf != null && anruf.getZuordnung() == TelefonZuordnung.MANUELL) {
                    s.setKunde(anruf.getKunde());
                    s.setLieferant(anruf.getLieferant());
                    s.setZuordnung(TelefonZuordnung.MANUELL);
                } else {
                    zuordnung.ordneAutomatischZu(s, verzeichnis);
                }
                nachrichtRepository.save(s);
            });
            return true;
        } catch (RuntimeException e) {
            ablage.loesche(dateiName);
            throw e;
        }
    }

    /** Anruf zur Nachricht: gleiche Nummer, Zeitpunkt ±1 Minute, bevorzugt vom selben AB entgegengenommen. */
    private TelefonAnruf passenderAnruf(String nummerRoh, LocalDateTime zeitpunkt, int abIndex) {
        List<TelefonAnruf> kandidaten = anrufRepository.findByNummerRohAndZeitpunktBetween(
                nummerRoh, zeitpunkt.minus(VERKNUEPFUNG_TOLERANZ), zeitpunkt.plus(VERKNUEPFUNG_TOLERANZ));
        return kandidaten.stream()
                .filter(a -> a.getArt() != TelefonAnrufArt.AUSGEHEND)
                .min(Comparator.comparing((TelefonAnruf a) -> !Integer.valueOf(abIndex).equals(a.getAnrufbeantworter()))
                        .thenComparing(a -> Duration.between(a.getZeitpunkt(), zeitpunkt).abs()))
                .orElse(null);
    }

    /**
     * Gleicht "Unbekannt"-Einträge erneut ab, z.B. nachdem ein Kontakt angelegt oder
     * seine Nummer geändert wurde. Standard: innerhalb der Aufbewahrungsfrist.
     */
    private int gleicheOffeneAb(RufnummernZuordnungService.Verzeichnis verzeichnis, boolean alle) {
        LocalDateTime nach = alle ? LocalDateTime.of(1970, 1, 1, 0, 0)
                : LocalDateTime.now(clock).minusMonths(einstellungen.aufbewahrungAnrufeMonate());
        Integer anzahl = tx.execute(status -> {
            int n = 0;
            for (TelefonAnruf a : anrufRepository.findByZuordnungAndNummerNormalisiertIsNotNullAndZeitpunktAfter(
                    TelefonZuordnung.KEINE, nach)) {
                if (zuordnung.ordneAutomatischZu(a, verzeichnis)) {
                    n++;
                }
            }
            for (Sprachnachricht s : nachrichtRepository.findByZuordnungAndNummerNormalisiertIsNotNullAndZeitpunktAfter(
                    TelefonZuordnung.KEINE, nach)) {
                if (zuordnung.ordneAutomatischZu(s, verzeichnis)) {
                    n++;
                }
            }
            return n;
        });
        return anzahl == null ? 0 : anzahl;
    }

    private int tageSeitLetzterAbholung() {
        LocalDateTime letzte = einstellungen.letzteAbholung();
        if (letzte == null) {
            return ERSTER_LAUF_TAGE;
        }
        long tage = Duration.between(letzte, LocalDateTime.now(clock)).toDays() + 1;
        return (int) Math.clamp(tage, 1, MAX_TAGE);
    }

    /**
     * Ist die eigene Nummer eine Geschäftsnummer? Vergleich über das einheitliche
     * Format (mit Ortsvorwahl), zur Sicherheit auch über die reinen Ziffern.
     */
    boolean istGeschaeftsnummer(String eigene, List<String> geschaeftlich) {
        if (eigene == null || eigene.isBlank()) {
            return false;
        }
        String eigeneNorm = zuordnung.normalisiere(eigene);
        String eigeneZiffern = ziffern(eigene);
        for (String g : geschaeftlich) {
            String gNorm = zuordnung.normalisiere(g);
            if (eigeneNorm != null && eigeneNorm.equals(gNorm)) {
                return true;
            }
            if (!eigeneZiffern.isEmpty() && eigeneZiffern.equals(ziffern(g))) {
                return true;
            }
        }
        return false;
    }

    private static String ziffern(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String kuerze(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > 40 ? t.substring(0, 40) : t;
    }
}
