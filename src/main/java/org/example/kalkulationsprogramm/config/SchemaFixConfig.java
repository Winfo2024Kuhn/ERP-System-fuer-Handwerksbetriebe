package org.example.kalkulationsprogramm.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Configuration
public class SchemaFixConfig {

    @Bean
    public CommandLineRunner schemaFixer(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                // Fix for cost center assignment: Allow projekt_id to be NULL
                // Check if index/constraint exists involves more logic, but simply trying to alter is safe enough for local dev if it handles existing state
                // MySQL syntax
                jdbcTemplate.execute("ALTER TABLE bestellung_projekt_zuordnung MODIFY COLUMN projekt_id BIGINT NULL");
                System.out.println("✅ Schema Fix: bestellung_projekt_zuordnung.projekt_id is now NULLABLE");
            } catch (Exception e) {
                // Ignore if it fails (e.g. table doesn't exist yet or other DB specific issues, but log it)
                System.out.println("ℹ️ Schema Fix Header: " + e.getMessage());
            }
        };
    }
}
