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
import java.math.BigDecimal
import java.time.LocalDate

@Entity
open class Mitarbeiter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false)
    open var vorname: String? = null

    @Column(nullable = false)
    open var nachname: String? = null

    open var strasse: String? = null
    open var plz: String? = null
    open var ort: String? = null

    @Column
    open var email: String? = null

    @Column
    open var telefon: String? = null

    @Column
    open var festnetz: String? = null

    @Enumerated(EnumType.STRING)
    @Column
    open var qualifikation: Qualifikation? = null

    @Column
    open var geburtstag: LocalDate? = null

    @Column
    open var eintrittsdatum: LocalDate? = null

    @Column(nullable = false)
    open var aktiv: Boolean? = true

    @Column(precision = 10, scale = 2)
    open var stundenlohn: BigDecimal? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "beschaeftigungsart", nullable = false)
    open var beschaeftigungsart: Beschaeftigungsart? = Beschaeftigungsart.REGULAER

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "krankenkasse_id")
    open var krankenkasse: Krankenkasse? = null

    @Column(nullable = false)
    open var kinderlos: Boolean? = false

    @Column(name = "ist_geschaeftsfuehrer", nullable = false)
    open var istGeschaeftsfuehrer: Boolean? = false

    @Column(name = "kalkulatorischer_lohn_monat", precision = 12, scale = 2)
    open var kalkulatorischerLohnMonat: BigDecimal? = null

    @Column(name = "geldwert_vorteil_monat", precision = 12, scale = 2)
    open var geldwertVorteilMonat: BigDecimal? = null

    @Column
    open var jahresUrlaub: Int? = null

    @Column
    open var resturlaubVorjahr: Int? = null

    @Column
    open var urlaubsKorrektur: Int? = null

    @Column(unique = true)
    open var loginToken: String? = null

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "mitarbeiter_abteilung",
        joinColumns = [JoinColumn(name = "mitarbeiter_id")],
        inverseJoinColumns = [JoinColumn(name = "abteilung_id")],
    )
    open var abteilungen: MutableSet<Abteilung> = HashSet()

    @OneToMany(mappedBy = "mitarbeiter", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var dokumente: MutableList<MitarbeiterDokument> = ArrayList()

    @OneToMany(mappedBy = "mitarbeiter", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var notizen: MutableList<MitarbeiterNotiz> = ArrayList()

    @OneToMany(mappedBy = "mitarbeiter", cascade = [CascadeType.ALL])
    open var lohnabrechnungen: MutableList<Lohnabrechnung> = ArrayList()
}
