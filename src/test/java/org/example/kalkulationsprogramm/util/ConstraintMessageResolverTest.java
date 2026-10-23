package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.util.DatabaseConstraintMetadataService.ColumnMetadata;
import org.example.kalkulationsprogramm.util.DatabaseConstraintMetadataService.ConstraintMetadata;
import org.example.kalkulationsprogramm.util.DatabaseConstraintMetadataService.ConstraintMetadata.ConstraintType;
import org.example.kalkulationsprogramm.util.DatabaseConstraintMetadataService.TableMetadata;
import org.hibernate.PropertyValueException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/**
 * Auch wenn PostgreSQL einen Speicherfehler meldet, soll der Handwerker verstehen,
 * was los ist - die Texte unten sind genau die, die PostgreSQL schickt.
 */
class ConstraintMessageResolverTest {

    private static final String ALLGEMEINE_MELDUNG =
            "Der Vorgang konnte nicht abgeschlossen werden, weil eine Datenbankvorgabe verletzt wurde.";

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

    private static DataIntegrityViolationException fehler(String meldung, String sqlState) {
        return new DataIntegrityViolationException("Fehler beim Speichern", new SQLException(meldung, sqlState));
    }

    private void constraint(String name, String tabelle, ConstraintType typ, List<String> spalten, String zielTabelle) {
        given(metadaten.findConstraint(name)).willReturn(Optional.of(new ConstraintMetadata(name, tabelle, typ, spalten,
                zielTabelle, zielTabelle == null ? List.of() : List.of("id"))));
    }

    // ---------------------------------------------------------------- 23505

