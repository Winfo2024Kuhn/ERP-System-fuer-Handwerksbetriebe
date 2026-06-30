package org.example.kalkulationsprogramm.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import java.util.Objects

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
open class Artikel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @OneToMany(mappedBy = "artikel", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var artikelpreis: MutableList<LieferantenArtikelPreise> = ArrayList()

    open var produktlinie: String? = null

    open var produktname: String? = null

    open var produkttext: String? = null

    open var verpackungseinheit: Long? = null

    open var hicadName: String? = null

    open var preiseinheit: String? = null

    @Enumerated(EnumType.STRING)
    open var verrechnungseinheit: Verrechnungseinheit? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kategorie_id")
    open var kategorie: Kategorie? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "werkstoff_id")
    open var werkstoff: Werkstoff? = null

    open fun getExterneArtikelnummer(): String? =
        artikelpreis
            .mapNotNull { it.externeArtikelnummer }
            .firstOrNull { it.isNotBlank() }

    open fun getExterneArtikelnummer(lieferant: Lieferanten?): String? =
        artikelpreis
            .asSequence()
            .filter { Objects.equals(lieferant, it.lieferant) }
            .mapNotNull { it.externeArtikelnummer }
            .firstOrNull { it.isNotBlank() }

    open fun setExterneArtikelnummer(nummer: String?) {
        artikelpreis.clear()
        if (nummer != null) {
            val externeNummer = LieferantenArtikelPreise()
            externeNummer.artikel = this
            externeNummer.externeArtikelnummer = nummer
            artikelpreis.add(externeNummer)
        }
    }

    open fun addExterneArtikelnummer(nummer: String?) {
        if (nummer != null && artikelpreis.none { nummer == it.externeArtikelnummer }) {
            val externeNummer = LieferantenArtikelPreise()
            externeNummer.artikel = this
            externeNummer.externeArtikelnummer = nummer
            artikelpreis.add(externeNummer)
        }
    }

    open fun addExterneArtikelnummer(lieferant: Lieferanten?, nummer: String?) {
        if (
            nummer != null &&
            artikelpreis.none { nummer == it.externeArtikelnummer && Objects.equals(lieferant, it.lieferant) }
        ) {
            val externeNummer = LieferantenArtikelPreise()
            externeNummer.artikel = this
            externeNummer.externeArtikelnummer = nummer
            externeNummer.lieferant = lieferant
            artikelpreis.add(externeNummer)
        }
    }
}
