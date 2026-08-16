package org.example.kalkulationsprogramm.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Uebernimmt Preise aus den Positionen eines Lieferantendokuments in die
 * Preishistorie.
 *
 * <p>Gemeinsame Anlaufstelle fuer alle drei Wege, auf denen eine Eingangsrechnung
 * ins System kommt: ZUGFeRD-PDF, reine XRechnung-XML und die KI-Auswertung eines
 * gescannten PDFs. Vorher hatte jeder Weg seine eigene - oder gar keine -
 * Preislogik.
 *
 * <p><b>Keine stille Korrektur.</b> Frueher zwang die Uebernahme jeden Preis in
 * den fuer Stahl-Kilopreise gedachten Bereich 0,50-10,00 EUR. Auf Stueckpreise
 * angewendet hat das Werte verfaelscht (62,00 EUR -&gt; 0,62 EUR) oder kommentarlos
 * verworfen. Stattdessen werden die Einheiten beider Seiten ausgewertet.
 *
 * <p><b>Umrechnung.</b> Die Rechnung nennt einen Preis je Mengenbasis ("1000 KGM"
 * = je Tonne, "100 C62" = je 100 Stueck). Gespeichert wird immer der Preis je
 * <b>einer</b> Verrechnungseinheit - je Kilogramm, je Stueck, je Meter. Das ist
 * die Konvention der ganzen Anwendung: {@code ArtikelImportService} teilt den
 * CSV-Preis genauso durch die Preiseinheit, und die Kalkulation multipliziert den
 * gespeicherten Wert mit Gewicht bzw. Stueckzahl.
 *
 * <p>Das Artikelfeld {@code preiseinheit} bleibt hier bewusst aussen vor. Es haelt
 * fest, wie der Lieferant seinen Preis <i>angibt</i> (Schrauben je 100 Stueck),
 * nicht wie er gespeichert ist.
 *
 * <p>Widersprechen sich die Einheiten dem Wesen nach - die Rechnung nennt einen
 * Stueckpreis, der Artikel wird in Kilogramm gefuehrt -, bleibt der bisherige
 * Preis stehen und der Fall landet als Warnung im Log.
 */
@Slf4j
@Service
@AllArgsConstructor
public class PreisUebernahmeService {

    /**
     * Nachkommastellen der Spalte {@code lieferanten_artikel_preise.preis}.
     *
     * <p>Vier, seit Migration V359. Mit zweien rutschte "1,83 EUR je 100 Schrauben"
     * auf 0,02 EUR je Stueck statt 0,0183 - ein Aufschlag von 9 % auf jede
     * Kalkulation mit Kleinteilen.
     */
    private static final int PREIS_SKALA = 4;

    private static final BigDecimal TAUSEND = BigDecimal.valueOf(1000);

    /** Laenge der Spalte {@code lieferanten_artikel_preise.notiz}. */
    private static final int NOTIZ_MAX_LAENGE = 255;

    /** Trennt eine fuehrende Mengenangabe vom Einheitenkuerzel: "1000 KGM" -&gt; 1000 + "kgm". */
    private static final Pattern MENGE_UND_EINHEIT = Pattern.compile("^([0-9]+(?:[.,][0-9]+)?)\\s*(.*)$");

    /** "je" und "pro" nur als eigenstaendiges Wort entfernen, nicht als Silbe. */
    private static final Pattern FUELLWORT = Pattern.compile("\\b(je|pro)\\b");

    private final LieferantenArtikelPreiseRepository artikelPreiseRepository;

    /**
     * Eine Rechnungsposition, unabhaengig davon, aus welchem Format sie stammt.
     *
     * @param externeArtikelnummer Artikelnummer des Lieferanten
     * @param einzelpreis          Preis fuer die in {@code preiseinheit} genannte Menge
     * @param preiseinheit         Mengenbasis des Preises, z.B. "1000 KGM", "100 kg", "1 C62"
     * @param mengeneinheit        Einheit der gelieferten Menge, dient als Rueckfallebene
     */
    public record Position(String externeArtikelnummer, BigDecimal einzelpreis,
                           String preiseinheit, String mengeneinheit) {
    }

