package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Schlägt zu einer laufenden Bestellung (AB oder Lieferschein ohne Rechnung) die
 * Rechnungen vor, die am wahrscheinlichsten dazugehören.
 *
 * <p>Bewertet wird mit {@link LieferantDokumentAbgleich#schaetzeEin}, also mit
 * denselben Merkmalen wie beim Mail-Import. Sichere Paare verknüpft der Import
 * bereits selbst; hier landen die Fälle, die er bewusst offen gelassen hat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechnungsVorschlagService {

    /** Darunter ist ein Vorschlag nur geraten und erscheint nicht auf der Karte. */
    public static final int MIN_QUOTE_KARTE = 40;

    /** Fehlermeldung, wenn die Rechnung schon an einer AB oder einem Lieferschein hängt. */
    public static final String SCHON_ZUGEORDNET = "Rechnung gehört schon zu einer anderen Bestellung.";

    private final LieferantDokumentAbgleich abgleich;
    private final LieferantDokumentRepository dokumentRepository;

    /**
     * @param rechnung              die vorgeschlagene Rechnung
     * @param bestellDokument       das Dokument der Bestellung, zu dem sie am besten passt
     * @param einschaetzung         Quote und Gründe
     */
    public record Vorschlag(LieferantDokument rechnung, LieferantDokument bestellDokument,
            LieferantDokumentAbgleich.Einschaetzung einschaetzung) {

        public int trefferquote() {
            return einschaetzung.trefferquote();
        }
    }

    /**
     * @param eindeutig {@code false}, wenn eine zweite Rechnung genauso gut passt
     */
    public record BesterVorschlag(Vorschlag vorschlag, boolean eindeutig) {
    }

    /** Neuer Merkmal-Zwischenspeicher – für viele Aufrufe hintereinander einmal anlegen. */
    public LieferantDokumentAbgleich.Merkmalspeicher neuerSpeicher() {
        return abgleich.neuerSpeicher();
    }

    /**
     * Bewertet alle Rechnungen gegen die Dokumente einer Bestellung, beste zuerst.
     * Je Rechnung zählt das Bestelldokument, zu dem sie am besten passt.
     */
    public List<Vorschlag> bewerte(Collection<LieferantDokument> bestellDokumente,
            Collection<LieferantDokument> rechnungen, LieferantDokumentAbgleich.Merkmalspeicher speicher) {
        List<LieferantDokument> bestellungen = bestellDokumente.stream()
                .filter(RechnungsVorschlagService::istBestellDokument)
                .filter(d -> d.getGeschaeftsdaten() != null)
                .toList();
        if (bestellungen.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> rechnungenJeBestellnummer = zaehleBestellnummern(rechnungen);

        List<Vorschlag> vorschlaege = new ArrayList<>();
        for (LieferantDokument rechnung : rechnungen) {
            if (rechnung == null || rechnung.getTyp() != LieferantDokumentTyp.RECHNUNG
                    || rechnung.getGeschaeftsdaten() == null) {
                continue;
            }
            Vorschlag bester = null;
            for (LieferantDokument bestellung : bestellungen) {
                var einschaetzung = abgleich.schaetzeEin(rechnung, bestellung,
                        trennscharf(bestellung, rechnungenJeBestellnummer), speicher);
                if (bester == null || einschaetzung.trefferquote() > bester.trefferquote()) {
                    bester = new Vorschlag(rechnung, bestellung, einschaetzung);
                }
            }
            if (bester != null) {
                vorschlaege.add(bester);
            }
        }
        vorschlaege.sort(Comparator.comparingInt(Vorschlag::trefferquote).reversed()
                .thenComparing(v -> datum(v.rechnung()), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(v -> v.rechnung().getId(), Comparator.nullsLast(Comparator.naturalOrder())));
        return vorschlaege;
    }

    /**
     * Der beste Vorschlag für die Karte – nur ab {@link #MIN_QUOTE_KARTE}.
     */
    public Optional<BesterVorschlag> besterVorschlag(Collection<LieferantDokument> bestellDokumente,
            Collection<LieferantDokument> rechnungen, LieferantDokumentAbgleich.Merkmalspeicher speicher) {
        List<Vorschlag> alle = bewerte(bestellDokumente, rechnungen, speicher);
        if (alle.isEmpty() || alle.get(0).trefferquote() < MIN_QUOTE_KARTE) {
            return Optional.empty();
        }
        boolean eindeutig = alle.size() < 2 || alle.get(1).trefferquote() < alle.get(0).trefferquote();
        return Optional.of(new BesterVorschlag(alle.get(0), eindeutig));
    }

    /**
     * Hängt die Rechnung an das Bestelldokument – in derselben Richtung wie der
     * Import (Nachfolger → Vorgänger).
     *
     * <p>Ein anderer Lieferant ist bewusst erlaubt: Die KI erkennt den Lieferanten
     * nicht immer richtig, und genau solche Rechnungen bleiben sonst liegen.
     *
     * @param benutzerId wer zuordnet – nur fürs Protokoll, darf fehlen
     * @throws java.util.NoSuchElementException wenn eines der Dokumente fehlt
     * @throws IllegalArgumentException         bei falschen Dokumenttypen, ausgeblendeten
     *                                          Dokumenten oder einer schon zugeordneten Rechnung
     */
    @Transactional
    public void verknuepfe(Long bestellDokumentId, Long rechnungId, Long benutzerId) {
        if (bestellDokumentId == null || rechnungId == null || bestellDokumentId.equals(rechnungId)) {
            throw new IllegalArgumentException("Bestellung und Rechnung müssen verschiedene Dokumente sein.");
        }
        LieferantDokument bestellung = dokumentRepository.findById(bestellDokumentId).orElseThrow();
        LieferantDokument rechnung = dokumentRepository.findById(rechnungId).orElseThrow();
        if (!istBestellDokument(bestellung)) {
            throw new IllegalArgumentException("Nur eine Auftragsbestätigung oder ein Lieferschein kann eine Rechnung bekommen.");
        }
        if (rechnung.getTyp() != LieferantDokumentTyp.RECHNUNG) {
            throw new IllegalArgumentException("Das gewählte Dokument ist keine Rechnung.");
        }
        if (bestellung.isAusgeblendet() || rechnung.isAusgeblendet()) {
            throw new IllegalArgumentException("Ausgeblendete Dokumente lassen sich nicht zuordnen.");
        }
        if (haengtAnBestellung(rechnung, bestellung)) {
            // Eine zweite Verknüpfung verschmölze zwei Bestellungen dauerhaft zu einer Kette.
            throw new IllegalArgumentException(SCHON_ZUGEORDNET);
        }
        rechnung.getVerknuepfteDokumente().add(bestellung);
        bestellung.getVerknuepftVon().add(rechnung);
        dokumentRepository.save(rechnung);
        // Nur IDs, keine Namen oder Beträge (DSGVO); reicht zur Nachvollziehbarkeit.
        log.info("[Bestellübersicht] Rechnung {} an Bestelldokument {} gehängt (Benutzer {})",
                rechnungId, bestellDokumentId, benutzerId);
    }

    /**
     * Hängt in der Kette der Rechnung (beide Richtungen, auch über ausgeblendete
     * Dokumente) schon eine AB oder ein Lieferschein?
     */
    public static boolean haengtAnBestellung(LieferantDokument rechnung) {
        return haengtAnBestellung(rechnung, null);
    }

    /** Wie {@link #haengtAnBestellung(LieferantDokument)}, das Ziel selbst zählt nicht. */
    private static boolean haengtAnBestellung(LieferantDokument rechnung, LieferantDokument ziel) {
        Set<LieferantDokument> besucht = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<LieferantDokument> offen = new ArrayDeque<>();
        offen.add(rechnung);
        while (!offen.isEmpty()) {
            LieferantDokument aktuell = offen.poll();
            // Das Ziel und seine Kette zählen nicht – erneutes Verknüpfen bleibt harmlos
            if (aktuell == ziel || !besucht.add(aktuell)) {
                continue;
            }
            if (istBestellDokument(aktuell)) {
                return true;
            }
            offen.addAll(aktuell.getVerknuepfteDokumente());
            offen.addAll(aktuell.getVerknuepftVon());
        }
        return false;
    }

    public static boolean istBestellDokument(LieferantDokument d) {
        return d != null && (d.getTyp() == LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG
                || d.getTyp() == LieferantDokumentTyp.LIEFERSCHEIN);
    }

    /**
     * Ähnlich wie beim Import: Teilen sich zu viele der übergebenen Rechnungen dieselbe
     * Bestellnummer, hat die KI meist die Kundennummer gelesen – dann zählt sie nicht als
     * sicher. Der Import zählt die Vorgänger desselben Lieferanten; hier zählen die
     * übergebenen Kandidaten (für die Karte: nur derselbe Lieferant).
     */
    private static boolean trennscharf(LieferantDokument bestellung, Map<String, Integer> rechnungenJeBestellnummer) {
        String nummer = LieferantDokumentAbgleich.normalisiereNummer(bestellung.getGeschaeftsdaten().getBestellnummer());
        if (nummer == null) {
            return false;
        }
        int anzahl = rechnungenJeBestellnummer.getOrDefault(nummer, 0);
        return anzahl > 0 && anzahl <= LieferantDokumentAbgleich.MAX_DOKUMENTE_JE_BESTELLNUMMER;
    }

    private static Map<String, Integer> zaehleBestellnummern(Collection<LieferantDokument> rechnungen) {
        Map<String, Integer> anzahl = new HashMap<>();
        for (LieferantDokument r : rechnungen) {
            if (r == null || r.getGeschaeftsdaten() == null) {
                continue;
            }
            String nummer = LieferantDokumentAbgleich.normalisiereNummer(r.getGeschaeftsdaten().getBestellnummer());
            if (nummer != null) {
                anzahl.merge(nummer, 1, Integer::sum);
            }
        }
        return anzahl;
    }

    private static LocalDate datum(LieferantDokument d) {
        return d.getGeschaeftsdaten() != null ? d.getGeschaeftsdaten().getDokumentDatum() : null;
    }
}
