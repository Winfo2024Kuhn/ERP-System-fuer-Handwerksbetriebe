package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungsUebersichtDto;
import org.example.kalkulationsprogramm.dto.Bestellung.DokumentRef;
import org.example.kalkulationsprogramm.dto.Bestellung.DokumentenKette;
import org.example.kalkulationsprogramm.dto.Bestellung.RechnungsVorschlagDto;
import org.example.kalkulationsprogramm.dto.Bestellung.Verbindung;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Baut die Bestellübersicht aus den Lieferanten-Dokumenten und sucht passende
 * Rechnungen zu laufenden Bestellungen.
 *
 * <p>Gruppiert die Dokumenten-Ketten nach Status:
 * <ul>
 *   <li>Offene Anfragen (nur Anfrage, keine Folgedokumente)</li>
 *   <li>Laufende Bestellungen (AB oder Lieferschein vorhanden, keine Rechnung)</li>
 *   <li>Abgeschlossen (Rechnung vorhanden, noch nicht zugeordnet)</li>
 *   <li>Zugeordnet (Rechnung vorhanden und Projekten zugeordnet)</li>
 *   <li>Ausgeblendet (erledigt: bezahlte/ausgeblendete Rechnung oder alles ausgeblendet)</li>
 * </ul>
 *
 * <p>Ketten entstehen aus ALLEN Dokumenten, auch ausgeblendeten: Bezahlte
 * Rechnungen sind fast immer ausgeblendet – ohne sie stünde ihr Lieferschein
 * dauerhaft unter „Rechnung fehlt“.
 *
 * <p>Ohne eigene Transaktion: Die Verknüpfungen ({@code verknuepfteDokumente},
 * {@code verknuepftVon}) laden lazy nach und brauchen den offenen EntityManager
 * des Web-Requests ({@code OpenEntityManagerInViewConfig}). Wer den Service
 * außerhalb eines Requests aufruft (Scheduler, Hintergrundjob), braucht dafür
 * eine eigene Transaktion.
 */
@Service
@RequiredArgsConstructor
public class BestellungsUebersichtService {

    /** Kartenvorschlag: nur Rechnungen von so vielen Tagen vor … */
    static final int KARTE_TAGE_VORHER = 30;
    /** … bis so vielen Tagen nach einem Bestelldokument. */
    static final int KARTE_TAGE_NACHHER = 180;
    /** Das Fenster „Rechnung suchen“ zeigt höchstens so viele Rechnungen (beste zuerst). */
    static final int MAX_VORSCHLAEGE = 200;

    private final LieferantDokumentRepository dokumentRepository;
    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    private final LieferantDokumentProjektAnteilRepository projektAnteilRepository;
    private final RechnungsVorschlagService rechnungsVorschlagService;

    /**
     * Alle Dokumenten-Ketten gruppiert nach Status, neueste zuerst. Jede laufende
     * Bestellung bekommt die wahrscheinlichste Rechnung als Vorschlag.
     *
     * @param sichtbareTypen Dokumenttypen, die der Aufrufer laut Abteilungsrechten sehen
     *                       darf; andere Dokumente tauchen weder in Ketten noch in
     *                       Vorschlägen auf
     */
    public BestellungsUebersichtDto ladeUebersicht(Set<LieferantDokumentTyp> sichtbareTypen) {
        Dokumentbestand bestand = ladeDokumente(sichtbareTypen);
        BestellungsUebersichtDto dto = gruppiereKetten(bestand);
        var speicher = rechnungsVorschlagService.neuerSpeicher();
        List<DokumentenKette> laufendMitVorschlag = dto.laufendeBestellungen().stream()
                .map(kette -> kette.mitVorschlag(besterVorschlag(kette, bestand, speicher)))
                .toList();
        return new BestellungsUebersichtDto(
                dto.offeneAnfragen(), laufendMitVorschlag, dto.abgeschlossen(), dto.zugeordnet(), dto.ausgeblendet());
    }

