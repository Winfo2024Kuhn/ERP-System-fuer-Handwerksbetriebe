package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.KasseEinstellung
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.repository.KasseEinstellungRepository
import org.example.kalkulationsprogramm.repository.SachkontoRepository
import org.example.kalkulationsprogramm.service.BelegService
import org.example.kalkulationsprogramm.service.EhegattengehaltSchedulerService
import org.example.kalkulationsprogramm.service.KasseSaldoService
import org.example.kalkulationsprogramm.service.KasseShortcutService
import org.example.kalkulationsprogramm.service.KasseUnterdeckungException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

@RestController
@RequestMapping("/api/buchhaltung/kasse")
class KasseShortcutController(
    private val belegService: BelegService,
    private val kasseShortcutService: KasseShortcutService,
    private val kasseSaldoService: KasseSaldoService,
    private val kasseEinstellungRepository: KasseEinstellungRepository,
    private val sachkontoRepository: SachkontoRepository,
) {
    @PostMapping("/bank-abhebung")
    fun bankAbhebung(
        @RequestBody req: BankAbhebungRequest,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = findScanner(token, auth) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        return try {
            val beleg = kasseShortcutService.bankAbhebung(
                req.betrag,
                req.datum,
                req.belegNr,
                req.beschreibung,
                caller,
            )
            ResponseEntity.ok(belegService.toDto(beleg))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        }
    }

    @PostMapping("/privateinlage")
    fun privateinlage(
        @RequestBody req: EinfacheKasseRequest,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = findScanner(token, auth) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        return try {
            val beleg = kasseShortcutService.privatEinlage(req.betrag, req.datum, req.beschreibung, caller)
            ResponseEntity.ok(belegService.toDto(beleg))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        }
    }

    @PostMapping("/privatentnahme")
    fun privatentnahme(
        @RequestBody req: EinfacheKasseRequest,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = findScanner(token, auth) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        return try {
            val beleg = kasseShortcutService.privatEntnahme(req.betrag, req.datum, req.beschreibung, caller)
            ResponseEntity.ok(belegService.toDto(beleg))
        } catch (ex: KasseUnterdeckungException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(saldoFehler(ex))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        }
    }

    @PostMapping("/lohn-zahlung")
    fun lohnZahlung(
        @RequestBody req: LohnZahlungRequest,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = findScanner(token, auth) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        val sachkonto = sachkontoRepository.findByNummer(EhegattengehaltSchedulerService.LOHN_SACHKONTO_NUMMER)
            .orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                mapOf(
                    "message" to "Lohn-Sachkonto ${EhegattengehaltSchedulerService.LOHN_SACHKONTO_NUMMER} (Loehne & Gehaelter) fehlt — bitte in Sachkonten anlegen",
                ),
            )
        return try {
            val result = kasseShortcutService.lohnZahlung(
                req.betrag,
                req.datum,
                req.empfaengerName,
                sachkonto,
                null,
                caller,
            )
            ResponseEntity.ok(
                mapOf(
                    "privateinlage" to result.privateinlage()?.let { belegService.toDto(it) },
                    "lohnBeleg" to result.lohnBeleg()?.let { belegService.toDto(it) },
                    "neuerSaldo" to result.neuerSaldo(),
                ),
            )
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        }
    }

    @GetMapping("/saldo")
    fun getSaldo(
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = findViewer(token, auth) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        return ResponseEntity.ok(
            mapOf(
                "saldo" to kasseSaldoService.berechneAktuellenSaldo(),
                "mindestbestand" to kasseSaldoService.getMindestbestand(),
            ),
        )
    }

    @GetMapping("/einstellung")
    fun getEinstellung(
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = findViewer(token, auth) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        return ResponseEntity.ok(
            kasseEinstellungRepository.findSingleton()
                .map(::toEinstellungDto)
                .orElseGet { EinstellungResponse(null, BigDecimal.ZERO, false, null, null, null, null) },
        )
    }

    @PutMapping("/einstellung")
    fun updateEinstellung(
        @RequestBody req: EinstellungRequest,
        @RequestParam(value = "token", required = false) token: String?,
        auth: Authentication?,
    ): ResponseEntity<*> {
        val caller = findScanner(token, auth) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        return try {
            val einstellung = kasseEinstellungRepository.findSingleton()
                .orElseThrow {
                    IllegalStateException("Kasse-Einstellung-Singleton fehlt — Migration V319 nicht ausgefuehrt?")
                }
            req.mindestbestand?.let { einstellung.mindestbestand = it }
            einstellung.ehegattengehaltAktiv = req.ehegattengehaltAktiv == true
            einstellung.ehegattengehaltBetrag = req.ehegattengehaltBetrag
            einstellung.ehegattengehaltTag = req.ehegattengehaltTag
            einstellung.ehegattengehaltEmpfaengerName = req.ehegattengehaltEmpfaengerName
            einstellung.privateinlageSachkonto = req.privateinlageSachkontoId?.let {
                sachkontoRepository.findById(it).orElse(null)
            }
            validateEhegattengehaltKonfig(einstellung)
            val gespeichert = kasseEinstellungRepository.save(einstellung)
            ResponseEntity.ok(toEinstellungDto(gespeichert))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        }
    }

    private fun findScanner(token: String?, auth: Authentication?): Mitarbeiter? =
        belegService.findCaller(token, auth)?.takeIf(belegService::darfScannen)

    private fun findViewer(token: String?, auth: Authentication?): Mitarbeiter? =
        belegService.findCaller(token, auth)?.takeIf(belegService::darfSehen)

    private fun validateEhegattengehaltKonfig(einstellung: KasseEinstellung) {
        if (!einstellung.isEhegattengehaltAktiv()) {
            return
        }
        val betrag = einstellung.ehegattengehaltBetrag
        if (betrag == null || betrag.signum() <= 0) {
            throw IllegalArgumentException("Ehegattengehalt: Betrag muss positiv sein")
        }
        val tag = einstellung.ehegattengehaltTag
        if (tag == null || tag < 1 || tag > 28) {
            throw IllegalArgumentException("Ehegattengehalt: Tag muss zwischen 1 und 28 liegen")
        }
        if (einstellung.privateinlageSachkonto == null) {
            throw IllegalArgumentException("Ehegattengehalt: Privateinlage-Sachkonto fehlt (fuer Auto-Auffuellen)")
        }
        if (sachkontoRepository.findByNummer(EhegattengehaltSchedulerService.LOHN_SACHKONTO_NUMMER).isEmpty) {
            throw IllegalArgumentException(
                "Ehegattengehalt: Sachkonto ${EhegattengehaltSchedulerService.LOHN_SACHKONTO_NUMMER} (Loehne & Gehaelter) fehlt in den Sachkonten",
            )
        }
    }

    private fun saldoFehler(ex: KasseUnterdeckungException): Map<String, Any?> =
        mapOf(
            "message" to ex.message,
            "projizierterSaldo" to ex.projizierterSaldo,
            "mindestbestand" to ex.mindestbestand,
        )

    private fun toEinstellungDto(einstellung: KasseEinstellung): EinstellungResponse =
        EinstellungResponse(
            einstellung.id,
            einstellung.mindestbestand,
            einstellung.isEhegattengehaltAktiv(),
            einstellung.ehegattengehaltBetrag,
            einstellung.ehegattengehaltTag,
            einstellung.ehegattengehaltEmpfaengerName,
            einstellung.privateinlageSachkonto?.id,
        )

    data class BankAbhebungRequest(
        val betrag: BigDecimal? = null,
        val datum: LocalDate? = null,
        val belegNr: String? = null,
        val beschreibung: String? = null,
    )

    data class EinfacheKasseRequest(
        val betrag: BigDecimal? = null,
        val datum: LocalDate? = null,
        val beschreibung: String? = null,
    )

    data class LohnZahlungRequest(
        val betrag: BigDecimal? = null,
        val datum: LocalDate? = null,
        val empfaengerName: String? = null,
    )

    data class EinstellungRequest(
        val mindestbestand: BigDecimal? = null,
        val ehegattengehaltAktiv: Boolean? = null,
        val ehegattengehaltBetrag: BigDecimal? = null,
        val ehegattengehaltTag: Int? = null,
        val ehegattengehaltEmpfaengerName: String? = null,
        val privateinlageSachkontoId: Long? = null,
    )

    data class EinstellungResponse(
        val id: Long?,
        val mindestbestand: BigDecimal?,
        val ehegattengehaltAktiv: Boolean,
        val ehegattengehaltBetrag: BigDecimal?,
        val ehegattengehaltTag: Int?,
        val ehegattengehaltEmpfaengerName: String?,
        val privateinlageSachkontoId: Long?,
    )
}