    /** Zaehlwerk fuer das Log - wie viele Positionen sind durchgekommen. */
    public record Ergebnis(int uebernommen, int uebersprungen) {
    }

    /**
     * Uebernimmt die Preise in einer eigenen Transaktion - der Einstieg fuer den
     * {@link PreisUebernahmeEventListener} nach dem Commit der Dokumentanalyse.
     *
     * <p>{@code REQUIRES_NEW} ist noetig, weil in der Phase {@code AFTER_COMMIT}
     * keine Transaktion mehr laeuft, von der die Schreibzugriffe erben koennten.
     * Die aeussere ist committet; ihre Verbindung wird erst in
     * {@code afterCompletion} freigegeben, es sind also fuer einen kurzen Moment
     * zwei Verbindungen gebunden - nicht mehr als vorher, aber auch nicht weniger.
     *
     * <p>Der Aufruf muss ueber die Bean-Grenze kommen, sonst greift der
     * Transaktions-Proxy nicht. Deshalb liegt der Listener in einer eigenen
     * Klasse; die Begruendung steht dort.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Ergebnis uebernehmePreiseInNeuerTransaktion(Lieferanten lieferant, PreisQuelle quelle, Date dokumentDatum,
                                                       String belegnummer, List<Position> positionen) {
        // Selbstaufruf ohne Proxy, aber ohne Folgen: uebernehmePreise traegt
        // REQUIRED und wuerde ohnehin genau diese Transaktion mitbenutzen.
        return uebernehmePreise(lieferant, quelle, dokumentDatum, belegnummer, positionen);
    }

    /**
     * Schreibt fuer jede zuordenbare Position einen neuen Preisstand.
     *
     * <p>Laeuft in der Transaktion des Aufrufers. Aus der Dokumentanalyse heraus
     * wird sie nicht mehr unmittelbar gerufen - dort meldet ein
     * {@link PreisUebernahmeEvent} den Bedarf nur an, und der
     * {@link PreisUebernahmeEventListener} bringt nach dem Commit ueber
     * {@link #uebernehmePreiseInNeuerTransaktion} die eigene Transaktion mit.
     * Frueher sass die Propagation {@code REQUIRES_NEW} deshalb hier, damit ein
     * Fehler in der Preisuebernahme die Analyse nicht mitreisst. Das erledigt
     * jetzt die Reihenfolge: Wenn hier ueberhaupt etwas passiert, ist die Analyse
     * bereits gespeichert.
     *
     * @param lieferant      Absender des Dokuments; ohne ihn ist keine Zuordnung moeglich
     * @param quelle         Herkunft des Preises, macht ihn in der Historie nachvollziehbar
     * @param dokumentDatum  fachliches Datum des Belegs; aeltere Belege ueberschreiben
     *                       einen juengeren Preisstand nicht. {@code null} = unbekannt
     * @param belegnummer    Nummer des Belegs; landet als Notiz am neuen Preisstand.
     *                       {@code null}, wenn das Dokument keine ausweist
     * @param positionen     die ausgelesenen Rechnungspositionen
     */
    @Transactional
    public Ergebnis uebernehmePreise(Lieferanten lieferant, PreisQuelle quelle, Date dokumentDatum,
                                     String belegnummer, List<Position> positionen) {
        if (lieferant == null || lieferant.getId() == null || positionen == null || positionen.isEmpty()) {
            return new Ergebnis(0, 0);
        }
        if (dokumentDatum == null) {
            // Ohne Belegdatum laesst sich nicht sagen, ob dieser Beleg neuer ist als
            // der gespeicherte Preis. Mit "jetzt" ersatzweise weiterzurechnen wuerde
            // die Stapel-Neuanalyse wieder kippen: sie laeuft von der neuesten zur
            // aeltesten Rechnung, am Ende gewaenne die aelteste.
            log.warn("Lieferant {}: Beleg ohne Datum - {} Position(en) nicht uebernommen",
                    lieferant.getLieferantenname(), positionen.size());
            return new Ergebnis(0, positionen.size());
        }

        // Veraenderlich: Jede geschriebene Position ersetzt hier ihren eigenen
        // Eintrag, damit die naechste Position mit derselben Artikelnummer den
        // gerade entstandenen Stand sieht und nicht den ueberholten.
        Map<String, List<LieferantenArtikelPreise>> bestand = ladeAktuelleStaende(positionen, lieferant);
        // Artikelnummern, fuer die dieser Lauf bereits einen Stand geschrieben hat.
        Set<String> ausDiesemLauf = new HashSet<>();

        int uebernommen = 0;
        int uebersprungen = 0;
        for (Position position : positionen) {
            try {
                if (uebernehmeEine(lieferant, quelle, dokumentDatum, belegnummer, position, bestand, ausDiesemLauf)) {
                    uebernommen++;
                } else {
                    uebersprungen++;
                }
            } catch (RuntimeException e) {
                // Eine unbrauchbare Position darf nicht die ganze Rechnung verwerfen.
                log.warn("Artikel {} bei Lieferant {}: Preis nicht uebernommen ({})",
                        position.externeArtikelnummer(), lieferant.getLieferantenname(), e.toString());
                uebersprungen++;
            }
        }

        log.info("Preisuebernahme fuer Lieferant {}: {} uebernommen, {} uebersprungen",
                lieferant.getLieferantenname(), uebernommen, uebersprungen);
        return new Ergebnis(uebernommen, uebersprungen);
    }

