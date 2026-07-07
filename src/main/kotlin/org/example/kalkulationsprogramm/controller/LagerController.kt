package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.dto.Lager.LagerbestandDto
import org.example.kalkulationsprogramm.dto.Lager.LagerbewegungDto
import org.example.kalkulationsprogramm.dto.Lager.LagerbewegungRequest
import org.example.kalkulationsprogramm.dto.Lager.LagerortDto
import org.example.kalkulationsprogramm.dto.Lager.LagerortRequest
import org.example.kalkulationsprogramm.service.LagerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/lager")
class LagerController(private val lagerService: LagerService) {
    @GetMapping("/bestand")
    fun bestand(@RequestParam(value = "q", required = false) query: String?): List<LagerbestandDto> =
        lagerService.sucheBestand(query)

    @GetMapping("/orte")
    fun lagerorte(): List<LagerortDto> = lagerService.lagerorte()

    @PostMapping("/orte")
    fun lagerortAnlegen(@RequestBody request: LagerortRequest): ResponseEntity<LagerortDto> =
        ResponseEntity.ok(lagerService.lagerortAnlegen(request))

    @GetMapping("/bewegungen")
    fun bewegungen(): List<LagerbewegungDto> = lagerService.bewegungen()

    @PostMapping("/bewegungen")
    fun buchen(@RequestBody request: LagerbewegungRequest): ResponseEntity<LagerbewegungDto> =
        ResponseEntity.ok(lagerService.buche(request))
}
