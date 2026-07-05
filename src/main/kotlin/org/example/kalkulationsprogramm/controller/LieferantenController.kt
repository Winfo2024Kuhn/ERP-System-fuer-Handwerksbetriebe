package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.dto.Lieferant.LieferantCreateRequestDto
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantListItemDto
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantSearchResponseDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/lieferanten")
class LieferantenController {
    @GetMapping
    fun sucheLieferanten(
        @RequestParam(value = "q", required = false) query: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): LieferantSearchResponseDto = LieferantSearchResponseDto(emptyList(), 0, 0, 0)

    @PostMapping
    fun createLieferant(@RequestBody request: LieferantCreateRequestDto): LieferantListItemDto =
        LieferantListItemDto()

    @PutMapping("/{id}")
    fun updateLieferant(@PathVariable id: Long, @RequestBody request: LieferantCreateRequestDto): ResponseEntity<LieferantDetailDto> =
        ResponseEntity.notFound().build()

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<LieferantDetailDto> = ResponseEntity.notFound().build()

    @GetMapping("/emails")
    fun getAllEmails(): List<String> = emptyList()

    class LieferantBildDto {
        var id: Long? = null
        var originalDateiname: String? = null
        var url: String? = null
        var beschreibung: String? = null
        var erstelltAm: LocalDateTime? = null
        var mitarbeiterVorname: String? = null
        var mitarbeiterNachname: String? = null
    }
}