    @Test
    @DisplayName("Doppelter Wert: Spalte aus den Metadaten, Wert aus dem Detail")
    void doppelterWert() {
        constraint("kunde_kundennummer_key", "kunde", ConstraintType.UNIQUE, List.of("kundennummer"), null);

        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                """
                ERROR: duplicate key value violates unique constraint "kunde_kundennummer_key"
                  Detail: Key (kundennummer)=(1001) already exists.""", "23505"));

        assertThat(ergebnis.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ergebnis.userMessage()).isEqualTo("Der Wert '1001' für Kundennummer ist bereits vergeben.");
        assertThat(ergebnis.constraintName()).isEqualTo("kunde_kundennummer_key");
        assertThat(ergebnis.fieldErrors()).extracting(FieldErrorDetail::field).containsExactly("kundennummer");
        assertThat(ergebnis.fieldErrors()).extracting(FieldErrorDetail::message).containsExactly("Wert bereits vergeben.");
    }

    @Test
    @DisplayName("Doppelter Wert: Beschriftung der Spalte kommt aus den Metadaten")
    void doppelterWertMitBeschriftung() {
        constraint("kunde_kundennummer_key", "kunde", ConstraintType.UNIQUE, List.of("kundennummer"), null);
        given(metadaten.findColumn("kunde", "kundennummer"))
                .willReturn(Optional.of(new ColumnMetadata("kunde", "kundennummer", "Kunden-Nr.", 20, false)));

        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                """
                ERROR: duplicate key value violates unique constraint "kunde_kundennummer_key"
                  Detail: Key (kundennummer)=(1001) already exists.""", "23505"));

        assertThat(ergebnis.userMessage()).isEqualTo("Der Wert '1001' für Kunden-Nr. ist bereits vergeben.");
        assertThat(ergebnis.fieldErrors()).extracting(FieldErrorDetail::label).containsExactly("Kunden-Nr.");
    }

    @Test
    @DisplayName("Doppelte Kombination aus mehreren Spalten: alle Felder werden genannt")
    void doppelteKombination() {
        constraint("uk_projekt_nummer", "projekt", ConstraintType.UNIQUE, List.of("kunden_id", "nummer", "jahr"), null);

        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                """
                ERROR: duplicate key value violates unique constraint "uk_projekt_nummer"
                  Detail: Key (kunden_id, nummer, jahr)=(7, 12, 2026) already exists.""", "23505"));

        assertThat(ergebnis.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ergebnis.userMessage()).isEqualTo("Die Kombination aus Kunden Id, Nummer und Jahr muss eindeutig sein.");
        assertThat(ergebnis.fieldErrors()).extracting(FieldErrorDetail::field).containsExactly("kunden_id", "nummer", "jahr");
        assertThat(ergebnis.fieldErrors()).extracting(FieldErrorDetail::message).containsOnly("Kombination muss eindeutig sein.");
    }

    @Test
    @DisplayName("Doppelter Wert ohne Metadaten: allgemeine Meldung mit Constraint-Namen")
    void doppelterWertOhneMetadaten() {
        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                """
                ERROR: duplicate key value violates unique constraint "kunde_email_key"
                  Detail: Key (email)=(max.mustermann@example.com) already exists.""", "23505"));

        assertThat(ergebnis.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ergebnis.userMessage())
                .isEqualTo("Der angegebene Wert ist bereits vorhanden (Datenbank-Constraint 'kunde_email_key').");
        assertThat(ergebnis.constraintName()).isEqualTo("kunde_email_key");
        assertThat(ergebnis.fieldErrors()).isEmpty();
    }

    @Test
    @DisplayName("Doppelter Wert, Metadaten ohne Spalten: allgemeine Meldung")
    void doppelterWertMetadatenOhneSpalten() {
        constraint("kunde_kundennummer_key", "kunde", ConstraintType.UNIQUE, List.of(), null);

        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                """
                ERROR: duplicate key value violates unique constraint "kunde_kundennummer_key"
                  Detail: Key (kundennummer)=(1001) already exists.""", "23505"));

        assertThat(ergebnis.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ergebnis.userMessage())
                .isEqualTo("Der angegebene Wert ist bereits vorhanden (Datenbank-Constraint 'kunde_kundennummer_key').");
        assertThat(ergebnis.constraintName()).isEqualTo("kunde_kundennummer_key");
        assertThat(ergebnis.fieldErrors()).isEmpty();
    }

    @Test
    @DisplayName("Doppelter Wert: sehr lange Werte werden in der Meldung gekuerzt")
    void doppelterWertGekuerzt() {
        constraint("artikel_bezeichnung_key", "artikel", ConstraintType.UNIQUE, List.of("bezeichnung"), null);
        String langerWert = "x".repeat(200);

        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                "ERROR: duplicate key value violates unique constraint \"artikel_bezeichnung_key\"\n"
                        + "  Detail: Key (bezeichnung)=(" + langerWert + ") already exists.", "23505"));

        assertThat(ergebnis.userMessage())
                .isEqualTo("Der Wert '" + "x".repeat(117) + "...' für Bezeichnung ist bereits vergeben.");
    }

    // ---------------------------------------------------------------- 23502

    @Test
    @DisplayName("Pflichtfeld leer: Spaltenname aus dem PostgreSQL-Text")
    void pflichtfeldLeer() {
        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                "ERROR: null value in column \"name\" of relation \"kunde\" violates not-null constraint", "23502"));

        assertThat(ergebnis.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ergebnis.userMessage()).isEqualTo("Das Feld 'Name' darf nicht leer sein.");
        assertThat(ergebnis.fieldErrors()).extracting(FieldErrorDetail::field).containsExactly("name");
        assertThat(ergebnis.fieldErrors()).extracting(FieldErrorDetail::message).containsExactly("Darf nicht leer sein.");
    }

    @Test
    @DisplayName("Pflichtfeld leer: Beschriftung aus den Metadaten, leere Beschriftung wird ersetzt")
    void pflichtfeldLeerMitBeschriftung() {
        given(metadaten.findColumnByName("plz"))
                .willReturn(Optional.of(new ColumnMetadata("kunde", "plz", "Postleitzahl", 10, false)));
        given(metadaten.findColumnByName("liefer_datum"))
                .willReturn(Optional.of(new ColumnMetadata("bestellung", "liefer_datum", " ", null, false)));

        ConstraintErrorDetail plz = resolver.resolve(fehler(
                "ERROR: null value in column \"plz\" of relation \"kunde\" violates not-null constraint", "23502"));
        ConstraintErrorDetail datum = resolver.resolve(fehler(
                "ERROR: null value in column \"liefer_datum\" of relation \"bestellung\" violates not-null constraint", "23502"));

        assertThat(plz.userMessage()).isEqualTo("Das Feld 'Postleitzahl' darf nicht leer sein.");
        assertThat(datum.userMessage()).isEqualTo("Das Feld 'Liefer Datum' darf nicht leer sein.");
    }

    @Test
    @DisplayName("Pflichtfeld leer, schon von Hibernate vor der Datenbank bemerkt")
    void pflichtfeldLeerHibernate() {
        given(metadaten.findColumnByName("ort"))
                .willReturn(Optional.of(new ColumnMetadata("kunde", "ort", "Wohnort", 100, false)));

        ConstraintErrorDetail ohneMetadaten = resolver.resolve(new DataIntegrityViolationException("Fehler beim Speichern",
                new PropertyValueException("not-null property references a null or transient value", "Kunde", "nachname")));
        ConstraintErrorDetail mitMetadaten = resolver.resolve(new DataIntegrityViolationException("Fehler beim Speichern",
                new PropertyValueException("not-null property references a null or transient value", "Kunde", "ort")));

        assertThat(ohneMetadaten.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ohneMetadaten.userMessage()).isEqualTo("Das Feld 'Nachname' darf nicht leer sein.");
        assertThat(ohneMetadaten.fieldErrors()).extracting(FieldErrorDetail::field).containsExactly("nachname");
        assertThat(mitMetadaten.userMessage()).isEqualTo("Das Feld 'Wohnort' darf nicht leer sein.");
    }

    // ---------------------------------------------------------------- 23503

    @Test
    @DisplayName("Loeschen eines noch gebrauchten Kunden: genannt wird die Tabelle, die noch auf ihn verweist")
    void loeschenBlockiert() {
        constraint("fk_projekt_kunde", "projekt", ConstraintType.FOREIGN_KEY, List.of("kunden_id"), "kunde");

        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                """
                ERROR: update or delete on table "kunde" violates foreign key constraint "fk_projekt_kunde" on table "projekt"
                  Detail: Key (id)=(7) is still referenced from table "projekt".""", "23503"));

        assertThat(ergebnis.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ergebnis.userMessage())
                .isEqualTo("Der Datensatz kann nicht gelöscht werden, da noch Einträge in 'Projekt' darauf verweisen.");
        assertThat(ergebnis.constraintName()).isEqualTo("fk_projekt_kunde");
        assertThat(ergebnis.fieldErrors()).isEmpty();
    }

    @Test
    @DisplayName("Loeschen blockiert: Beschriftung der verweisenden Tabelle kommt aus den Metadaten")
    void loeschenBlockiertMitBeschriftung() {
        constraint("fk_projekt_kunde", "projekt", ConstraintType.FOREIGN_KEY, List.of("kunden_id"), "kunde");
        given(metadaten.findTable("projekt")).willReturn(Optional.of(new TableMetadata("projekt", "Projekte")));

        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                "ERROR: update or delete on table \"kunde\" violates foreign key constraint \"fk_projekt_kunde\" on table \"projekt\"",
                "23503"));

        assertThat(ergebnis.userMessage())
                .isEqualTo("Der Datensatz kann nicht gelöscht werden, da noch Einträge in 'Projekte' darauf verweisen.");
    }

    @Test
    @DisplayName("Verweis auf nicht vorhandenen Datensatz (Einfuegen) ist kein Loesch-Fall")
    void verweisUngueltig() {
        constraint("fk_projekt_kunde", "projekt", ConstraintType.FOREIGN_KEY, List.of("kunden_id"), "kunde");
        given(metadaten.findTable("kunde")).willReturn(Optional.of(new TableMetadata("kunde", "Kunden")));

        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                """
                ERROR: insert or update on table "projekt" violates foreign key constraint "fk_projekt_kunde"
                  Detail: Key (kunden_id)=(99) is not present in table "kunde".""", "23503"));

        assertThat(ergebnis.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ergebnis.userMessage()).isEqualTo(
                "Der angegebene Wert für Kunden Id ist ungültig – es existiert kein passender Eintrag in 'Kunden'.");
        assertThat(ergebnis.fieldErrors()).extracting(FieldErrorDetail::field).containsExactly("kunden_id");
        assertThat(ergebnis.fieldErrors()).extracting(FieldErrorDetail::message).containsExactly("Verweis ist ungültig.");
    }

    @Test
    @DisplayName("Verweis ohne Metadaten: Tabellen kommen aus dem PostgreSQL-Text")
    void verweisOhneMetadaten() {
        ConstraintErrorDetail einfuegen = resolver.resolve(fehler(
                """
                ERROR: insert or update on table "projekt" violates foreign key constraint "fk_unbekannt"
                  Detail: Key (kunden_id)=(99) is not present in table "kunde".""", "23503"));
        ConstraintErrorDetail loeschen = resolver.resolve(fehler(
                """
                ERROR: update or delete on table "kunde" violates foreign key constraint "fk_unbekannt" on table "projekt"
                  Detail: Key (id)=(7) is still referenced from table "projekt".""", "23503"));

        assertThat(einfuegen.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(einfuegen.userMessage())
                .isEqualTo("Die ausgewählte Referenz ist ungültig – es existiert kein passender Eintrag in 'Kunde'.");
        assertThat(einfuegen.constraintName()).isEqualTo("fk_unbekannt");
        assertThat(einfuegen.fieldErrors()).isEmpty();
        assertThat(loeschen.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(loeschen.userMessage())
                .isEqualTo("Der Datensatz kann nicht gelöscht werden, da noch Einträge in 'Projekt' darauf verweisen.");
        assertThat(loeschen.fieldErrors()).isEmpty();
    }

    @Test
    @DisplayName("Verweis: Metadaten kennen die Zieltabelle nicht - sie kommt aus dem PostgreSQL-Text")
    void verweisZieltabelleNurImText() {
        constraint("fk_projekt_kunde", "projekt", ConstraintType.FOREIGN_KEY, List.of("kunden_id"), null);

        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                """
                ERROR: insert or update on table "projekt" violates foreign key constraint "fk_projekt_kunde"
                  Detail: Key (kunden_id)=(99) is not present in table "kunde".""", "23503"));

        assertThat(ergebnis.userMessage()).isEqualTo(
                "Der angegebene Wert für Kunden Id ist ungültig – es existiert kein passender Eintrag in 'Kunde'.");
        assertThat(ergebnis.fieldErrors()).extracting(FieldErrorDetail::field).containsExactly("kunden_id");
    }

    @Test
    @DisplayName("Verweis, Tabelle nirgends genannt: Satz ohne leere Anfuehrungszeichen")
    void verweisOhneTabellenname() {
        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(
                "ERROR: insert or update on table \"projekt\" violates foreign key constraint \"fk_unbekannt\"", "23503"));

        assertThat(ergebnis.userMessage())
                .isEqualTo("Die ausgewählte Referenz ist ungültig – es existiert kein passender Eintrag.");
    }

    // ---------------------------------------------------------------- 22001

    @Test
    @DisplayName("Zu langer Text: Hoechstlaenge aus dem PostgreSQL-Text statt allgemeiner Meldung")
    void zuLangerText() {
        ConstraintErrorDetail text = resolver.resolve(fehler("ERROR: value too long for type character varying(255)", "22001"));
        ConstraintErrorDetail fest = resolver.resolve(fehler("ERROR: value too long for type character(64)", "22001"));

        assertThat(text.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(text.userMessage()).isEqualTo("Ein eingegebener Text ist zu lang. Maximal erlaubt: 255 Zeichen.");
        assertThat(fest.userMessage()).isEqualTo("Ein eingegebener Text ist zu lang. Maximal erlaubt: 64 Zeichen.");
    }

    @Test
    @DisplayName("Zu langer Text mit uebersetzter Servermeldung: am SQLState erkannt")
    void zuLangerTextUebersetzt() {
        ConstraintErrorDetail ergebnis = resolver.resolve(fehler("FEHLER: Wert zu lang für Typ character varying(255)", "22001"));

        assertThat(ergebnis.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ergebnis.userMessage()).isEqualTo("Ein eingegebener Text ist zu lang.");
    }

    // ---------------------------------------------------------------- Rest

    @Test
    @DisplayName("CHECK-Regel verletzt (23514): allgemeine Meldung, technischer Text bleibt erhalten")
    void checkVerletzt() {
        String text = "ERROR: new row for relation \"kunde\" violates check constraint \"kunde_anrede_check\"";

        ConstraintErrorDetail ergebnis = resolver.resolve(fehler(text, "23514"));

        assertThat(ergebnis.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ergebnis.userMessage()).isEqualTo(ALLGEMEINE_MELDUNG);
        assertThat(ergebnis.technicalMessage()).isEqualTo(text);
        assertThat(ergebnis.fieldErrors()).isEmpty();
    }

    @Test
    @DisplayName("Fehler ohne SQL-Ursache oder ganz ohne Text: allgemeine Meldung")
    void ohneSqlUrsache() {
        ConstraintErrorDetail mitText = resolver.resolve(new DataIntegrityViolationException("Speichern fehlgeschlagen"));
        ConstraintErrorDetail ohneText = resolver.resolve(new DataIntegrityViolationException(null));

        assertThat(mitText.userMessage()).isEqualTo(ALLGEMEINE_MELDUNG);
        assertThat(mitText.technicalMessage()).isEqualTo("Speichern fehlgeschlagen");
        assertThat(ohneText.userMessage()).isEqualTo(ALLGEMEINE_MELDUNG);
        assertThat(ohneText.technicalMessage()).isEmpty();
    }
}
