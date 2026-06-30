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
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "zeitkonto_korrektur")
open class ZeitkontoKorrektur {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    open var mitarbeiter: Mitarbeiter? = null

    @Column(nullable = false)
    open var datum: LocalDate? = null

    @Column(nullable = false, precision = 10, scale = 2)
    open var stunden: BigDecimal? = null

    @Column(length = 500, nullable = false)
    open var grund: String? = null

    @Column(nullable = false)
    open var version: Int? = 1

    @Column(nullable = false)
    open var erstelltAm: LocalDateTime? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "erstellt_von_id")
    open var erstelltVon: Mitarbeiter? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var typ: KorrekturTyp? = KorrekturTyp.STUNDEN

    @Column(nullable = false)
    open var storniert: Boolean? = false

    @Column
    open var storniertAm: LocalDateTime? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storniert_von_id")
    open var storniertVon: Mitarbeiter? = null

    @Column(length = 500)
    open var stornierungsgrund: String? = null

    @PrePersist
    protected open fun onCreate() {
        if (erstelltAm == null) {
            erstelltAm = LocalDateTime.now()
        }
        if (version == null) {
            version = 1
        }
    }

    open fun erhoeheVersion() {
        version = (version ?: 1) + 1
    }
}
