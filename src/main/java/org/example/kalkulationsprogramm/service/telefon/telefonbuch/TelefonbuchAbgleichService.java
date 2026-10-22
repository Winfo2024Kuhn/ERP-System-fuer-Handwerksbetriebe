package org.example.kalkulationsprogramm.service.telefon.telefonbuch;

import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.TelefonbuchZuordnung;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonbuchAbgleichErgebnisDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonbuchStatusDto;
import org.example.kalkulationsprogramm.repository.TelefonbuchZuordnungRepository;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.example.kalkulationsprogramm.service.telefon.TelefonEinstellungenService;
import org.example.kalkulationsprogramm.service.telefon.TelefonZugang;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchSollListe.Schluessel;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gleicht die ERP-Kontakte mit dem FRITZ!Box-Telefonbuch „ERP-Kontakte“ ab – nur in
 * Richtung ERP → Box.
 * <p>
 * Ablauf: Soll-Liste aus der Datenbank berechnen, Telefonbuch suchen bzw. anlegen,
 * dann nur Neue anlegen, Geänderte (anderer Inhalts-Hash) überschreiben und
 * Weggefallene löschen. Beim Vollabgleich wird zusätzlich der Ist-Stand der Box
 * gelesen: Fehlende Einträge kommen wieder hinein, fremde Einträge im
 * ERP-Telefonbuch fliegen heraus. Fehlt das Telefonbuch (von Hand gelöscht, Box
 * zurückgesetzt), wird es neu angelegt und alles neu gesendet.
 * <p>
 * Nach dem Ausschalten löscht der nächste Lauf das Telefonbuch „ERP-Kontakte“ aus der Box
 * (nur dieses, am Namen geprüft). Klappt das nicht, bleibt „Löschung offen“ stehen und
 * wird beim Start, nachts oder per Knopf nachgeholt.
 * <p>
 * Läufe laufen nie parallel. Protokolliert werden nur Zähler – keine Namen, keine Nummern.
 */
@Slf4j
@Service
public class TelefonbuchAbgleichService {

    public static final String TELEFONBUCH_NAME = "ERP-Kontakte";
    /** Lehnt die Box so viele Einträge hintereinander ab, ohne einen anzunehmen, bricht der Lauf ab. */
    static final int MAX_ABGELEHNT_OHNE_ERFOLG = 5;
    static final String UNERWARTETER_FEHLER = "Beim Abgleich mit dem FRITZ!Box-Telefonbuch ist ein unerwarteter Fehler "
            + "aufgetreten. Bitte später noch einmal versuchen.";

    private final TelefonbuchAnlage anlage;
    private final TelefonEinstellungenService telefonEinstellungen;
    private final TelefonbuchEinstellungen einstellungen;
    private final TelefonbuchSollListe sollListe;
    private final TelefonbuchZuordnungRepository zuordnungen;
    private final Clock clock;
    private final ReentrantLock lauf = new ReentrantLock();
    private final AtomicBoolean angefordert = new AtomicBoolean();
    private volatile TelefonbuchAbgleichErgebnisDto letztesErgebnis;
    /** Zuletzt gewarnte abgelehnte Löschungen (Anzahl + Detail) – dieselbe Warnung nicht jeden Lauf wiederholen. */
    private String letzteLoeschWarnung;

    public TelefonbuchAbgleichService(TelefonbuchAnlage anlage,
                                      TelefonEinstellungenService telefonEinstellungen,
                                      TelefonbuchEinstellungen einstellungen,
                                      TelefonbuchSollListe sollListe,
                                      TelefonbuchZuordnungRepository zuordnungen,
                                      Clock clock) {
        this.anlage = anlage;
        this.telefonEinstellungen = telefonEinstellungen;
        this.einstellungen = einstellungen;
        this.sollListe = sollListe;
        this.zuordnungen = zuordnungen;
        this.clock = clock;
    }

