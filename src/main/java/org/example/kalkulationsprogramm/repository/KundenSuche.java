package org.example.kalkulationsprogramm.repository;

import java.util.Locale;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Suche der Kundenliste ({@code GET /api/kunden}): Freitext, einzelne Felder, E-Mail und
 * Typ (Kunde mit Projekt / Anfrager ohne Projekt).
 *
 * <p>Bewusst ohne {@code DISTINCT}: Die Liste sortiert ohne Groß-/Kleinschreibung
 * ({@code ORDER BY lower(name)}), und PostgreSQL verlangt bei {@code SELECT DISTINCT}, dass
 * jeder Sortierausdruck auch ausgewählt wird (Fehler 42P10 - Filter "Typ" und "E-Mail" der
 * Kundenliste lieferten dort einen Fehler statt Treffer). Die E-Mail-Suche läuft als
 * {@code EXISTS}-Unterabfrage, der Typ über {@code EXISTS}/{@code NOT EXISTS} auf die Projekte;
 * doppelte Zeilen entstehen so erst gar nicht. Gleiches Vorgehen wie {@link LieferantenSuche}.</p>
 */
public final class KundenSuche {

    /** Sortierung der Liste: nach Name, ohne Groß-/Kleinschreibung. */
    public static final Sort SORTIERUNG = Sort.by(Sort.Order.asc("name").ignoreCase());

    private KundenSuche() {
    }

    /**
     * @param freitext sucht in Name, Kundennummer, Ort, Straße, Ansprechpartner, Telefonnummern und E-Mail-Adressen
     * @param email    sucht in den hinterlegten E-Mail-Adressen
     * @param typ      {@code KUNDE} (hat Projekte) oder {@code ANFRAGER} (noch keins), sonst alle
     */
    public static Specification<Kunde> spezifikation(String freitext, String name, String kundennummer, String ort,
            String email, String typ) {
        return spezifikation(freitext, name, kundennummer, ort, email, typ, false);
    }

    /**
     * Wie {@link #spezifikation(String, String, String, String, String, String)}. Aus Datenschutzgründen
     * gesperrte Kunden (Issue #264) erscheinen nur auf ausdrücklichen Wunsch.
     *
     * @param gesperrte {@code true} = nur gesperrte Kunden, {@code false} = nur nicht gesperrte
     */
    public static Specification<Kunde> spezifikation(String freitext, String name, String kundennummer, String ort,
            String email, String typ, boolean gesperrte) {
        Specification<Kunde> spec = Specification.where((root, cq, cb) -> gesperrte
                ? cb.isNotNull(root.get("gesperrtAm"))
                : cb.isNull(root.get("gesperrtAm")));
        if (StringUtils.hasText(freitext)) {
            final String muster = muster(freitext);
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), muster),
                    cb.like(cb.lower(root.get("kundennummer")), muster),
                    cb.like(cb.lower(root.get("ort")), muster),
                    cb.like(cb.lower(root.get("strasse")), muster),
                    cb.like(cb.lower(root.get("ansprechspartner")), muster),
                    cb.like(cb.lower(cb.coalesce(root.get("telefon"), "")), muster),
                    cb.like(cb.lower(cb.coalesce(root.get("mobiltelefon"), "")), muster),
                    cb.exists(emailTreffer(root, cq, cb, muster))));
        }
        spec = spec.and(feld("name", name));
        spec = spec.and(feld("kundennummer", kundennummer));
        spec = spec.and(feld("ort", ort));
        if (StringUtils.hasText(email)) {
            final String muster = muster(email);
            spec = spec.and((root, cq, cb) -> cb.exists(emailTreffer(root, cq, cb, muster)));
        }
        if ("KUNDE".equalsIgnoreCase(typ)) {
            spec = spec.and((root, cq, cb) -> cb.isNotEmpty(root.get("projekts")));
        } else if ("ANFRAGER".equalsIgnoreCase(typ)) {
            spec = spec.and((root, cq, cb) -> cb.isEmpty(root.get("projekts")));
        }
        return spec;
    }

    /** Unterabfrage: hat der Kunde eine E-Mail-Adresse, die auf das Muster passt? */
    private static Subquery<String> emailTreffer(Root<Kunde> root, CriteriaQuery<?> cq, CriteriaBuilder cb,
            String muster) {
        Subquery<String> treffer = cq.subquery(String.class);
        Root<Kunde> kunde = treffer.correlate(root);
        ListJoin<Kunde, String> adresse = kunde.joinList("kundenEmails");
        treffer.select(adresse).where(cb.like(cb.lower(adresse), muster));
        return treffer;
    }

    private static Specification<Kunde> feld(String feld, String wert) {
        if (!StringUtils.hasText(wert)) {
            return Specification.unrestricted();
        }
        final String muster = muster(wert);
        return (root, cq, cb) -> cb.like(cb.lower(root.get(feld)), muster);
    }

    private static String muster(String wert) {
        return ("%" + wert.trim() + "%").toLowerCase(Locale.GERMAN);
    }
}