    /**
     * @param bestand       aktueller Preisstand je Artikelnummer; wird nach jedem
     *                      Schreiben fortgeschrieben
     * @param ausDiesemLauf Artikelnummern, die dieser Lauf schon angefasst hat
     * @return true, wenn ein neuer Preisstand geschrieben wurde
     */
    private boolean uebernehmeEine(Lieferanten lieferant, PreisQuelle quelle, Date stand,
                                   String belegnummer, Position position,
                                   Map<String, List<LieferantenArtikelPreise>> bestand,
                                   Set<String> ausDiesemLauf) {
        String nummer = position.externeArtikelnummer() == null ? "" : position.externeArtikelnummer().trim();
        if (nummer.isEmpty()) {
            log.debug("Position ohne Artikelnummer - keine Zuordnung moeglich");
            return false;
        }

        BigDecimal rechnungspreis = position.einzelpreis();
        if (rechnungspreis == null || rechnungspreis.signum() <= 0) {
            // Null- und Minusbetraege stehen fuer Freipositionen, Rabattzeilen und
            // Gutschriften - daraus laesst sich kein Einkaufspreis ableiten.
            log.debug("Artikel {}: kein verwertbarer Preis ({})", nummer, rechnungspreis);
            return false;
        }

        String schluessel = schluessel(nummer);
        Optional<LieferantenArtikelPreise> bisherOpt = ermittleAktuellenStand(nummer, lieferant, bestand);
        if (bisherOpt.isEmpty()) {
            return false;
        }
        LieferantenArtikelPreise bisher = bisherOpt.get();

        // Steht der bisherige Stand aus diesem Lauf, traegt er zwangslaeufig das
        // Datum dieses Belegs - der Vergleich unten wuerde jede weitere Position
        // derselben Artikelnummer verwerfen. Innerhalb eines Belegs gilt deshalb
        // die letzte Position, nicht die erste.
        boolean schonGeschrieben = ausDiesemLauf.contains(schluessel);
        if (!schonGeschrieben
                && bisher.getPreisAenderungsdatum() != null && !stand.after(bisher.getPreisAenderungsdatum())) {
            // Die Stapel-Neuanalyse laeuft von der neuesten zur aeltesten Rechnung.
            // Ohne diesen Vergleich wuerde am Ende die aelteste gewinnen.
            log.debug("Artikel {}: Beleg vom {} ist nicht juenger als der Preisstand vom {}",
                    nummer, stand, bisher.getPreisAenderungsdatum());
            return false;
        }

        Artikel artikel = bisher.getArtikel();
        Preisbasis rechnungsbasis = deutePreisbasis(position.preiseinheit(), position.mengeneinheit());
        Verrechnungseinheit artikelEinheit = artikel == null ? null : artikel.getVerrechnungseinheit();

        if (rechnungsbasis.art() != null && artikelEinheit != null && rechnungsbasis.art() != artikelEinheit) {
            log.warn("Artikel {} bei Lieferant {}: Rechnung nennt {} ({} EUR je {}), "
                            + "der Artikel wird aber in {} gefuehrt - Preis nicht uebernommen",
                    nummer, lieferant.getLieferantenname(), rechnungsbasis.art(), rechnungspreis,
                    position.preiseinheit(), artikelEinheit);
            return false;
        }

        BigDecimal neuerPreis = rechnungspreis.divide(rechnungsbasis.menge(), PREIS_SKALA, RoundingMode.HALF_UP);

        if (neuerPreis.signum() == 0) {
            // Die Preisspalte fuehrt vier Nachkommastellen. Was danach immer noch auf
            // 0,00 faellt, ist ein Rundungsartefakt und kein Einkaufspreis - es wuerde
            // einen gueltigen Bestandspreis zerstoeren. Seit V359 faengt die Bremse nur
            // noch solche echten Nullwerte ab; Cent-Artikel je 100 Stueck kommen jetzt
            // als 0,0183 durch, statt hier haengen zu bleiben.
            log.warn("Artikel {} bei Lieferant {}: {} EUR je {} ergibt gerundet 0,00 EUR - "
                            + "Preis nicht uebernommen",
                    nummer, lieferant.getLieferantenname(), rechnungspreis, position.preiseinheit());
            return false;
        }

        if (bisher.getPreis() != null && bisher.getPreis().compareTo(neuerPreis) == 0) {
            // Dieselbe Rechnung wird bei jeder Neuanalyse erneut gelesen. Ohne diese
            // Bremse wuechse der Preisverlauf mit lauter identischen Staenden zu.
            log.debug("Artikel {}: Preis unveraendert bei {} EUR", nummer, neuerPreis);
            return false;
        }

        LieferantenArtikelPreise neuerStand = bisher.neuerPreisstand(neuerPreis, quelle);
        neuerStand.setPreisAenderungsdatum(stand);
        neuerStand.setNotiz(belegnotiz(belegnummer));
        artikelPreiseRepository.save(bisher);
        artikelPreiseRepository.save(neuerStand);

        // Ohne diese Fortschreibung saehe eine zweite Position mit derselben
        // Artikelnummer weiterhin den eben entwerteten Stand: Sie wuerde gegen den
        // alten Preis vergleichen und einen zweiten Satz mit aktuell = true anlegen.
        // Der Artikel faellt danach dauerhaft in den Zweig "mehrere aktuelle
        // Preisstaende". Doppelte Positionen sind Alltag - Teillieferung, dieselbe
        // Schraubenkiste zweimal auf dem Beleg -, und keiner der Auslesewege fasst
        // sie vorher zusammen.
        bestand.put(schluessel, List.of(neuerStand));
        ausDiesemLauf.add(schluessel);

        log.info("Artikel {} bei Lieferant {}: Preis {} -> {} EUR (Quelle {}, Beleg vom {}, "
                        + "Rechnungsangabe {} je {})",
                nummer, lieferant.getLieferantenname(), bisher.getPreis(), neuerPreis, quelle, stand,
                rechnungspreis, position.preiseinheit());
        return true;
    }