    public TelefonbuchStatusDto status() {
        return new TelefonbuchStatusDto(einstellungen.istAktiv(), telefonEinstellungen.istAktiv(), TELEFONBUCH_NAME,
                einstellungen.letzterAbgleich(), einstellungen.anzahlEintraege(), einstellungen.letzterFehler(),
                einstellungen.istLoeschungOffen(), laeuftGerade(), letztesErgebnis);
    }

    /** Ein Lauf ist angefordert (noch nicht gestartet) oder läuft gerade. */
    public boolean laeuftGerade() {
        return angefordert.get() || lauf.isLocked();
    }

    /** Merkt, dass gleich ein Lauf startet – damit der Status schon vor dem Start „läuft“ zeigt. */
    public void markiereAngefordert() {
        angefordert.set(true);
    }

    /** Anforderung zurücknehmen, z. B. wenn der Lauf nicht geplant werden konnte. */
    public void verwerfeAnforderung() {
        angefordert.set(false);
    }

    /**
     * Schalter umlegen. Einschalten: alles wird (neu) übertragen. Ausschalten: Das
     * Telefonbuch „ERP-Kontakte“ soll aus der FRITZ!Box gelöscht werden – das erledigt
     * der nächste Lauf; bis dahin steht „Löschung offen“ im Status.
     */
    public TelefonbuchStatusDto schalte(boolean aktiv) {
        einstellungen.setzeAktiv(aktiv);
        einstellungen.setzeLoeschungOffen(!aktiv);
        log.info("Telefonbuch-Abgleich {}", aktiv ? "eingeschaltet" : "ausgeschaltet, Telefonbuch wird gelöscht");
        return status();
    }

    /**
     * Ob es etwas zu tun gibt: übertragen (Schalter und Telefon-Anbindung an) oder eine
     * offene Löschung nachholen (dafür reicht ein eingerichteter Zugang).
     */
    public boolean istBereit() {
        return grundGegenLauf() == null;
    }

    /** {@code null}, wenn ein Lauf jetzt möglich ist; sonst der Grund in Klartext. */
    public String grundGegenLauf() {
        boolean aktiv = einstellungen.istAktiv();
        if (!aktiv && !einstellungen.istLoeschungOffen()) {
            return "Die Übertragung ins FRITZ!Box-Telefonbuch ist ausgeschaltet.";
        }
        if (aktiv && !telefonEinstellungen.istAktiv()) {
            return "Die Telefon-Anbindung ist ausgeschaltet.";
        }
        if (telefonEinstellungen.zugang().isEmpty()) {
            return TelefonAnlageException.Grund.NICHT_EINGERICHTET.text();
        }
        return null;
    }

