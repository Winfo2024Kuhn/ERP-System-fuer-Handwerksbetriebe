package org.example.kalkulationsprogramm.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Bedingung fuer ein Wort aus dem Freitext der Artikelsuche.
 *
 * <p>Zahlen werden als ganze Zahl gesucht, nicht als Teilstueck: "flachstahl 20x10"
 * findet "Flachstahl 20 x 10", aber nicht mehr "Flachstahl 120 x 10" oder
 * "Flachstahl 100 x 20" - die "20" steckt dort nur in einer anderen Zahl. "20"
 * findet auch "20,0", aber nicht "20,5". Komma und Punkt gelten als dasselbe
 * Dezimalzeichen ("42,4" findet "42.4").
 *
 * <p>Nummern (eigene Artikelnummer, Lieferanten-Nummer, Werkstoffnummer) duerfen ab
 * vier Zeichen weiter als Teilstueck getroffen werden: Wer "3270" tippt, sucht
 * "A-003270", und "4301" meint "1.4301".
 *
 * <p>Woerter ohne Zahl am Rand bleiben Teilstuecke ("flach" findet "Flachstahl").
 * Umlaute und ihre Umschreibung gelten als gleich ("träger" findet "traeger").
 */
public final class ArtikelFreitextSuche {

    /** Ab dieser Laenge trifft ein Wort mit Ziffern Nummern auch als Teilstueck. */
    private static final int MINDESTLAENGE_TEILNUMMER = 4;

    /** Vor einer Zahl: Anfang, kein Ziffer/Dezimalzeichen, oder ein Dezimalzeichen ohne Ziffer davor. */
    private static final String ZAHL_ANFANG = "(^|[^0-9.,]|[^0-9][.,])";

    /** Nach einer Zahl: optional ",0"/".00", dann Ende, keine Ziffer und kein Dezimalteil. */
    private static final String ZAHL_ENDE = "([.,]0+)?($|[^0-9.,]|[.,]($|[^0-9]))";

    private static final String REGEX_ZEICHEN = "\\^$.|?*+()[]{}";

    private ArtikelFreitextSuche() {
    }

    /** Bedingung fuer die ganze Eingabe: Jedes Wort daraus muss vorkommen. */
    public static Specification<Artikel> fuer(String eingabe) {
        Specification<Artikel> spec = (root, cq, cb) -> cb.conjunction();
        for (String wort : zerlege(eingabe)) {
            spec = spec.and(wort(wort));
        }
        return spec;
    }

    /**
     * Zerlegt eine Sucheingabe in die Woerter, die alle vorkommen muessen.
     *
     * <p>Neben Leerzeichen trennt auch das Malzeichen zwischen zwei Zahlen, damit
     * "42.4x2" zu "42.4" und "2" wird - Anwender schreiben Abmessungen mal mit
     * und mal ohne Leerzeichen. Ein Komma zwischen zwei Ziffern ist ein
     * Dezimalkomma ("42,4") und trennt nicht. Ebenso wird ein dem Mass vorangestelltes
     * "x" abgetrennt, denn "42.4 x2" ist eine ebenso uebliche Schreibweise. Ein
     * allein stehendes "x" faellt weg.
     */
    static List<String> zerlege(String eingabe) {
        if (eingabe == null || eingabe.isBlank()) {
            return List.of();
        }
        return Arrays.stream(eingabe.trim().toLowerCase(Locale.GERMAN).split("[\\s;]+|(?<!\\d),|,(?!\\d)"))
                .flatMap(teil -> Arrays.stream(teil.split("(?<=\\d)[x*](?=\\d)")))
                // "x2" als eigenes Wort: das Malzeichen gehoert nicht zur Zahl.
                .map(w -> w.replaceFirst("^[x*](?=\\d)", ""))
                .map(String::trim)
                .filter(w -> !w.isEmpty() && !"x".equals(w) && !"*".equals(w))
                .distinct()
                .toList();
    }

