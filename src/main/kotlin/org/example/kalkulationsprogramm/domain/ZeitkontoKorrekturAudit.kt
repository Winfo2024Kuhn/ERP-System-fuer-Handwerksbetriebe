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
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "zeitkonto_korrektur_audit",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_zeitkonto_korrektur_audit_version",
            columnNames = ["zeitkonto_korrektur_id", "version"],
        ),
    ],
)
open class ZeitkontoKorrekturAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(name = "zeitkonto_korrektur_id", nullable = false)
    open var zeitkontoKorrekturId: Long? = null

    @Column(nullable = false)
    open var version: Int? = null

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    open var aktion: AuditAktion? = null

    @Column(name = "mitarbeiter_id", nullable = false)
    open var mitarbeiterId: Long? = null

    @Column(name = "datum", nullable = false)
    open var datum: LocalDate? = null

    @Column(name = "stunden", nullable = false, precision = 10, scale = 2)
    open var stunden: BigDecimal? = null

    @Column(name = "grund", columnDefinition = "TEXT")
    open var grund: String? = null

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
        fun fromKorrektur(
            korrektur: ZeitkontoKorrektur,
            aktion: AuditAktion,
            bearbeiter: Mitarbeiter,
            quelle: ErfassungsQuelle,
            aenderungsgrund: String?,
        ): ZeitkontoKorrekturAudit {
            val audit = ZeitkontoKorrekturAudit()
            audit.zeitkontoKorrekturId = korrektur.id
            audit.version = korrektur.version
            audit.aktion = aktion
            audit.mitarbeiterId = korrektur.mitarbeiter?.longValue("getId")
            audit.datum = korrektur.datum
            audit.stunden = korrektur.stunden
            audit.grund = korrektur.grund
            audit.geaendertVon = bearbeiter
            audit.geaendertAm = LocalDateTime.now()
            audit.geaendertVia = quelle
            audit.aenderungsgrund = aenderungsgrund
            return audit
        }

        private fun Any.longValue(getter: String): Long? =
            javaClass.getMethod(getter).invoke(this) as? Long
    }
}