    /**
     * Gleicht ab, sofern eingeschaltet – oder löscht nach dem Ausschalten das Telefonbuch
     * aus der Box. Läuft schon ein Lauf, kommt sofort
     * {@link TelefonbuchAbgleichErgebnisDto#beschaeftigt()} zurück.
     *
     * @param vollabgleich zusätzlich den Ist-Stand der Box lesen und reparieren
     */
    public TelefonbuchAbgleichErgebnisDto abgleichen(boolean vollabgleich) {
        String grund = grundGegenLauf();
        if (grund != null) {
            angefordert.set(false);
            return TelefonbuchAbgleichErgebnisDto.nichtGelaufen(grund);
        }
        TelefonZugang zugang = telefonEinstellungen.zugang().orElseThrow();
        if (!lauf.tryLock()) {
            return TelefonbuchAbgleichErgebnisDto.beschaeftigt();
        }
        angefordert.set(false);
        String vorherigerFehler = einstellungen.letzterFehler();
        TelefonbuchAbgleichErgebnisDto ergebnis;
        try {
            if (einstellungen.istAktiv()) {
                ergebnis = durchfuehren(zugang, vollabgleich);
                if (ergebnis.neu() + ergebnis.geaendert() + ergebnis.geloescht() > 0 || vorherigerFehler != null) {
                    log.info("Telefonbuch abgeglichen: {} neu, {} geändert, {} gelöscht, {} abgelehnt, {} Einträge",
                            ergebnis.neu(), ergebnis.geaendert(), ergebnis.geloescht(), ergebnis.abgelehnt(),
                            ergebnis.eintraege());
                }
                // Abgelehnte Einträge nur melden, wenn sich der Grund geändert hat – nicht jede Nacht erneut.
                String gemerkterFehler = einstellungen.letzterFehler();
                if (ergebnis.abgelehnt() > 0 && gemerkterFehler != null && !gemerkterFehler.equals(vorherigerFehler)) {
                    log.warn("Telefonbuch: {}", gemerkterFehler);
                }
            } else {
                ergebnis = loescheTelefonbuch(zugang);
            }
        } catch (TelefonAnlageException e) {
            // Mit Schritt und Fehlercode der Box (z. B. „SetPhonebookEntryUID: Fehlercode 402“) –
            // nur technische Angaben, keine Namen oder Nummern.
            String fehler = e.textMitDetail();
            if (!fehler.equals(vorherigerFehler)) {
                log.warn("Telefonbuch-Lauf fehlgeschlagen: {} ({})", e.getGrund(),
                        e.getDetail() == null ? "ohne Detail" : e.getDetail());
            }
            einstellungen.merkeFehler(fehler);
            ergebnis = TelefonbuchAbgleichErgebnisDto.nichtGelaufen(fehler);
        } catch (RuntimeException e) {
            // Unerwartetes (z. B. Datenbank): nur der Klassenname ins Log – keine Namen, keine Nummern.
            log.warn("Telefonbuch-Lauf unerwartet abgebrochen: {}", e.getClass().getSimpleName());
            einstellungen.merkeFehler(UNERWARTETER_FEHLER);
            ergebnis = TelefonbuchAbgleichErgebnisDto.nichtGelaufen(UNERWARTETER_FEHLER);
        } finally {
            lauf.unlock();
        }
        letztesErgebnis = ergebnis;
        return ergebnis;
    }

    /** Nach dem Ausschalten: das eigene Telefonbuch aus der Box löschen und die Zuordnungen vergessen. */
    private TelefonbuchAbgleichErgebnisDto loescheTelefonbuch(TelefonZugang zugang) {
        int eintraege = (int) zuordnungen.count();
        boolean geloescht = anlage.loescheTelefonbuch(zugang, TELEFONBUCH_NAME, einstellungen.telefonbuchId());
        zuordnungen.deleteAllInBatch();
        einstellungen.merkeTelefonbuchGeloescht(LocalDateTime.now(clock));
        log.info("Telefonbuch nach dem Ausschalten {} ({} Zuordnungen vergessen)",
                geloescht ? "aus der FRITZ!Box gelöscht" : "war in der FRITZ!Box nicht (mehr) vorhanden", eintraege);
        return new TelefonbuchAbgleichErgebnisDto(true, false,
                "Das Telefonbuch „" + TELEFONBUCH_NAME + "“ ist aus der FRITZ!Box gelöscht.", 0, 0, eintraege, 0, 0);
    }

