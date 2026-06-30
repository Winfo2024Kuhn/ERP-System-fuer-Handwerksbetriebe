package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto
import org.example.kalkulationsprogramm.repository.ArtikelRepository
import org.example.kalkulationsprogramm.repository.KategorieRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.example.kalkulationsprogramm.repository.WerkstoffRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArtikelService(
    private val artikelRepository: ArtikelRepository,
    private val kategorieRepository: KategorieRepository,
    private val werkstoffRepository: WerkstoffRepository,
    private val lieferantenRepository: LieferantenRepository,
) : ArtikelServiceContract {
    @Transactional
    override fun erstelleArtikel(dto: ArtikelCreateDto): Artikel {
        val artikel = Artikel()
        artikel.produktname = dto.produktname
        artikel.produktlinie = dto.produktlinie
        artikel.produkttext = dto.produkttext
        artikel.verpackungseinheit = dto.verpackungseinheit
        artikel.preiseinheit = dto.preiseinheit
        artikel.verrechnungseinheit = dto.verrechnungseinheit

        val kategorieId = dto.kategorieId
        if (kategorieId != null) {
            kategorieRepository.findById(Math.toIntExact(kategorieId)).ifPresent { artikel.kategorie = it }
        }

        val werkstoffId = dto.werkstoffId
        if (werkstoffId != null) {
            werkstoffRepository.findById(werkstoffId).ifPresent { artikel.werkstoff = it }
        }

        val saved = artikelRepository.save(artikel)

        if (dto.preis != null || !dto.externeArtikelnummer.isNullOrBlank()) {
            val preis = LieferantenArtikelPreise()
            preis.artikel = saved
            preis.preis = dto.preis
            preis.externeArtikelnummer = dto.externeArtikelnummer
            if (dto.lieferantId != null) {
                lieferantenRepository.findById(dto.lieferantId).ifPresent { preis.lieferant = it }
            }
            saved.artikelpreis.add(preis)
            artikelRepository.save(saved)
        }

        return saved
    }

    @Transactional(readOnly = true)
    override fun findeAlleByIds(ids: List<Long>): List<Artikel> =
        artikelRepository.findAllById(ids)

    @Transactional(readOnly = true)
    override fun suche(specification: Specification<Artikel>, pageable: Pageable): Page<Artikel> =
        artikelRepository.findAll(specification, pageable)

    @Transactional(readOnly = true)
    fun sucheNullable(specification: Specification<Artikel>?, pageable: Pageable): Page<Artikel> =
        if (specification == null) artikelRepository.findAll(pageable) else artikelRepository.findAll(specification, pageable)

    @Transactional(readOnly = true)
    override fun findeProduktlinienOhneLieferant(lieferantId: Long?): List<String> =
        artikelRepository.findDistinctProduktlinieExcludingLieferant(lieferantId)

    @Transactional
    override fun fuegeExterneNummerHinzu(artikelId: Long?, lieferant: Lieferanten, nummer: String) {
        if (artikelId == null) {
            return
        }
        artikelRepository.findById(artikelId).ifPresent {
            it.addExterneArtikelnummer(lieferant, nummer)
            artikelRepository.save(it)
        }
    }
}
