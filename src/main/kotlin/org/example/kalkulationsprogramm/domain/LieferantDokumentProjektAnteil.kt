package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

@Entity
@Table(name = "lieferant_dokument_projekt_anteil")
open class LieferantDokumentProjektAnteil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dokument_id", nullable = false)
    open var dokument: LieferantDokument? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    open var projekt: Projekt? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kostenstelle_id")
    open var kostenstelle: Kostenstelle? = null

    open var prozent: Int? = null

    @Column(precision = 12, scale = 2)
    open var absoluterBetrag: BigDecimal? = null

    @Column(precision = 12, scale = 2)
    open var berechneterBetrag: BigDecimal? = null

    @Column(length = 255)
    open var beschreibung: String? = null

    @Column(nullable = true)
    open var zugeordnetAm: LocalDateTime? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zugeordnet_von_user_id")
    open var zugeordnetVon: FrontendUserProfile? = null

    @Column(nullable = false)
    open var streckungJahre: Int? = 1

    open var streckungStartJahr: Int? = null

    @PrePersist
    protected open fun onCreate() {
        if (zugeordnetAm == null) {
            zugeordnetAm = LocalDateTime.now()
        }
    }

    open fun berechneAnteil(nettoBetrag: BigDecimal?, bruttoBetrag: BigDecimal?) {
        val absolut = absoluterBetrag
        if (absolut != null) {
            berechneterBetrag = absolut
            return
        }
        val prozentWert = prozent ?: return
        val basis = if (isKostenstellenZuordnung()) {
            nettoBetrag ?: bruttoBetrag
        } else {
            bruttoBetrag
        } ?: return
        berechneterBetrag = basis
            .multiply(BigDecimal.valueOf(prozentWert.toLong()))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
    }

    @Deprecated("Use berechneAnteil(nettoBetrag, bruttoBetrag) instead.")
    open fun berechneAnteil(gesamtBetrag: BigDecimal?) {
        berechneAnteil(gesamtBetrag, gesamtBetrag)
    }

    @get:Transient
    open val jahresanteil: BigDecimal
        get() {
            val betrag = berechneterBetrag ?: return BigDecimal.ZERO
            val jahre = streckungJahre
            if (jahre == null || jahre <= 1) {
                return betrag
            }
            return betrag.divide(BigDecimal.valueOf(jahre.toLong()), 2, RoundingMode.HALF_UP)
        }

    @Transient
    open fun isStreckungAktivFuerJahr(jahr: Int): Boolean {
        val startJahr = streckungStartJahr
        val jahre = streckungJahre
        if (startJahr == null || jahre == null || jahre <= 1) {
            return startJahr == null || startJahr == jahr
        }
        return jahr >= startJahr && jahr < startJahr + jahre
    }

    @Transient
    open fun isKostenstellenZuordnung(): Boolean = kostenstelle != null
}