    /**
     * Rechnungen, bewertet gegen die Dokumente einer Bestellung – beste zuerst,
     * bei gleicher Quote die zeitlich nächste. Für das Fenster „Rechnung suchen“.
     *
     * <p>Kandidaten sind alle Rechnungen desselben Lieferanten, auch ausgeblendete,
     * bezahlte und schon anderswo verknüpfte (Teillieferungen, Teilrechnungen).
     * Rechnungen, die schon in dieser Kette hängen, fehlen.
     *
     * @param dokumentIds     die Dokumente der Bestellungs-Kette (AB, Lieferschein, …)
     * @param alleLieferanten auch Rechnungen anderer Lieferanten (KI hat den
     *                        Lieferanten falsch erkannt)
     * @param sichtbareTypen  Dokumenttypen, die der Aufrufer sehen darf (andere gelten als nicht vorhanden)
     * @return leer, wenn keine der IDs zu einer sichtbaren AB oder einem Lieferschein gehört
     */
    public Optional<List<RechnungsVorschlagDto>> rechnungsVorschlaege(List<Long> dokumentIds,
            boolean alleLieferanten, Set<LieferantDokumentTyp> sichtbareTypen) {
        Dokumentbestand bestand = ladeDokumente(sichtbareTypen);
        List<LieferantDokument> bestellDokumente = dokumentIds.stream()
                .map(bestand.nachId()::get)
                .filter(Objects::nonNull)
                .filter(RechnungsVorschlagService::istBestellDokument)
                .toList();
        if (bestellDokumente.isEmpty()) {
            return Optional.empty();
        }
        Set<Long> kettenIds = new HashSet<>();
        bestellDokumente.forEach(d -> collectKettenIds(d, kettenIds));
        Long lieferantId = bestellDokumente.stream()
                .map(d -> d.getLieferant() != null ? d.getLieferant().getId() : null)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        List<LieferantDokument> kandidaten = (alleLieferanten ? bestand.rechnungen() : bestand.rechnungenVon(lieferantId))
                .stream()
                .filter(r -> !kettenIds.contains(r.getId()))
                .toList();
        var vorschlaege = rechnungsVorschlagService.bewerte(bestellDokumente, kandidaten,
                lieferantId != null ? bestand.dokumenteVon(lieferantId) : null, rechnungsVorschlagService.neuerSpeicher());
        int anzahl = Math.min(vorschlaege.size(), MAX_VORSCHLAEGE);
        List<RechnungsVorschlagDto> liste = new ArrayList<>(anzahl);
        for (int i = 0; i < anzahl; i++) {
            int quote = vorschlaege.get(i).trefferquote();
            // Gleichstand mit dem Nachbarn: dann ist der Vorschlag nicht eindeutig
            boolean gleichauf = (i > 0 && vorschlaege.get(i - 1).trefferquote() == quote)
                    || (i + 1 < vorschlaege.size() && vorschlaege.get(i + 1).trefferquote() == quote);
            liste.add(toVorschlagDto(vorschlaege.get(i), !gleichauf));
        }
        return Optional.of(liste);
    }

    private RechnungsVorschlagDto besterVorschlag(DokumentenKette kette, Dokumentbestand bestand,
            LieferantDokumentAbgleich.Merkmalspeicher speicher) {
        List<LieferantDokument> bestellDokumente = kette.dokumente().stream()
                .map(ref -> bestand.nachId().get(ref.id))
                .filter(Objects::nonNull)
                .toList();
        // Auf der Karte nur Rechnungen desselben Lieferanten (auch ausgeblendete): hält
        // die Übersicht schnell. Fremde Lieferanten zeigt das Fenster „Rechnung suchen“.
        if (kette.lieferantId() == null) {
            return null;
        }
        Set<Long> kettenIds = kette.dokumente().stream().map(d -> d.id).collect(Collectors.toSet());
        List<LocalDate> bestellDaten = bestellDokumente.stream()
                .filter(RechnungsVorschlagService::istBestellDokument)
                .map(d -> d.getGeschaeftsdaten() != null ? d.getGeschaeftsdaten().getDokumentDatum() : null)
                .filter(Objects::nonNull)
                .toList();
        // Nur Rechnungen im Zeitfenster bewerten – sonst wächst die Übersicht mit jedem Jahr.
        List<LieferantDokument> kandidaten = bestand.rechnungenVon(kette.lieferantId()).stream()
                .filter(r -> !kettenIds.contains(r.getId()))
                .filter(r -> imKartenFenster(r, bestellDaten))
                .toList();
        return rechnungsVorschlagService.besterVorschlag(bestellDokumente, kandidaten,
                        bestand.dokumenteVon(kette.lieferantId()), speicher)
                .map(b -> toVorschlagDto(b.vorschlag(), b.eindeutig()))
                .orElse(null);
    }

