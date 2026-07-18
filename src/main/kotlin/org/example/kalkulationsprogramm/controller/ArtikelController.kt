package org.example.kalkulationsprogramm.controller

import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Root
import jakarta.persistence.criteria.Subquery
import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe
import org.example.kalkulationsprogramm.domain.Kategorie
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise
import org.example.kalkulationsprogramm.domain.Werkstoff
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelResponseDto
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelSearchResponseDto
import org.example.kalkulationsprogramm.dto.Artikel.ExterneNummerDto
import org.example.kalkulationsprogramm.dto.Artikel.LieferantPreisDto
import org.example.kalkulationsprogramm.dto.ImportAnalysisResult
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.example.kalkulationsprogramm.repository.WerkstoffRepository
import org.example.kalkulationsprogramm.service.ArtikelImportService
import org.example.kalkulationsprogramm.service.ArtikelMatchingService
import org.example.kalkulationsprogramm.service.ArtikelServiceContract
import org.example.kalkulationsprogramm.service.KategorieService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.Locale
import java.util.TreeSet
import java.util.stream.Stream

@RestController
@RequestMapping("/api/artikel")
class ArtikelController(
    private val artikelService: ArtikelServiceContract,
    private val artikelImportService: ArtikelImportService,
    private val artikelMatchingService: ArtikelMatchingService,
    private val lieferantenRepository: LieferantenRepository,
    private val kategorieService: KategorieService,
    private val werkstoffRepository: WerkstoffRepository,
) {
    @PostMapping
    @Transactional
    fun erstelle(@RequestBody dto: ArtikelCreateDto): ResponseEntity<ArtikelResponseDto> {
        val erstellt = artikelService.erstelleArtikel(dto)
        return ResponseEntity.ok(toDto(erstellt, null))
    }

    @PostMapping("/import/headers")
    fun readHeaders(@RequestParam("file") file: MultipartFile): ResponseEntity<List<String>> =
        ResponseEntity.ok(artikelImportService.readHeaders(file))

    @PostMapping("/import/analyze")
    fun analyzeImport(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("lieferant") lieferant: String,
        @RequestParam spaltenZuordnung: MutableMap<String, String>,
    ): ResponseEntity<ImportAnalysisResult> {
        spaltenZuordnung.remove("lieferant")
        return ResponseEntity.ok(artikelImportService.analyzeImport(file, lieferant, spaltenZuordnung))
    }

    @PostMapping("/import")
    fun importiere(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("lieferant") lieferant: String,
        @RequestParam(value = "kategorieId", required = false) kategorieId: Long?,
        @RequestParam spaltenZuordnung: MutableMap<String, String>,
    ): ResponseEntity<Void> {
        spaltenZuordnung.remove("lieferant")
        spaltenZuordnung.remove("kategorieId")
        artikelImportService.importiereCsv(file, lieferant, spaltenZuordnung, kategorieId)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/lieferanten")
    fun alleLieferanten(): List<String> =
        lieferantenRepository.findAll()
            .asSequence()
            .filter { it.istAktiv == true }
            .mapNotNull { it.lieferantenname?.trim() }
            .filter { it.isNotEmpty() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()

    @GetMapping("/produktlinien")
    fun alleProduktlinien(): List<String> =
        artikelService.findeProduktlinienOhneLieferant(1L)
            .asSequence()
            .map { it.trim() }
            .toCollection(TreeSet(String.CASE_INSENSITIVE_ORDER))
            .toList()

    @GetMapping("/werkstoffe")
    fun alleWerkstoffe(): List<String> =
        werkstoffRepository.findAll()
            .asSequence()
            .mapNotNull { it.name }
            .filter { StringUtils.hasText(it) }
            .map { it.trim() }
            .toCollection(TreeSet(String.CASE_INSENSITIVE_ORDER))
            .toList()

    @GetMapping("/werkstoffe/details")
    fun alleWerkstoffeDetails(): List<Map<String, Any?>> =
        werkstoffRepository.findAll()
            .map { mapOf("id" to it.id, "name" to it.name) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it["name"] as? String ?: "" })

    @GetMapping("/match")
    @Transactional(readOnly = true)
    fun match(
        @RequestParam(required = false) produktname: String?,
        @RequestParam(required = false) produktlinie: String?,
    ): List<ArtikelResponseDto> =
        artikelMatchingService.findeBesteTreffer(produktname, produktlinie)
            .map { toDto(it, null) }

    @GetMapping
    @Transactional(readOnly = true)
    fun sucheArtikel(
        @RequestParam(value = "q", required = false) query: String?,
        @RequestParam(value = "lieferant", required = false) lieferant: String?,
        @RequestParam(value = "produktlinie", required = false) produktlinie: String?,
        @RequestParam(value = "werkstoff", required = false) werkstoff: String?,
        @RequestParam(value = "kategorieId", required = false) kategorieId: Int?,
        @RequestParam(value = "nurMitLieferantenpreis", defaultValue = "false") nurMitLieferantenpreis: Boolean,
        @RequestParam(value = "page", defaultValue = "0") page: Int,
        @RequestParam(value = "size", defaultValue = "50") size: Int,
        @RequestParam(value = "sort", defaultValue = "produktname") sort: String,
        @RequestParam(value = "dir", defaultValue = "asc") direction: String,
    ): ArtikelSearchResponseDto {
        val pageIndex = page.coerceAtLeast(0)
        val pageSize = size.coerceAtLeast(1).coerceAtMost(MAX_PAGE_SIZE)
        val kategorieIds = kategorieService.findeKategorieUndUnterkategorieIds(kategorieId)
        val specification = buildArtikelSpecification(
            query,
            lieferant,
            produktlinie,
            werkstoff,
            kategorieId,
            kategorieIds,
            nurMitLieferantenpreis,
        )
        val result = artikelService.suche(specification, PageRequest.of(pageIndex, pageSize, buildSort(sort, direction)))
        val daten = result.stream().flatMap { mappeArtikelZuDtos(it) }.toList()

        return ArtikelSearchResponseDto().apply {
            artikel = daten
            gesamt = result.totalElements
            seite = result.number
            seitenGroesse = result.size
        }
    }

    private fun toDto(artikel: Artikel, preis: LieferantenArtikelPreise?): ArtikelResponseDto {
        val lieferant = preis?.lieferant
        var externeNummer = artikel.getExterneArtikelnummer(lieferant)
        if (externeNummer == null && preis != null) {
            val nummer = preis.externeArtikelnummer
            if (!nummer.isNullOrBlank()) {
                externeNummer = nummer
            }
        }
        if (externeNummer == null && lieferant == null) {
            externeNummer = artikel.getExterneArtikelnummer()
        }

        return ArtikelResponseDto().apply {
            id = artikel.id
            externeArtikelnummer = externeNummer
            produktlinie = artikel.produktlinie
            produktname = artikel.produktname
            produkttext = artikel.produkttext
            verpackungseinheit = artikel.verpackungseinheit
            preiseinheit = artikel.preiseinheit
            verrechnungseinheit = artikel.verrechnungseinheit
            if (artikel is ArtikelWerkstoffe && artikel.masse != null) {
                kgProMeter = artikel.masse
            }
            if (preis != null) {
                this.preis = preis.preis
                preisDatum = preis.preisAenderungsdatum
            }
            if (preis?.lieferant != null) {
                val lp = LieferantPreisDto().apply {
                    lieferantId = preis.lieferant!!.id
                    lieferantName = preis.lieferant!!.lieferantenname
                    this.preis = preis.preis
                }
                lieferantenpreise = listOf(lp)
                lieferantId = lp.lieferantId
                lieferantenname = lp.lieferantName
            } else {
                lieferantenpreise = emptyList()
            }
            artikel.kategorie?.let { kategorie ->
                kategorieId = kategorie.id?.toLong()
                kategoriePfad = buildPfad(kategorie)
                isMeterware = istKategorieEinsOderUnterkategorie(kategorie)
                kategorie.parentKategorie?.let { parentKategorieId = it.id?.toLong() }
                var root = kategorie
                while (root.parentKategorie != null) {
                    root = root.parentKategorie!!
                }
                rootKategorieId = root.id?.toLong()
                rootKategorieName = root.beschreibung
            }
            artikel.werkstoff?.let {
                werkstoffId = it.id
                werkstoffName = it.name
            }
        }
    }

    @PostMapping("/{id}/externe-nummer")
    fun setzeExterneNummer(
        @PathVariable("id") artikelId: Long,
        @RequestBody dto: ExterneNummerDto,
    ): ResponseEntity<Void> {
        val lieferant = dto.lieferantId?.let { lieferantenRepository.findById(it).orElse(null) }
        artikelService.fuegeExterneNummerHinzu(artikelId, lieferant!!, dto.nummer!!)
        return ResponseEntity.ok().build()
    }

    private fun mappeArtikelZuDtos(artikel: Artikel): Stream<ArtikelResponseDto> {
        val gruppiert = artikel.artikelpreis
            .filter { it.preis != null }
            .groupBy { it.lieferant }
        if (gruppiert.isEmpty()) {
            return Stream.of(toDto(artikel, null))
        }
        return gruppiert.values
            .map { preise ->
                val preis = preise.maxWithOrNull(compareBy(nullsLast()) { it.preisAenderungsdatum })
                toDto(artikel, preis)
            }
            .stream()
    }

    private fun buildArtikelSpecification(
        query: String?,
        lieferant: String?,
        produktlinie: String?,
        werkstoff: String?,
        kategorieId: Int?,
        kategorieIds: List<Int>?,
        nurMitLieferantenpreis: Boolean,
    ): Specification<Artikel> {
        var specification: Specification<Artikel> = Specification.where { _, _, cb -> cb.conjunction() }

        if (StringUtils.hasText(query)) {
            val likeValue = wrapLike(query).lowercase(Locale.GERMAN)
            specification = specification.and { root, cq, cb ->
                val werkstoffJoin: Join<Artikel, Werkstoff> = root.join("werkstoff", JoinType.LEFT)
                val preisSubquery: Subquery<Long> = cq.subquery(Long::class.java)
                val subRoot: Root<LieferantenArtikelPreise> = preisSubquery.from(LieferantenArtikelPreise::class.java)
                val subLieferant: Join<LieferantenArtikelPreise, Lieferanten> = subRoot.join("lieferant", JoinType.LEFT)
                preisSubquery.select(cb.literal(1L))
                preisSubquery.where(
                    cb.equal(subRoot.get<Artikel>("artikel"), root),
                    cb.or(
                        cb.like(cb.lower(subRoot.get("externeArtikelnummer")), likeValue),
                        cb.like(cb.lower(subLieferant.get("lieferantenname")), likeValue),
                    ),
                )
                cb.or(
                    cb.like(cb.lower(root.get("produktname")), likeValue),
                    cb.like(cb.lower(root.get("produktlinie")), likeValue),
                    cb.like(cb.lower(root.get("produkttext")), likeValue),
                    cb.like(cb.lower(werkstoffJoin.get("name")), likeValue),
                    cb.exists(preisSubquery),
                )
            }
        }

        if (StringUtils.hasText(lieferant)) {
            val normalized = lieferant!!.trim().lowercase(Locale.GERMAN)
            specification = specification.and { root, cq, cb ->
                val priceSubquery: Subquery<Long> = cq.subquery(Long::class.java)
                val subRoot: Root<LieferantenArtikelPreise> = priceSubquery.from(LieferantenArtikelPreise::class.java)
                val subLieferant: Join<LieferantenArtikelPreise, Lieferanten> = subRoot.join("lieferant", JoinType.LEFT)
                priceSubquery.select(cb.literal(1L))
                priceSubquery.where(
                    cb.equal(subRoot.get<Artikel>("artikel"), root),
                    cb.equal(cb.lower(subLieferant.get("lieferantenname")), normalized),
                    cb.isNotNull(subRoot.get<Any>("preis")),
                )
                cb.exists(priceSubquery)
            }
        }

        if (StringUtils.hasText(produktlinie)) {
            val likeValue = wrapLike(produktlinie)
            specification = specification.and { root, _, cb ->
                cb.like(cb.lower(root.get("produktlinie")), likeValue.lowercase(Locale.GERMAN))
            }
        }

        if (StringUtils.hasText(werkstoff)) {
            val normalized = werkstoff!!.trim().lowercase(Locale.GERMAN)
            specification = specification.and { root, _, cb ->
                val werkstoffJoin: Join<Artikel, Werkstoff> = root.join("werkstoff", JoinType.LEFT)
                cb.equal(cb.lower(werkstoffJoin.get("name")), normalized)
            }
        }

        if (kategorieId != null) {
            specification = if (kategorieIds.isNullOrEmpty()) {
                specification.and { _, _, cb -> cb.disjunction() }
            } else {
                specification.and { root, _, _ -> root.join<Artikel, Kategorie>("kategorie", JoinType.LEFT).get<Int>("id").`in`(kategorieIds) }
            }
        }

        if (nurMitLieferantenpreis) {
            specification = specification.and { root, cq, cb ->
                val preisSubquery: Subquery<Long> = cq.subquery(Long::class.java)
                val subRoot: Root<LieferantenArtikelPreise> = preisSubquery.from(LieferantenArtikelPreise::class.java)
                preisSubquery.select(cb.literal(1L))
                preisSubquery.where(
                    cb.equal(subRoot.get<Artikel>("artikel"), root),
                    cb.isNotNull(subRoot.get<Any>("preis")),
                    cb.isNotNull(subRoot.get<Any>("lieferant")),
                )
                cb.exists(preisSubquery)
            }
        }

        return specification
    }

    private fun buildSort(sort: String, direction: String): Sort {
        val field = SORT_FIELDS[sort] ?: SORT_FIELDS.getValue("produktname")
        val dir = if ("desc".equals(direction, ignoreCase = true)) Sort.Direction.DESC else Sort.Direction.ASC
        var order = Sort.Order(dir, field.property)
        if (field.ignoreCase) {
            order = order.ignoreCase()
        }
        return Sort.by(order)
    }

    private fun wrapLike(value: String?): String = "%${value!!.trim()}%"

    private fun istKategorieEinsOderUnterkategorie(k: Kategorie): Boolean {
        var current: Kategorie? = k
        while (current != null) {
            if (current.id == 1) return true
            current = current.parentKategorie
        }
        return false
    }

    private fun buildPfad(kategorie: Kategorie): String {
        val parts = ArrayList<String>()
        var current: Kategorie? = kategorie
        while (current != null) {
            current.beschreibung?.let { parts.add(it) }
            current = current.parentKategorie
        }
        parts.reverse()
        return parts.joinToString(" > ")
    }

    private data class SortField(val property: String, val ignoreCase: Boolean)

    private companion object {
        private const val MAX_PAGE_SIZE = 50
        private val SORT_FIELDS = mapOf(
            "externeArtikelnummer" to SortField("artikelpreis.externeArtikelnummer", true),
            "produktlinie" to SortField("produktlinie", true),
            "produktname" to SortField("produktname", true),
            "produkttext" to SortField("produkttext", true),
            "verpackungseinheit" to SortField("verpackungseinheit", false),
            "werkstoffName" to SortField("werkstoff.name", true),
            "lieferantenname" to SortField("artikelpreis.lieferant.lieferantenname", true),
            "preis" to SortField("artikelpreis.preis", false),
            "preisDatum" to SortField("artikelpreis.preisAenderungsdatum", false),
        )
    }
}
