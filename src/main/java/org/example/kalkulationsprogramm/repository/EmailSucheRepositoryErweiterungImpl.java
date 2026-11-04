package org.example.kalkulationsprogramm.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.EmailOrdner;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

/**
 * Baut die Suchabfrage aus festen Bausteinen ({@link EmailOrdnerSql}, {@link EmailSichtbarkeitSql}) und
 * benannten Parametern.
 * Nutzereingaben stehen nie im SQL-Text: die Parameternamen ({@code w0}, {@code w1}, …) zählt der
 * Code selbst hoch.
 */
@RequiredArgsConstructor
class EmailSucheRepositoryErweiterungImpl implements EmailSucheRepositoryErweiterung {

    /** Domains kommen aus {@code EmailAdressen.domainVon} – nur diese Zeichen sind möglich. */
    private static final Pattern DOMAIN = Pattern.compile("[a-z0-9.-]++");

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<Long> sucheIds(EmailSuchAbfrage abfrage) {
        if (abfrage.woerter().isEmpty() || keineSteuerberater(abfrage)) {
            return List.of();
        }
        Map<String, Object> parameter = new HashMap<>();
        StringBuilder sql = new StringBuilder("SELECT e.id FROM email e WHERE (")
                .append(EmailOrdnerSql.bedingung(abfrage.ordner())).append(')');
        if (abfrage.ordner() == EmailOrdner.TAX_ADVISORS) {
            parameter.put(EmailOrdnerSql.STEUERBERATER_MUSTER, steuerberaterMuster(abfrage.steuerberaterDomains()));
        }
        if (abfrage.sichtbarePostfachIds() != null) {
            sql.append(" AND ").append(EmailSichtbarkeitSql.SIEHT);
            parameter.put(EmailSichtbarkeitSql.PARAMETER,
                    EmailSichtbarkeitSql.parameterWert(abfrage.sichtbarePostfachIds()));
        }
        woerterAnhaengen(sql, parameter, abfrage.woerter());
        sql.append(" ORDER BY e.sent_at DESC NULLS LAST, e.id DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        parameter.forEach(query::setParameter);
        query.setFirstResult(Math.max(0, abfrage.offset()));
        query.setMaxResults(Math.max(1, abfrage.limit()));
        return ((List<Object>) query.getResultList()).stream()
                .map(id -> ((Number) id).longValue())
                .toList();
    }

    private static void woerterAnhaengen(StringBuilder sql, Map<String, Object> parameter, List<String> woerter) {
        for (int i = 0; i < woerter.size(); i++) {
            sql.append(" AND e.suchtext LIKE :w").append(i).append(" ESCAPE '\\'");
            parameter.put("w" + i, woerter.get(i));
        }
    }

    private static boolean keineSteuerberater(EmailSuchAbfrage abfrage) {
        return abfrage.ordner() == EmailOrdner.TAX_ADVISORS
                && steuerberaterMuster(abfrage.steuerberaterDomains()).isEmpty();
    }

    /**
     * {@code @(kanzlei\.de|steuer\.de)($|[^a-z0-9.-])}: Adresse genau aus einer der Domains;
     * leer, wenn es keine (gültige) Domain gibt.
     */
    static String steuerberaterMuster(Set<String> domains) {
        String auswahl = domains.stream()
                .filter(d -> d != null && DOMAIN.matcher(d).matches())
                .sorted()
                .map(d -> d.replace(".", "\\."))
                .collect(Collectors.joining("|"));
        return auswahl.isEmpty() ? "" : "@(" + auswahl + ")($|[^a-z0-9.-])";
    }
}
