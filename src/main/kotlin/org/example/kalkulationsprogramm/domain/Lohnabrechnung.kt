package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "lohnabrechnung",
    indexes = [
        Index(name = "idx_lohnabrechnung_mitarbeiter", columnList = "mitarbeiter_id"),
        Index(name = "idx_lohnabrechnung_periode", columnList = "jahr, monat"),
        Index(name = "idx_lohnabrechnung_steuerberater", columnList = "steuerberater_id"),
    ],
)
open class Lohnabrechnung {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    open var mitarbeiter: Mitarbeiter? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "steuerberater_id")
    open var steuerberater: SteuerberaterKontakt? = null

    @Column(nullable = false)
    open var jahr: Int? = null

    @Column(nullable = false)
    open var monat: Int? = null

    @Column(length = 255)
    open var originalDateiname: String? = null

    @Column(length = 255, nullable = false)
    open var gespeicherterDateiname: String? = null

    @Column(precision = 10, scale = 2)
    open var bruttolohn: BigDecimal? = null

    @Column(precision = 10, scale = 2)
    open var nettolohn: BigDecimal? = null

    @Column(nullable = false)
    open var importDatum: LocalDateTime? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_id")
    open var sourceEmail: Email? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var status: LohnabrechnungStatus? = LohnabrechnungStatus.IMPORTIERT

    @Column(columnDefinition = "TEXT")
    open var aiRawJson: String? = null

    @PrePersist
    protected open fun onCreate() {
        if (importDatum == null) {
            importDatum = LocalDateTime.now()
        }
    }
}