    /**
     * Rechnung liegt höchstens {@link #KARTE_TAGE_VORHER} Tage vor bzw.
     * {@link #KARTE_TAGE_NACHHER} Tage nach einem Bestelldokument der Kette. Ohne
     * Datum (Rechnung oder Bestellung) bleibt sie im Rennen.
     */
    public static boolean imKartenFenster(LieferantDokument rechnung, List<LocalDate> bestellDaten) {
        LocalDate datum = rechnung.getGeschaeftsdaten() != null ? rechnung.getGeschaeftsdaten().getDokumentDatum() : null;
        if (datum == null || bestellDaten.isEmpty()) {
            return true;
        }
        return bestellDaten.stream().anyMatch(b -> !datum.isBefore(b.minusDays(KARTE_TAGE_VORHER))
                && !datum.isAfter(b.plusDays(KARTE_TAGE_NACHHER)));
    }

    private static RechnungsVorschlagDto toVorschlagDto(RechnungsVorschlagService.Vorschlag v, boolean eindeutig) {
        LieferantDokument bestellung = v.bestellDokument();
        var bestellDaten = bestellung.getGeschaeftsdaten();
        return new RechnungsVorschlagDto(
                toDokumentRef(v.rechnung()),
                v.rechnung().getLieferant() != null ? v.rechnung().getLieferant().getLieferantenname() : null,
                bestellung.getId(),
                bestellung.getTyp(),
                bestellDaten != null ? bestellDaten.getDokumentNummer() : null,
                v.trefferquote(),
                v.einschaetzung().sicher(),
                eindeutig,
                v.einschaetzung().gruende(),
                RechnungsVorschlagService.gehoertSchonZu(v.rechnung()));
    }

    /**
     * Alle Lieferanten-Dokumente, einmal geladen und für die Vorschläge sortiert.
     *
     * @param nachId              alle Dokumente nach ID (auch ausgeblendete)
     * @param dokumenteJeLieferant alle Dokumente je Lieferant – das Umfeld, an dem
     *                             sich Kundennummern von Auftragsnummern unterscheiden
     * @param rechnungen           alle Rechnungen
     */
    private record Dokumentbestand(List<LieferantDokument> alle, Map<Long, LieferantDokument> nachId,
            Map<Long, List<LieferantDokument>> dokumenteJeLieferant, List<LieferantDokument> rechnungen) {

        List<LieferantDokument> dokumenteVon(Long lieferantId) {
            return lieferantId == null ? List.of() : dokumenteJeLieferant.getOrDefault(lieferantId, List.of());
        }

        List<LieferantDokument> rechnungenVon(Long lieferantId) {
            return dokumenteVon(lieferantId).stream()
                    .filter(d -> d.getTyp() == LieferantDokumentTyp.RECHNUNG)
                    .toList();
        }
    }

    private Dokumentbestand ladeDokumente(Set<LieferantDokumentTyp> sichtbareTypen) {
        List<LieferantDokument> alle = dokumentRepository.findAll().stream()
                .filter(d -> sichtbareTypen.contains(d.getTyp()))
                .toList();
        Map<Long, LieferantDokument> nachId = new HashMap<>();
        Map<Long, List<LieferantDokument>> jeLieferant = new HashMap<>();
        List<LieferantDokument> rechnungen = new ArrayList<>();
        for (LieferantDokument d : alle) {
            nachId.put(d.getId(), d);
            if (d.getLieferant() != null && d.getLieferant().getId() != null) {
                jeLieferant.computeIfAbsent(d.getLieferant().getId(), k -> new ArrayList<>()).add(d);
            }
            if (d.getTyp() == LieferantDokumentTyp.RECHNUNG) {
                rechnungen.add(d);
            }
        }
        return new Dokumentbestand(alle, nachId, jeLieferant, rechnungen);
    }

