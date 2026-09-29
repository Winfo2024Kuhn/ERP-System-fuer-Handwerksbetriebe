package org.example.kalkulationsprogramm.service.telefon;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.SteuerberaterAnsprechpartner;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.example.kalkulationsprogramm.domain.TelefonAnruf;
import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.domain.TelefonZuordnung;
import org.example.kalkulationsprogramm.dto.Telefon.KontaktKurzDto;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Filter und Freitext-Suche der Anrufliste.
 *
 * <p>Die Suche arbeitet wortweise: Jedes Wort muss irgendwo beim Anruf oder beim
 * zugeordneten Kontakt vorkommen. „Max Würzburg" findet so den Anruf von
 * Max Mustermann aus Würzburg. Durchsucht werden Nummer und FRITZ!Box-Name,
 * beim Kunden Name, Ansprechpartner, Kundennummer, Adresse, Telefon und
 * E-Mail, dazu Bauvorhaben, Auftragsnummer und Baustellen-Adresse seiner
 * Projekte und Anfragen; beim Lieferanten Name, Kurzname, Vertreter,
 * Adresse, unsere Kundennummer, Telefon und E-Mail; beim Steuerberater
 * Kanzlei, Ansprechpartner (Name, Telefon, E-Mail), Telefon und E-Mail.</p>
 */
final class AnrufSuche {

    static final int MAX_SUCHE = 100;
    static final int MAX_WOERTER = 6;
    private static final char ESCAPE = '\\';

    private AnrufSuche() {
    }

    /**
     * @param tag        nur Anrufe dieses Tages (Ortszeit, 0:00 bis unter 24:00); null = alle Tage
     * @param kontaktart nur Anrufe von Kunden, Lieferanten oder Steuerberatern
     *                   ({@link KontaktKurzDto#KUNDE} usw.); null = alle
     */
    static Specification<TelefonAnruf> filter(TelefonAnrufArt art, boolean nurUnbekannt, Long kundeId,
                                              Long lieferantId, String suche, LocalDate tag, String kontaktart) {
        pruefeKontaktart(kontaktart);
        List<String> muster = suchmuster(suche);
        return (root, query, cb) -> {
            Join<TelefonAnruf, Kunde> kunde = kontaktJoin(root, query, "kunde");
            Join<TelefonAnruf, Lieferanten> lieferant = kontaktJoin(root, query, "lieferant");
            Join<TelefonAnruf, SteuerberaterKontakt> steuerberater = kontaktJoin(root, query, "steuerberater");

            List<Predicate> bedingungen = new ArrayList<>();
            if (art != null) {
                bedingungen.add(cb.equal(root.get("art"), art));
            }
            if (nurUnbekannt) {
                bedingungen.add(cb.equal(root.get("zuordnung"), TelefonZuordnung.KEINE));
            }
            if (kundeId != null) {
                bedingungen.add(cb.equal(kunde.get("id"), kundeId));
            }
            if (lieferantId != null) {
                bedingungen.add(cb.equal(lieferant.get("id"), lieferantId));
            }
            if (KontaktKurzDto.KUNDE.equals(kontaktart)) {
                bedingungen.add(cb.isNotNull(root.get("kunde")));
            } else if (KontaktKurzDto.LIEFERANT.equals(kontaktart)) {
                bedingungen.add(cb.isNotNull(root.get("lieferant")));
            } else if (KontaktKurzDto.STEUERBERATER.equals(kontaktart)) {
                bedingungen.add(cb.isNotNull(root.get("steuerberater")));
            }
            if (tag != null) {
                bedingungen.add(cb.greaterThanOrEqualTo(root.get("zeitpunkt"), tag.atStartOfDay()));
                bedingungen.add(cb.lessThan(root.get("zeitpunkt"), tag.plusDays(1).atStartOfDay()));
            }
            for (String wort : muster) {
                bedingungen.add(trifft(wort, root, kunde, lieferant, steuerberater, query, cb));
            }
            return cb.and(bedingungen.toArray(Predicate[]::new));
        };
    }

    /**
     * Zerlegt die Eingabe in klein geschriebene LIKE-Muster ({@code %wort%}),
     * höchstens {@link #MAX_WOERTER} Stück aus den ersten {@link #MAX_SUCHE} Zeichen.
     */
    static List<String> suchmuster(String suche) {
        if (suche == null || suche.isBlank()) {
            return List.of();
        }
        String s = suche.strip();
        if (s.length() > MAX_SUCHE) {
            s = s.substring(0, MAX_SUCHE);
        }
        return Arrays.stream(s.toLowerCase(Locale.ROOT).split("[\\s,;]++"))
                .filter(w -> !w.isEmpty())
                .distinct()
                .limit(MAX_WOERTER)
                .map(w -> "%" + escapeLike(w) + "%")
                .toList();
    }

