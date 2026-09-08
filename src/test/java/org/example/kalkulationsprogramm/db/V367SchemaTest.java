package org.example.kalkulationsprogramm.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Liest {@code V367__langzeitkrankmeldung.sql} als reinen Text und prueft,
 * dass jede fuer die Entities noetige Spalte darin vorkommt.
 *
 * <p>Grund fuer diesen Test statt eines reinen Vertrauens auf die
 * Repository-Tests: Das Testprofil (siehe
 * src/test/resources/application.properties) hat
 * {@code spring.flyway.enabled=false} und {@code ddl-auto=create-drop} --
 * das Schema entsteht in allen anderen Tests direkt aus den
 * JPA-Annotationen, nie aus dieser Migration. Eine Spalte, die im Entity
 * existiert, aber in V367 vergessen wurde, faellt deshalb in keinem Test
 * auf, sondern erst beim echten Produktionsstart mit
 * {@code spring.jpa.hibernate.ddl-auto=validate} -- dort bricht der Start
 * komplett ab. Dieser Test ist das einzige Sicherheitsnetz dagegen.
 *
 * <p>Zusaetzlich wird geprueft, dass die beiden Enum-Spalten ({@code status},
 * {@code typ}) als natives {@code ENUM(...)} deklariert sind, nicht als
 * {@code VARCHAR} -- sonst scheitert dieselbe Schema-Validierung mit
 * "wrong column type" (siehe BACKEND_ARCH.md, Vorbild
 * V366__datensatz_lock_entitaet_typ_enum.sql).
 */
class V367SchemaTest {

    private static final String MIGRATION_RESOURCE = "/db/migration/V367__langzeitkrankmeldung.sql";

    @Test
    @DisplayName("V367 enthaelt alle Spalten, die die Langzeitkrankmeldung-Entities brauchen")
    void enthaeltAlleErwartetenSpalten() {
        String sql = ladeMigration();

        assertThat(sql)
                .contains("langzeitkrankmeldung")
                .contains("langzeitkrankmeldung_phase")
                .contains("mitarbeiter_id")
                .contains("beginn")
                .contains("ende")
                .contains("status")
                .contains("lohnfortzahlung_bis")
                .contains("notiz")
                .contains("version")
                .contains("typ")
                .contains("von_datum")
                .contains("bis_datum")
                .contains("stunden_pro_tag")
                .contains("langzeitkrankmeldung_id")
                .contains("langzeitkrankmeldung_phase_id");
    }

    @Test
    @DisplayName("status und typ sind native ENUM-Spalten, keine VARCHAR-Spalten")
    void enumSpaltenSindNativeEnums() {
        String sql = ladeMigration();

        // Spaltendefinitionen sind wie im restlichen Bestand ueblich
        // ausgerichtet (mehrere Leerzeichen vor dem Typ) -- deshalb \s+ statt
        // eines einzelnen Leerzeichens.
        assertThat(Pattern.compile("\\bstatus\\s+ENUM\\(").matcher(sql).find())
                .as("status muss als natives ENUM(...) deklariert sein, nicht als VARCHAR")
                .isTrue();
        assertThat(Pattern.compile("\\btyp\\s+ENUM\\(").matcher(sql).find())
                .as("typ muss als natives ENUM(...) deklariert sein, nicht als VARCHAR")
                .isTrue();
    }

    private String ladeMigration() {
        try (InputStream in = getClass().getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in)
                    .as("Migration %s muss unter src/main/resources%s existieren", MIGRATION_RESOURCE, MIGRATION_RESOURCE)
                    .isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + MIGRATION_RESOURCE + " nicht lesen", e);
        }
    }
}
