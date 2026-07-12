package org.example.kalkulationsprogramm.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class AusgangsGeschaeftsDokumentAuditCanonicalTest {

    @Test
    fun canonicalFormHatGenauDieErwarteteFeldanzahl() {
        val canonical = vollAusgefuellt().canonicalForm()
        val trennzeichen = canonical.count { it == '|' }
        assertThat(trennzeichen)
            .`as`(
                "canonicalForm muss exakt %d Felder (= %d Pipes) serialisieren. " +
                    "Stimmt das nicht, wurde ein Feld hinzugefügt/entfernt und die Hash-Kette würde brechen.",
                ERWARTETE_FELDANZAHL,
                ERWARTETE_FELDANZAHL - 1,
            )
            .isEqualTo(ERWARTETE_FELDANZAHL - 1)
    }

    @Test
    fun canonicalFormIstDeterministisch() {
        val audit = vollAusgefuellt()
        assertThat(audit.canonicalForm()).isEqualTo(audit.canonicalForm())
        assertThat(audit.computeEntryHash()).isEqualTo(audit.computeEntryHash())
    }

    @Test
    fun aenderungAmBetragAendertDieKanonischeForm() {
        val audit = vollAusgefuellt()
        val vorher = audit.canonicalForm()
        audit.betragNetto = BigDecimal("999.99")
        assertThat(audit.canonicalForm())
            .`as`("Jede inhaltliche Änderung muss die kanonische Form verändern")
            .isNotEqualTo(vorher)
    }

    @Test
    fun nullFelderWerdenAlsLeererStringSerialisiert() {
        val audit = vollAusgefuellt()
        audit.abschlagsNummer = null
        audit.projektId = null
        val trennzeichen = audit.canonicalForm().count { it == '|' }
        assertThat(trennzeichen).isEqualTo(ERWARTETE_FELDANZAHL - 1)
    }

    private fun vollAusgefuellt(): AusgangsGeschaeftsDokumentAudit =
        AusgangsGeschaeftsDokumentAudit().apply {
            chainIndex = 5L
            dokumentId = 42L
            aktion = AusgangsGeschaeftsDokumentAuditAktion.GEBUCHT
            dokumentNummer = "RE-2026/01/00001"
            typ = AusgangsGeschaeftsDokumentTyp.RECHNUNG
            datum = LocalDate.of(2026, 1, 15)
            betreff = "Auftrag Max Mustermann"
            betragNetto = BigDecimal("100.00")
            betragBrutto = BigDecimal("119.00")
            mwstSatz = BigDecimal("0.1900")
            abschlagsNummer = 2
            projektId = 7L
            anfrageId = 11L
            kundeId = 99L
            vorgaengerId = 3L
            versandDatum = LocalDate.of(2026, 1, 16)
            gebucht = true
            gebuchtAm = LocalDate.of(2026, 1, 16)
            storniert = false
            storniertAm = null
            digitalAngenommen = false
            inhaltHash = "b".repeat(64)
            geaendertAm = LocalDateTime.of(2026, 1, 15, 10, 30, 30, 123_456_000)
            aenderungsgrund = "Festschreibung/Buchung"
            ipAdresse = "192.168.0.1"
        }

    private companion object {
        const val ERWARTETE_FELDANZAHL = 26
    }
}
