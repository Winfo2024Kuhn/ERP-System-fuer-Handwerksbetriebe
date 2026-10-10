package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/**
 * Kunden-Installationen laufen auf PostgreSQL - dessen Fehlertexte sehen anders
 * aus als die von MySQL. Auch dort soll der Handwerker verstehen, was los ist.
 */
class ConstraintMessageResolverPostgresTest {

    private DatabaseConstraintMetadataService metadaten;
    private ConstraintMessageResolver resolver;

    @BeforeEach
    void vorbereiten() {
        metadaten = Mockito.mock(DatabaseConstraintMetadataService.class);
        given(metadaten.findConstraint(anyString())).willReturn(Optional.empty());
        given(metadaten.findColumn(any(), any())).willReturn(Optional.empty());
        given(metadaten.findColumnByName(anyString())).willReturn(Optional.empty());
        given(metadaten.findTable(any())).willReturn(Optional.empty());
        resolver = new ConstraintMessageResolver(metadaten);
    }

    private static DataIntegrityViolationException fehler(String meldung) {
        return new DataIntegrityViolationException("Fehler beim Speichern", new SQLException(meldung, "23505"));
    }

    @Test
    @DisplayName("Doppelter Wert: Spalte aus den Metadaten, Wert aus dem Detail")
    void doppelterWert() {
        given(metadaten.findConstraint("kunde_kundennummer_key")).willReturn(Optional.of(
                new DatabaseConstraintMetadataService.ConstraintMetadata("kunde_kundennummer_key", "kunde",
                        DatabaseConstraintMetadataService.ConstraintMetadata.ConstraintType.UNIQUE,
                        List.of("kundennummer"), null, List.of())));

        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                "ERROR: duplicate key value violates unique constraint \"kunde_kundennummer_key\"\n"
                        + "  Detail: Key (kundennummer)=(1001) already exists."));

        assertThat(ergebnis.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ergebnis.userMessage()).isEqualTo("Der Wert '1001' für Kundennummer ist bereits vergeben.");
        assertThat(ergebnis.fieldErrors()).extracting(FieldErrorDetail::field).containsExactly("kundennummer");
    }

    @Test
    @DisplayName("Pflichtfeld leer: Spaltenname aus dem PostgreSQL-Text")
    void pflichtfeldLeer() {
        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                "ERROR: null value in column \"name\" of relation \"kunde\" violates not-null constraint"));

        assertThat(ergebnis.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ergebnis.userMessage()).isEqualTo("Das Feld 'Name' darf nicht leer sein.");
    }

    @Test
    @DisplayName("Loeschen eines noch gebrauchten Datensatzes wird als solches erkannt")
    void loeschenBlockiert() {
        given(metadaten.findConstraint("fk_projekt_kunde")).willReturn(Optional.of(
                new DatabaseConstraintMetadataService.ConstraintMetadata("fk_projekt_kunde", "projekt",
                        DatabaseConstraintMetadataService.ConstraintMetadata.ConstraintType.FOREIGN_KEY,
                        List.of("kunden_id"), "kunde", List.of("id"))));

        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                "ERROR: update or delete on table \"kunde\" violates foreign key constraint \"fk_projekt_kunde\" on table \"projekt\""));

        ConstraintErrorDetail mysqlFall = resolver.resolve(fehler(
                "Cannot delete or update a parent row: a foreign key constraint fails (`db`.`projekt`, "
                        + "CONSTRAINT `fk_projekt_kunde` FOREIGN KEY (`kunden_id`) REFERENCES `kunde` (`id`))"));
        // Gleiche Lage, gleiche Meldung - egal welche Datenbank
        assertThat(ergebnis.status()).isEqualTo(mysqlFall.status());
        assertThat(ergebnis.userMessage()).isEqualTo(mysqlFall.userMessage());
        assertThat(ergebnis.constraintName()).isEqualTo("fk_projekt_kunde");
    }

    @Test
    @DisplayName("Verweis auf nicht vorhandenen Datensatz (Einfuegen) ist kein Loesch-Fall")
    void verweisUngueltig() {
        given(metadaten.findConstraint("fk_projekt_kunde")).willReturn(Optional.of(
                new DatabaseConstraintMetadataService.ConstraintMetadata("fk_projekt_kunde", "projekt",
                        DatabaseConstraintMetadataService.ConstraintMetadata.ConstraintType.FOREIGN_KEY,
                        List.of("kunden_id"), "kunde", List.of("id"))));

        ConstraintErrorDetail einfuegen = resolver.resolve(fehler(
                "ERROR: insert or update on table \"projekt\" violates foreign key constraint \"fk_projekt_kunde\""));
        ConstraintErrorDetail loeschen = resolver.resolve(fehler(
                "ERROR: update or delete on table \"kunde\" violates foreign key constraint \"fk_projekt_kunde\" on table \"projekt\""));

        assertThat(einfuegen.userMessage()).isNotEqualTo(loeschen.userMessage());
        assertThat(einfuegen.fieldErrors()).extracting(FieldErrorDetail::field).containsExactly("kunden_id");
    }
}
