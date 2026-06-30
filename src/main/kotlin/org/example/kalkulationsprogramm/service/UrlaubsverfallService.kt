package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit
import java.util.Optional

@Service
class UrlaubsverfallService(
    private val mitarbeiterRepository: MitarbeiterRepository,
) {
    @Transactional(readOnly = true)
    fun pruefeVerfallWarnung(mitarbeiterId: Long?): Optional<Map<String, Any>> {
        val heute = LocalDate.now()
        if (!isImWarnzeitraum(heute)) {
            return Optional.empty()
        }

        val mitarbeiter = if (mitarbeiterId == null) null else mitarbeiterRepository.findById(mitarbeiterId).orElse(null)
        if (mitarbeiter == null) {
            return Optional.empty()
        }

        val resturlaub = mitarbeiter.resturlaubVorjahr
        if (resturlaub == null || resturlaub <= 0) {
            return Optional.empty()
        }

        val verfallsDatum = berechneVerfallsDatum(heute)
        val tageVerbleibend = ChronoUnit.DAYS.between(heute, verfallsDatum)

        val warnung = LinkedHashMap<String, Any>()
        warnung["resturlaubTage"] = resturlaub
        warnung["verfallsDatum"] = verfallsDatum.toString()
        warnung["verfallsJahr"] = verfallsDatum.year
        warnung["tageVerbleibend"] = tageVerbleibend
        warnung["dringend"] = tageVerbleibend <= 30

        return Optional.of(warnung)
    }

    @Transactional(readOnly = true)
    fun pruefeVerfallWarnungByToken(token: String?): Optional<Map<String, Any>> =
        if (token == null) {
            Optional.empty()
        } else {
            mitarbeiterRepository.findByLoginTokenAndAktivTrue(token)
                .flatMap { pruefeVerfallWarnung(it.id) }
        }

    private fun isImWarnzeitraum(datum: LocalDate): Boolean {
        val monat = datum.month
        return monat == Month.DECEMBER || monat == Month.JANUARY
    }

    private fun berechneVerfallsDatum(datum: LocalDate): LocalDate {
        val jahr = datum.year
        return if (datum.month == Month.DECEMBER) {
            LocalDate.of(jahr + 1, 2, 1)
        } else {
            LocalDate.of(jahr, 2, 1)
        }
    }

    @Transactional
    fun fuehreJaehrlichenUebertragDurch(mitarbeiterId: Long?, verbleibendeTage: Int) {
        val mitarbeiter = if (mitarbeiterId == null) {
            null
        } else {
            mitarbeiterRepository.findById(mitarbeiterId).orElse(null)
        } ?: throw IllegalArgumentException("Mitarbeiter nicht gefunden")

        mitarbeiter.resturlaubVorjahr = verbleibendeTage
        mitarbeiterRepository.save(mitarbeiter)
    }

    @Transactional
    fun loescheVerfallenenResturlaub(mitarbeiterId: Long?) {
        val mitarbeiter = if (mitarbeiterId == null) {
            null
        } else {
            mitarbeiterRepository.findById(mitarbeiterId).orElse(null)
        } ?: throw IllegalArgumentException("Mitarbeiter nicht gefunden")

        mitarbeiter.resturlaubVorjahr = 0
        mitarbeiterRepository.save(mitarbeiter)
    }
}
