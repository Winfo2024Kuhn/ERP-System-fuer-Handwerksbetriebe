package org.example.kalkulationsprogramm.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import java.time.LocalDateTime

@Entity
@Table(name = "lieferant_dokument")
open class LieferantDokument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id", nullable = false)
    @BatchSize(size = 50)
    open var lieferant: Lieferanten? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id")
    @BatchSize(size = 50)
    open var attachment: EmailAttachment? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var typ: LieferantDokumentTyp? = null

    open var originalDateiname: String? = null
    open var gespeicherterDateiname: String? = null

    @Column(nullable = false)
    open var uploadDatum: LocalDateTime? = null

    @Column(nullable = false)
    open var ausgeblendet: Boolean = false

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    @BatchSize(size = 50)
    open var uploadedBy: Mitarbeiter? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beleg_id")
    @BatchSize(size = 50)
    open var beleg: Beleg? = null

    @OneToOne(mappedBy = "dokument", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    open var geschaeftsdaten: LieferantGeschaeftsdokument? = null

    @OneToMany(mappedBy = "dokument", cascade = [CascadeType.ALL], orphanRemoval = true)
    @BatchSize(size = 50)
    open var projektAnteile: MutableSet<LieferantDokumentProjektAnteil> = HashSet()

    @ManyToMany
    @JoinTable(
        name = "lieferant_dokument_verknuepfung",
        joinColumns = [JoinColumn(name = "dokument_id")],
        inverseJoinColumns = [JoinColumn(name = "verknuepft_id")],
    )
    @BatchSize(size = 50)
    open var verknuepfteDokumente: MutableSet<LieferantDokument> = HashSet()

    @ManyToMany(mappedBy = "verknuepfteDokumente")
    @BatchSize(size = 50)
    open var verknuepftVon: MutableSet<LieferantDokument> = HashSet()

    @PrePersist
    protected open fun onCreate() {
        if (uploadDatum == null) {
            uploadDatum = LocalDateTime.now()
        }
    }

    open fun getEffektiverDateiname(): String? =
        attachment?.stringValue("getOriginalFilename") ?: originalDateiname

    open fun getEffektiverGespeicherterDateiname(): String? =
        attachment?.stringValue("getStoredFilename") ?: gespeicherterDateiname

    open fun isAusgeblendet(): Boolean = ausgeblendet

    private fun Any.stringValue(getter: String): String? =
        javaClass.getMethod(getter).invoke(this) as? String
}