    /**
     * Für die Liste werden Kunde und Lieferant gleich mitgeladen (kein N+1),
     * für die Zählabfrage nur verbunden – FETCH ist dort nicht erlaubt.
     */
    @SuppressWarnings("unchecked")
    private static <T> Join<TelefonAnruf, T> kontaktJoin(Root<TelefonAnruf> root, CriteriaQuery<?> query, String feld) {
        Class<?> ergebnis = query.getResultType();
        if (ergebnis == Long.class || ergebnis == long.class) {
            return root.join(feld, JoinType.LEFT);
        }
        // Hibernate liefert für FETCH ein Objekt, das zugleich Join ist.
        Object fetch = root.fetch(feld, JoinType.LEFT);
        return (Join<TelefonAnruf, T>) fetch;
    }

    private static void pruefeKontaktart(String kontaktart) {
        if (kontaktart != null && !KontaktKurzDto.KUNDE.equals(kontaktart) && !KontaktKurzDto.LIEFERANT.equals(kontaktart)
                && !KontaktKurzDto.STEUERBERATER.equals(kontaktart)) {
            throw new IllegalArgumentException("Unbekannte Kontaktart.");
        }
    }

    private static Predicate trifft(String muster, Root<TelefonAnruf> anruf, Join<TelefonAnruf, Kunde> kunde,
                                    Join<TelefonAnruf, Lieferanten> lieferant,
                                    Join<TelefonAnruf, SteuerberaterKontakt> steuerberater, CriteriaQuery<?> query,
                                    CriteriaBuilder cb) {
        List<Predicate> treffer = new ArrayList<>();
        felder(treffer, cb, muster, anruf, "nummerRoh", "nummerNormalisiert", "nameFritzbox");
        felder(treffer, cb, muster, kunde, "name", "ansprechspartner", "kundennummer", "strasse", "plz", "ort",
                "telefon", "mobiltelefon");
        felder(treffer, cb, muster, lieferant, "lieferantenname", "aliasName", "vertreter", "strasse", "plz", "ort",
                "eigeneKundennummer", "telefon", "mobiltelefon");
        felder(treffer, cb, muster, steuerberater, "name", "ansprechpartner", "telefon", "email");
        treffer.add(cb.exists(ansprechpartnerTrifft(steuerberater, muster, query, cb)));
        treffer.add(cb.exists(emailTrifft(Kunde.class, kunde, muster, query, cb)));
        treffer.add(cb.exists(emailTrifft(Lieferanten.class, lieferant, muster, query, cb)));
        treffer.add(cb.exists(projektTrifft(kunde, muster, query, cb)));
        treffer.add(cb.exists(anfrageTrifft(kunde, muster, query, cb)));
        return cb.or(treffer.toArray(Predicate[]::new));
    }

    private static void felder(List<Predicate> ziel, CriteriaBuilder cb, String muster, From<?, ?> quelle,
                               String... namen) {
        for (String name : namen) {
            ziel.add(wie(cb, quelle.get(name), muster));
        }
    }

    private static Predicate wie(CriteriaBuilder cb, Expression<String> feld, String muster) {
        return cb.like(cb.lower(feld), muster, ESCAPE);
    }

    private static <K> Subquery<Integer> emailTrifft(Class<K> typ, Join<TelefonAnruf, K> kontakt, String muster,
                                                     CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<K> k = sq.from(typ);
        Join<K, String> email = k.join("kundenEmails");
        return sq.select(cb.literal(1))
                .where(cb.equal(k, kontakt), wie(cb, email, muster));
    }

    private static Subquery<Integer> ansprechpartnerTrifft(Join<TelefonAnruf, SteuerberaterKontakt> steuerberater,
                                                           String muster, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<SteuerberaterAnsprechpartner> a = sq.from(SteuerberaterAnsprechpartner.class);
        return sq.select(cb.literal(1)).where(
                cb.equal(a.get("steuerberater"), steuerberater),
                cb.or(wie(cb, a.get("vorname"), muster),
                        wie(cb, a.get("nachname"), muster),
                        wie(cb, a.get("telefon"), muster),
                        wie(cb, a.get("email"), muster)));
    }

    private static Subquery<Integer> projektTrifft(Join<TelefonAnruf, Kunde> kunde, String muster,
                                                   CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<Projekt> p = sq.from(Projekt.class);
        return sq.select(cb.literal(1)).where(
                cb.equal(p.get("kundenId"), kunde),
                cb.or(wie(cb, p.get("bauvorhaben"), muster),
                        wie(cb, p.get("auftragsnummer"), muster),
                        wie(cb, p.get("strasse"), muster),
                        wie(cb, p.get("ort"), muster)));
    }

    private static Subquery<Integer> anfrageTrifft(Join<TelefonAnruf, Kunde> kunde, String muster,
                                                   CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<Anfrage> a = sq.from(Anfrage.class);
        return sq.select(cb.literal(1)).where(
                cb.equal(a.get("kunde"), kunde),
                cb.or(wie(cb, a.get("bauvorhaben"), muster),
                        wie(cb, a.get("projektStrasse"), muster),
                        wie(cb, a.get("projektOrt"), muster)));
    }

    static String escapeLike(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' || c == '_' || c == ESCAPE) {
                sb.append(ESCAPE);
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
