package org.example.kalkulationsprogramm.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.EmailBezug;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

/**
 * Setzt die Bausteine aus {@link EmailReiterSql} und {@link EmailSichtbarkeitSql} zusammen und bindet
 * alle Werte als benannte Parameter.
 */
@RequiredArgsConstructor
class EmailReiterRepositoryErweiterungImpl implements EmailReiterRepositoryErweiterung {

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<EmailVerlaufZeile> verlaeufe(EmailReiterAbfrage abfrage) {
        Map<String, Object> parameter = new HashMap<>();
        Query query = abfrage(EmailReiterSql.seite(anker(abfrage, parameter)), parameter);
        query.setFirstResult(Math.max(0, abfrage.offset()));
        query.setMaxResults(Math.max(1, abfrage.limit()));
        return ((List<Object[]>) query.getResultList()).stream()
                .map(zeile -> new EmailVerlaufZeile(zahl(zeile[0]), zahl(zeile[1])))
                .toList();
    }

    @Override
    public long zaehleVerlaeufe(EmailReiterAbfrage abfrage) {
        Map<String, Object> parameter = new HashMap<>();
        Object anzahl = abfrage(EmailReiterSql.anzahl(anker(abfrage, parameter)), parameter).getSingleResult();
        return anzahl == null ? 0L : zahl(anzahl);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<EmailVerlaufKennzahl> kennzahlen(Collection<Long> wurzelIds, Set<Long> sichtbarePostfachIds) {
        if (wurzelIds == null || wurzelIds.isEmpty()) {
            return List.of();
        }
        Map<String, Object> parameter = new HashMap<>();
        parameter.put(EmailReiterSql.WURZELN, wurzelIds);
        String lesbar = "TRUE";
        if (sichtbarePostfachIds != null) {
            lesbar = EmailSichtbarkeitSql.LIEST;
            parameter.put(EmailSichtbarkeitSql.PARAMETER, EmailSichtbarkeitSql.parameterWert(sichtbarePostfachIds));
        }
        return ((List<Object[]>) abfrage(EmailReiterSql.kennzahlen(lesbar), parameter).getResultList()).stream()
                .map(z -> new EmailVerlaufKennzahl(zahl(z[0]), (int) zahl(z[1]), zahl(z[2]) > 0, (String) z[3]))
                .toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<EmailVerlaufAnhang> anhaenge(EmailReiterAbfrage abfrage, Collection<Long> wurzelIds) {
        if (wurzelIds == null || wurzelIds.isEmpty()) {
            return List.of();
        }
        Map<String, Object> parameter = new HashMap<>();
        parameter.put(EmailReiterSql.WURZELN, wurzelIds);
        parameter.put(EmailReiterSql.BEZUG_ID, abfrage.bezugId());
        return ((List<Object[]>) abfrage(EmailReiterSql.anhaenge(abfrage.bezug()), parameter).getResultList())
                .stream()
                .map(z -> new EmailVerlaufAnhang(zahl(z[0]), zahl(z[1])))
                .toList();
    }

    @Override
    public boolean bezugVorhanden(EmailBezug bezug, long id) {
        Object anzahl = entityManager.createNativeQuery(EmailReiterSql.vorhanden(bezug))
                .setParameter(EmailReiterSql.BEZUG_ID, id)
                .getSingleResult();
        return anzahl != null && zahl(anzahl) > 0;
    }

    /**
     * Abfrage {@code id, parent_id, sent_at} der Mails des Bereichs, die zur Abfrage passen: je Teil des Bereichs
     * ein {@code SELECT} mit denselben Filtern, verbunden mit {@code UNION ALL}. Füllt die Parameter.
     */
    static String anker(EmailReiterAbfrage abfrage, Map<String, Object> parameter) {
        StringBuilder filter = new StringBuilder();
        parameter.put(EmailReiterSql.BEZUG_ID, abfrage.bezugId());
        if (abfrage.sichtbarePostfachIds() != null) {
            filter.append(" AND ").append(EmailSichtbarkeitSql.LIEST);
            parameter.put(EmailSichtbarkeitSql.PARAMETER,
                    EmailSichtbarkeitSql.parameterWert(abfrage.sichtbarePostfachIds()));
        }
        if (abfrage.richtung() != null) {
            filter.append(" AND e.direction = :").append(EmailReiterSql.RICHTUNG);
            parameter.put(EmailReiterSql.RICHTUNG, abfrage.richtung().name());
        }
        List<String> woerter = abfrage.woerter();
        for (int i = 0; i < woerter.size(); i++) {
            filter.append(" AND e.suchtext LIKE :w").append(i).append(" ESCAPE '\\'");
            parameter.put("w" + i, woerter.get(i));
        }
        return EmailReiterSql.bezugTeile(abfrage.bezug()).stream()
                .map(teil -> "SELECT e.id, e.parent_email_id AS parent_id, e.sent_at " + teil + filter)
                .collect(Collectors.joining(" UNION ALL "));
    }

    private Query abfrage(String sql, Map<String, Object> parameter) {
        Query query = entityManager.createNativeQuery(sql);
        parameter.forEach(query::setParameter);
        return query;
    }

    private static long zahl(Object wert) {
        return ((Number) wert).longValue();
    }
}