    /** Die Ketten des Bestands, nach Status gruppiert und neueste zuerst – noch ohne Vorschläge. */
    private BestellungsUebersichtDto gruppiereKetten(Dokumentbestand bestand) {
        // IDs aller bereits zugeordneten Dokumente
        Set<Long> zugeordneteDokumentIds = projektAnteilRepository.findAll().stream()
                .map(a -> a.getDokument().getId())
                .collect(Collectors.toSet());

        // IDs aller Lagerbestellungen (Bestellungen ohne Projekt-Zuordnung)
        Set<Long> lagerbestellungIds = geschaeftsdokumentRepository.findAll().stream()
                .filter(gd -> Boolean.TRUE.equals(gd.getLagerbestellung()))
                .map(gd -> gd.getDokument() != null ? gd.getDokument().getId() : -1L)
                .collect(Collectors.toSet());

        // Ketten aus ALLEN Dokumenten: Die bezahlte (ausgeblendete) Rechnung gehört
        // zu ihrem Lieferschein, sonst stünde der ewig unter „Rechnung fehlt“.
        List<DokumentenKette> alleKetten = buildKetten(bestand.alle());

        List<DokumentenKette> offeneAnfragen = new ArrayList<>();
        List<DokumentenKette> laufendeBestellungen = new ArrayList<>();
        List<DokumentenKette> abgeschlossen = new ArrayList<>();
        List<DokumentenKette> zugeordnet = new ArrayList<>();
        List<DokumentenKette> ausgeblendet = new ArrayList<>();

        for (DokumentenKette kette : alleKetten) {
            switch (einordnen(kette, zugeordneteDokumentIds, lagerbestellungIds)) {
                case RECHNUNG_ZUORDNEN -> abgeschlossen.add(kette);
                case ERLEDIGT -> ausgeblendet.add(kette);
                case ZUGEORDNET -> zugeordnet.add(kette);
                case LAUFEND -> laufendeBestellungen.add(kette);
                case ANFRAGE -> offeneAnfragen.add(kette);
                case KEINE -> { /* z. B. nur sonstige Dokumente – gehört nicht in die Übersicht */ }
            }
        }

        // Nach Datum sortieren (neueste zuerst); ohne Belegdatum zählt der Eingang
        Comparator<DokumentenKette> byDate = (a, b) -> neuestesDatum(b).compareTo(neuestesDatum(a));

        offeneAnfragen.sort(byDate);
        laufendeBestellungen.sort(byDate);
        abgeschlossen.sort(byDate);
        zugeordnet.sort(byDate);
        ausgeblendet.sort(byDate);

        return new BestellungsUebersichtDto(
                offeneAnfragen, laufendeBestellungen, abgeschlossen, zugeordnet, ausgeblendet);
    }

    private enum Bereich {
        ANFRAGE, LAUFEND, RECHNUNG_ZUORDNEN, ZUGEORDNET, ERLEDIGT, KEINE
    }

    /**
     * Status-Regel je Kette, in dieser Reihenfolge:
     * <ol>
     *   <li>Eine eingeblendete Rechnung, die weder Projekten zugeordnet noch
     *       Lagerbestellung ist → „Rechnung zuordnen“.</li>
     *   <li>Eine ausgeblendete Rechnung oder alles ausgeblendet → erledigt.</li>
     *   <li>Sonst wie bisher über alle Dokumente: zugeordnet, laufend, Anfrage.</li>
     * </ol>
     */
    private static Bereich einordnen(DokumentenKette kette, Set<Long> zugeordneteDokumentIds,
            Set<Long> lagerbestellungIds) {
        List<DokumentRef> dokumente = kette.dokumente();
        boolean offeneRechnung = dokumente.stream()
                .anyMatch(d -> d.typ == LieferantDokumentTyp.RECHNUNG && !d.ausgeblendet
                        && !zugeordneteDokumentIds.contains(d.id) && !lagerbestellungIds.contains(d.id));
        if (offeneRechnung) {
            return Bereich.RECHNUNG_ZUORDNEN;
        }
        boolean ausgeblendeteRechnung = dokumente.stream()
                .anyMatch(d -> d.typ == LieferantDokumentTyp.RECHNUNG && d.ausgeblendet);
        if (ausgeblendeteRechnung || dokumente.stream().allMatch(d -> d.ausgeblendet)) {
            return Bereich.ERLEDIGT;
        }
        if (dokumente.stream().anyMatch(d -> d.typ == LieferantDokumentTyp.RECHNUNG)) {
            // Alle Rechnungen eingeblendet und zugeordnet (sonst griffe Regel 1)
            return Bereich.ZUGEORDNET;
        }
        if (dokumente.stream().anyMatch(d -> d.typ == LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG
                || d.typ == LieferantDokumentTyp.LIEFERSCHEIN)) {
            // Ware ist bestellt oder schon geliefert, die Rechnung fehlt noch.
            return Bereich.LAUFEND;
        }
        if (dokumente.stream().allMatch(d -> d.typ == LieferantDokumentTyp.ANGEBOT)) {
            return Bereich.ANFRAGE;
        }
        return Bereich.KEINE;
    }

