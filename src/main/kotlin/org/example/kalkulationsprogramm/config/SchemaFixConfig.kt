package org.example.kalkulationsprogramm.config

import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.slf4j.LoggerFactory
import javax.sql.DataSource

@Configuration
class SchemaFixConfig {
    private val log = LoggerFactory.getLogger(SchemaFixConfig::class.java)

    @Bean
    fun schemaFixer(jdbcTemplate: JdbcTemplate, dataSource: DataSource): CommandLineRunner = CommandLineRunner {
        val databaseProductName = dataSource.connection.use { it.metaData.databaseProductName.lowercase() }
        if ("mysql" !in databaseProductName && "mariadb" !in databaseProductName) {
            return@CommandLineRunner
        }

        try {
            jdbcTemplate.execute("ALTER TABLE bestellung_projekt_zuordnung MODIFY COLUMN projekt_id BIGINT NULL")
            log.info("Schema fix applied: bestellung_projekt_zuordnung.projekt_id is now nullable")
        } catch (e: Exception) {
            log.debug("Schema fix skipped or already applied", e)
        }
    }
}
