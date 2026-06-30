package org.example.kalkulationsprogramm.service

import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantArtikelpreisDto
import org.example.kalkulationsprogramm.repository.ArtikelRepository
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import java.math.BigDecimal
import java.util.Date
import java.util.Locale
import java.util.Optional

@Service
class LieferantArtikelpreisService(
    private val artikelPreiseRepository: LieferantenArtikelPreiseRepository,
    private val lieferantenRepository: LieferantenRepository,
    private val artikelRepository: ArtikelRepository,
    private val mapper: LieferantArtikelpreisMapper,
) {
    @Transactional(readOnly = true)
    fun suche(lieferantId: Long?, query: String?, pageable: Pageable): Page<LieferantArtikelpreisDto> {
        var spec = Specification.where(byLieferant(lieferantId))
        if (StringUtils.hasText(query)) {
            spec = spec.and(buildSuchkriterium(query!!))
        }
        return artikelPreiseRepository.findAll(spec, pageable).map { mapper.toDto(it) }
    }

    @Transactional
    fun aktualisiere(
        lieferantId: Long?,
        artikelId: Long?,
        preis: BigDecimal?,
        externeArtikelnummer: String?,
    ): Optional<LieferantArtikelpreisDto> {
        if (lieferantId == null || artikelId == null) {
            return Optional.empty()
        }
        return artikelPreiseRepository.findByArtikel_IdAndLieferant_Id(artikelId, lieferantId)
            .map { entity ->
                entity.preis = preis
                entity.preisAenderungsdatum = Date()
                entity.externeArtikelnummer = normalizeExterneArtikelnummer(externeArtikelnummer)
                mapper.toDto(artikelPreiseRepository.save(entity))
            }
    }

    @Transactional
    fun anlegen(
        lieferantId: Long?,
        artikelId: Long?,
        preis: BigDecimal?,
        externeArtikelnummer: String?,
    ): Optional<LieferantArtikelpreisDto> {
        if (lieferantId == null || artikelId == null) {
            return Optional.empty()
        }
        val lieferant = lieferantenRepository.findById(lieferantId).orElse(null) ?: return Optional.empty()
        val artikel = artikelRepository.findById(artikelId).orElse(null) ?: return Optional.empty()
        val entity = artikelPreiseRepository.findByArtikel_IdAndLieferant_Id(artikelId, lieferantId)
            .orElseGet {
                val neu = LieferantenArtikelPreise()
                neu.artikel = artikel
                neu.lieferant = lieferant
                neu
            }
        entity.preis = preis
        entity.preisAenderungsdatum = Date()
        entity.externeArtikelnummer = normalizeExterneArtikelnummer(externeArtikelnummer)
        return Optional.ofNullable(mapper.toDto(artikelPreiseRepository.save(entity)))
    }

    private fun byLieferant(lieferantId: Long?): Specification<LieferantenArtikelPreise> =
        Specification { root, query, cb ->
            query.distinct(true)
            cb.equal(root.join<LieferantenArtikelPreise, Any>("lieferant").get<Any>("id"), lieferantId)
        }

    private fun buildSuchkriterium(search: String): Specification<LieferantenArtikelPreise> =
        Specification { root, cq, cb ->
            cq.distinct(true)
            val like = "%" + search.trim().lowercase(Locale.GERMAN) + "%"
            val artikelJoin: Join<LieferantenArtikelPreise, Artikel> = root.join("artikel", JoinType.LEFT)
            val werkstoffJoin: Join<Artikel, *> = artikelJoin.join<Artikel, Any>("werkstoff", JoinType.LEFT)
            val externe: Predicate = cb.like(cb.lower(root.get("externeArtikelnummer")), like)
            val produktname: Predicate = cb.like(cb.lower(artikelJoin.get("produktname")), like)
            val produkttext: Predicate = cb.like(cb.lower(artikelJoin.get("produkttext")), like)
            val werkstoff: Predicate = cb.like(cb.lower(werkstoffJoin.get("name")), like)
            cb.or(externe, produktname, produkttext, werkstoff)
        }

    private fun normalizeExterneArtikelnummer(externeArtikelnummer: String?): String? =
        if (!StringUtils.hasText(externeArtikelnummer)) null else externeArtikelnummer!!.trim()
}
