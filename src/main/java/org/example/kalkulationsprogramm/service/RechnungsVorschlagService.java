package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantDokumentVerknuepfungSperre;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentVerknuepfungSperreRepository;
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
 *
 * <p>Außerdem: Rechnungen von Hand anhängen und Dokumente abhängen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechnungsVorschlagService {

    /** Darunter ist ein Vorschlag nur geraten und erscheint nicht auf der Karte. */
    public static final int MIN_QUOTE_KARTE = 40;

    private final LieferantDokumentAbgleich abgleich;
    private final LieferantDokumentRepository dokumentRepository;
    private final LieferantDokumentVerknuepfungSperreRepository sperreRepository;

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

    /** Wie {@link #bewerte(Collection, Collection, Collection, LieferantDokumentAbgleich.Merkmalspeicher)}; Umfeld = Rechnungen + Bestelldokumente. */
    public List<Vorschlag> bewerte(Collection<LieferantDokument> bestellDokumente,
            Collection<LieferantDokument> rechnungen, LieferantDokumentAbgleich.Merkmalspeicher speicher) {
        return bewerte(bestellDokumente, rechnungen, null, speicher);
    }

    /**
     * Bewertet alle Rechnungen gegen die Dokumente einer Bestellung, beste zuerst
     * (bei gleicher Quote die zeitlich nächste). Je Rechnung zählt das
     * Bestelldokument, zu dem sie am besten passt.
     *
     * @param umfeld Belege des Lieferanten, an denen sich zeigt, ob eine gemeinsame
     *               Nummer trennscharf ist (Kundennummer über Monate). Dieselbe
     *               Sammlung für viele Aufrufe wiederverwenden – sie wird im
     *               Speicher gemerkt. {@code null} = Rechnungen + Bestelldokumente.
     */
    public List<Vorschlag> bewerte(Collection<LieferantDokument> bestellDokumente,
            Collection<LieferantDokument> rechnungen, Collection<LieferantDokument> umfeld,
            LieferantDokumentAbgleich.Merkmalspeicher speicher) {
        List<LieferantDokument> bestellungen = bestellDokumente.stream()
                .filter(RechnungsVorschlagService::istBestellDokument)
                .filter(d -> d.getGeschaeftsdaten() != null)
                .toList();
        if (bestellungen.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> rechnungenJeBestellnummer = zaehleBestellnummern(rechnungen);
        Collection<LieferantDokument> vergleich = umfeld;
        if (vergleich == null) {
            List<LieferantDokument> beide = new ArrayList<>(rechnungen);
            beide.addAll(bestellungen);
            vergleich = beide;
        }
        LieferantDokumentAbgleich.Streuung streuung = abgleich.streuung(vergleich, speicher);

        List<Vorschlag> vorschlaege = new ArrayList<>();
        for (LieferantDokument rechnung : rechnungen) {
            if (rechnung == null || rechnung.getTyp() != LieferantDokumentTyp.RECHNUNG
                    || rechnung.getGeschaeftsdaten() == null) {
                continue;
            }
            Vorschlag bester = null;
            for (LieferantDokument bestellung : bestellungen) {
                var einschaetzung = abgleich.schaetzeEin(rechnung, bestellung,
                        trennscharf(bestellung, rechnungenJeBestellnummer), streuung, speicher);
                if (bester == null || einschaetzung.trefferquote() > bester.trefferquote()) {
                    bester = new Vorschlag(rechnung, bestellung, einschaetzung);
                }
            }
            if (bester != null) {
                vorschlaege.add(bester);
            }
        }
        vorschlaege.sort(Comparator.comparingInt(Vorschlag::trefferquote).reversed()
                .thenComparing(RechnungsVorschlagService::tageAbstand, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(v -> v.rechnung().getId(), Comparator.nullsLast(Comparator.naturalOrder())));
        return vorschlaege;
    }

    /** Wie {@link #besterVorschlag(Collection, Collection, Collection, LieferantDokumentAbgleich.Merkmalspeicher)} ohne eigenes Umfeld. */
    public Optional<BesterVorschlag> besterVorschlag(Collection<LieferantDokument> bestellDokumente,
            Collection<LieferantDokument> rechnungen, LieferantDokumentAbgleich.Merkmalspeicher speicher) {
        return besterVorschlag(bestellDokumente, rechnungen, null, speicher);
    }

    /**
     * Der beste Vorschlag für die Karte – nur ab {@link #MIN_QUOTE_KARTE}.
     */
    public Optional<BesterVorschlag> besterVorschlag(Collection<LieferantDokument> bestellDokumente,
            Collection<LieferantDokument> rechnungen, Collection<LieferantDokument> umfeld,
            LieferantDokumentAbgleich.Merkmalspeicher speicher) {
        List<Vorschlag> alle = bewerte(bestellDokumente, rechnungen, umfeld, speicher);
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
     * <p>Eine Rechnung darf an mehreren Bestelldokumenten hängen (Teillieferungen:
     * eine Rechnung, mehrere Lieferscheine) und ein Bestelldokument an mehreren
     * Rechnungen (Teilrechnungen). Auch ausgeblendete Dokumente lassen sich
     * zuordnen – bezahlte Rechnungen sind meist ausgeblendet.
     *
     * <p>Ein anderer Lieferant ist bewusst erlaubt: Die KI erkennt den Lieferanten
     * nicht immer richtig, und genau solche Rechnungen bleiben sonst liegen.
     *
     * <p>Wurde das Paar früher von Hand gelöst, ist die Sperre damit aufgehoben.
     *
     * @param benutzerId wer zuordnet – nur fürs Protokoll, darf fehlen
     * @throws java.util.NoSuchElementException wenn eines der Dokumente fehlt
     * @throws IllegalArgumentException         bei falschen Dokumenttypen
     */
    @Transactional
    public void verknuepfe(Long bestellDokumentId, Long rechnungId, Long benutzerId) {
        if (bestellDokumentId == null || rechnungId == null || bestellDokumentId.equals(rechnungId)) {
            throw new BelegAbgelehntException("Bestellung und Rechnung müssen verschiedene Dokumente sein.");
        }
        LieferantDokument bestellung = dokumentRepository.findById(bestellDokumentId).orElseThrow();
        LieferantDokument rechnung = dokumentRepository.findById(rechnungId).orElseThrow();
        if (!istBestellDokument(bestellung)) {
            throw new BelegAbgelehntException("Nur eine Auftragsbestätigung oder ein Lieferschein kann eine Rechnung bekommen.");
        }
        if (rechnung.getTyp() != LieferantDokumentTyp.RECHNUNG) {
            throw new BelegAbgelehntException("Das gewählte Dokument ist keine Rechnung.");
        }
        sperreRepository.loeschePaar(rechnungId, bestellDokumentId);
        rechnung.getVerknuepfteDokumente().add(bestellung);
        bestellung.getVerknuepftVon().add(rechnung);
        dokumentRepository.save(rechnung);
        // Nur IDs, keine Namen oder Beträge (DSGVO); reicht zur Nachvollziehbarkeit.
        log.info("[Bestellübersicht] Rechnung {} an Bestelldokument {} gehängt (Benutzer {})",
                rechnungId, bestellDokumentId, benutzerId);
    }

    /**
     * Löst alle Verknüpfungen eines Dokuments (zu Vorgängern und Nachfolgern) und
     * sperrt jedes gelöste Paar, damit der automatische Abgleich es nicht wieder
     * verknüpft.
     *
     * @param benutzerId wer abhängt – nur fürs Protokoll, darf fehlen
     * @return Anzahl gelöster Verknüpfungen
     * @throws java.util.NoSuchElementException wenn das Dokument fehlt
     */
    @Transactional
    public int haengeAb(Long dokumentId, Long benutzerId) {
        if (dokumentId == null) {
            throw new BelegAbgelehntException("Dokument fehlt.");
        }
        LieferantDokument dokument = dokumentRepository.findById(dokumentId).orElseThrow();
        LocalDateTime jetzt = LocalDateTime.now();
        List<LieferantDokumentVerknuepfungSperre> sperren = new ArrayList<>();

        for (LieferantDokument vorgaenger : new ArrayList<>(dokument.getVerknuepfteDokumente())) {
            dokument.getVerknuepfteDokumente().remove(vorgaenger);
            vorgaenger.getVerknuepftVon().remove(dokument);
            sperren.add(new LieferantDokumentVerknuepfungSperre(dokument.getId(), vorgaenger.getId(), jetzt));
        }
        for (LieferantDokument nachfolger : new ArrayList<>(dokument.getVerknuepftVon())) {
            // Die Verknüpfung gehört dem Nachfolger – nur dort entfernen wirkt in der Datenbank.
            nachfolger.getVerknuepfteDokumente().remove(dokument);
            dokument.getVerknuepftVon().remove(nachfolger);
            dokumentRepository.save(nachfolger);
            sperren.add(new LieferantDokumentVerknuepfungSperre(nachfolger.getId(), dokument.getId(), jetzt));
        }
        dokumentRepository.save(dokument);
        sperreRepository.saveAll(sperren);
        // Nur IDs (DSGVO)
        log.info("[Bestellübersicht] Dokument {} abgehängt: {} Verknüpfungen gelöst (Benutzer {})",
                dokumentId, sperren.size(), benutzerId);
        return sperren.size();
    }

    /**
     * Zu welchem Bestelldokument eine Rechnung schon gehört – für den Hinweis im
     * Fenster „Rechnung suchen“, z. B. „Lieferschein LS-4711“.
     *
     * @return {@code null}, wenn sie an keiner AB und keinem Lieferschein hängt
     */
    public static String gehoertSchonZu(LieferantDokument rechnung) {
        if (rechnung == null) {
            return null;
        }
        return rechnung.getVerknuepfteDokumente().stream()
                .filter(RechnungsVorschlagService::istBestellDokument)
                .min(Comparator.comparing(LieferantDokument::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(RechnungsVorschlagService::bezeichnung)
                .orElse(null);
    }

    private static String bezeichnung(LieferantDokument d) {
        String art = d.getTyp() == LieferantDokumentTyp.LIEFERSCHEIN ? "Lieferschein" : "Auftragsbestätigung";
        String nummer = d.getGeschaeftsdaten() != null ? d.getGeschaeftsdaten().getDokumentNummer() : null;
        return nummer != null && !nummer.isBlank() ? art + " " + nummer.trim() : art;
    }

    /** Tage zwischen Rechnung und Bestelldokument – kleiner ist näher. */
    private static Long tageAbstand(Vorschlag v) {
        LocalDate a = datum(v.rechnung());
        LocalDate b = datum(v.bestellDokument());
        return a == null || b == null ? null : Math.abs(ChronoUnit.DAYS.between(b, a));
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
