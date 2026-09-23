package org.example.kalkulationsprogramm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DokumentnummerServiceTest {
    private DokumentnummerService service;
    private TransactionTemplate outerTransaction;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:nummern;DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS dokumentnummer_counter");
        jdbc.execute("CREATE TABLE dokumentnummer_counter (id BIGINT AUTO_INCREMENT PRIMARY KEY, month_key VARCHAR(10) NOT NULL UNIQUE, counter BIGINT NOT NULL)");
        var transactionManager = new DataSourceTransactionManager(dataSource);
        service = new DokumentnummerService(jdbc, transactionManager);
        outerTransaction = new TransactionTemplate(transactionManager);
    }

    @Test
    void vergibtGetrennteEinkaufsNummernNachJahrUndKreis() {
        assertEquals("PA-2026-00001", service.naechsteEinkaufsnummer("PA", LocalDate.of(2026, 1, 4)));
        assertEquals("B-2026-00001", service.naechsteEinkaufsnummer("B", LocalDate.of(2026, 12, 31)));
        assertEquals("PA-2027-00001", service.naechsteEinkaufsnummer("PA", LocalDate.of(2027, 1, 1)));
        assertEquals("PA-2026-00002", service.naechsteEinkaufsnummer("PA", LocalDate.of(2026, 12, 1)));
    }

    @Test
    void reservierteVerkaufsnummerUeberlebtRollbackDerFachtransaktion() {
        outerTransaction.executeWithoutResult(status -> {
            assertEquals("09/00001", service.naechsteVerkaufsnummer(YearMonth.of(2026, 9)));
            status.setRollbackOnly();
        });

        assertEquals("09/00002", service.naechsteVerkaufsnummer(YearMonth.of(2026, 9)));
    }
}