    /**
     * Bildet Dokumenten-Ketten basierend auf Verknüpfungen. Eine Kette umfasst alle
     * Dokumente, die über Verknüpfungen (egal welcher Richtung) zusammenhängen –
     * auch n:m (Teillieferungen, Teilrechnungen).
     */
    public List<DokumentenKette> buildKetten(List<LieferantDokument> dokumente) {
        // Map für schnellen Zugriff
        Map<Long, LieferantDokument> dokMap = dokumente.stream()
                .collect(Collectors.toMap(LieferantDokument::getId, d -> d, (a, b) -> a));

        // Set für bereits verarbeitete Dokumente
        Set<Long> verarbeitet = new HashSet<>();
        List<DokumentenKette> ketten = new ArrayList<>();

        for (LieferantDokument dok : dokumente) {
            if (verarbeitet.contains(dok.getId()))
                continue;

            // Sammle alle verknüpften Dokumente
            Set<Long> kettenIds = new HashSet<>();
            collectKettenIds(dok, kettenIds);

            List<DokumentRef> refs = new ArrayList<>();
            LieferantDokument first = null;
            for (Long id : kettenIds) {
                LieferantDokument d = dokMap.get(id);
                if (d != null) {
                    refs.add(toDokumentRef(d));
                    verarbeitet.add(id);
                    if (first == null) {
                        first = d;
                    }
                }
            }

            // Falls kein einziges verknüpftes Dokument in der gefilterten Liste war, Kette überspringen
            if (refs.isEmpty() || first == null) {
                continue;
            }

            // Sortieren: Erst nach Typ-Rang, dann nach Datum
            refs.sort(Comparator.comparingInt((DokumentRef r) -> getTypReihenfolge(r.typ))
                    .thenComparing(r -> r.dokumentDatum, Comparator.nullsLast(Comparator.naturalOrder())));

            String lieferantName = first.getLieferant() != null
                    ? first.getLieferant().getLieferantenname()
                    : null;
            Long lieferantId = first.getLieferant() != null
                    ? first.getLieferant().getId()
                    : null;

            ketten.add(new DokumentenKette(
                    UUID.randomUUID().toString(),
                    lieferantId,
                    lieferantName,
                    refs,
                    verbindungen(refs, dokMap)));
        }

        return ketten;
    }

    /**
     * Alle Verknüpfungen innerhalb einer Kette, jede Kante einmal: von =
     * Nachfolger (z. B. Rechnung), zu = Vorgänger (z. B. Lieferschein).
     */
    private static List<Verbindung> verbindungen(List<DokumentRef> refs, Map<Long, LieferantDokument> dokMap) {
        Set<Long> ids = refs.stream().map(r -> r.id).collect(Collectors.toSet());
        Set<String> gesehen = new HashSet<>();
        List<Verbindung> verbindungen = new ArrayList<>();
        for (DokumentRef ref : refs) {
            LieferantDokument d = dokMap.get(ref.id);
            if (d == null || d.getVerknuepfteDokumente() == null) {
                continue;
            }
            for (LieferantDokument vorgaenger : d.getVerknuepfteDokumente()) {
                Long zu = vorgaenger.getId();
                if (zu == null || zu.equals(d.getId()) || !ids.contains(zu)) {
                    continue;
                }
                String kante = Math.min(d.getId(), zu) + ":" + Math.max(d.getId(), zu);
                if (gesehen.add(kante)) {
                    verbindungen.add(new Verbindung(d.getId(), zu));
                }
            }
        }
        verbindungen.sort(Comparator.comparing(Verbindung::vonId).thenComparing(Verbindung::zuId));
        return verbindungen;
    }

