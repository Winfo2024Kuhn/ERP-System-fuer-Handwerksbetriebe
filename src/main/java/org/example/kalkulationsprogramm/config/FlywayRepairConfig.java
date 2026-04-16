package org.example.kalkulationsprogramm.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy repairAndMigrateStrategy() {
        return flyway -> {
            // Repariert zunaechst etwaige FAILED Migrationen (loescht den failed status aus der schema_history Tabelle)
            flyway.repair();
            // Startet danach die normale Migration
            flyway.migrate();
        };
    }
}
