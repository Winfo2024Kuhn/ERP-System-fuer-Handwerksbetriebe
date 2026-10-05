package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Entscheidet, welche Lieferanten-Dokumente zu einer Dokumentenkette gehören
 * (Angebot → Auftragsbestätigung → Lieferschein → Rechnung → Gutschrift).
 *
 * <p>Früher zählte nur eine einzige Referenznummer. Angebote tragen aber noch
 * keine Bestellnummer, und viele Auftragsbestätigungen nennen die
 * Angebotsnummer nicht – solche Paare blieben unverknüpft. Deshalb sammelt der
 * Abgleich jetzt mehrere Hinweise und vergibt Punkte:
 *
 * <ul>
 *   <li><b>Sicher</b> (immer verknüpfen): Ein Dokument nennt die Nummer des
 *       anderen (in beide Richtungen), oder beide tragen dieselbe
 *       Bestellnummer.</li>
 *   <li><b>Hinweis</b>: gleiche Kommission/Bauvorhaben, gleicher Betrag,
 *       gleiche Artikelnummern. Zählt nur, wenn das Datum zur Kette passt;
 *       fehlt ein Datum, braucht es mindestens zwei Merkmale. Geliefert wird
 *       höchstens ein Kandidat – und nur, wenn er eindeutig vorne liegt. Der
 *       Aufrufer hängt ihn nur an, wenn noch kein Vorgänger dieses Typs da ist.</li>
 * </ul>
 *
 * <p>Ein geändertes Angebot (Revision) hängt am ursprünglichen Angebot – so
 * landen beide in derselben Kette. Weil das ältere Angebot stets der Vorgänger
 * ist, entsteht keine Verknüpfung in beide Richtungen.
 *
 * <p>Kommission, weitere Referenzen und Artikelnummern liest der Abgleich aus
 * der gespeicherten KI-Antwort. So profitieren auch bereits analysierte
 * Dokumente beim Neu-Verknüpfen, ohne dass die KI noch einmal laufen muss.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LieferantDokumentAbgleich {

    static final int PUNKTE_NUMMERNBEZUG = 100;
    static final int PUNKTE_GLEICHE_BESTELLNUMMER = 80;
    static final int SCHWELLE_SICHER = 80;

    static final int PUNKTE_KOMMISSION = 40;
    static final int PUNKTE_BETRAG = 40;
    static final int PUNKTE_ARTIKEL = 40;
    static final int PUNKTE_EINZELARTIKEL = 20;
    static final int SCHWELLE_HINWEIS = 40;
    /** Ohne Datum fehlt die zeitliche Grenze – dann reicht ein einzelnes Merkmal nicht. */
    static final int SCHWELLE_HINWEIS_OHNE_DATUM = 80;
    /**
     * Zwei Angebote desselben Bauvorhabens sind nicht automatisch Revisionen
     * voneinander – deshalb braucht eine Revision mindestens zwei Merkmale.
     */
    static final int SCHWELLE_REVISION = 80;
    static final int PUNKTE_NUMMERNSTAMM = 40;

    /**
     * Revisions-Endung einer Belegnummer: "AN-4711-2", "AN 4711 Rev. 3",
     * "AN-4711/b". Possessive Quantifizierer gegen Backtracking (ReDoS).
     */
    private static final Pattern REVISIONS_ENDUNG = Pattern.compile(
            "(?i)[\\s\\-_/.]++((?:rev|v|version|index|idx)[\\s.]*+)?(?:\\d{1,2}|[a-z])$");
    /** Längere Eingaben sind keine Belegnummer – dort keine Endungs-Erkennung. */
    private static final int MAX_NUMMER_LAENGE = 64;

    /**
     * Teilen mehr Kandidaten dieselbe Bestellnummer, ist sie nicht trennscharf –
     * meist hat die KI dann die Kundennummer als Bestellnummer gelesen.
     */
    static final int MAX_DOKUMENTE_JE_BESTELLNUMMER = 3;

    /** Ziffernkern-Vergleich erst ab dieser Länge, sonst zu viele Zufallstreffer. */
    private static final int MIN_ZIFFERN_KERN = 5;
    private static final int MIN_KOMMISSION_LAENGE = 4;
    /** Teilstring-Treffer erst ab dieser Länge, sonst treffen sich "lager" und "lagerhalle". */
    private static final int MIN_KOMMISSION_TEILSTRING = 8;

    private final ObjectMapper objectMapper;

    /**
     * Welche Dokumenttypen in der Kette direkt vor dem gegebenen Typ stehen.
     */
    public static List<LieferantDokumentTyp> vorgaengerTypen(LieferantDokumentTyp typ) {
        if (typ == null) {
            return List.of();
        }
        return switch (typ) {
            case RECHNUNG -> List.of(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, LieferantDokumentTyp.LIEFERSCHEIN);
            case GUTSCHRIFT -> List.of(LieferantDokumentTyp.RECHNUNG);
            case LIEFERSCHEIN -> List.of(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
            case AUFTRAGSBESTAETIGUNG -> List.of(LieferantDokumentTyp.ANGEBOT);
            // Geändertes Angebot -> ursprüngliches Angebot
            case ANGEBOT -> List.of(LieferantDokumentTyp.ANGEBOT);
            default -> List.of();
        };
    }

    /**
     * Position in der Dokumentenkette (Angebot zuerst). Bewusst explizit statt
     * Enum-Reihenfolge, damit ein Umsortieren des Enums nichts verschiebt.
     */
    public static int kettenRang(LieferantDokumentTyp typ) {
        if (typ == null) {
            return Integer.MAX_VALUE;
        }
        return switch (typ) {
            case ANGEBOT -> 0;
            case AUFTRAGSBESTAETIGUNG -> 1;
            case LIEFERSCHEIN -> 2;
            case RECHNUNG -> 3;
            case GUTSCHRIFT -> 4;
            default -> 5;
        };
    }

    /**
     * Ergebnis eines Abgleichs.
     *
     * @param sicher  alle sicheren Vorgänger – immer verknüpfen
     * @param hinweis bester Hinweis-Treffer, nur gesetzt wenn es keinen sicheren gibt
     */
    public record Ergebnis(List<LieferantDokument> sicher, LieferantDokument hinweis) {

        static final Ergebnis LEER = new Ergebnis(List.of(), null);
    }

    /**
     * Zwischenspeicher für die Merkmale eines Abgleich-Laufs. Beim Neu-Verknüpfen
     * vieler Dokumente würde sonst die KI-Antwort jedes Kandidaten für jedes
     * Dokument erneut eingelesen. Nur für einen Lauf verwenden – ändern sich
     * Geschäftsdaten, ist der Inhalt veraltet.
     */
    public static final class Merkmalspeicher {
        private final Map<LieferantGeschaeftsdokument, Merkmale> werte = new IdentityHashMap<>();
    }

    public Merkmalspeicher neuerSpeicher() {
        return new Merkmalspeicher();
    }

    /** Wie {@link #findeVorgaenger(LieferantDokument, LieferantGeschaeftsdokument, Collection, Merkmalspeicher)} mit eigenem Speicher. */
    public Ergebnis findeVorgaenger(LieferantDokument dokument,
            LieferantGeschaeftsdokument geschaeftsdaten, Collection<LieferantDokument> kandidaten) {
        return findeVorgaenger(dokument, geschaeftsdaten, kandidaten, neuerSpeicher());
    }

    /**
     * Sucht unter den Kandidaten die Vorgänger-Dokumente des gegebenen Dokuments.
     *
     * @param dokument        das Dokument, dessen Vorgänger gesucht werden
     * @param geschaeftsdaten seine Geschäftsdaten (können bei der Erstanalyse noch
     *                        nicht am Dokument hängen)
     * @param kandidaten      Dokumente desselben Lieferanten; ungeeignete Typen und
     *                        das Dokument selbst werden ignoriert
     * @param speicher        Merkmal-Zwischenspeicher des laufenden Abgleichs
     */
    public Ergebnis findeVorgaenger(LieferantDokument dokument, LieferantGeschaeftsdokument geschaeftsdaten,
            Collection<LieferantDokument> kandidaten, Merkmalspeicher speicher) {
        if (dokument == null || geschaeftsdaten == null || kandidaten == null) {
            return Ergebnis.LEER;
        }
        List<LieferantDokumentTyp> typen = vorgaengerTypen(dokument.getTyp());
        if (typen.isEmpty()) {
            return Ergebnis.LEER;
        }

        Merkmale ich = merkmale(dokument.getTyp(), geschaeftsdaten, speicher);
        List<Kandidat> bewertbar = new ArrayList<>();
        for (LieferantDokument kandidat : kandidaten) {
            if (kandidat == null || kandidat == dokument || !typen.contains(kandidat.getTyp())
                    || kandidat.getGeschaeftsdaten() == null
                    || (kandidat.getId() != null && kandidat.getId().equals(dokument.getId()))) {
                continue;
            }
            Merkmale merkmale = merkmale(kandidat.getTyp(), kandidat.getGeschaeftsdaten(), speicher);
            // Bei gleichem Typ (Angebots-Revision) ist nur das ältere Dokument Vorgänger.
            if (kandidat.getTyp() == dokument.getTyp() && !istAelter(kandidat, merkmale, dokument, ich)) {
                continue;
            }
            bewertbar.add(new Kandidat(kandidat, merkmale));
        }

        long gleicheBestellnummer = ich.bestellnummer() == null ? 0
                : bewertbar.stream().filter(k -> ich.bestellnummer().equals(k.merkmale().bestellnummer())).count();
        boolean bestellnummerTrennscharf = gleicheBestellnummer > 0
                && gleicheBestellnummer <= MAX_DOKUMENTE_JE_BESTELLNUMMER;

        List<LieferantDokument> sicher = new ArrayList<>();
        List<Bewertung> hinweise = new ArrayList<>();
        for (Kandidat k : bewertbar) {
            boolean revision = k.merkmale().typ() == ich.typ();
            // Eine Bestellnummer tragen Angebote nicht – für Revisionen zählt sie nicht.
            int sicherePunkte = sicherePunkte(ich, k.merkmale(), bestellnummerTrennscharf && !revision);
            if (sicherePunkte >= SCHWELLE_SICHER) {
                sicher.add(k.dokument());
                log.debug("[Abgleich] Sicherer Treffer: Dokument {} -> {} ({} Punkte)",
                        dokument.getId(), k.dokument().getId(), sicherePunkte);
                continue;
            }
            int hinweisPunkte = hinweisPunkte(ich, k.merkmale());
            if (revision && gleicherNummernstamm(ich, k.merkmale())) {
                hinweisPunkte += PUNKTE_NUMMERNSTAMM;
            }
            int schwelle = revision ? SCHWELLE_REVISION
                    : ich.datum() != null && k.merkmale().datum() != null
                            ? SCHWELLE_HINWEIS
                            : SCHWELLE_HINWEIS_OHNE_DATUM;
            if (hinweisPunkte >= schwelle) {
                hinweise.add(new Bewertung(k.dokument(), hinweisPunkte));
            }
        }

        if (!sicher.isEmpty()) {
            return new Ergebnis(List.copyOf(sicher), null);
        }
        if (hinweise.isEmpty()) {
            return Ergebnis.LEER;
        }

        hinweise.sort(Comparator.comparingInt(Bewertung::punkte).reversed());
        Bewertung beste = hinweise.get(0);
        List<LieferantDokument> gleichauf = hinweise.stream()
                .filter(b -> b.punkte() == beste.punkte())
                .map(Bewertung::dokument)
                .toList();
        if (gleichauf.size() > 1) {
            // Mehrere Fassungen desselben Angebots sind kein Widerspruch: dann gilt die neueste.
            LieferantDokument neueste = neuesteFassung(gleichauf);
            if (neueste != null) {
                log.debug("[Abgleich] Hinweis-Treffer: Dokument {} -> neueste Angebotsfassung {}",
                        dokument.getId(), neueste.getId());
                return new Ergebnis(List.of(), neueste);
            }
            log.debug("[Abgleich] Dokument {}: mehrere Kandidaten gleichauf mit {} Punkten – kein Hinweis-Treffer",
                    dokument.getId(), beste.punkte());
            return Ergebnis.LEER;
        }
        log.debug("[Abgleich] Hinweis-Treffer: Dokument {} -> {} ({} Punkte)",
                dokument.getId(), beste.dokument().getId(), beste.punkte());
        return new Ergebnis(List.of(), beste.dokument());
    }

    // ------------------------------------------------------- Einschätzung

    /** Ab dieser Trefferquote gilt ein Paar als sicher – der Import hätte es selbst verknüpft. */
    public static final int QUOTE_SICHER = 95;
    /** Höchste Quote für reine Hinweise: Ohne Nummernbezug bleibt ein Rest Unsicherheit. */
    static final int QUOTE_HINWEIS_MAX = 94;
    static final int ABZUG_ANDERER_LIEFERANT = 25;
    /** Quote bei Nummernbezug – noch sicherer als eine gleiche Bestellnummer. */
    static final int QUOTE_NUMMERNBEZUG = 97;
    /** Je so viele Hinweispunkte steigt ein sicherer Treffer um einen Prozentpunkt. */
    static final int HINWEISPUNKTE_JE_PROZENT_SICHER = 20;
    /** Hinweis-Quote = Sockel + Punkte × Faktor (gedeckelt bei {@link #QUOTE_HINWEIS_MAX}). */
    static final int QUOTE_HINWEIS_SOCKEL = 45;
    static final double QUOTE_HINWEIS_FAKTOR = 0.6;
    /** Nur zeitliche Nähe ohne weiteres Merkmal – reicht für die Sortierung, nicht für die Karte. */
    static final int QUOTE_NUR_ZEITNAH = 25;
    static final int QUOTE_NUR_ZEITLICH_PASSEND = 15;
    static final int QUOTE_OHNE_DATUM = 10;
    static final int TAGE_ZEITNAH = 45;

    /**
     * Einschätzung für die Oberfläche: wie wahrscheinlich ein Vorgänger zum
     * Nachfolger gehört, mit den Gründen in Klartext.
     *
     * @param trefferquote 0–100 %
     * @param sicher       ein Nummernbezug oder eine trennscharfe Bestellnummer
     * @param gruende      kurze, verständliche Begründungen („Gleiche Bestellnummer“)
     */
    public record Einschaetzung(int trefferquote, boolean sicher, List<String> gruende) {
    }

    /**
     * Schätzt ein, wie gut ein Vorgänger (z. B. AB oder Lieferschein) zu einem
     * Nachfolger (z. B. Rechnung) passt. Nutzt dieselben Merkmale und Punkte wie
     * {@link #findeVorgaenger}, liefert aber für jedes Paar eine Quote statt einer
     * Ja/Nein-Entscheidung – auch für Paare, die der Import bewusst nicht verknüpft
     * hat (Gleichstand, fehlendes Datum, anderer Lieferant).
     *
     * @param bestellnummerTrennscharf ob die gemeinsame Bestellnummer unter den
     *                                 Kandidaten selten genug ist, um sicher zu zählen
     */
    public Einschaetzung schaetzeEin(LieferantDokument nachfolger, LieferantDokument vorgaenger,
            boolean bestellnummerTrennscharf, Merkmalspeicher speicher) {
        if (nachfolger == null || vorgaenger == null
                || nachfolger.getGeschaeftsdaten() == null || vorgaenger.getGeschaeftsdaten() == null) {
            return new Einschaetzung(0, false, List.of());
        }
        Merkmale ich = merkmale(nachfolger.getTyp(), nachfolger.getGeschaeftsdaten(), speicher);
        Merkmale vor = merkmale(vorgaenger.getTyp(), vorgaenger.getGeschaeftsdaten(), speicher);
        List<String> gruende = new ArrayList<>();

        boolean gleicherLieferant = nachfolger.getLieferant() != null && vorgaenger.getLieferant() != null
                && nachfolger.getLieferant().getId() != null
                && nachfolger.getLieferant().getId().equals(vorgaenger.getLieferant().getId());
        if (gleicherLieferant) {
            gruende.add("Gleicher Lieferant");
        } else {
            gruende.add("Anderer Lieferant");
        }

        int sicherePunkte = sicherePunkte(ich, vor, bestellnummerTrennscharf);
        boolean gleicheBestellnummer = ich.bestellnummer() != null && ich.bestellnummer().equals(vor.bestellnummer());
        if (sicherePunkte >= PUNKTE_NUMMERNBEZUG) {
            gruende.add("Belegnummer wird genannt");
        } else if (sicherePunkte >= SCHWELLE_SICHER) {
            gruende.add("Gleiche Bestellnummer");
        } else if (gleicheBestellnummer) {
            gruende.add("Gleiche Bestellnummer (bei mehreren Belegen)");
        }

        boolean datumPasst = datumPasst(ich, vor);
        int hinweisPunkte = hinweisPunkte(ich, vor);
        if (datumPasst) {
            if (kommissionPasst(ich.kommission(), vor.kommission())) {
                gruende.add("Gleiche Kommission");
            }
            if (betragPasst(ich, vor)) {
                gruende.add("Gleicher Betrag");
            }
            int artikel = artikelPunkte(ich.artikel(), vor.artikel());
            if (artikel >= PUNKTE_ARTIKEL) {
                gruende.add("Gleiche Artikelnummern");
            } else if (artikel > 0) {
                gruende.add("Gleiche Artikelnummer");
            }
            if (sicherePunkte < SCHWELLE_SICHER && gleicheBestellnummer) {
                // Nicht trennscharf, aber immer noch ein Hinweis
                hinweisPunkte += PUNKTE_KOMMISSION;
            }
        }
        if (ich.datum() != null && vor.datum() != null) {
            long tage = java.time.temporal.ChronoUnit.DAYS.between(vor.datum(), ich.datum());
            if (!datumPasst) {
                gruende.add("Datum passt nicht zur Bestellung");
            } else if (tage >= 0) {
                gruende.add(tage == 0 ? "Am selben Tag" : tage == 1 ? "1 Tag danach" : tage + " Tage danach");
            }
        } else {
            gruende.add("Datum fehlt");
        }

        int quote;
        boolean sicher = sicherePunkte >= SCHWELLE_SICHER;
        if (sicher) {
            quote = Math.min(100, (sicherePunkte >= PUNKTE_NUMMERNBEZUG ? QUOTE_NUMMERNBEZUG : QUOTE_SICHER)
                    + hinweisPunkte / HINWEISPUNKTE_JE_PROZENT_SICHER);
        } else if (hinweisPunkte > 0) {
            // Ohne Datum fehlt die zeitliche Grenze – der Import verlangt dann doppelt so viel.
            int punkte = ich.datum() != null && vor.datum() != null ? hinweisPunkte : hinweisPunkte / 2;
            quote = Math.min(QUOTE_HINWEIS_MAX, QUOTE_HINWEIS_SOCKEL + (int) (punkte * QUOTE_HINWEIS_FAKTOR));
        } else if (datumPasst && ich.datum() != null && vor.datum() != null) {
            // Nur zeitliche Nähe: kein Beleg, aber für die Sortierung hilfreich
            long tage = java.time.temporal.ChronoUnit.DAYS.between(vor.datum(), ich.datum());
            quote = tage >= 0 && tage <= TAGE_ZEITNAH ? QUOTE_NUR_ZEITNAH : QUOTE_NUR_ZEITLICH_PASSEND;
        } else {
            quote = datumPasst ? QUOTE_OHNE_DATUM : 0;
        }
        if (!gleicherLieferant) {
            quote = Math.max(0, quote - ABZUG_ANDERER_LIEFERANT);
            sicher = sicher && quote >= QUOTE_SICHER;
        }
        return new Einschaetzung(quote, sicher, List.copyOf(gruende));
    }

    /**
     * Liegt der Kandidat zeitlich vor dem Dokument? Maßgeblich ist das Belegdatum,
     * bei gleichem oder fehlendem Datum die Reihenfolge des Eingangs (ID).
     */
    private static boolean istAelter(LieferantDokument kandidat, Merkmale kandidatMerkmale,
            LieferantDokument dokument, Merkmale dokumentMerkmale) {
        LocalDate a = kandidatMerkmale.datum();
        LocalDate b = dokumentMerkmale.datum();
        if (a != null && b != null && !a.isEqual(b)) {
            return a.isBefore(b);
        }
        return kandidat.getId() != null && dokument.getId() != null && kandidat.getId() < dokument.getId();
    }

    /**
     * Sind alle gleichauf liegenden Kandidaten Fassungen desselben Angebots (über
     * Revisions-Verknüpfungen zusammenhängend, egal in welcher Richtung), gilt die
     * neueste. Ungerichtet, weil Änderungen oft alle am Ursprung hängen (Stern:
     * v2 -> v1, v3 -> v1).
     */
    private static LieferantDokument neuesteFassung(List<LieferantDokument> gleichauf) {
        if (gleichauf.stream().anyMatch(d -> d.getTyp() != LieferantDokumentTyp.ANGEBOT)) {
            return null;
        }
        LieferantDokument neueste = gleichauf.stream()
                .max(Comparator.comparing((LieferantDokument d) -> d.getGeschaeftsdaten().getDokumentDatum(),
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(LieferantDokument::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        if (neueste == null) {
            return null;
        }
        Set<LieferantDokument> erreichbar = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        java.util.ArrayDeque<LieferantDokument> offen = new java.util.ArrayDeque<>();
        offen.add(neueste);
        while (!offen.isEmpty()) {
            LieferantDokument aktuell = offen.poll();
            if (!erreichbar.add(aktuell)) {
                continue;
            }
            angebotsNachbarn(aktuell, gleichauf).forEach(offen::add);
        }
        return erreichbar.containsAll(gleichauf) ? neueste : null;
    }

    /**
     * Angebote, die mit dem Dokument in irgendeiner Richtung verknüpft sind. Die
     * Rückrichtung kommt aus {@code verknuepftVon} und – weil das in einem
     * laufenden Stapel nicht im Speicher nachgezogen wird – zusätzlich aus den
     * Verknüpfungen der gleichauf liegenden Kandidaten.
     */
    private static List<LieferantDokument> angebotsNachbarn(LieferantDokument dokument,
            List<LieferantDokument> gleichauf) {
        List<LieferantDokument> nachbarn = new ArrayList<>(dokument.getVerknuepfteDokumente());
        nachbarn.addAll(dokument.getVerknuepftVon());
        gleichauf.stream()
                .filter(k -> k.getVerknuepfteDokumente().contains(dokument))
                .forEach(nachbarn::add);
        return nachbarn.stream().filter(n -> n.getTyp() == LieferantDokumentTyp.ANGEBOT).toList();
    }

    // ------------------------------------------------------------------ Punkte

    private int sicherePunkte(Merkmale ich, Merkmale vorgaenger, boolean bestellnummerTrennscharf) {
        if (nenntNummerExakt(ich, vorgaenger.nummer()) || nenntNummerExakt(vorgaenger, ich.nummer())) {
            return PUNKTE_NUMMERNBEZUG;
        }
        // Der Ziffernkern ist unschärfer (gleich aufgebaute Nummernkreise verschiedener
        // Firmen) – deshalb nur für ausdrücklich genannte Belegnummern und nur, wenn
        // das Datum zur Kette passt.
        if (datumPasst(ich, vorgaenger) && (nenntZiffernkern(ich.referenzen(), vorgaenger.nummer())
                || nenntZiffernkern(vorgaenger.referenzen(), ich.nummer()))) {
            return PUNKTE_NUMMERNBEZUG;
        }
        if (bestellnummerTrennscharf && ich.bestellnummer() != null
                && ich.bestellnummer().equals(vorgaenger.bestellnummer())) {
            return PUNKTE_GLEICHE_BESTELLNUMMER;
        }
        return 0;
    }

    private int hinweisPunkte(Merkmale ich, Merkmale vorgaenger) {
        if (!datumPasst(ich, vorgaenger)) {
            return 0;
        }
        int punkte = 0;
        if (kommissionPasst(ich.kommission(), vorgaenger.kommission())) {
            punkte += PUNKTE_KOMMISSION;
        }
        if (betragPasst(ich, vorgaenger)) {
            punkte += PUNKTE_BETRAG;
        }
        punkte += artikelPunkte(ich.artikel(), vorgaenger.artikel());
        return punkte;
    }

    /**
     * Der Vorgänger muss vor dem Dokument liegen (mit etwas Spielraum für
     * Rückdatierungen). Angebote dürfen deutlich älter sein als die AB – zwischen
     * Angebot und Bestellung vergehen oft Monate.
     */
    private boolean datumPasst(Merkmale ich, Merkmale vorgaenger) {
        if (ich.datum() == null || vorgaenger.datum() == null) {
            return true;
        }
        boolean angebot = vorgaenger.typ() == LieferantDokumentTyp.ANGEBOT;
        LocalDate fruehestens = ich.datum().minusDays(angebot ? 365 : 120);
        LocalDate spaetestens = ich.datum().plusDays(angebot ? 14 : 31);
        return !vorgaenger.datum().isBefore(fruehestens) && !vorgaenger.datum().isAfter(spaetestens);
    }

    private boolean betragPasst(Merkmale ich, Merkmale vorgaenger) {
        // Gutschriften mindern die positive Rechnung; die Betragsgröße muss übereinstimmen.
        boolean nurBetrag = ich.typ() == LieferantDokumentTyp.GUTSCHRIFT;
        return gleich(ich.brutto(), vorgaenger.brutto(), nurBetrag)
                || gleich(ich.netto(), vorgaenger.netto(), nurBetrag);
    }

    private static boolean gleich(BigDecimal a, BigDecimal b, boolean nurBetrag) {
        if (a == null || b == null || a.signum() == 0) {
            return false;
        }
        return nurBetrag ? a.abs().compareTo(b.abs()) == 0 : a.compareTo(b) == 0;
    }

    /**
     * Gleicher Stamm zählt nur, wenn eine Nummer der unveränderte Ursprung ist
     * ("AN-4711" zu "AN-4711-2") oder eine Endung ausdrücklich eine Revision nennt
     * ("Rev. 2"). Sonst hätten fortlaufende Kreise wie "2026-1" und "2026-2"
     * denselben Stamm.
     */
    private static boolean gleicherNummernstamm(Merkmale a, Merkmale b) {
        Nummernstamm x = a.nummernstamm();
        Nummernstamm y = b.nummernstamm();
        if (x == null || y == null || !x.stamm().equals(y.stamm())) {
            return false;
        }
        return x.ohneEndung() != y.ohneEndung() || x.revisionsWort() || y.revisionsWort();
    }

    private static boolean kommissionPasst(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        String kurz = a.length() <= b.length() ? a : b;
        String lang = kurz == a ? b : a;
        return kurz.length() >= MIN_KOMMISSION_TEILSTRING && lang.contains(kurz);
    }

    private static int artikelPunkte(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> gemeinsam = new HashSet<>(a);
        gemeinsam.retainAll(b);
        if (gemeinsam.isEmpty()) {
            return 0;
        }
        double anteil = (double) gemeinsam.size() / Math.min(a.size(), b.size());
        if (anteil < 0.6) {
            return 0;
        }
        return gemeinsam.size() >= 2 ? PUNKTE_ARTIKEL : PUNKTE_EINZELARTIKEL;
    }

    /**
     * Nennt das Dokument die Nummer als Referenz? Die eigene Bestellnummer zählt
     * mit ("Ihre Bestellung: Angebot 4711").
     */
    private static boolean nenntNummerExakt(Merkmale dokument, String nummer) {
        return nummer != null && (dokument.referenzen().contains(nummer) || nummer.equals(dokument.bestellnummer()));
    }

    private static boolean nenntZiffernkern(Set<String> referenzen, String nummer) {
        if (nummer == null || referenzen.isEmpty()) {
            return false;
        }
        // "AB 4711234" vs. "4711234": Präfixe schreibt jeder Lieferant anders.
        String kern = ziffernKern(nummer);
        return kern != null && referenzen.stream().map(LieferantDokumentAbgleich::ziffernKern)
                .anyMatch(kern::equals);
    }

    // --------------------------------------------------------------- Merkmale

    private Merkmale merkmale(LieferantDokumentTyp typ, LieferantGeschaeftsdokument gd, Merkmalspeicher speicher) {
        return speicher.werte.computeIfAbsent(gd, g -> merkmale(typ, g));
    }

    private Merkmale merkmale(LieferantDokumentTyp typ, LieferantGeschaeftsdokument gd) {
        String nummer = normalisiereNummer(gd.getDokumentNummer());
        String bestellnummer = normalisiereNummer(gd.getBestellnummer());

        Set<String> referenzen = new HashSet<>();
        fuegeNummerHinzu(referenzen, gd.getReferenzNummer());

        String kommission = null;
        Set<String> artikel = new HashSet<>();
        JsonNode json = leseKiAntwort(gd.getAiRawJson());
        if (json != null) {
            JsonNode weitere = json.get("weitereReferenzen");
            if (weitere != null && weitere.isArray()) {
                weitere.forEach(r -> fuegeNummerHinzu(referenzen, r.isTextual() ? r.asText() : null));
            } else if (weitere != null && weitere.isTextual()) {
                for (String teil : weitere.asText().split("[,;]")) {
                    fuegeNummerHinzu(referenzen, teil);
                }
            }
            kommission = normalisiereKommission(text(json, "kommission"));
            JsonNode positionen = json.get("artikelPositionen");
            if (positionen != null && positionen.isArray()) {
                positionen.forEach(p -> {
                    String artikelnummer = normalisiereNummer(text(p, "externeArtikelnummer"));
                    if (artikelnummer != null && artikelnummer.length() >= 3) {
                        artikel.add(artikelnummer);
                    }
                });
            }
        }
        if (nummer != null) {
            referenzen.remove(nummer);
        }

        return new Merkmale(typ, nummer, zerlegeNummer(gd.getDokumentNummer()), referenzen, bestellnummer,
                kommission, gd.getDokumentDatum(), gd.getBetragNetto(), gd.getBetragBrutto(), artikel);
    }

    /** Belegnummer ohne Revisions-Endung: "AN-4711-2" und "AN-4711" ergeben "AN4711". */
    static String nummernstamm(String nummer) {
        Nummernstamm stamm = zerlegeNummer(nummer);
        return stamm != null ? stamm.stamm() : null;
    }

    private static Nummernstamm zerlegeNummer(String nummer) {
        if (nummer == null) {
            return null;
        }
        String roh = nummer.trim();
        if (roh.length() > MAX_NUMMER_LAENGE) {
            String normalisiert = normalisiereNummer(roh);
            return normalisiert != null ? new Nummernstamm(normalisiert, true, false) : null;
        }
        java.util.regex.Matcher endung = REVISIONS_ENDUNG.matcher(roh);
        boolean hatEndung = endung.find();
        String stamm = normalisiereNummer(hatEndung ? roh.substring(0, endung.start()) : roh);
        if (stamm == null) {
            return null;
        }
        return new Nummernstamm(stamm, !hatEndung, hatEndung && endung.group(1) != null);
    }

    private JsonNode leseKiAntwort(String aiRawJson) {
        if (aiRawJson == null || aiRawJson.isBlank()) {
            return null;
        }
        try {
            JsonNode json = objectMapper.readTree(aiRawJson);
            return json != null && json.isObject() ? json : null;
        } catch (Exception e) {
            // Nur der Fehlertyp: Jackson-Meldungen zitieren Teile des Inhalts (DSGVO).
            log.debug("[Abgleich] KI-Antwort nicht lesbar: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static String text(JsonNode node, String feld) {
        JsonNode wert = node == null ? null : node.get(feld);
        return wert != null && wert.isTextual() ? wert.asText() : null;
    }

    private static void fuegeNummerHinzu(Set<String> ziel, String roh) {
        String nummer = normalisiereNummer(roh);
        if (nummer != null) {
            ziel.add(nummer);
        }
    }

    /**
     * Nur Buchstaben und Ziffern, Großschreibung. Werte ohne Ziffer ("telefonisch",
     * "Herr Mustermann") sind keine Nummer und werden verworfen.
     */
    static String normalisiereNummer(String nummer) {
        if (nummer == null) {
            return null;
        }
        String normalisiert = nummer.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (normalisiert.length() < 3 || normalisiert.chars().noneMatch(Character::isDigit)) {
            return null;
        }
        return normalisiert;
    }

    private static String ziffernKern(String nummer) {
        if (nummer == null) {
            return null;
        }
        String ziffern = nummer.replaceAll("[^0-9]", "").replaceFirst("^0++", "");
        return ziffern.length() >= MIN_ZIFFERN_KERN ? ziffern : null;
    }

    static String normalisiereKommission(String kommission) {
        if (kommission == null) {
            return null;
        }
        String normalisiert = kommission.toLowerCase(Locale.GERMAN).replaceAll("[^\\p{L}\\p{N}]", "");
        return normalisiert.length() >= MIN_KOMMISSION_LAENGE ? normalisiert : null;
    }

    /**
     * @param ohneEndung    die Nummer hat keine Revisions-Endung (= möglicher Ursprung)
     * @param revisionsWort die Endung nennt ausdrücklich eine Revision ("Rev", "V", "Index")
     */
    private record Nummernstamm(String stamm, boolean ohneEndung, boolean revisionsWort) {
    }

    private record Merkmale(LieferantDokumentTyp typ, String nummer, Nummernstamm nummernstamm,
            Set<String> referenzen,
            String bestellnummer, String kommission, LocalDate datum, BigDecimal netto, BigDecimal brutto,
            Set<String> artikel) {
    }

    private record Kandidat(LieferantDokument dokument, Merkmale merkmale) {
    }

    private record Bewertung(LieferantDokument dokument, int punkte) {
    }
}