    /**
     * Haelt am Preisstand fest, aus welchem Beleg er stammt.
     *
     * <p>Ohne die Nummer bleibt die Notiz leer statt einen nichtssagenden Platzhalter
     * zu fuehren - "Beleg null" waere in der Preishistorie schlicht falsch.
     *
     * @return die Notiz, oder {@code null} wenn keine Belegnummer vorliegt
     */
    private static String belegnotiz(String belegnummer) {
        if (belegnummer == null || belegnummer.isBlank()) {
            return null;
        }
        String notiz = "Beleg " + belegnummer.trim();
        return notiz.length() > NOTIZ_MAX_LAENGE ? notiz.substring(0, NOTIZ_MAX_LAENGE) : notiz;
    }

    /**
     * Schluessel des Bestands: die Artikelnummer in Grossschreibung.
     *
     * <p>Die Zuordnung muss gross-/kleinschreibungsunabhaengig bleiben, deshalb
     * derselbe Schluessel beim Laden, Nachschlagen und Fortschreiben - und
     * derselbe, den die Sammelabfrage per {@code UPPER(...)} bildet (siehe
     * {@link LieferantenArtikelPreiseRepository#findByLieferant_IdAndAktuellTrueAndExterneArtikelnummerIn}).
     */
    private static String schluessel(String artikelnummer) {
        return artikelnummer.toUpperCase(Locale.ROOT);
    }

