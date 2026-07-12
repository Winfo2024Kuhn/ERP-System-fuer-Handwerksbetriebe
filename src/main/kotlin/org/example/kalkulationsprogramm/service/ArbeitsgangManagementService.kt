package org.example.kalkulationsprogramm.service

import jakarta.transaction.Transactional
import org.example.kalkulationsprogramm.domain.Arbeitsgang
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangErstellenDto
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangStundensatzDto
import org.example.kalkulationsprogramm.repository.AbteilungRepository
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class ArbeitsgangManagementService(
    private val arbeitsgangRepository: ArbeitsgangRepository,
    private val zeitbuchungRepository: ZeitbuchungRepository,
    private val stundensatzRepository: ArbeitsgangStundensatzRepository,
    private val abteilungRepository: AbteilungRepository,
) {
    @Transactional
    fun erstelleArbeitsgang(dto: ArbeitsgangErstellenDto): Arbeitsgang {
        val abteilungId = dto.abteilungId ?: throw IllegalArgumentException("Abteilung muss angegeben werden.")
        val abteilung = abteilungRepository.findById(abteilungId)
            .orElseThrow { RuntimeException("Abteilung nicht gefunden: $abteilungId") }

        val neuerArbeitsgang = Arbeitsgang()
        neuerArbeitsgang.beschreibung = dto.beschreibung
        neuerArbeitsgang.abteilung = abteilung
        return arbeitsgangRepository.save(neuerArbeitsgang)
    }

    @Transactional
    fun loescheArbeitsgang(arbeitsgangID: Long) {
        val arbeitsgang = arbeitsgangRepository.findById(arbeitsgangID)
            .orElseThrow {
                RuntimeException("Dieser Arbeitsgang konnte nicht gefunden werden! ID: $arbeitsgangID")
            }
        val referenzen = zeitbuchungRepository.countByArbeitsgangId(arbeitsgangID)
        if (referenzen > 0) {
            throw IllegalStateException("Arbeitsgang kann nicht geloescht werden, da er referenziert wird.")
        }
        arbeitsgangRepository.delete(arbeitsgang)
    }

    fun findeAlle(): List<Arbeitsgang> =
        arbeitsgangRepository.findAll()

    @Transactional
    fun aktualisiereStundensaetze(dtos: List<ArbeitsgangStundensatzDto>) {
        val jahr = LocalDate.now().year
        for (dto in dtos) {
            aktualisiereEinzelnenStundensatz(dto.arbeitsgangId, dto.stundensatz, jahr)
        }
    }

    @Transactional
    fun aktualisiereEinzelnenStundensatz(arbeitsgangId: Long, neuerSatz: BigDecimal) {
        val jahr = LocalDate.now().year
        aktualisiereEinzelnenStundensatz(arbeitsgangId, neuerSatz, jahr)
    }

    private fun aktualisiereEinzelnenStundensatz(
        arbeitsgangId: Long?,
        neuerSatz: BigDecimal?,
        jahr: Int,
    ) {
        val arbeitsgang = arbeitsgangRepository.findById(requireNotNull(arbeitsgangId))
            .orElseThrow { RuntimeException("Arbeitsgang nicht gefunden") }

        val satz = stundensatzRepository
            .findTopByArbeitsgangIdAndJahrOrderByIdDesc(arbeitsgang.id, jahr)
            .orElseGet {
                ArbeitsgangStundensatz().apply {
                    this.arbeitsgang = arbeitsgang
                    this.jahr = jahr
                }
            }

        satz.satz = neuerSatz
        stundensatzRepository.save(satz)
    }
}
