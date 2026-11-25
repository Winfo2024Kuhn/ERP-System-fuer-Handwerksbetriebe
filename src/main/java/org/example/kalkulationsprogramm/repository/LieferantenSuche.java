package org.example.kalkulationsprogramm.repository;

import java.util.Locale;

import org.example.kalkulationsprogramm.domain.LieferantAnsprechpartner;
import org.example.kalkulationsprogramm.domain.LieferantRolle;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Suche der Lieferantenliste ({@code GET /api/lieferanten}): Freitext und einzelne Felder.
 *
 * <p>Bewusst ohne {@code DISTINCT}: Die Liste sortiert ohne Groß-/Kleinschreibung
 * ({@code ORDER BY lower(lieferantenname)}), und PostgreSQL verlangt bei {@code SELECT DISTINCT},
 * dass jeder Sortierausdruck auch ausgewählt wird (Fehler 42P10). Die Suche in den
 * E-Mail-Adressen läuft deshalb als {@code EXISTS}-Unterabfrage statt als Join - so entstehen
 * erst gar keine doppelten Zeilen, auch bei mehreren passenden Adressen.</p>
 */
public final class LieferantenSuche {

    /** Sortierung der Liste: nach Name, ohne Groß-/Kleinschreibung. */
    public static final Sort SORTIERUNG = Sort.by(Sort.Order.asc("lieferantenname").ignoreCase());

    private LieferantenSuche() {
    }

    /**
     * @param freitext sucht in Name, zweitem Namen, Typ, Ort, Straße und Telefonnummern sowie bei den
     *                 Ansprechpartnern in Vorname, Nachname, Aufgabe, Telefon, Handy und E-Mail
     * @param email    sucht in den hinterlegten E-Mail-Adressen
     */
    public static Specification<Lieferanten> spezifikation(String freitext, String name, String typ, String ort,
            String email) {
        return spezifikation(freitext, name, typ, ort, email, null);
    }

    /**
     * Wie {@link #spezifikation(String, String, String, String, String)}, zusätzlich gefiltert nach einer
     * Rolle. Die Rolle steht in einer eigenen Tabelle ({@code lieferanten_rollen}); {@code MEMBER OF} wird
     * zu einer Unterabfrage, die Zeilen bleiben also einfach.
     *
     * @param rolle nur Lieferanten mit dieser Rolle; {@code null} = alle
     */
    public static Specification<Lieferanten> spezifikation(String freitext, String name, String typ, String ort,
            String email, LieferantRolle rolle) {
        Specification<Lieferanten> spec = Specification.unrestricted();
        if (StringUtils.hasText(freitext)) {
            final String muster = muster(freitext);
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("lieferantenname")), muster),
                    cb.like(cb.lower(cb.coalesce(root.get("aliasName"), "")), muster),
                    cb.like(cb.lower(root.get("lieferantenTyp")), muster),
                    cb.like(cb.lower(root.get("ort")), muster),
                    cb.like(cb.lower(root.get("strasse")), muster),
                    cb.like(cb.lower(cb.coalesce(root.get("telefon"), "")), muster),
                    cb.like(cb.lower(cb.coalesce(root.get("mobiltelefon"), "")), muster),
                    ansprechpartnerTrifft(root, cq, cb, muster)));
        }
        spec = spec.and(feld("lieferantenname", name));
        spec = spec.and(feld("lieferantenTyp", typ));
        spec = spec.and(feld("ort", ort));
        if (StringUtils.hasText(email)) {
            final String muster = muster(email);
            spec = spec.and((root, cq, cb) -> {
                Subquery<String> treffer = cq.subquery(String.class);
                Root<Lieferanten> lieferant = treffer.correlate((Root<Lieferanten>) root);
                ListJoin<Lieferanten, String> adresse = lieferant.joinList("kundenEmails");
                treffer.select(adresse).where(cb.like(cb.lower(adresse), muster));
                return cb.exists(treffer);
            });
        }
        if (rolle != null) {
            spec = spec.and((root, cq, cb) -> cb.isMember(rolle, root.<java.util.Set<LieferantRolle>>get("rollen")));
        }
        return spec;
    }

    /** Ein Ansprechpartner passt – als {@code EXISTS}, damit keine doppelten Zeilen entstehen. */
    private static Predicate ansprechpartnerTrifft(Root<Lieferanten> root, CriteriaQuery<?> cq, CriteriaBuilder cb,
            String muster) {
        Subquery<Integer> treffer = cq.subquery(Integer.class);
        Root<LieferantAnsprechpartner> a = treffer.from(LieferantAnsprechpartner.class);
        treffer.select(cb.literal(1)).where(
                cb.equal(a.get("lieferant"), root),
                cb.or(cb.like(cb.lower(cb.coalesce(a.get("vorname"), "")), muster),
                        cb.like(cb.lower(a.get("nachname")), muster),
                        cb.like(cb.lower(cb.coalesce(a.get("aufgabe"), "")), muster),
                        cb.like(cb.lower(cb.coalesce(a.get("telefon"), "")), muster),
                        cb.like(cb.lower(cb.coalesce(a.get("mobiltelefon"), "")), muster),
                        cb.like(cb.lower(cb.coalesce(a.get("email"), "")), muster)));
        return cb.exists(treffer);
    }

    /**
     * Suche der Handy-App ({@code GET /api/zeiterfassung/lieferanten?search=}): Name, zweiter Name,
     * E-Mail-Adressen und Ansprechpartner (Vorname, Nachname, Aufgabe, Telefon, Handy, E-Mail) –
     * auch inaktive Lieferanten. Ohne Suchwort nur die aktiven.
     */
    public static Specification<Lieferanten> handySuche(String suchwort) {
        if (!StringUtils.hasText(suchwort)) {
            return (root, cq, cb) -> cb.isTrue(root.get("istAktiv"));
        }
        final String muster = muster(suchwort);
        return (root, cq, cb) -> {
            Subquery<String> adresse = cq.subquery(String.class);
            Root<Lieferanten> lieferant = adresse.correlate(root);
            ListJoin<Lieferanten, String> email = lieferant.joinList("kundenEmails");
            adresse.select(email).where(cb.like(cb.lower(email), muster));
            return cb.or(
                    cb.like(cb.lower(root.get("lieferantenname")), muster),
                    cb.like(cb.lower(cb.coalesce(root.get("aliasName"), "")), muster),
                    cb.exists(adresse),
                    ansprechpartnerTrifft(root, cq, cb, muster));
        };
    }

    private static Specification<Lieferanten> feld(String feld, String wert) {
        if (!StringUtils.hasText(wert)) {
            return Specification.unrestricted();
        }
        final String muster = muster(wert);
        return (root, cq, cb) -> cb.like(cb.lower(root.get(feld)), muster);
    }

    private static String muster(String wert) {
        return ("%" + wert.trim() + "%").toLowerCase(Locale.ROOT);
    }
}
