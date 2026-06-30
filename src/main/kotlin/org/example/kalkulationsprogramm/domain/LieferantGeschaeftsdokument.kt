package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "lieferant_geschaeftsdokument")
open class LieferantGeschaeftsdokument {
    @Id
    open var id: Long? = null

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    open var dokument: LieferantDokument? = null

    @Column(length = 50)
    open var dokumentNummer: String? = null

    open var dokumentDatum: LocalDate? = null

    @Column(precision = 12, scale = 2)
    open var betragNetto: BigDecimal? = null

    @Column(precision = 12, scale = 2)
    open var betragBrutto: BigDecimal? = null

    @Column(precision = 5, scale = 4)
    open var mwstSatz: BigDecimal? = null

    open var liefertermin: LocalDate? = null

    @Column(length = 50)
    open var bestellnummer: String? = null

    @Column(length = 50)
    open var referenzNummer: String? = null

    @Column(columnDefinition = "TEXT")
    open var aiRawJson: String? = null

    open var aiConfidence: Double? = null
    open var analysiertAm: LocalDateTime? = null
    open var zahlungsziel: LocalDate? = null

    @Column(nullable = false)
    open var bezahlt: Boolean? = false

    open var bezahltAm: LocalDate? = null
    open var bereitsGezahlt: Boolean? = false

    @Column(length = 50)
    open var zahlungsart: String? = null

    open var skontoTage: Int? = null

    @Column(precision = 5, scale = 2)
    open var skontoProzent: BigDecimal? = null

    open var nettoTage: Int? = null

    @Column(precision = 12, scale = 2)
    open var tatsaechlichGezahlt: BigDecimal? = null

    open var mitSkonto: Boolean? = false

    @Column(nullable = false)
    open var lagerbestellung: Boolean? = false

    open var verifiziert: Boolean? = false
    open var datenquelle: String? = null

    @Column(nullable = false)
    open var genehmigt: Boolean? = false

    @Column(nullable = false)
    open var manuellePruefungErforderlich: Boolean? = false

    @Transient
    open var detectedTyp: LieferantDokumentTyp? = null

    open fun isBezahlt(): Boolean? = bezahlt
}
