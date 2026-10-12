package org.example.kalkulationsprogramm.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class EmailGelesenRepositoryErweiterungImpl implements EmailGelesenRepositoryErweiterung {

    /** So viele IDs je Abfrage – PostgreSQL erlaubt nur begrenzt viele Parameter. */
    static final int BLOCKGROESSE = 1000;

    private static final String GELESENE = "SELECT g.email.id FROM EmailGelesen g "
            + "WHERE g.benutzer.id = :benutzerId AND g.email.id IN :emailIds";

    private static final String MERKEN = "INSERT INTO email_gelesen (benutzer_id, email_id, gelesen_am) "
            + "SELECT :benutzerId, e.id, :jetzt FROM email e WHERE e.id IN (:emailIds) "
            + "AND NOT EXISTS (SELECT 1 FROM email_gelesen g WHERE g.benutzer_id = :benutzerId AND g.email_id = e.id)";

    /**
     * Fängt auf PostgreSQL den gleichzeitigen Doppelklick ab, ohne die Transaktion abzubrechen. Die
     * H2-Testdatenbank kennt das nicht – dort genügt das {@code NOT EXISTS}.
     */
    private static final String NUR_POSTGRESQL = " ON CONFLICT (benutzer_id, email_id) DO NOTHING";

    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Set<Long> geleseneAus(Long benutzerId, Collection<Long> emailIds) {
        Set<Long> gelesen = new HashSet<>();
        for (List<Long> block : bloecke(emailIds)) {
            gelesen.addAll(entityManager.createQuery(GELESENE, Long.class)
                    .setParameter("benutzerId", benutzerId)
                    .setParameter("emailIds", block)
                    .getResultList());
        }
        return gelesen;
    }

    @Override
    @Transactional
    public void alsGelesenMerken(Long benutzerId, Collection<Long> emailIds, LocalDateTime jetzt) {
        String sql = istPostgreSql() ? MERKEN + NUR_POSTGRESQL : MERKEN;
        for (List<Long> block : bloecke(emailIds)) {
            entityManager.createNativeQuery(sql)
                    .setParameter("benutzerId", benutzerId)
                    .setParameter("emailIds", block)
                    .setParameter("jetzt", jetzt)
                    .executeUpdate();
        }
    }

    private boolean istPostgreSql() {
        return entityManager.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class)
                .getJdbcServices().getDialect() instanceof PostgreSQLDialect;
    }

    static List<List<Long>> bloecke(Collection<Long> ids) {
        List<Long> eindeutig = new ArrayList<>(new LinkedHashSet<>(ids));
        eindeutig.removeIf(Objects::isNull);
        List<List<Long>> bloecke = new ArrayList<>();
        for (int start = 0; start < eindeutig.size(); start += BLOCKGROESSE) {
            bloecke.add(eindeutig.subList(start, Math.min(start + BLOCKGROESSE, eindeutig.size())));
        }
        return bloecke;
    }
}