    /**
     * Laedt die aktuellen Preisstaende aller in den Positionen genannten
     * Artikelnummern in einem Rutsch.
     *
     * <p>Vorher schlug jede Position einzeln nach - bei einer 50-Positionen-Rechnung
     * 50 Datenbank-Roundtrips.
     *
     * <p>Die zurueckgegebene Map ist <b>veraenderlich</b>: Die Uebernahme schreibt
     * dort jeden neuen Stand hinein, damit eine weitere Position mit derselben
     * Artikelnummer nicht gegen den ueberholten Stand rechnet.
     */
    private Map<String, List<LieferantenArtikelPreise>> ladeAktuelleStaende(List<Position> positionen,
                                                                             Lieferanten lieferant) {
        Set<String> nummernGross = positionen.stream()
                .map(Position::externeArtikelnummer)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(nummer -> !nummer.isEmpty())
                .map(PreisUebernahmeService::schluessel)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (nummernGross.isEmpty()) {
            return new HashMap<>();
        }

        List<LieferantenArtikelPreise> treffer = artikelPreiseRepository
                .findByLieferant_IdAndAktuellTrueAndExterneArtikelnummerIn(lieferant.getId(), nummernGross);
        return treffer.stream()
                .collect(Collectors.groupingBy(p -> schluessel(p.getExterneArtikelnummer()),
                        HashMap::new, Collectors.toList()));
    }

    /**
     * Liest den derzeit gueltigen Preisstand einer Position aus der vorab
     * geladenen Map.
     *
     * <p>Auf {@code aktuell} liegt kein Unique-Constraint. Altdaten koennen daher
     * mehrere aktuelle Staende je Artikelnummer tragen - das darf die Analyse
     * nicht mit einer Exception abbrechen, sondern nur diese eine Position
     * ueberspringen.
     */
    private Optional<LieferantenArtikelPreise> ermittleAktuellenStand(String nummer, Lieferanten lieferant,
                                                                       Map<String, List<LieferantenArtikelPreise>> bestand) {
        List<LieferantenArtikelPreise> treffer = bestand.getOrDefault(schluessel(nummer), List.of());
        if (treffer.isEmpty()) {
            log.debug("Artikelnummer {} ist bei Lieferant {} nicht hinterlegt",
                    nummer, lieferant.getLieferantenname());
            return Optional.empty();
        }
        if (treffer.size() > 1) {
            log.warn("Artikelnummer {} hat bei Lieferant {} mehrere aktuelle Preisstaende - "
                            + "Preis nicht uebernommen, Stammdaten pruefen",
                    nummer, lieferant.getLieferantenname());
            return Optional.empty();
        }
        return Optional.of(treffer.get(0));
    }

    /**
     * Mengenbasis eines Preises: "1000 KGM" bedeutet 1000 Kilogramm.
     *
     * @param menge Anzahl der Einheiten, auf die sich der Preis bezieht
     * @param art   die Einheit selbst, oder {@code null} wenn nicht deutbar
     */
    record Preisbasis(BigDecimal menge, Verrechnungseinheit art) {
    }