    private TelefonbuchAbgleichErgebnisDto durchfuehren(TelefonZugang zugang, boolean vollabgleich) {
        Map<Schluessel, TelefonbuchEintrag> soll = sollListe.berechne();
        String gemerkteId = einstellungen.telefonbuchId();
        TelefonbuchAnlage.Telefonbuch buch = anlage.stelleTelefonbuchBereit(zugang, TELEFONBUCH_NAME, gemerkteId);
        boolean voll = vollabgleich;
        if (buch.neu() || !buch.id().equals(gemerkteId)) {
            // Anderes oder neues Telefonbuch: Die gemerkten Einträge gelten nicht mehr.
            zuordnungen.deleteAllInBatch();
            einstellungen.merkeTelefonbuchId(buch.id());
            voll = true;
        }
        Map<Schluessel, TelefonbuchZuordnung> ist = new HashMap<>();
        for (TelefonbuchZuordnung z : zuordnungen.findAll()) {
            ist.put(new Schluessel(z.getQuelleTyp(), z.getQuelleId()), z);
        }
        Zaehler zaehler = new Zaehler();
        if (voll) {
            repariereNachIstStand(zugang, buch.id(), ist, zaehler);
        }
        for (Map.Entry<Schluessel, TelefonbuchEintrag> e : soll.entrySet()) {
            uebertrage(zugang, buch.id(), e.getKey(), e.getValue(), ist.remove(e.getKey()), zaehler);
        }
        for (TelefonbuchZuordnung weg : ist.values()) {
            // Lässt die Box den Eintrag nicht löschen, bleibt die Zuordnung – der nächste Lauf versucht es wieder.
            if (loescheOderUeberspringe(zugang, buch.id(), weg.getEintragId(), zaehler)) {
                zuordnungen.delete(weg);
            }
        }
        meldeNichtGeloeschte(zaehler);
        String fehler = zaehler.abgelehnt == 0 ? null
                : (zaehler.abgelehnt == 1 ? "1 Eintrag hat die FRITZ!Box abgelehnt"
                : zaehler.abgelehnt + " Einträge hat die FRITZ!Box abgelehnt")
                + (zaehler.letzteAblehnung == null ? "." : " (zuletzt " + zaehler.letzteAblehnung + ").");
        einstellungen.merkeAbgleich(LocalDateTime.now(clock), soll.size() - zaehler.abgelehnt, fehler);
        String meldung = (fehler != null ? fehler
                : zaehler.neu + zaehler.geaendert + zaehler.geloescht == 0 ? "Das Telefonbuch ist schon aktuell."
                : "Telefonbuch abgeglichen.")
                + (zaehler.nichtGeloescht == 0 ? ""
                : (zaehler.nichtGeloescht == 1 ? " 1 alter Eintrag ließ sich in der FRITZ!Box nicht löschen."
                : " " + zaehler.nichtGeloescht + " alte Einträge ließen sich in der FRITZ!Box nicht löschen.")
                + (fehler == null ? " Das schadet nicht, die Kontakte sind übertragen." : ""));
        return new TelefonbuchAbgleichErgebnisDto(fehler == null, false, meldung, zaehler.neu, zaehler.geaendert,
                zaehler.geloescht, zaehler.abgelehnt, soll.size() - zaehler.abgelehnt);
    }

    /**
     * Liest, was wirklich in der Box steht: Einträge, die die Tabelle kennt, die Box
     * aber nicht mehr, werden neu angelegt; Einträge, die nicht vom ERP stammen,
     * werden aus dem ERP-Telefonbuch gelöscht.
     */
    private void repariereNachIstStand(TelefonZugang zugang, String telefonbuchId,
                                       Map<Schluessel, TelefonbuchZuordnung> ist, Zaehler zaehler) {
        Set<String> inDerBox = anlage.ladeEintragsIds(zugang, telefonbuchId);
        Set<String> bekannt = new HashSet<>();
        for (Iterator<TelefonbuchZuordnung> it = ist.values().iterator(); it.hasNext(); ) {
            TelefonbuchZuordnung z = it.next();
            if (inDerBox.contains(z.getEintragId())) {
                bekannt.add(z.getEintragId());
            } else {
                zuordnungen.delete(z);
                it.remove();
            }
        }
        for (String fremd : inDerBox) {
            if (!bekannt.contains(fremd)) {
                loescheOderUeberspringe(zugang, telefonbuchId, fremd, zaehler);
            }
        }
    }

    /**
     * Löscht einen Eintrag aus der Box. Lehnt die Box das ab (z. B. Fehlercode 820 bei einem
     * Eintrag, den ihr Export noch nennt, den es aber nicht mehr gibt), wird er übersprungen und
     * der Abgleich läuft weiter. Hat die Box in diesem Lauf schon
     * {@link #MAX_ABGELEHNT_OHNE_ERFOLG} Löschungen abgelehnt und keine angenommen, wird es nicht
     * weiter versucht; die übrigen Einträge zählen dann ohne Versuch als nicht gelöscht.
     * Fehlende Rechte oder eine nicht erreichbare Box brechen den Lauf weiter ab.
     *
     * @return {@code true}, wenn der Eintrag gelöscht ist
     */
    private boolean loescheOderUeberspringe(TelefonZugang zugang, String telefonbuchId, String eintragsId,
                                            Zaehler zaehler) {
        if (zaehler.nichtGeloescht >= MAX_ABGELEHNT_OHNE_ERFOLG && zaehler.geloescht == 0) {
            zaehler.nichtGeloescht++;
            return false;
        }
        try {
            anlage.loescheEintrag(zugang, telefonbuchId, eintragsId);
        } catch (TelefonAnlageException e) {
            if (e.getGrund() != TelefonAnlageException.Grund.UNERWARTETE_ANTWORT) {
                throw e;
            }
            zaehler.nichtGeloescht++;
            zaehler.letzteLoeschAblehnung = e.getDetail();
            return false;
        }
        zaehler.geloescht++;
        return true;
    }

