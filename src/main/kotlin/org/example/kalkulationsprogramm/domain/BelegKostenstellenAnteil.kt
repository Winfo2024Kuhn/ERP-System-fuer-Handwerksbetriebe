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
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

@Entity
@Table(name = "beleg_kostenstellen_anteil")
open class BelegKostenstellenAnteil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beleg_id", nullable = false)
    open var beleg: Beleg? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kostenstelle_id", nullable = false)
    open var kostenstelle: Kostenstelle? = null

    open var prozent: Int? = null

    @Column(name = "absoluter_betrag", precision = 15, scale = 2)
    open var absoluterBetrag: BigDecimal? = null

    @Column(name = "berechneter_betrag", precision = 15, scale = 2)
    open var berechneterBetrag: BigDecimal? = null

    @Column(length = 255)
    open var beschreibung: String? = null

    @Column(name = "zugeordnet_am")
    open var zugeordnetAm: LocalDateTime? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zugeordnet_von_user_id")
    open var zugeordnetVon: FrontendUserProfile? = null

    @Column(name = "streckung_jahre", nullable = false)
    open var streckungJahre: Int? = 1

    @Column(name = "streckung_start_jahr")
    open var streckungStartJahr: Int? = null

    @PrePersist
    @PreUpdate
    protected open fun onSave() {
        if (zugeordnetAm == null) {
            zugeordnetAm = LocalDateTime.now()
        }
        if (streckungJahre == null) {
            streckungJahre = 1
        }
        if (prozent == null && absoluterBetrag == null) {
            throw IllegalStateException(
                "BelegKostenstellenAnteil: entweder prozent ODER absoluterBetrag muss gesetzt sein",
            )
        }
        if (prozent != null && absoluterBetrag != null) {
            throw IllegalStateException(
                "BelegKostenstellenAnteil: nur EINES von prozent oder absoluterBetrag darf gesetzt sein",
            )
        }
        val prozentWert = prozent
        if (prozentWert != null && (prozentWert < 0 || prozentWert > 100)) {
            throw IllegalStateException(
                "BelegKostenstellenAnteil: prozent muss zwischen 0 und 100 liegen",
            )
        }
    }

    open fun berechneAnteil(nettoBetrag: BigDecimal?, bruttoBetrag: BigDecimal?) {
        val absolut = absoluterBetrag
        if (absolut != null) {
            berechneterBetrag = absolut
            return
        }
        val prozentWert = prozent ?: return
        val basis = nettoBetrag ?: bruttoBetrag ?: return
        berechneterBetrag = basis
            .multiply(BigDecimal.valueOf(prozentWert.toLong()))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
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
}