    private static LocalDate neuestesDatum(DokumentenKette kette) {
        return kette.dokumente().stream()
                .map(d -> d.dokumentDatum != null ? d.dokumentDatum : d.eingangsDatum)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.MIN);
    }

    private static void collectKettenIds(LieferantDokument start, Set<Long> collected) {
        // Iterativ statt rekursiv: lange Ketten (viele Teillieferungen) sprengen sonst den Stack.
        // Verknüpfungen in beide Richtungen durchlaufen: Gespeichert wird nur
        // Nachfolger -> Vorgänger (Rechnung -> AB). Ohne die Rückrichtung bildete eine
        // AB, die vor ihrer Rechnung an der Reihe war, eine eigene Kette ohne Rechnung
        // und blieb dauerhaft bei den laufenden Bestellungen stehen.
        ArrayDeque<LieferantDokument> offen = new ArrayDeque<>();
        if (start != null) {
            offen.push(start);
        }
        while (!offen.isEmpty()) {
            LieferantDokument dok = offen.pop();
            if (!collected.add(dok.getId())) {
                continue;
            }
            if (dok.getVerknuepfteDokumente() != null) {
                dok.getVerknuepfteDokumente().stream().filter(Objects::nonNull).forEach(offen::push);
            }
            if (dok.getVerknuepftVon() != null) {
                dok.getVerknuepftVon().stream().filter(Objects::nonNull).forEach(offen::push);
            }
        }
    }

    private static int getTypReihenfolge(LieferantDokumentTyp typ) {
        return switch (typ) {
            case ANGEBOT -> 0;
            case AUFTRAGSBESTAETIGUNG -> 1;
            case LIEFERSCHEIN -> 2;
            // Das Zeugnis gehört zur Lieferung und steht direkt dahinter.
            case WERKSTOFFZEUGNIS -> 3;
            case RECHNUNG -> 4;
            case GUTSCHRIFT -> 5;
            case SONSTIG -> 99;
            case BELEG -> 100; // gehört nicht zur Bestellkette, wird hier nicht erwartet
        };
    }

    /**
     * Ein Lieferanten-Dokument als Eintrag der Bestellübersicht, inklusive Link zur
     * Datei. Rein abbildend – deshalb statisch und auch ohne Service-Instanz nutzbar.
     */
    public static DokumentRef toDokumentRef(LieferantDokument d) {
        DokumentRef ref = new DokumentRef();
        ref.id = d.getId();
        ref.typ = d.getTyp();
        ref.dateiname = d.getEffektiverDateiname();
        ref.eingangsDatum = d.getUploadDatum() != null ? d.getUploadDatum().toLocalDate() : null;
        ref.ausgeblendet = d.isAusgeblendet();

        if (d.getGeschaeftsdaten() != null) {
            var gd = d.getGeschaeftsdaten();
            ref.dokumentNummer = gd.getDokumentNummer();
            ref.dokumentDatum = gd.getDokumentDatum();
            ref.betragBrutto = gd.getBetragBrutto() != null ? gd.getBetragBrutto().doubleValue() : null;
            ref.betragNetto = gd.getBetragNetto() != null ? gd.getBetragNetto().doubleValue() : null;
            ref.liefertermin = gd.getLiefertermin();
        }

        // PDF-URL: Mehrere Quellen prüfen
        // 1. E-Mail Attachment
        if (d.getAttachment() != null && d.getAttachment().getEmail() != null) {
            var att = d.getAttachment();
            ref.pdfUrl = "/api/emails/" + att.getEmail().getId() +
                    "/attachments/" + att.getId();
        }
        // 2. Gespeicherte Datei über Lieferant
        else if (d.getLieferant() != null && d.getGespeicherterDateiname() != null) {
            ref.pdfUrl = "/api/lieferanten/" + d.getLieferant().getId() +
                    "/dokumente/" + d.getId() + "/download";
        }
        // 3. Fallback: Generischer Dokument-Endpoint
        else if (d.getId() != null) {
            ref.pdfUrl = "/api/lieferant-dokumente/" + d.getId() + "/download";
        }

        return ref;
    }
}