    /**
     * Warnt über nicht löschbare Einträge nur, wenn sich Anzahl oder Detail geändert haben –
     * Geister-Einträge der Box blieben sonst jeden Lauf im Log.
     */
    private void meldeNichtGeloeschte(Zaehler zaehler) {
        if (zaehler.nichtGeloescht == 0) {
            letzteLoeschWarnung = null;
            return;
        }
        String detail = zaehler.letzteLoeschAblehnung == null ? "ohne Detail" : zaehler.letzteLoeschAblehnung;
        String warnung = (zaehler.nichtGeloescht == 1 ? "1 Eintrag konnte"
                : zaehler.nichtGeloescht + " Einträge konnte") + " die FRITZ!Box nicht löschen (" + detail + ")";
        if (warnung.equals(letzteLoeschWarnung)) {
            log.debug("Telefonbuch: {}", warnung);
        } else {
            log.warn("Telefonbuch: {}", warnung);
            letzteLoeschWarnung = warnung;
        }
    }

    private void uebertrage(TelefonZugang zugang, String telefonbuchId, Schluessel schluessel,
                            TelefonbuchEintrag eintrag, TelefonbuchZuordnung vorhanden, Zaehler zaehler) {
        String hash = eintrag.inhaltHash();
        if (vorhanden != null && hash.equals(vorhanden.getInhaltHash())) {
            return;
        }
        String eintragsId;
        try {
            eintragsId = anlage.speichereEintrag(zugang, telefonbuchId, eintrag,
                    vorhanden == null ? null : vorhanden.getEintragId());
        } catch (TelefonAnlageException e) {
            // Lehnt die Box nur diesen Eintrag ab, geht es mit den übrigen weiter. Ein schon
            // vorhandener Eintrag behält seinen alten Stand; der nächste Lauf versucht es wieder.
            if (e.getGrund() != TelefonAnlageException.Grund.UNERWARTETE_ANTWORT) {
                throw e;
            }
            zaehler.abgelehnt++;
            zaehler.letzteAblehnung = e.getDetail();
            if (zaehler.abgelehnt >= MAX_ABGELEHNT_OHNE_ERFOLG && zaehler.neu + zaehler.geaendert == 0) {
                throw e; // Box nimmt gar nichts an – nicht noch hunderte Male versuchen
            }
            return;
        }
        TelefonbuchZuordnung z = vorhanden != null ? vorhanden : new TelefonbuchZuordnung();
        z.setQuelleTyp(schluessel.quelle());
        z.setQuelleId(schluessel.id());
        z.setEintragId(eintragsId);
        z.setInhaltHash(hash);
        z.setGesendetAm(LocalDateTime.now(clock));
        zuordnungen.save(z);
        if (vorhanden == null) {
            zaehler.neu++;
        } else {
            zaehler.geaendert++;
        }
    }

    private static final class Zaehler {
        int neu;
        int geaendert;
        int geloescht;
        int abgelehnt;
        /** Detail (Schritt, Fehlercode) der letzten Ablehnung durch die Box oder {@code null}. */
        String letzteAblehnung;
        /** Einträge, die die Box nicht löschen ließ – übersprungen, der Abgleich lief weiter. */
        int nichtGeloescht;
        /** Detail der letzten abgelehnten Löschung oder {@code null}. */
        String letzteLoeschAblehnung;
    }
}
