package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentVerknuepfungSperreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Schlägt zu einer Dokumentenkette die Lieferanten-Dokumente vor, die am
 * wahrscheinlichsten noch dazugehören – egal welcher Art: das Werkstoffzeugnis
 * zum Lieferschein, die AB zum Angebot, die Rechnung zur AB.
 *
 * <p>Für jedes Paar aus Kandidat und Kettendokument bestimmt
 * {@link LieferantDokumentAbgleich#vorgaengerTypen}, wer Vorgänger ist. Passen
 * zwei Arten nicht direkt zusammen (Angebot und Rechnung), gibt es kein Paar.
 * Bewertet wird mit {@link LieferantDokumentAbgleich#schaetzeEin} – denselben
 * Merkmalen wie beim Mail-Import.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KettenVorschlagService {

    /** Diese Arten bilden Ketten; Sonstiges und Kassenbelege gehören nicht dazu. */
    public static final Set<LieferantDokumentTyp> KETTEN_TYPEN = EnumSet.of(
            LieferantDokumentTyp.ANGEBOT, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG,
            LieferantDokumentTyp.LIEFERSCHEIN, LieferantDokumentTyp.WERKSTOFFZEUGNIS,
            LieferantDokumentTyp.RECHNUNG, LieferantDokumentTyp.GUTSCHRIFT);

    private final LieferantDokumentAbgleich abgleich;
    private final LieferantDokumentRepository dokumentRepository;
    private final LieferantDokumentVerknuepfungSperreRepository sperreRepository;

    /** Gespeichert wird immer Nachfolger → Vorgänger (Rechnung → Lieferschein). */
    public record Paar(LieferantDokument nachfolger, LieferantDokument vorgaenger) {
    }

    /**
     * @param dokument       das vorgeschlagene Dokument
     * @param kettenDokument das Dokument der Kette, an das es am besten passt
     */
    public record Vorschlag(LieferantDokument dokument, LieferantDokument kettenDokument,
            LieferantDokumentAbgleich.Einschaetzung einschaetzung) {

        public int trefferquote() {
            return einschaetzung.trefferquote();
        }
    }

    public LieferantDokumentAbgleich.Merkmalspeicher neuerSpeicher() {
        return abgleich.neuerSpeicher();
    }

    /**
     * Wer von beiden ist Vorgänger? Bei gleicher Art (Angebots-Fassungen) das ältere.
     *
     * @return leer, wenn die Arten nicht direkt zusammenpassen
     */
    public static Optional<Paar> paar(LieferantDokument a, LieferantDokument b) {
        if (a == null || b == null || a.getTyp() == null || b.getTyp() == null) {
            return Optional.empty();
        }
        boolean aFolgtB = LieferantDokumentAbgleich.vorgaengerTypen(a.getTyp()).contains(b.getTyp());
        boolean bFolgtA = LieferantDokumentAbgleich.vorgaengerTypen(b.getTyp()).contains(a.getTyp());
        if (aFolgtB && bFolgtA) {
            return Optional.of(istAelter(b, a) ? new Paar(a, b) : new Paar(b, a));
        }
        if (aFolgtB) {
            return Optional.of(new Paar(a, b));
        }
        return bFolgtA ? Optional.of(new Paar(b, a)) : Optional.empty();
    }

    /**
     * Bewertet die Kandidaten gegen die Dokumente einer Kette, beste zuerst (bei
     * gleicher Quote die zeitlich nächste). Je Kandidat zählt das Kettendokument, zu
     * dem er am besten passt. Kandidaten, die zu keinem Kettendokument passen, fehlen.
     *
     * @param umfeld Belege des Lieferanten, an denen sich zeigt, ob eine gemeinsame
     *               Nummer trennscharf ist; {@code null} = Kandidaten + Kette
     */
    public List<Vorschlag> bewerte(Collection<LieferantDokument> kette, Collection<LieferantDokument> kandidaten,
            Collection<LieferantDokument> umfeld, LieferantDokumentAbgleich.Merkmalspeicher speicher) {
        List<LieferantDokument> kettenDokumente = kette.stream()
                .filter(d -> d != null && d.getGeschaeftsdaten() != null)
                .toList();
        if (kettenDokumente.isEmpty()) {
            return List.of();
        }
        Collection<LieferantDokument> vergleich = umfeld;
        if (vergleich == null) {
            List<LieferantDokument> beide = new ArrayList<>(kandidaten);
            beide.addAll(kettenDokumente);
            vergleich = beide;
        }
        LieferantDokumentAbgleich.Streuung streuung = abgleich.streuung(vergleich, speicher);
        // Über das ganze Umfeld zählen, nicht über die (gefilterten) Kandidaten – sonst
        // änderte ein Filter-Chip die Quote desselben Dokuments. Die eigene Kette zählt
        // nicht mit: AB, Lieferschein und Rechnung mit derselben Bestellnummer machen sie
        // nicht zur Kundennummer.
        Set<LieferantDokument> eigene = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        eigene.addAll(kettenDokumente);
        Map<String, Integer> jeBestellnummer = zaehleBestellnummern(
                vergleich.stream().filter(d -> !eigene.contains(d)).toList());

        List<Vorschlag> vorschlaege = new ArrayList<>();
        for (LieferantDokument kandidat : kandidaten) {
            if (kandidat == null || kandidat.getGeschaeftsdaten() == null || !KETTEN_TYPEN.contains(kandidat.getTyp())) {
                continue;
            }
            Vorschlag bester = null;
            for (LieferantDokument kettenDokument : kettenDokumente) {
                Optional<Paar> paar = paar(kandidat, kettenDokument);
                if (paar.isEmpty()) {
                    continue;
                }
                var einschaetzung = abgleich.schaetzeEin(paar.get().nachfolger(), paar.get().vorgaenger(),
                        trennscharf(kettenDokument, jeBestellnummer), streuung, speicher);
                if (bester == null || einschaetzung.trefferquote() > bester.trefferquote()) {
                    bester = new Vorschlag(kandidat, kettenDokument, einschaetzung);
                }
            }
            if (bester != null) {
                vorschlaege.add(bester);
            }
        }
        vorschlaege.sort(Comparator.comparingInt(Vorschlag::trefferquote).reversed()
                .thenComparing(KettenVorschlagService::tageAbstand, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(v -> v.dokument().getId(), Comparator.nullsLast(Comparator.naturalOrder())));
        return vorschlaege;
    }

    /**
     * Hängt ein Dokument an ein Dokument der Kette – in der Richtung, die die
     * Dokumentarten vorgeben. Auch ausgeblendete Dokumente und andere Lieferanten
     * sind erlaubt (die KI erkennt den Lieferanten nicht immer richtig). Eine
     * frühere Sperre des Paars (von Hand gelöst) ist damit aufgehoben.
     *
     * @param benutzerId wer zuordnet – nur fürs Protokoll, darf fehlen
     * @throws java.util.NoSuchElementException wenn eines der Dokumente fehlt
     * @throws BelegAbgelehntException          wenn die Arten nicht zusammenpassen
     */
    @Transactional
    public void verknuepfe(Long kettenDokumentId, Long dokumentId, Long benutzerId) {
        if (kettenDokumentId == null || dokumentId == null || kettenDokumentId.equals(dokumentId)) {
            throw new BelegAbgelehntException("Bitte zwei verschiedene Dokumente wählen.");
        }
        LieferantDokument kettenDokument = dokumentRepository.findById(kettenDokumentId).orElseThrow();
        LieferantDokument dokument = dokumentRepository.findById(dokumentId).orElseThrow();
        if (!KETTEN_TYPEN.contains(kettenDokument.getTyp()) || !KETTEN_TYPEN.contains(dokument.getTyp())) {
            throw new BelegAbgelehntException("Sonstige Dokumente gehören in keine Bestellung.");
        }
        Paar paar = paar(dokument, kettenDokument).orElseThrow(() -> new BelegAbgelehntException(
                bezeichnung(dokument.getTyp()) + " und " + bezeichnung(kettenDokument.getTyp())
                        + " lassen sich nicht direkt verbinden."));
        LieferantDokument nachfolger = paar.nachfolger();
        LieferantDokument vorgaenger = paar.vorgaenger();
        // Schon verbunden – in welcher Richtung auch immer (bei Angebots-Fassungen kann
        // ein geändertes Datum die Richtung drehen; ein Kreis A↔B wäre falsch).
        if (nachfolger.getVerknuepfteDokumente().contains(vorgaenger)
                || vorgaenger.getVerknuepfteDokumente().contains(nachfolger)) {
            return;
        }
        sperreRepository.loeschePaar(nachfolger.getId(), vorgaenger.getId());
        nachfolger.getVerknuepfteDokumente().add(vorgaenger);
        vorgaenger.getVerknuepftVon().add(nachfolger);
        dokumentRepository.save(nachfolger);
        // Nur IDs, keine Namen oder Beträge (DSGVO)
        log.info("[Kette] Dokument {} an Dokument {} gehängt (Benutzer {})",
                nachfolger.getId(), vorgaenger.getId(), benutzerId);
    }

    /**
     * Zu welchem Dokument einer anderen Kette ein Kandidat schon gehört – für den
     * Hinweis im Suchfenster, z. B. „Lieferschein LS-4711“. Nur Partner, die der
     * Aufrufer sehen darf – sonst verrieten Art und Nummer eine Rechnung, für die er
     * keine Rechte hat.
     *
     * @param sichtbareTypen Dokumenttypen, die der Aufrufer sehen darf
     * @return {@code null}, wenn er an keinem sichtbaren Dokument hängt
     */
    public static String gehoertSchonZu(LieferantDokument dokument, Set<LieferantDokumentTyp> sichtbareTypen) {
        if (dokument == null || sichtbareTypen == null) {
            return null;
        }
        Comparator<LieferantDokument> nachId = Comparator.comparing(LieferantDokument::getId,
                Comparator.nullsLast(Comparator.naturalOrder()));
        Optional<LieferantDokument> verbunden = dokument.getVerknuepfteDokumente().stream()
                .filter(d -> sichtbareTypen.contains(d.getTyp())).min(nachId)
                .or(() -> dokument.getVerknuepftVon().stream()
                        .filter(d -> sichtbareTypen.contains(d.getTyp())).min(nachId));
        return verbunden.map(KettenVorschlagService::bezeichnung).orElse(null);
    }

    private static String bezeichnung(LieferantDokument d) {
        String nummer = d.getGeschaeftsdaten() != null ? d.getGeschaeftsdaten().getDokumentNummer() : null;
        String art = bezeichnung(d.getTyp());
        return nummer != null && !nummer.isBlank() ? art + " " + nummer.trim() : art;
    }

    static String bezeichnung(LieferantDokumentTyp typ) {
        if (typ == null) {
            return "Dokument";
        }
        return switch (typ) {
            case ANGEBOT -> "Angebot";
            case AUFTRAGSBESTAETIGUNG -> "Auftragsbestätigung";
            case LIEFERSCHEIN -> "Lieferschein";
            case WERKSTOFFZEUGNIS -> "Werkstoffzeugnis";
            case RECHNUNG -> "Rechnung";
            case GUTSCHRIFT -> "Gutschrift";
            default -> "Dokument";
        };
    }

    /** Älter = früheres Belegdatum, bei gleichem oder fehlendem Datum die kleinere ID. */
    private static boolean istAelter(LieferantDokument a, LieferantDokument b) {
        LocalDate da = datum(a);
        LocalDate db = datum(b);
        if (da != null && db != null && !da.isEqual(db)) {
            return da.isBefore(db);
        }
        return a.getId() != null && b.getId() != null && a.getId() < b.getId();
    }

    /** Tage zwischen Kandidat und Kettendokument – kleiner ist näher. */
    private static Long tageAbstand(Vorschlag v) {
        LocalDate a = datum(v.dokument());
        LocalDate b = datum(v.kettenDokument());
        return a == null || b == null ? null : Math.abs(ChronoUnit.DAYS.between(b, a));
    }

    /**
     * Teilen sich zu viele Kandidaten dieselbe Bestellnummer, hat die KI meist die
     * Kundennummer gelesen – dann zählt sie nicht als sicher (wie beim Import).
     */
    private static boolean trennscharf(LieferantDokument kettenDokument, Map<String, Integer> jeBestellnummer) {
        String nummer = LieferantDokumentAbgleich.normalisiereNummer(kettenDokument.getGeschaeftsdaten().getBestellnummer());
        if (nummer == null) {
            return false;
        }
        int anzahl = jeBestellnummer.getOrDefault(nummer, 0);
        return anzahl > 0 && anzahl <= LieferantDokumentAbgleich.MAX_DOKUMENTE_JE_BESTELLNUMMER;
    }

    private static Map<String, Integer> zaehleBestellnummern(Collection<LieferantDokument> dokumente) {
        Map<String, Integer> anzahl = new HashMap<>();
        for (LieferantDokument d : dokumente) {
            if (d == null || d.getGeschaeftsdaten() == null) {
                continue;
            }
            String nummer = LieferantDokumentAbgleich.normalisiereNummer(d.getGeschaeftsdaten().getBestellnummer());
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
