package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Kosten;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAngebotService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class EinkaufAngebotServiceTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44")
            .withDatabaseName("einkauf_angebote_dummy").withUsername("test").withPassword("test");

    @Test
    void kommunikationsUndAngebotsmigrationenLegenTabellenUndSeparateAbweichungsfreigabeAn() throws Exception {
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE email (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
                statement.execute("CREATE TABLE einkaufsanfrage_lieferant (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
                statement.execute("CREATE TABLE einkaufsanfrage_revision (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
                statement.execute("CREATE TABLE einkaufsanfrage_position (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
                statement.execute("CREATE TABLE einkauf_datei (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
            }
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("db/migration/V388__einkauf_kommunikation.sql")));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("db/migration/V389__einkauf_angebote.sql")));
            try (var statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                            + "AND table_name = 'einkauf_angebot_version' AND column_name IN "
                            + "('abweichung_bestaetigt_von','abweichung_bestaetigt_am','abweichung_bestaetigung')")) {
                org.junit.jupiter.api.Assertions.assertTrue(rows.next());
                org.junit.jupiter.api.Assertions.assertEquals(3, rows.getInt(1));
            }
        }
    }

    @Test
    void bestaetigteHistorischeAngebotsfassungWirdBeiNeuerVersionAlsAbgeloestMarkiert() {
        var version = new org.example.kalkulationsprogramm.domain.einkauf.AngebotVersion(
                null, null, 1, "A-1", null, null, "EUR", null, null, null, null, null);
        version.bestaetigen(9L);

        version.abloesen();

        org.junit.jupiter.api.Assertions.assertEquals("ABGELOEST", version.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(9L, version.getBestaetigtVon());
    }

    @Test
    void erneuteErfassungUeberschreibtBestehendeAngebotsversionNicht() {
        var offers = mock(org.example.kalkulationsprogramm.repository.EinkaufAngebotRepository.class);
        var versions = mock(org.example.kalkulationsprogramm.repository.AngebotVersionRepository.class);
        var participations = mock(org.example.kalkulationsprogramm.repository.AnfrageLieferantRepository.class);
        var revisions = mock(org.example.kalkulationsprogramm.repository.AnfrageRevisionRepository.class);
        var emails = mock(org.example.kalkulationsprogramm.repository.EmailRepository.class);
        var files = mock(org.example.kalkulationsprogramm.repository.EinkaufDateiRepository.class);
        var attachments = mock(org.example.kalkulationsprogramm.repository.EmailAttachmentRepository.class);
        var supplierDocs = mock(org.example.kalkulationsprogramm.repository.LieferantDokumentRepository.class);
        var participation = mock(org.example.kalkulationsprogramm.domain.einkauf.AnfrageLieferant.class);
        var offer = mock(org.example.kalkulationsprogramm.domain.einkauf.EinkaufAngebot.class);
        when(participations.findById(5L)).thenReturn(Optional.of(participation));
        when(offers.findByBeteiligungId(5L)).thenReturn(Optional.of(offer));
        when(offer.getVersionen()).thenReturn(List.of(mock(org.example.kalkulationsprogramm.domain.einkauf.AngebotVersion.class)));
        var service = new EinkaufAngebotService(offers, versions, participations, revisions, emails, files, attachments, supplierDocs);

        assertThrows(IllegalStateException.class, () -> service.erfassen(5L, null, 9L));
    }

    @Test
    void lehntZyklischeProzentkostenAb() {
        var freight = new Kosten("F", "FRACHT", BigDecimal.TEN, "PROZENT", BigDecimal.ONE, "R", false, false, "Mail");
        var rabatt = new Kosten("R", "RABATT", BigDecimal.ONE, "PROZENT", BigDecimal.ONE, "F", false, false, "Mail");
        assertThrows(IllegalArgumentException.class, () -> EinkaufAngebotService.pruefeProzentbasiszyklen(List.of(freight, rabatt)));
    }

    @Test
    void akzeptiertProzentkostenMitVorherigerExpliziterBasis() {
        var material = new Kosten("M", "MATERIAL", BigDecimal.TEN, "STUECK", BigDecimal.ONE, null, false, false, "E-Mail");
        var freight = new Kosten("F", "FRACHT", BigDecimal.ONE, "PROZENT", BigDecimal.TEN, "M", false, false, "E-Mail");
        assertDoesNotThrow(() -> EinkaufAngebotService.pruefeProzentbasiszyklen(List.of(material, freight)));
    }
}