    /** Bedingung: Das Wort kommt am Artikel, seinem Werkstoff oder bei einem seiner Lieferanten vor. */
    static Specification<Artikel> wort(String wort) {
        Suchwort s = Suchwort.aus(wort);
        return (root, cq, cb) -> {
            Join<Artikel, Werkstoff> werkstoff = root.join("werkstoff", JoinType.LEFT);
            Subquery<Long> lieferantTreffer = cq.subquery(Long.class);
            Root<LieferantenArtikelPreise> preis = lieferantTreffer.from(LieferantenArtikelPreise.class);
            Join<LieferantenArtikelPreise, Lieferanten> lieferant = preis.join("lieferant", JoinType.LEFT);
            lieferantTreffer.select(cb.literal(1L));
            lieferantTreffer.where(
                    cb.equal(preis.get("artikel"), root),
                    cb.or(
                            s.nummer(cb, preis.get("externeArtikelnummer")),
                            s.text(cb, lieferant.get("lieferantenname"))));
            return cb.or(
                    // Der Suchtext buendelt alle Schreibweisen und Synonyme.
                    s.text(cb, root.get("suchtext")),
                    s.text(cb, root.get("produktname")),
                    s.text(cb, root.get("produktlinie")),
                    s.text(cb, root.get("produkttext")),
                    s.nummer(cb, root.get("artikelnummer")),
                    s.nummer(cb, werkstoff.get("name")),
                    // "V2A" statt "1.4301": in der Werkstatt wird der
                    // Kurzname getippt, nicht die Werkstoffnummer.
                    s.text(cb, werkstoff.get("werkstattname")),
                    cb.exists(lieferantTreffer));
        };
    }

    /**
     * Ein Suchwort, fertig aufbereitet: als regulaerer Ausdruck, wenn am Rand eine
     * Ziffer steht, sonst als LIKE-Muster in allen Umlaut-Schreibweisen.
     */
    record Suchwort(String regex, List<String> likeMuster, String teilnummerMuster) {

        static Suchwort aus(String roh) {
            String wort = roh.trim().toLowerCase(Locale.GERMAN);
            boolean zifferVorn = !wort.isEmpty() && Character.isDigit(wort.charAt(0));
            boolean zifferHinten = !wort.isEmpty() && Character.isDigit(wort.charAt(wort.length() - 1));
            String teilnummer = wort.chars().anyMatch(Character::isDigit) && wort.length() >= MINDESTLAENGE_TEILNUMMER
                    ? like(wort) : null;
            if (!zifferVorn && !zifferHinten) {
                return new Suchwort(null, umlautVarianten(wort).stream().map(Suchwort::like).toList(), teilnummer);
            }
            String regex = (zifferVorn ? ZAHL_ANFANG : "") + regexKern(wort) + (zifferHinten ? ZAHL_ENDE : "");
            return new Suchwort(regex, List.of(), teilnummer);
        }

        /** Wort in einem beschreibenden Feld (Name, Text, Suchtext). */
        Predicate text(CriteriaBuilder cb, Expression<String> feld) {
            Expression<String> klein = cb.lower(feld);
            if (regex != null) {
                return cb.isTrue(cb.function("regexp_like", Boolean.class, klein, gebunden(cb, regex)));
            }
            return cb.or(likeMuster.stream().map(m -> cb.like(klein, m)).toArray(Predicate[]::new));
        }

        /** Wort in einem Nummernfeld: zusaetzlich als Teilstueck, wenn es lang genug ist. */
        Predicate nummer(CriteriaBuilder cb, Expression<String> feld) {
            Predicate ganz = text(cb, feld);
            return teilnummerMuster == null ? ganz : cb.or(ganz, cb.like(cb.lower(feld), teilnummerMuster));
        }

        /**
         * Das Muster als gebundener Parameter statt als Text im SQL: Die Eingabe steht so nie im
         * SQL-Text, und jede Suche nutzt dieselbe vorbereitete Abfrage.
         */
        private static Expression<String> gebunden(CriteriaBuilder cb, String wert) {
            return cb instanceof HibernateCriteriaBuilder hcb ? hcb.value(wert) : cb.literal(wert);
        }

        private static String like(String wort) {
            return "%" + wort + "%";
        }

        /** Escaped Sonderzeichen; Komma und Punkt werden beide zu "Dezimalzeichen". */
        private static String regexKern(String wort) {
            StringBuilder sb = new StringBuilder();
            for (char c : wort.toCharArray()) {
                if (c == '.' || c == ',') {
                    sb.append("[.,]");
                } else if (REGEX_ZEICHEN.indexOf(c) >= 0) {
                    sb.append('\\').append(c);
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        /** "träger" und "traeger" - in beide Richtungen, damit Eingabe und Bestand zueinander finden. */
        static Set<String> umlautVarianten(String wort) {
            Set<String> varianten = new LinkedHashSet<>();
            varianten.add(wort);
            varianten.add(wort.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss"));
            varianten.add(wort.replace("ae", "ä").replace("oe", "ö").replace("ue", "ü"));
            return varianten;
        }
    }
}
