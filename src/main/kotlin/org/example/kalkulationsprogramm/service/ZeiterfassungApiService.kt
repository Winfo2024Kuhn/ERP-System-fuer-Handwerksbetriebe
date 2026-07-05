package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangResponseDto
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

@Service
class ZeiterfassungApiService {
    fun getOpenProjekte(limit: Int?, search: String?): List<Map<String, Any>> = emptyList()

    fun getKategorienMitPfad(): List<Map<String, Any>> = emptyList()

    fun getKategorienByProjektId(projektId: Long): List<Map<String, Any>> = emptyList()

    fun getArbeitsgaengeByMitarbeiterToken(token: String): Optional<List<ArbeitsgangResponseDto>> =
        Optional.of(emptyList())

    fun getLieferanten(limit: Int?, search: String?): List<Map<String, Any>> = emptyList()

    fun startZeiterfassung(
        token: String,
        projektId: Long,
        arbeitsgangId: Long,
        produktkategorieId: Long?,
        originalStartZeit: LocalDateTime?,
        idempotencyKey: String?,
    ): Map<String, Any> = mapOf("started" to false)

    fun stopZeiterfassung(token: String, originalEndeZeit: LocalDateTime?, idempotencyKey: String?): Map<String, Any> =
        mapOf("stopped" to false)

    fun startPause(token: String, originalZeit: LocalDateTime?, idempotencyKey: String?): Map<String, Any> =
        mapOf("paused" to false)

    fun getAktiveBuchung(token: String): Optional<Map<String, Any>> = Optional.empty()

    fun getHeuteGearbeitet(token: String): Map<String, Any> = mapOf("minuten" to 0)

    fun getBuchungenByDatum(token: String, datum: LocalDate): List<Map<String, Any>> = emptyList()

    fun getProjektBilder(projektId: Long): List<Map<String, Any>> = emptyList()

    fun getFeiertage(jahr: Int): List<Map<String, Any>> = emptyList()

    fun getSaldo(token: String, jahr: Int?, monat: Int?, gesamtBisHeute: Boolean?): Map<String, Any> =
        mapOf("saldoMinuten" to 0)

    fun getUrlaubsverfallWarnung(token: String): Map<String, Any> = emptyMap()

    fun getBuchungszeitfenster(token: String): Map<String, Any> = emptyMap()
}
