package org.example.kalkulationsprogramm.controller

import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.ListJoin
import jakarta.validation.Valid
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantCreateRequestDto
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantListItemDto
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantSearchResponseDto
import org.example.kalkulationsprogramm.event.EmailAddressChangedEvent
import org.example.kalkulationsprogramm.mapper.LieferantMapper
import org.example.kalkulationsprogramm.repository.KostenstelleRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.example.kalkulationsprogramm.service.LieferantEmailResolver
import org.example.kalkulationsprogramm.service.LieferantenDetailService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@RestController
@RequestMapping("/api/lieferanten")
class LieferantenController(
    private val lieferantenRepository: LieferantenRepository,
    private val kostenstelleRepository: KostenstelleRepository,
    private val lieferantMapper: LieferantMapper,
    private val lieferantEmailResolver: LieferantEmailResolver,
    private val lieferantenDetailService: LieferantenDetailService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @GetMapping
    fun sucheLieferanten(
        @RequestParam(value = "q", required = false) query: String?,
        @RequestParam(value = "name", required = false) name: String?,
        @RequestParam(value = "typ", required = false) typ: String?,
        @RequestParam(value = "ort", required = false) ort: String?,
        @RequestParam(value = "email", required = false) email: String?,
        @RequestParam(value = "page", defaultValue = "0") page: Int,
        @RequestParam(value = "size", defaultValue = "50") size: Int,
    ): LieferantSearchResponseDto {
        val pageIndex = page.coerceAtLeast(0)
        val pageSize = size.coerceAtLeast(1).coerceAtMost(MAX_PAGE_SIZE)

        var specs: Specification<Lieferanten> = Specification.where(null)
        if (StringUtils.hasText(query)) {
            val likeValue = wrapLike(query).lowercase(Locale.ROOT)
            specs = specs.and { root, cq, cb ->
                cq.distinct(true)
                cb.or(
                    cb.like(cb.lower(root.get("lieferantenname")), likeValue),
                    cb.like(cb.lower(root.get("lieferantenTyp")), likeValue),
                    cb.like(cb.lower(root.get("vertreter")), likeValue),
                    cb.like(cb.lower(root.get("ort")), likeValue),
                    cb.like(cb.lower(root.get("strasse")), likeValue),
                    cb.like(cb.lower(cb.coalesce(root.get("telefon"), "")), likeValue),
                    cb.like(cb.lower(cb.coalesce(root.get("mobiltelefon"), "")), likeValue),
                )
            }
        }
        specs = specs.and(buildSpec("lieferantenname", name))
        specs = specs.and(buildSpec("lieferantenTyp", typ))
        specs = specs.and(buildSpec("ort", ort))
        if (StringUtils.hasText(email)) {
            val likeValue = wrapLike(email).lowercase(Locale.ROOT)
            specs = specs.and { root, cq, cb ->
                cq.distinct(true)
                val join: ListJoin<Lieferanten, String> = root.joinList("kundenEmails", JoinType.LEFT)
                cb.like(cb.lower(join), likeValue)
            }
        }

        val result = lieferantenRepository.findAll(
            specs,
            PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Order.asc("lieferantenname").ignoreCase())),
        )
        return LieferantSearchResponseDto(
            lieferanten = result.stream().map { lieferantMapper.toListItem(it)!! }.toList(),
            gesamt = result.totalElements,
            seite = pageIndex,
            seitenGroesse = pageSize,
        )
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun createLieferant(@Valid @RequestBody request: LieferantCreateRequestDto): LieferantListItemDto {
        val name = request.lieferantenname!!.trim()
        lieferantenRepository.findByLieferantennameIgnoreCase(name).ifPresent {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Lieferant existiert bereits.")
        }

        val saved = lieferantenRepository.save(
            Lieferanten().apply {
                applyRequest(this, request)
                lieferantenname = name
            },
        )
        refreshResolver(saved, true)
        return lieferantMapper.toListItem(saved)!!
    }

    @PutMapping("/{id}")
    @Transactional
    fun updateLieferant(
        @PathVariable id: Long,
        @Valid @RequestBody request: LieferantCreateRequestDto,
    ): ResponseEntity<LieferantDetailDto> {
        val lieferant = lieferantenRepository.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        val name = request.lieferantenname!!.trim()
        if (!lieferant.lieferantenname.equals(name, ignoreCase = true)) {
            lieferantenRepository.findByLieferantennameIgnoreCase(name).ifPresent {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Lieferant existiert bereits.")
            }
        }
        applyRequest(lieferant, request)
        lieferant.lieferantenname = name
        val saved = lieferantenRepository.save(lieferant)
        refreshResolver(saved, false)
        return ResponseEntity.ok(lieferantenDetailService.loadDetails(id))
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<LieferantDetailDto> =
        lieferantenDetailService.loadDetails(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @GetMapping("/emails")
    fun getAllEmails(): List<String> =
        lieferantenRepository.findAllWithEmails()
            .flatMap { it.kundenEmails }
            .filter { StringUtils.hasText(it) }
            .map { it.trim().lowercase(Locale.ROOT) }
            .distinct()
            .sorted()

    private fun applyRequest(lieferant: Lieferanten, request: LieferantCreateRequestDto) {
        lieferant.eigeneKundennummer = trimToNull(request.eigeneKundennummer)
        lieferant.lieferantenTyp = trimToNull(request.lieferantenTyp)
        lieferant.rollen = request.rollen?.toMutableSet() ?: mutableSetOf()
        lieferant.vertreter = trimToNull(request.vertreter)
        lieferant.strasse = trimToNull(request.strasse)
        lieferant.plz = trimToNull(request.plz)
        lieferant.ort = trimToNull(request.ort)
        lieferant.telefon = trimToNull(request.telefon)
        lieferant.mobiltelefon = trimToNull(request.mobiltelefon)
        lieferant.istAktiv = request.istAktiv ?: true
        lieferant.startZusammenarbeit = toDate(request.startZusammenarbeit)
        lieferant.kundenEmails = ArrayList(normalizeEmails(request.kundenEmails))
        lieferant.standardKostenstelle = request.standardKostenstelleId?.let { id ->
            kostenstelleRepository.findById(id).orElse(null)
        }
    }

    private fun refreshResolver(lieferant: Lieferanten, isNew: Boolean) {
        try {
            lieferantEmailResolver.refresh()
            val id = lieferant.id ?: return
            val emails = ArrayList(lieferant.kundenEmails)
            val event = if (isNew) {
                EmailAddressChangedEvent.forNewEntity(EmailAddressChangedEvent.EntityType.LIEFERANT, id, emails)
            } else {
                EmailAddressChangedEvent.forAddressChange(EmailAddressChangedEvent.EntityType.LIEFERANT, id, emails, emails)
            }
            eventPublisher.publishEvent(event)
        } catch (_: Exception) {
            // Resolver refresh is best-effort; persistence already succeeded.
        }
    }

    private fun buildSpec(field: String, value: String?): Specification<Lieferanten>? {
        if (!StringUtils.hasText(value)) return null
        val likeValue = wrapLike(value).lowercase(Locale.ROOT)
        return Specification { root, _, cb -> cb.like(cb.lower(root.get(field)), likeValue) }
    }

    private fun wrapLike(value: String?): String = "%${value!!.trim()}%"

    private fun trimToNull(value: String?): String? =
        if (StringUtils.hasText(value)) value!!.trim() else null

    private fun normalizeEmails(emails: List<String>?): Set<String> {
        if (emails == null) return emptySet()
        val normalized = LinkedHashSet<String>()
        emails.filter { StringUtils.hasText(it) }
            .forEach { normalized.add(it.trim().lowercase(Locale.ROOT)) }
        return normalized
    }

    private fun toDate(date: LocalDate?): Date? =
        date?.let { Date.from(it.atStartOfDay(ZoneId.systemDefault()).toInstant()) }

    class LieferantBildDto {
        var id: Long? = null
        var originalDateiname: String? = null
        var url: String? = null
        var beschreibung: String? = null
        var erstelltAm: LocalDateTime? = null
        var mitarbeiterVorname: String? = null
        var mitarbeiterNachname: String? = null
    }

    companion object {
        private const val MAX_PAGE_SIZE = 1000
    }
}
