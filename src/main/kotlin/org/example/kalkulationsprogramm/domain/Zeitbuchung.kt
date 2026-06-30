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
    name = "zeitbuchung",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_zeitbuchung_mitarbeiter_start",
            columnNames = ["mitarbeiter_id", "start_zeit"],
        ),
    ],
)
open class Zeitbuchung {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "mitarbeiter_id", nullable = true)
    open var mitarbeiter: Mitarbeiter? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "projekt_id", nullable = true)
    open var projekt: Projekt? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arbeitsgang_id")
    open var arbeitsgang: Arbeitsgang? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arbeitsgang_stundensatz_id")
    open var arbeitsgangStundensatz: ArbeitsgangStundensatz? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_produktkategorie_id")
    open var projektProduktkategorie: ProjektProduktkategorie? = null

    @Column(nullable = true)
    open var startZeit: LocalDateTime? = null

    @Column
    open var endeZeit: LocalDateTime? = null

    @Column(precision = 10, scale = 2)
    open var anzahlInStunden: BigDecimal? = null

    @Column(length = 500)
    open var notiz: String? = null

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    open var typ: BuchungsTyp? = BuchungsTyp.ARBEIT

    @Column(nullable = false)
    open var version: Int? = 1

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "erfasst_von_mitarbeiter_id")
    open var erfasstVon: Mitarbeiter? = null

    @Column
    open var erfasstAm: LocalDateTime? = null

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    open var erfasstVia: ErfassungsQuelle? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zuletzt_geaendert_von")
    open var zuletztGeaendertVon: Mitarbeiter? = null

    @Column
    open var zuletztGeaendertAm: LocalDateTime? = null

    @Column(length = 36, unique = true)
    open var idempotencyKey: String? = null

    @Column(length = 36, unique = true)
    open var stopIdempotencyKey: String? = null

    open fun markiereAlsGeaendert(bearbeiter: Mitarbeiter?) {
        version = if (version == null) 2 else version!! + 1
        zuletztGeaendertVon = bearbeiter
        zuletztGeaendertAm = LocalDateTime.now()
    }
}
