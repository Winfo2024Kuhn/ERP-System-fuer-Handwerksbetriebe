package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.service.DokumentnummerService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class DokumentnummerParallelTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44")
            .withDatabaseName("nummern_dummy")
            .withUsername("test")
            .withPassword("test");

    @Test
    void vergibtParalleleErstnummernEindeutigUndCommitUnabhaengig() throws Exception {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE dokumentnummer_counter (id BIGINT AUTO_INCREMENT PRIMARY KEY, month_key VARCHAR(10) NOT NULL UNIQUE, counter BIGINT NOT NULL)");
        var transactionManager = new DataSourceTransactionManager(dataSource);
        var service = new DokumentnummerService(jdbc, transactionManager);
        var numbers = new ConcurrentLinkedQueue<String>();
        var pool = Executors.newFixedThreadPool(30);
        var ready = new java.util.concurrent.CountDownLatch(30);
        var start = new java.util.concurrent.CountDownLatch(1);
        for (int i = 0; i < 30; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    numbers.add(service.naechsteEinkaufsnummer("PA", LocalDate.of(2026, 9, 23)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });
        }
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        org.junit.jupiter.api.Assertions.assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(30, new HashSet<>(numbers).size());
        assertEquals("PA-2026-00031", service.naechsteEinkaufsnummer("PA", LocalDate.of(2026, 9, 23)));

        var outer = new TransactionTemplate(transactionManager);
        outer.executeWithoutResult(status -> {
            assertEquals("B-2026-00001", service.naechsteEinkaufsnummer("B", LocalDate.of(2026, 9, 23)));
            status.setRollbackOnly();
        });
        assertEquals("B-2026-00002", service.naechsteEinkaufsnummer("B", LocalDate.of(2026, 9, 23)));
    }
}
