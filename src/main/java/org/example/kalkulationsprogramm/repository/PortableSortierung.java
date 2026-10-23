package org.example.kalkulationsprogramm.repository;

import java.util.List;

import jakarta.persistence.criteria.Nulls;
import org.hibernate.metamodel.model.domain.ManagedDomainType;
import org.hibernate.metamodel.model.domain.PersistentAttribute;
import org.hibernate.query.criteria.JpaOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;

/**
 * Sortierung fuer Listen mit frei waehlbarer Sortierspalte, bei der leere Werte
 * stehen wie gewohnt.
 *
 * <p>Leere Werte ({@code NULL}) gelten - wie seit jeher auf MySQL - als kleinster
 * Wert: aufsteigend stehen sie oben, absteigend unten. PostgreSQL sortiert sie von
 * sich aus als groessten Wert ein; "neueste zuerst" zeigte dort sonst zuerst alle
 * Eintraege ohne Datum.
 *
 * <p>Spring Data reicht {@link Sort.NullHandling} an Criteria-Abfragen nicht weiter,
 * deshalb haengt {@link #mit(Specification, Sort)} die Sortierung selbst an die
 * Abfrage; die Seitenangabe geht ohne Sortierung an das Repository
 * ({@link #ohneSortierung(Pageable)}):
 * <pre>
 * repository.findAll(PortableSortierung.mit(filter, seite.getSort()), PortableSortierung.ohneSortierung(seite));
 * </pre>
 * Verweise ({@code "artikel.werkstoff.name"}) werden per LEFT JOIN aufgeloest wie bei
 * Spring Data, {@code ignoreCase()} sortiert nach {@code lower(...)}.
 */
public final class PortableSortierung {

    private PortableSortierung() {
    }

    /** Der Filter plus die Sortierung - die Zaehlabfrage der Seite bleibt unsortiert. */
    public static <T> Specification<T> mit(Specification<T> filter, Sort sortierung) {
        if (sortierung == null || sortierung.isUnsorted()) {
            return filter;
        }
        return (root, query, cb) -> {
            var bedingung = filter == null ? null : filter.toPredicate(root, query, cb);
            Class<?> ergebnis = query.getResultType();
            if (ergebnis != Long.class && ergebnis != long.class) {
                query.orderBy(sortierung.stream().map(o -> reihenfolge(o, root, cb)).toList());
            }
            return bedingung;
        };
    }

    /** Die Seite ohne Sortierung - die steckt schon in {@link #mit(Specification, Sort)}. */
    public static Pageable ohneSortierung(Pageable seite) {
        return seite.isPaged() ? PageRequest.of(seite.getPageNumber(), seite.getPageSize()) : Pageable.unpaged();
    }

    static Order reihenfolge(Sort.Order order, Root<?> root, CriteriaBuilder cb) {
        Expression<?> ausdruck = pfad(root, order.getProperty(), cb);
        if (order.isIgnoreCase() && String.class.equals(ausdruck.getJavaType())) {
            @SuppressWarnings("unchecked")
            Expression<String> text = (Expression<String>) ausdruck;
            ausdruck = cb.lower(text);
        }
        JpaOrder reihenfolge = (JpaOrder) (order.isAscending() ? cb.asc(ausdruck) : cb.desc(ausdruck));
        return reihenfolge.nullPrecedence(leerwerte(order));
    }

    /** Ausdrueckliche Angabe am Sort.Order geht vor, sonst wie bisher: leer ist der kleinste Wert. */
    static Nulls leerwerte(Sort.Order order) {
        return switch (order.getNullHandling()) {
            case NULLS_FIRST -> Nulls.FIRST;
            case NULLS_LAST -> Nulls.LAST;
            case NATIVE -> order.isAscending() ? Nulls.FIRST : Nulls.LAST;
        };
    }

    private static Expression<?> pfad(Root<?> root, String eigenschaft, CriteriaBuilder cb) {
        String[] teile = eigenschaft.split("\\.");
        From<?, ?> von = root;
        for (int i = 0; i < teile.length - 1; i++) {
            von = verbinden(von, teile[i]);
        }
        return feld(von, teile[teile.length - 1], cb);
    }

    /**
     * Ein Feld, das nur eine Unterklasse hat (z. B. {@code masse} von {@code ArtikelWerkstoffe}
     * in der Artikelliste), loest Hibernate 7 nicht mehr von selbst auf - es braucht ein
     * {@code treat}. Zeilen ohne dieses Feld bleiben in der Liste und zaehlen als leer.
     */
    private static Expression<?> feld(From<?, ?> von, String attribut, CriteriaBuilder cb) {
        if (von instanceof Root<?> wurzel && wurzel.getModel() instanceof ManagedDomainType<?> typ
                && typ.findAttribute(attribut) == null) {
            PersistentAttribute<?, ?> ausUnterklasse = typ.findSubTypesAttribute(attribut);
            if (ausUnterklasse != null) {
                return alsUnterklasse(wurzel, ausUnterklasse.getDeclaringType().getJavaType(), cb).get(attribut);
            }
        }
        return von.get(attribut);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Root<?> alsUnterklasse(Root wurzel, Class unterklasse, CriteriaBuilder cb) {
        return cb.treat(wurzel, unterklasse);
    }

    /** Vorhandenen LEFT JOIN wiederverwenden (z. B. aus dem Filter), sonst neu anlegen. */
    private static From<?, ?> verbinden(From<?, ?> von, String attribut) {
        List<? extends Join<?, ?>> vorhanden = von.getJoins().stream()
                .filter(j -> j.getAttribute().getName().equals(attribut) && j.getJoinType() == JoinType.LEFT)
                .toList();
        return vorhanden.isEmpty() ? von.join(attribut, JoinType.LEFT) : vorhanden.get(0);
    }
}
