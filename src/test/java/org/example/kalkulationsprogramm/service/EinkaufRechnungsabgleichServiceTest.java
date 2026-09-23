package org.example.kalkulationsprogramm.service.einkauf;


import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class EinkaufRechnungsabgleichServiceTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44")
            .withDatabaseName("einkauf_rechnungsabgleich_dummy").withUsername("test").withPassword("test");

    @Test
    void summiertTeilrechnungenUndVergleichtNurMitDerVereinbartenMenge() {
        var stand = EinkaufRechnungsabgleichService.mengenstand(
                new BigDecimal("4"), new BigDecimal("2"), new BigDecimal("2"));
        assertEquals(0, stand.offen().compareTo(BigDecimal.ZERO));
        assertEquals(0, stand.differenz().compareTo(BigDecimal.ZERO));
    }

    @Test
    void zeigtUeberabrechnungAlsPositiveAbweichungStattSieZuVerbergen() {
        var stand = EinkaufRechnungsabgleichService.mengenstand(
                new BigDecimal("4"), new BigDecimal("4"), new BigDecimal("80"));
        assertEquals(0, stand.offen().compareTo(BigDecimal.ZERO));
        assertEquals(0, stand.differenz().compareTo(new BigDecimal("80")));
    }

    @Test
    void rechnetGutschriftUndStornoMitNegativemVorzeichen() {
        assertEquals(0, EinkaufRechnungsabgleichService.signed("GUTSCHRIFT", new BigDecimal("80")).compareTo(new BigDecimal("-80")));
        assertEquals(0, EinkaufRechnungsabgleichService.signed("STORNO", new BigDecimal("2")).compareTo(new BigDecimal("-2")));
        assertEquals(0, EinkaufRechnungsabgleichService.signed("RECHNUNG", new BigDecimal("2")).compareTo(new BigDecimal("2")));
    }

    @Test
    void persistiertTeilrechnungenUndKorrekturMitEinmaligerFracht() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE lieferant_dokument(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
            statement.execute("CREATE TABLE einkauf_bestellung(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
            statement.execute("CREATE TABLE einkauf_bestellung_position(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
            statement.execute("CREATE TABLE lieferant_reklamation(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V395__einkauf_rechnungsabgleich.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V395__einkauf_rechnungsabgleich.sql"));
            statement.execute("INSERT INTO einkauf_bestellung VALUES (5)");
            statement.execute("INSERT INTO einkauf_bestellung_position VALUES (7)");
            statement.execute("INSERT INTO lieferant_dokument VALUES (11),(12),(13)");
            statement.execute("INSERT INTO einkauf_beleg_zuordnung(dokument_id,bestellung_id,art,idempotenz_key,payload_hash,akteur_id) VALUES "
                    + "(11,5,'RECHNUNG','11111111-1111-4111-8111-111111111111','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',9),"
                    + "(12,5,'RECHNUNG','22222222-2222-4222-8222-222222222222','bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',9),"
                    + "(13,5,'GUTSCHRIFT','33333333-3333-4333-8333-333333333333','cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',9)");
            statement.execute("INSERT INTO einkauf_beleg_position(dokument_id,bestell_position_id,original_positionsnummer,menge,einheit,original_menge,original_einheit,einzelpreis,waehrung,kosten_snapshot,quellen_snapshot,pruefen) VALUES "
                    + "(11,7,'1',2,'STUECK',2,'STUECK',10,'EUR','[{\"schluessel\":\"material\",\"betrag\":20,\"enthalten\":true},{\"schluessel\":\"fracht\",\"betrag\":80,\"enthalten\":true}]','[]',false),"
                    + "(12,7,'1',2,'STUECK',2,'STUECK',10,'EUR','[{\"schluessel\":\"material\",\"betrag\":20,\"enthalten\":true}]','[]',false),"
                    + "(13,7,'1',1,'STUECK',1,'STUECK',10,'EUR','[{\"schluessel\":\"material\",\"betrag\":10,\"enthalten\":true}]','[]',false)");
            try (var result = statement.executeQuery("SELECT SUM(CASE WHEN z.art IN ('GUTSCHRIFT','STORNO') THEN -p.menge ELSE p.menge END) FROM einkauf_beleg_position p JOIN einkauf_beleg_zuordnung z ON z.dokument_id=p.dokument_id WHERE z.bestellung_id=5 AND p.bestell_position_id=7")) {
                assertTrue(result.next());
                assertEquals(0, result.getBigDecimal(1).compareTo(new BigDecimal("3.000000")));
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM einkauf_beleg_position WHERE JSON_CONTAINS_PATH(kosten_snapshot,'one','$[0].schluessel')")) {
                assertTrue(result.next());
                assertEquals(3,result.getInt(1));
            }
        }
    }
}
