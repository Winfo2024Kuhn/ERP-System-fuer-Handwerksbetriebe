package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "dokument_freigabe")
open class DokumentFreigabe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false, unique = true, length = 36)
    open var uuid: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "quell_typ", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    open var quellTyp: FreigabeQuellTyp? = null

    @Column(name = "quell_dokument_id", nullable = false)
    open var quellDokumentId: Long? = null

    @Column(name = "dokument_nummer", nullable = false, length = 100)
    open var dokumentNummer: String? = null

    @Column(name = "dokument_art", nullable = false, length = 50)
    open var dokumentArt: String? = null

    @Column(name = "dokument_betrag", precision = 12, scale = 2)
    open var dokumentBetrag: BigDecimal? = null

    @Column(name = "dokument_datei", length = 255)
    open var dokumentDatei: String? = null

    @Column(name = "bauvorhaben", length = 500)
    open var bauvorhaben: String? = null

    @Column(name = "kunde_name", length = 255)
    open var kundeName: String? = null

    @Column(name = "kunde_email", length = 255)
    open var kundeEmail: String? = null

    @Column(name = "erstellt_am", nullable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    open var erstelltAm: LocalDateTime? = LocalDateTime.now()

    @Column(name = "ablauf_datum", nullable = false)
    open var ablaufDatum: LocalDateTime? = null

    @Column(name = "hash_original", nullable = false, length = 128)
    open var hashOriginal: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    open var status: FreigabeStatus? = FreigabeStatus.PENDING

    @Column(name = "akzeptiert_am")
    open var akzeptiertAm: LocalDateTime? = null

    @Column(name = "akzeptiert_ip", length = 45)
    open var akzeptiertIp: String? = null

    @Column(name = "akzeptiert_user_agent", length = 500)
    open var akzeptiertUserAgent: String? = null

    @Column(name = "akzeptiert_email", length = 255)
    open var akzeptiertEmail: String? = null

    @Column(name = "unterzeichner_vorname", length = 80)
    open var unterzeichnerVorname: String? = null

    @Column(name = "unterzeichner_nachname", length = 80)
    open var unterzeichnerNachname: String? = null

    @Column(name = "unterzeichner_name", length = 160)
    open var unterzeichnerName: String? = null

    @Column(name = "hash_acceptance", length = 128)
    open var hashAcceptance: String? = null

    @Column(name = "akzeptierte_alternativen", columnDefinition = "LONGTEXT")
    open var akzeptierteAlternativen: String? = null

    @Column(name = "akzeptierter_betrag", precision = 12, scale = 2)
    open var akzeptierterBetrag: BigDecimal? = null

    @Column(name = "positionen_snapshot", columnDefinition = "LONGTEXT")
    open var positionenSnapshot: String? = null

    @Column(name = "basis_netto", precision = 12, scale = 2)
    open var basisNetto: BigDecimal? = null

    @Column(name = "mwst_satz", precision = 5, scale = 4)
    open var mwstSatz: BigDecimal? = null

    @PrePersist
    protected open fun onCreate() {
        if (erstelltAm == null) {
            erstelltAm = LocalDateTime.now()
        }
    }

    @Transient
    open fun istAbgelaufen(): Boolean {
        val ablauf = ablaufDatum ?: return false
        return LocalDateTime.now().isAfter(ablauf)
    }
}
