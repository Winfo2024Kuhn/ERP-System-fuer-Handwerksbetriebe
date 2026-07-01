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
import jakarta.persistence.OneToMany
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.EnumSet

@Entity
@Table(name = "ausgangs_geschaeftsdokument")
open class AusgangsGeschaeftsDokument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false, unique = true, length = 20)
    open var dokumentNummer: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "varchar(30)")
    open var typ: AusgangsGeschaeftsDokumentTyp? = null

    @Column(nullable = false)
    open var datum: LocalDate? = null

    @Column(length = 500)
    open var betreff: String? = null

    @Column(precision = 12, scale = 2)
    open var betragNetto: BigDecimal? = null

    @Column(precision = 12, scale = 2)
    open var betragBrutto: BigDecimal? = null

    @Column(precision = 5, scale = 4)
    open var mwstSatz: BigDecimal? = null

    open var abschlagsNummer: Int? = null

    @Column(columnDefinition = "LONGTEXT")
    open var htmlInhalt: String? = null

    @Column(columnDefinition = "LONGTEXT")
    open var positionenJson: String? = null

    @Column(nullable = false)
    open var gebucht: Boolean = false

    open var gebuchtAm: LocalDate? = null

    @Column(nullable = false)
    open var storniert: Boolean = false

    @Column(nullable = false)
    open var digitalAngenommen: Boolean = false

    open var storniertAm: LocalDate? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    open var projekt: Projekt? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anfrage_id")
    open var anfrage: Anfrage? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kunde_id")
    open var kunde: Kunde? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vorgaenger_id")
    open var vorgaenger: AusgangsGeschaeftsDokument? = null

    @OneToMany(mappedBy = "vorgaenger")
    open var nachfolger: MutableList<AusgangsGeschaeftsDokument> = mutableListOf()

    open var zahlungszielTage: Int? = null

    open var versandDatum: LocalDate? = null

    @Column(length = 500)
    open var rechnungsadresseOverride: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "erstellt_von_id")
    open var erstelltVon: FrontendUserProfile? = null

    @Column(nullable = false, updatable = false)
    open var erstelltAm: LocalDateTime? = null

    open var geaendertAm: LocalDateTime? = null

    @PrePersist
    protected open fun onCreate() {
        erstelltAm = LocalDateTime.now()
        geaendertAm = LocalDateTime.now()
        if (datum == null) {
            datum = LocalDate.now()
        }
        if (mwstSatz == null) {
            mwstSatz = BigDecimal("0.19")
        }
    }

    @PreUpdate
    protected open fun onUpdate() {
        geaendertAm = LocalDateTime.now()
    }

    open fun getMwstBetrag(): BigDecimal {
        val netto = betragNetto
        val satz = mwstSatz
        if (netto == null || satz == null) {
            return BigDecimal.ZERO
        }
        return netto.multiply(satz)
    }

    open fun isGebucht(): Boolean = gebucht

    open fun isStorniert(): Boolean = storniert

    open fun isDigitalAngenommen(): Boolean = digitalAngenommen

    open fun istBearbeitbar(): Boolean {
        if (storniert) return false
        if (digitalAngenommen) return false
        if (gebucht && SPERRBARE_TYPEN.contains(typ)) return false
        return true
    }

    companion object {
        private val SPERRBARE_TYPEN = EnumSet.of(
            AusgangsGeschaeftsDokumentTyp.RECHNUNG,
            AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT,
            AusgangsGeschaeftsDokumentTyp.STORNO,
        )
    }
}
