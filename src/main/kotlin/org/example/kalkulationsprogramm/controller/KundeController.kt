package org.example.kalkulationsprogramm.controller

import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.ListJoin
import jakarta.validation.Valid
import org.example.kalkulationsprogramm.domain.Anrede
import org.example.kalkulationsprogramm.domain.Kunde
import org.example.kalkulationsprogramm.domain.KundeNotiz
import org.example.kalkulationsprogramm.dto.Kunde.KundeCreateRequestDto
import org.example.kalkulationsprogramm.dto.Kunde.KundeDetailDto
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatResponseDto
import org.example.kalkulationsprogramm.dto.Kunde.KundeListItemDto
import org.example.kalkulationsprogramm.dto.Kunde.KundeNotizDto
import org.example.kalkulationsprogramm.dto.Kunde.KundeSearchResponseDto
import org.example.kalkulationsprogramm.dto.Kunde.KundeUpdateRequestDto
import org.example.kalkulationsprogramm.event.EmailAddressChangedEvent
import org.example.kalkulationsprogramm.mapper.KundeMapper
import org.example.kalkulationsprogramm.repository.KundeNotizRepository
import org.example.kalkulationsprogramm.repository.KundeRepository
import org.example.kalkulationsprogramm.service.KundeDuplikatService
import org.example.kalkulationsprogramm.service.KundenDetailService
import org.example.kalkulationsprogramm.service.KundennummerService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.Locale

