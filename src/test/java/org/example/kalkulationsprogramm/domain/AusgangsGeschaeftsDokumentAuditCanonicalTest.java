package org.example.kalkulationsprogramm.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reiner Unit-Test (ohne DB) für die kanonische Serialisierung der Audit-Hash-Kette.
 *
 * <p>Die kanonische Form ist der GoBD-Vertrag: Ändert jemand Reihenfolge, Trennzeichen
 * oder Feldanzahl, werden ALLE gespeicherten Hashes ungültig und die komplette Kette
 * gilt fälschlich als manipuliert. Dieser Test pinnt das Format unabhängig von einer
 * laufenden Datenbank, damit ein versehentliches Hinzufügen/Entfernen eines Feldes
 * sofort in der CI auffällt.</p>
 */
class AusgangsGeschaeftsDokumentAuditCanonicalTest {

    /** Anzahl der Felder in {@code canonicalForm()} — bei Änderung MUSS dieser Wert mitwandern. */
    private static final int ERWARTETE_FELDANZAHL = 26;

    @Test
    void canonicalFormHatGenauDieErwarteteFeldanzahl() {
        String canonical = vollAusgefuellt().canonicalForm();
        // Felder ohne '|' im Inhalt -> Trennzeichen = Felder - 1.
        long trennzeichen = canonical.chars().filter(c -> c == '|').count();
        assertThat(trennzeichen)
                .as("canonicalForm muss exakt %d Felder (= %d Pipes) serialisieren. "
                        + "Stimmt das nicht, wurde ein Feld hinzugefügt/entfernt und die "
                        + "Hash-Kette würde brechen.", ERWARTETE_FELDANZAHL, ERWARTETE_FELDANZAHL - 1)
                .isEqualTo(ERWARTETE_FELDANZAHL - 1);
    }

    @Test
    void canonicalFormIstDeterministisch() {
        AusgangsGeschaeftsDokumentAudit a = vollAusgefuellt();
        assertThat(a.canonicalForm()).isEqualTo(a.canonicalForm());
        assertThat(a.computeEntryHash()).isEqualTo(a.computeEntryHash());
    }

    @Test
    void aenderungAmBetragAendertDieKanonischeForm() {
        AusgangsGeschaeftsDokumentAudit a = vollAusgefuellt();
        String vorher = a.canonicalForm();
        a.setBetragNetto(new BigDecimal("999.99"));
        assertThat(a.canonicalForm())
                .as("Jede inhaltliche Änderung muss die kanonische Form verändern")
                .isNotEqualTo(vorher);
    }

    @Test
    void nullFelderWerdenAlsLeererStringSerialisiert() {
        AusgangsGeschaeftsDokumentAudit a = vollAusgefuellt();
        a.setAbschlagsNummer(null);
        a.setProjektId(null);
        // Pipe-Anzahl bleibt gleich, weil NULL -> "" (kein Feld entfällt).
        long trennzeichen = a.canonicalForm().chars().filter(c -> c == '|').count();
        assertThat(trennzeichen).isEqualTo(ERWARTETE_FELDANZAHL - 1);
    }

    private AusgangsGeschaeftsDokumentAudit vollAusgefuellt() {
        AusgangsGeschaeftsDokumentAudit a = new AusgangsGeschaeftsDokumentAudit();
        a.setChainIndex(5L);
        a.setDokumentId(42L);
        a.setAktion(AusgangsGeschaeftsDokumentAuditAktion.GEBUCHT);
        a.setDokumentNummer("RE-2026/01/00001");
        a.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
        a.setDatum(LocalDate.of(2026, 1, 15));
        a.setBetreff("Auftrag Max Mustermann");
        a.setBetragNetto(new BigDecimal("100.00"));
        a.setBetragBrutto(new BigDecimal("119.00"));
        a.setMwstSatz(new BigDecimal("0.1900"));
        a.setAbschlagsNummer(2);
        a.setProjektId(7L);
        a.setAnfrageId(11L);
        a.setKundeId(99L);
        a.setVorgaengerId(3L);
        a.setVersandDatum(LocalDate.of(2026, 1, 16));
        a.setGebucht(true);
        a.setGebuchtAm(LocalDate.of(2026, 1, 16));
        a.setStorniert(false);
        a.setStorniertAm(null);
        a.setDigitalAngenommen(false);
        a.setInhaltHash("b".repeat(64));
        a.setGeaendertAm(LocalDateTime.of(2026, 1, 15, 10, 30, 30, 123_456_000));
        a.setAenderungsgrund("Festschreibung/Buchung");
        a.setIpAdresse("192.168.0.1");
        return a;
    }
}
