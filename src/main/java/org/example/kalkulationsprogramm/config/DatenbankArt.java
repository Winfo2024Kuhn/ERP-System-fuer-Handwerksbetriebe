package org.example.kalkulationsprogramm.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

import javax.sql.DataSource;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;

/**
 * Welche Datenbank laeuft gerade? Der eigene Produktivserver nutzt MySQL,
 * Kunden-Installationen PostgreSQL (Profil "postgres"), die Release-.exe H2.
 *
 * <p>Fast alles ist datenbankneutral (JPQL, Hibernate-Dialekt). Nur die wenigen
 * nativen SQL-Abfragen, die es portabel nicht gibt (Upsert, Datumsrechnung),
 * fragen hier nach und waehlen ihre Variante.
 */
@Component
public class DatenbankArt {

    private final boolean postgres;

    @Autowired
    public DatenbankArt(DataSource dataSource) {
        this.postgres = istPostgres(dataSource);
    }

    /** Fuer Tests ohne Datenbank. */
    DatenbankArt(boolean postgres) {
        this.postgres = postgres;
    }

    public static DatenbankArt fuerTest(boolean postgres) {
        return new DatenbankArt(postgres);
    }

    public boolean istPostgres() {
        return postgres;
    }

    /**
     * Fuer Repository-Fragmente: liest den Hibernate-Dialekt direkt am
     * EntityManager ab. Braucht kein eigenes Bean - so laufen auch
     * {@code @DataJpaTest}-Slices, die nur JPA-Bestandteile laden.
     */
    public static boolean istPostgres(EntityManager entityManager) {
        return entityManager.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class)
                .getJdbcServices().getDialect() instanceof PostgreSQLDialect;
    }

    static boolean istPostgres(DataSource dataSource) {
        try (Connection verbindung = dataSource.getConnection()) {
            String produkt = verbindung.getMetaData().getDatabaseProductName();
            return produkt != null && produkt.toLowerCase(Locale.ROOT).contains("postgres");
        } catch (SQLException e) {
            throw new IllegalStateException("Datenbankart konnte nicht ermittelt werden", e);
        }
    }
}
