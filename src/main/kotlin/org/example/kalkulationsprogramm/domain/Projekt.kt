package org.example.kalkulationsprogramm.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Transient
import org.hibernate.annotations.BatchSize
import java.math.BigDecimal
import java.time.LocalDate

@Entity
open class Projekt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false)
    open var bauvorhaben: String? = null

    @Column
    open var strasse: String? = null

    @Column
    open var plz: String? = null

    @Column
    open var ort: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    open var kurzbeschreibung: String? = null

    @Column(nullable = false, unique = true)
    open var auftragsnummer: String? = null

    @Column(nullable = false)
    open var anlegedatum: LocalDate? = null

    @Column
    open var abschlussdatum: LocalDate? = null

    @Column(name = "bild_url")
    open var bildUrl: String? = null

    @Column(nullable = false)
    open var bruttoPreis: BigDecimal? = null

    @Column(nullable = false)
    open var bezahlt: Boolean = false

    @Column(nullable = false)
    open var abgeschlossen: Boolean = false

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var projektArt: ProjektArt = ProjektArt.PAUSCHAL

    @BatchSize(size = 30)
    @OneToMany(mappedBy = "projekt", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var projektProduktkategorien: MutableList<ProjektProduktkategorie> = mutableListOf()

    @OneToMany(mappedBy = "projekt", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var materialkosten: MutableList<Materialkosten> = mutableListOf()

    @OneToMany(mappedBy = "projekt", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var artikelInProjekt: MutableList<ArtikelInProjekt> = mutableListOf()

    @OneToMany(mappedBy = "projekt", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var zeitbuchungen: MutableList<Zeitbuchung> = mutableListOf()

    @OneToMany(mappedBy = "projekt", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var projektDokument: MutableList<ProjektDokument> = mutableListOf()

    @OneToMany(mappedBy = "projekt", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var anfragen: MutableList<Anfrage> = mutableListOf()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kundenId")
    open var kundenId: Kunde? = null

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "projekt_kunden_emails", joinColumns = [JoinColumn(name = "projekt_id")])
    @Column(name = "email")
    open var kundenEmails: MutableList<String> = mutableListOf()

    @Transient
    open fun isProduktiv(): Boolean = projektArt.isProduktiv()

    @Transient
    open fun getKunde(): String? = kundenId?.name

    @Transient
    open fun getKundennummer(): String? = kundenId?.kundennummer

    @Transient
    open fun getAllEmails(): MutableList<String> {
        val allEmails = ArrayList(kundenEmails)
        kundenId?.kundenEmails?.forEach { email ->
            if (!allEmails.contains(email)) {
                allEmails.add(email)
            }
        }
        return allEmails
    }

    open fun isBezahlt(): Boolean = bezahlt

    open fun isAbgeschlossen(): Boolean = abgeschlossen
}
