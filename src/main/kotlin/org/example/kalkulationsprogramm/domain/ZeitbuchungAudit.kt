package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "zeitbuchung_audit",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_zeitbuchung_audit_version",
            columnNames = ["zeitbuchung_id", "version"],
        ),
    ],
)
open class ZeitbuchungAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(name = "zeitbuchung_id", nullable = false)
    open var zeitbuchungId: Long? = null

    @Column(nullable = false)
    open var version: Int? = null

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    open var aktion: AuditAktion? = null

    @Column(name = "mitarbeiter_id", nullable = false)
    open var mitarbeiterId: Long? = null

    @Column(name = "projekt_id")
    open var projektId: Long? = null

    @Column(name = "arbeitsgang_id")
    open var arbeitsgangId: Long? = null

    @Column(name = "arbeitsgang_stundensatz_id")
    open var arbeitsgangStundensatzId: Long? = null

    @Column(name = "projekt_produktkategorie_id")
    open var projektProduktkategorieId: Long? = null

    @Column(name = "start_zeit", nullable = false)
    open var startZeit: LocalDateTime? = null

    @Column(name = "ende_zeit")
    open var endeZeit: LocalDateTime? = null

    @Column(name = "anzahl_in_stunden", precision = 10, scale = 2)
    open var anzahlInStunden: BigDecimal? = null

    @Column(columnDefinition = "TEXT")
    open var notiz: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geaendert_von_mitarbeiter_id", nullable = false)
    open var geaendertVon: Mitarbeiter? = null

    @Column(name = "geaendert_am", nullable = false)
    open var geaendertAm: LocalDateTime? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "geaendert_via", length = 50, nullable = false)
    open var geaendertVia: ErfassungsQuelle? = null

    @Column(name = "aenderungsgrund", columnDefinition = "TEXT")
    open var aenderungsgrund: String? = null

    companion object {
        @JvmStatic
        fun fromZeitbuchung(
            buchung: Zeitbuchung,
            aktion: AuditAktion,
            bearbeiter: Mitarbeiter,
            quelle: ErfassungsQuelle,
            grund: String?,
        ): ZeitbuchungAudit {
            val audit = ZeitbuchungAudit()
            audit.zeitbuchungId = buchung.id
            audit.version = buchung.version
            audit.aktion = aktion
            audit.mitarbeiterId = buchung.mitarbeiter?.id
            audit.projektId = buchung.projekt?.longValue("getId")
            audit.arbeitsgangId = buchung.arbeitsgang?.longValue("getId")
            audit.arbeitsgangStundensatzId = buchung.arbeitsgangStundensatz?.longValue("getId")
            audit.projektProduktkategorieId = buchung.projektProduktkategorie?.longValue("getId")
            audit.startZeit = buchung.startZeit
            audit.endeZeit = buchung.endeZeit
            audit.anzahlInStunden = buchung.anzahlInStunden
            audit.notiz = buchung.notiz
            audit.geaendertVon = bearbeiter
            audit.geaendertAm = LocalDateTime.now()
            audit.geaendertVia = quelle
            audit.aenderungsgrund = grund
            return audit
        }

        private fun Any.longValue(getter: String): Long? =
            javaClass.getMethod(getter).invoke(this) as? Long
    }
}