@RestController
@RequestMapping("/api/kunden")
class KundeController(
    private val kundeRepository: KundeRepository,
    private val kundeMapper: KundeMapper,
    private val kundenDetailService: KundenDetailService,
    private val eventPublisher: ApplicationEventPublisher,
    private val kundennummerService: KundennummerService,
    private val kundeDuplikatService: KundeDuplikatService,
    private val kundeNotizRepository: KundeNotizRepository,
) {
    @GetMapping
    fun sucheKunden(
        @RequestParam(value = "q", required = false) query: String?,
        @RequestParam(value = "name", required = false) name: String?,
        @RequestParam(value = "nummer", required = false) kundennummer: String?,
        @RequestParam(value = "ort", required = false) ort: String?,
        @RequestParam(value = "email", required = false) email: String?,
        @RequestParam(value = "typ", required = false) typ: String?,
        @RequestParam(value = "page", defaultValue = "0") page: Int,
        @RequestParam(value = "size", defaultValue = "50") size: Int,
    ): KundeSearchResponseDto {
        val pageIndex = page.coerceAtLeast(0)
        val pageSize = size.coerceAtLeast(1).coerceAtMost(50)

        var specs: Specification<Kunde> = Specification.where(null)
        if (StringUtils.hasText(query)) {
            val likeValue = wrapLike(query)
            specs = specs.and { root, _, cb ->
                val term = likeValue.lowercase(Locale.GERMAN)
                cb.or(
                    cb.like(cb.lower(root.get("name")), term),
                    cb.like(cb.lower(root.get("kundennummer")), term),
                    cb.like(cb.lower(root.get("ort")), term),
                    cb.like(cb.lower(root.get("strasse")), term),
                    cb.like(cb.lower(root.get("ansprechspartner")), term),
                )
            }
        }
        specs = specs.and(buildSpec("name", name))
        specs = specs.and(buildSpec("kundennummer", kundennummer))
        specs = specs.and(buildSpec("ort", ort))
        if (StringUtils.hasText(email)) {
            val likeValue = wrapLike(email)
            specs = specs.and { root, cq, cb ->
                cq.distinct(true)
                val join: ListJoin<Kunde, String> = root.joinList("kundenEmails", JoinType.LEFT)
                cb.like(cb.lower(join), likeValue.lowercase(Locale.GERMAN))
            }
        }
        if (StringUtils.hasText(typ)) {
            if ("KUNDE".equals(typ, ignoreCase = true)) {
                specs = specs.and { root, cq, cb ->
                    cq.distinct(true)
                    cb.isNotEmpty(root.get("projekts"))
                }
            } else if ("ANFRAGER".equals(typ, ignoreCase = true)) {
                specs = specs.and { root, cq, cb ->
                    cq.distinct(true)
                    cb.isEmpty(root.get("projekts"))
                }
            }
        }

        val result = kundeRepository.findAll(
            specs,
            PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Order.asc("name").ignoreCase())),
        )

        return KundeSearchResponseDto().apply {
            kunden = result.stream().map { kundeMapper.toListItem(it)!! }.toList()
            gesamt = result.totalElements
            seite = pageIndex
            seitenGroesse = pageSize
        }
    }

    @GetMapping("/duplikat-check")
    fun duplikatCheck(
        @RequestParam(required = false) email: String?,
        @RequestParam(required = false) telefon: String?,
        @RequestParam(required = false) mobiltelefon: String?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) plz: String?,
        @RequestParam(required = false) strasse: String?,
    ): KundeDuplikatResponseDto =
        kundeDuplikatService.findeDuplikate(email, telefon, mobiltelefon, name, plz, strasse)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun createKunde(
        @Valid @RequestBody request: KundeCreateRequestDto,
        @RequestHeader(value = "X-Duplikat-Bestaetigt", required = false) duplikatBestaetigt: String?,
    ): KundeListItemDto {
        if (!"true".equals(duplikatBestaetigt, ignoreCase = true)) {
            val erstEmail = request.kundenEmails?.firstOrNull()
            val duplikate = kundeDuplikatService.findeDuplikate(
                erstEmail,
                request.telefon,
                request.mobiltelefon,
                request.name,
                request.plz,
                request.strasse,
            )
            if (!duplikate.duplikate.isNullOrEmpty()) {
                throw KundeDuplikatException(duplikate)
            }
        }

        if (!StringUtils.hasText(request.kundennummer)) {
            request.kundennummer = kundennummerService.reserviereNaechsteKundennummer()
        }

        val kundennummer = request.kundennummer!!.trim()
        if (kundeRepository.findByKundennummerIgnoreCase(kundennummer).isPresent) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Kundennummer ist bereits vergeben.")
        }

        val saved = kundeRepository.save(Kunde().apply { applyRequest(this, request) })
        if (!saved.kundenEmails.isNullOrEmpty()) {
            eventPublisher.publishEvent(
                EmailAddressChangedEvent.forNewEntity(
                    EmailAddressChangedEvent.EntityType.KUNDE,
                    saved.id!!,
                    ArrayList(saved.kundenEmails),
                ),
            )
        }
        return kundeMapper.toListItem(saved)!!
    }

    @PutMapping("/{id}")
    @Transactional
    fun updateKunde(
        @PathVariable id: Long,
        @Valid @RequestBody request: KundeUpdateRequestDto,
    ): KundeListItemDto {
        val existing = kundeRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Kunde wurde nicht gefunden.") }

        val oldEmails = existing.kundenEmails.toHashSet()

        if (StringUtils.hasText(request.kundennummer)) {
            val neueNummer = request.kundennummer!!.trim()
            kundeRepository.findByKundennummerIgnoreCase(neueNummer)
                .filter { k -> k.id != existing.id }
                .ifPresent {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "Kundennummer ist bereits vergeben.")
                }
        }

        applyRequest(existing, request)
        val updated = kundeRepository.save(existing)

        val newEmails = ArrayList<String>()
        updated.kundenEmails.forEach { email ->
            if (!oldEmails.contains(email)) {
                newEmails.add(email)
            }
        }

        if (newEmails.isNotEmpty()) {
            eventPublisher.publishEvent(
                EmailAddressChangedEvent.forAddressChange(
                    EmailAddressChangedEvent.EntityType.KUNDE,
                    updated.id!!,
                    newEmails,
                    ArrayList(updated.kundenEmails),
                ),
            )
        }
        return kundeMapper.toListItem(updated)!!
    }

    @GetMapping("/next-kundennummer")
    fun getNextKundennummer(): Map<String, String> =
        mapOf("kundennummer" to generateNextKundennummer())

    @ExceptionHandler(KundeDuplikatException::class)
    fun handleDuplikat(ex: KundeDuplikatException): ResponseEntity<KundeDuplikatResponseDto> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ex.antwort)

    @GetMapping("/{id}")
    fun getKundeDetail(@PathVariable id: Long): KundeDetailDto =
        kundenDetailService.loadDetails(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Kunde wurde nicht gefunden.") }

    private fun buildSpec(field: String, value: String?): Specification<Kunde>? {
        if (!StringUtils.hasText(value)) {
            return null
        }
        val likeValue = wrapLike(value)
        return Specification { root, _, cb ->
            cb.like(cb.lower(root.get(field)), likeValue.lowercase(Locale.GERMAN))
        }
    }

    private fun wrapLike(value: String?): String = "%${value!!.trim()}%"

    private fun generateNextKundennummer(): String =
        kundennummerService.generiereNaechsteKundennummer()

    private fun applyRequest(kunde: Kunde, request: KundeCreateRequestDto) {
        if (StringUtils.hasText(request.kundennummer)) {
            kunde.kundennummer = request.kundennummer!!.trim()
        }
        kunde.name = request.name!!.trim()
        kunde.anrede = Anrede.fromString(request.anrede)
        kunde.ansprechspartner = trimToNull(request.ansprechspartner)
        kunde.strasse = trimToNull(request.strasse)
        kunde.plz = trimToNull(request.plz)
        kunde.ort = trimToNull(request.ort)
        kunde.telefon = trimToNull(request.telefon)
        kunde.mobiltelefon = trimToNull(request.mobiltelefon)
        kunde.kundenEmails = ArrayList(normalizeEmails(request.kundenEmails))
    }

    private fun trimToNull(value: String?): String? =
        if (StringUtils.hasText(value)) value!!.trim() else null

    @PostMapping("/{id}/emails")
    @Transactional
    fun addEmail(
        @PathVariable id: Long,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<Map<String, Any>> {
        var email = body["email"]
        if (!StringUtils.hasText(email)) {
            return ResponseEntity.badRequest().body(mapOf("error" to "E-Mail-Adresse fehlt"))
        }
        email = email!!.trim().lowercase(Locale.GERMAN)
        val kunde = kundeRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Kunde nicht gefunden") }
        if (kunde.kundenEmails == null) {
            kunde.kundenEmails = ArrayList()
        }
        if (kunde.kundenEmails.contains(email)) {
            return ResponseEntity.ok(mapOf("message" to "E-Mail-Adresse bereits vorhanden", "added" to false))
        }
        kunde.kundenEmails.add(email)
        kundeRepository.save(kunde)
        eventPublisher.publishEvent(
            EmailAddressChangedEvent.forAddressChange(
                EmailAddressChangedEvent.EntityType.KUNDE,
                kunde.id!!,
                listOf(email),
                ArrayList(kunde.kundenEmails),
            ),
        )
        return ResponseEntity.ok(mapOf("message" to "E-Mail-Adresse gespeichert", "added" to true))
    }

    @GetMapping("/{id}/notizen")
    fun listNotizen(
        @PathVariable id: Long,
        @RequestParam(value = "q", required = false) query: String?,
    ): ResponseEntity<List<KundeNotizDto>> {
        if (!kundeRepository.existsById(id)) {
            return ResponseEntity.notFound().build()
        }
        val notizen = if (StringUtils.hasText(query)) {
            kundeNotizRepository.findByKundeIdAndTextContainingIgnoreCaseOrderByErstelltAmDesc(id, query)
        } else {
            kundeNotizRepository.findByKundeIdOrderByErstelltAmDesc(id)
        }
        return ResponseEntity.ok(notizen.map { toNotizDto(it) })
    }

    @PostMapping("/{id}/notizen")
    @Transactional
    fun createNotiz(
        @PathVariable id: Long,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<KundeNotizDto> {
        val kunde = kundeRepository.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        val text = body["text"]
        if (!StringUtils.hasText(text)) {
            return ResponseEntity.badRequest().build()
        }
        val notiz = kundeNotizRepository.save(
            KundeNotiz().apply {
                this.kunde = kunde
                this.text = text!!.trim()
            },
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(toNotizDto(notiz))
    }

    @DeleteMapping("/{id}/notizen/{notizId}")
    @Transactional
    fun deleteNotiz(
        @PathVariable id: Long,
        @PathVariable notizId: Long,
    ): ResponseEntity<Void> {
        if (!kundeRepository.existsById(id)) {
            return ResponseEntity.notFound().build()
        }
        val notiz = kundeNotizRepository.findById(notizId).orElse(null)
        if (notiz?.kunde?.id != id) {
            return ResponseEntity.notFound().build()
        }
        kundeNotizRepository.delete(notiz)
        return ResponseEntity.noContent().build()
    }

    private fun toNotizDto(notiz: KundeNotiz): KundeNotizDto =
        KundeNotizDto(
            id = notiz.id,
            text = notiz.text,
            erstelltAm = notiz.erstelltAm,
        )

    private fun normalizeEmails(emails: List<String>?): Set<String> {
        if (emails == null) {
            return emptySet()
        }
        val normalized = LinkedHashSet<String>()
        for (email in emails) {
            if (!StringUtils.hasText(email)) {
                continue
            }
            normalized.add(email.trim().lowercase(Locale.GERMAN))
        }
        return normalized
    }
}
