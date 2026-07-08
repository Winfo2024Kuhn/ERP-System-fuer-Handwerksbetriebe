package org.example.kalkulationsprogramm.config

import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@Configuration
class SchemaFixConfig {
    @Bean
    fun schemaFixer(jdbcTemplate: JdbcTemplate, dataSource: DataSource): CommandLineRunner = CommandLineRunner {
        val databaseProductName = dataSource.connection.use { it.metaData.databaseProductName.lowercase() }
        if ("mysql" !in databaseProductName && "mariadb" !in databaseProductName) {
            return@CommandLineRunner
        }

        try {
            jdbcTemplate.execute("ALTER TABLE bestellung_projekt_zuordnung MODIFY COLUMN projekt_id BIGINT NULL")
            println("Schema Fix: bestellung_projekt_zuordnung.projekt_id is now NULLABLE")
        } catch (e: Exception) {
            println("Schema Fix Header: ${e.message}")
        }
    }
}
