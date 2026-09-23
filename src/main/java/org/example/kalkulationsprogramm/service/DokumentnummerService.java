package org.example.kalkulationsprogramm.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
public class DokumentnummerService {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate nummernTransaktion;

    public DokumentnummerService(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.nummernTransaktion = new TransactionTemplate(transactionManager);
        this.nummernTransaktion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String naechsteEinkaufsnummer(String nummernkreis, LocalDate datum) {
        if (!"PA".equals(nummernkreis) && !"B".equals(nummernkreis)) {
            throw new IllegalArgumentException("Der Einkaufsnummernkreis muss PA oder B sein.");
        }
        if (datum == null) {
            throw new IllegalArgumentException("Das Datum muss angegeben werden.");
        }
        String key = nummernkreis + "-" + datum.getYear();
        return nummernTransaktion.execute(status -> nummerFuerSchluessel(key,
                nummernkreis + "-" + datum.getYear() + "-"));
    }

    public String naechsteVerkaufsnummer(YearMonth periode) {
        if (periode == null) {
            throw new IllegalArgumentException("Die Periode muss angegeben werden.");
        }
        String key = "%04d%02d".formatted(periode.getYear(), periode.getMonthValue());
        return nummernTransaktion.execute(status -> {
            long nummer = naechsterZaehler(key);
            return "%02d/%05d".formatted(periode.getMonthValue(), nummer);
        });
    }

    private String nummerFuerSchluessel(String key, String prefix) {
        long nummer = naechsterZaehler(key);
        return "%s%05d".formatted(prefix, nummer);
    }

    private long naechsterZaehler(String key) {
        jdbcTemplate.update("INSERT INTO dokumentnummer_counter (month_key, counter) VALUES (?, 0) "
                + "ON DUPLICATE KEY UPDATE counter = counter", key);
        Long aktuellerWert = jdbcTemplate.queryForObject(
                "SELECT counter FROM dokumentnummer_counter WHERE month_key = ? FOR UPDATE", Long.class, key);
        if (aktuellerWert == null || aktuellerWert == Long.MAX_VALUE) {
            throw new IllegalStateException("Der Dokumentnummern-Zähler ist ungültig oder ausgeschöpft.");
        }
        long naechsterWert = aktuellerWert + 1;
        jdbcTemplate.update("UPDATE dokumentnummer_counter SET counter = ? WHERE month_key = ?", naechsterWert, key);
        return naechsterWert;
    }
}