    /**
     * Deutet die Preisangabe einer Rechnungsposition.
     *
     * <p>Massgeblich ist die Preiseinheit (ZUGFeRD {@code BasisQuantity}, bei der KI
     * die Angabe "€/t", "100 kg", ...). Nennt sie keine bekannte Einheit, dient die
     * Mengeneinheit der Lieferung als Rueckfallebene - aber nur, wenn die
     * Preiseinheit ganz fehlt.
     *
     * <p>Steht dort etwas Unbekanntes ("6 m Stangen"), wird nicht geraten: dann
     * gilt Menge 1 und der Preis wird unveraendert uebernommen. Eine Zahl vor einem
     * unverstandenen Kuerzel als Teiler zu nehmen, waere Raten mit Ansage.
     */
    static Preisbasis deutePreisbasis(String preiseinheit, String mengeneinheit) {
        Preisbasis ausPreis = leseEinheit(preiseinheit);
        if (ausPreis.art() != null) {
            return ausPreis;
        }
        if (preiseinheit == null || preiseinheit.isBlank()) {
            return leseEinheit(mengeneinheit);
        }
        return ausPreis;
    }

    private static Preisbasis leseEinheit(String roh) {
        if (roh == null || roh.isBlank()) {
            return new Preisbasis(BigDecimal.ONE, null);
        }

        String text = roh.toLowerCase(Locale.ROOT)
                .replace("€", " ")
                .replace("eur", " ")
                .replace("/", " ");
        text = FUELLWORT.matcher(text).replaceAll(" ").trim();

        BigDecimal menge = BigDecimal.ONE;
        String code = text;
        Matcher matcher = MENGE_UND_EINHEIT.matcher(text);
        if (matcher.matches()) {
            menge = leseMenge(matcher.group(1));
            code = matcher.group(2).trim();
        }

        // Kuerzel: links die UN/ECE-Codes aus ZUGFeRD, rechts was die KI aus dem
        // PDF-Text liest.
        return switch (code) {
            case "kg", "kgm", "kilo", "kilogramm" -> new Preisbasis(menge, Verrechnungseinheit.KILOGRAMM);
            case "t", "to", "tne", "ton", "tonne", "tonnen" ->
                    new Preisbasis(menge.multiply(TAUSEND), Verrechnungseinheit.KILOGRAMM);
            case "g", "gr", "grm", "gramm" ->
                    new Preisbasis(menge.divide(TAUSEND, 6, RoundingMode.HALF_UP), Verrechnungseinheit.KILOGRAMM);
            case "c62", "h87", "ea", "pce", "pcs", "st", "stk", "stck", "stueck", "stück", "piece" ->
                    new Preisbasis(menge, Verrechnungseinheit.STUECK);
            case "mtr", "m", "lm", "lfm", "lfdm", "meter", "laufmeter" ->
                    new Preisbasis(menge, Verrechnungseinheit.LAUFENDE_METER);
            case "mtk", "m2", "m²", "qm", "quadratmeter" ->
                    new Preisbasis(menge, Verrechnungseinheit.QUADRATMETER);
            // Unbekanntes Kuerzel: die gelesene Zahl gehoert nicht zwingend zu einer
            // Mengenbasis, also nicht damit teilen.
            default -> new Preisbasis(BigDecimal.ONE, null);
        };
    }

    /**
     * Liest die Mengenangabe vor der Einheit.
     *
     * <p>Der Punkt ist mehrdeutig: "1.000 kg" ist deutsche Tausendertrennung,
     * "1.5 kg" englische Dezimalschreibweise. Drei Ziffern hinter dem Punkt und
     * keine weitere Stelle sprechen fuer den Tausender - "1.000" als 1 zu lesen
     * haette einen Tonnenpreis unveraendert als Kilopreis gebucht.
     */
    private static BigDecimal leseMenge(String roh) {
        String text = roh.trim();
        if (text.matches("\\d{1,3}\\.\\d{3}")) {
            text = text.replace(".", "");
        } else {
            text = text.replace(',', '.');
        }
        try {
            BigDecimal menge = new BigDecimal(text);
            return menge.signum() > 0 ? menge : BigDecimal.ONE;
        } catch (NumberFormatException e) {
            return BigDecimal.ONE;
        }
    }
}
