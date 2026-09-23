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
                statement.execute("CREATE TABLE email (id BIGINT NOT NULL PRIMARY KEY, konto_id VARCHAR(16) NOT NULL, direction ENUM('IN','OUT') NOT NULL) ENGINE=InnoDB");
                statement.execute("CREATE TABLE einkaufsanfrage_lieferant (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
                statement.execute("CREATE TABLE einkaufsanfrage_revision (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
                statement.execute("CREATE TABLE einkaufsanfrage_position (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
                statement.execute("CREATE TABLE einkauf_datei (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
            }
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("db/migration/V388__einkauf_kommunikation.sql")));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("db/migration/V389__einkauf_angebote.sql")));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("db/migration/V388__einkauf_kommunikation.sql")));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("db/migration/V389__einkauf_angebote.sql")));
            try (var statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                            + "AND table_name = 'einkauf_angebot_version' AND column_name IN "
                            + "('abweichung_bestaetigt_von','abweichung_bestaetigt_am','abweichung_bestaetigung')")) {
                org.junit.jupiter.api.Assertions.assertTrue(rows.next());
                org.junit.jupiter.api.Assertions.assertEquals(3, rows.getInt(1));
            }
            try (var statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                            + "AND table_name = 'einkauf_kommunikation_vorschau'")) {
                org.junit.jupiter.api.Assertions.assertTrue(rows.next());
                org.junit.jupiter.api.Assertions.assertEquals(1, rows.getInt(1));
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
        var service = new EinkaufAngebotService(offers, versions, participations, revisions, emails, files, attachments, supplierDocs, mock(org.example.kalkulationsprogramm.repository.EinkaufMailZuordnungRepository.class));

        assertThrows(IllegalStateException.class, () -> service.erfassen(5L, null, 9L));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "false,true,ANFRAGE,11,5,22", "true,false,ANFRAGE,11,5,22",
        "true,true,BESTELLUNG,11,5,22", "true,true,ANFRAGE,99,5,22",
        "true,true,ANFRAGE,11,99,22", "true,true,ANFRAGE,11,5,99"
    })
    void quellmailBrauchtBestaetigteZuordnungZurGenauenAnfragefassung(boolean vorhanden, boolean bestaetigt,
            String typ, Long anfrageId, Long beteiligungId, Long revisionId) {
        var fixture = quellmailFixture(vorhanden, bestaetigt, typ, anfrageId, beteiligungId, revisionId);
        var error = assertThrows(IllegalArgumentException.class,
                () -> fixture.service().erfassen(5L, fixture.request(), 9L));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("bestätigte Zuordnung"));
    }

    @Test
    void passendBestaetigteQuellmailWirdAlsAngebotsquelleUebernommen() {
        var fixture = quellmailFixture(true, true, "ANFRAGE", 11L, 5L, 22L);
        org.junit.jupiter.api.Assertions.assertEquals(42L,
                fixture.service().erfassen(5L, fixture.request(), 9L).emailId());
    }

    private SourceFixture quellmailFixture(boolean vorhanden, boolean bestaetigt, String typ,
            Long anfrageId, Long beteiligungId, Long revisionId) {
        var offers = mock(org.example.kalkulationsprogramm.repository.EinkaufAngebotRepository.class);
        var versions = mock(org.example.kalkulationsprogramm.repository.AngebotVersionRepository.class);
        var participations = mock(org.example.kalkulationsprogramm.repository.AnfrageLieferantRepository.class);
        var revisions = mock(org.example.kalkulationsprogramm.repository.AnfrageRevisionRepository.class);
        var emails = mock(org.example.kalkulationsprogramm.repository.EmailRepository.class);
        var links = mock(org.example.kalkulationsprogramm.repository.EinkaufMailZuordnungRepository.class);
        var participation = mock(org.example.kalkulationsprogramm.domain.einkauf.AnfrageLieferant.class,
                org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(participation.getId()).thenReturn(5L);
        when(participation.getRevision().getId()).thenReturn(22L);
        when(participation.getRevision().getAnfrage().getId()).thenReturn(11L);
        when(participation.getKontakt()).thenReturn(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot(1L, 1L, "Dummy", "test@example.com", "Max Mustermann", null, null));
        var requestPosition = mock(org.example.kalkulationsprogramm.domain.einkauf.AnfragePosition.class);
        when(requestPosition.getId()).thenReturn(33L);
        when(participation.getRevision().getPositionen()).thenReturn(List.of(requestPosition));
        var requestRevision = participation.getRevision();
        when(revisions.findById(22L)).thenReturn(Optional.of(requestRevision));
        when(participations.findById(5L)).thenReturn(Optional.of(participation));
        var offer = new org.example.kalkulationsprogramm.domain.einkauf.EinkaufAngebot(participation);
        org.springframework.test.util.ReflectionTestUtils.setField(offer, "id", 77L);
        when(offers.findByBeteiligungId(5L)).thenReturn(Optional.of(offer));
        when(offers.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        var email = new org.example.kalkulationsprogramm.domain.Email();
        email.setKontoId("EINKAUF"); email.setFromAddress("test@example.com");
        email.setDirection(org.example.kalkulationsprogramm.domain.EmailDirection.IN);
        when(emails.findById(42L)).thenReturn(Optional.of(email));
        var link = new org.example.kalkulationsprogramm.domain.einkauf.EinkaufMailZuordnung(42L);
        if (bestaetigt) link.bestaetigen(typ, anfrageId, beteiligungId, revisionId, "Quelle geprüft", 9L, "ANGEBOT");
        else link.automatisch(typ, anfrageId, beteiligungId, revisionId, "ANGEBOT", "CODE_ABSENDER");
        when(links.findByEmailId(42L)).thenReturn(vorhanden ? Optional.of(link) : Optional.empty());
        var service = new EinkaufAngebotService(offers, versions, participations, revisions, emails,
                mock(org.example.kalkulationsprogramm.repository.EinkaufDateiRepository.class),
                mock(org.example.kalkulationsprogramm.repository.EmailAttachmentRepository.class),
                mock(org.example.kalkulationsprogramm.repository.LieferantDokumentRepository.class), links);
        var position = new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Position(
                33L, "P1", "Profil", null, null, null, null, List.of(), List.of(), List.of());
        var request = new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Erfassung(
                22L, "A1", null, null, "EUR", List.of(position), List.of(), null, null, null, 42L, null);
        return new SourceFixture(service, request);
    }

    private record SourceFixture(EinkaufAngebotService service,
            org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Erfassung request) {}

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
