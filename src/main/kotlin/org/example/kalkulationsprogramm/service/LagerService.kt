package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.Lagerbestand
import org.example.kalkulationsprogramm.domain.Lagerbewegung
import org.example.kalkulationsprogramm.domain.LagerbewegungTyp
import org.example.kalkulationsprogramm.domain.Lagerort
import org.example.kalkulationsprogramm.dto.Lager.LagerbestandDto
import org.example.kalkulationsprogramm.dto.Lager.LagerbewegungDto
import org.example.kalkulationsprogramm.dto.Lager.LagerbewegungRequest
import org.example.kalkulationsprogramm.dto.Lager.LagerortDto
import org.example.kalkulationsprogramm.dto.Lager.LagerortRequest
import org.example.kalkulationsprogramm.repository.ArtikelRepository
import org.example.kalkulationsprogramm.repository.LagerbestandRepository
import org.example.kalkulationsprogramm.repository.LagerbewegungRepository
import org.example.kalkulationsprogramm.repository.LagerortRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class LagerService(
    private val artikelRepository: ArtikelRepository,
    private val lagerortRepository: LagerortRepository,
    private val lagerbestandRepository: LagerbestandRepository,
    private val lagerbewegungRepository: LagerbewegungRepository,
) {
    @Transactional(readOnly = true)
    fun sucheBestand(query: String?): List<LagerbestandDto> =
        lagerbestandRepository.suche(query?.trim().orEmpty()).map { it.toDto() }

    @Transactional(readOnly = true)
    fun lagerorte(): List<LagerortDto> =
        lagerortRepository.findByAktivTrueOrderByCodeAsc().map { it.toDto() }

    @Transactional
    fun lagerortAnlegen(request: LagerortRequest): LagerortDto {
        val code = request.code?.trim()?.uppercase()
            ?: throw IllegalArgumentException("Lagerort-Code fehlt.")
        val name = request.name?.trim().takeUnless { it.isNullOrEmpty() } ?: code
        val lagerort = lagerortRepository.findByCodeIgnoreCase(code) ?: Lagerort().apply { this.code = code }
        lagerort.name = name
        lagerort.regal = request.regal?.trim().takeUnless { it.isNullOrEmpty() }
        lagerort.fach = request.fach?.trim().takeUnless { it.isNullOrEmpty() }
        lagerort.aktiv = true
        return lagerortRepository.save(lagerort).toDto()
    }

    @Transactional(readOnly = true)
    fun bewegungen(): List<LagerbewegungDto> =
        lagerbewegungRepository.findeNeueste().take(100).map { it.toDto() }

    @Transactional
    fun buche(request: LagerbewegungRequest): LagerbewegungDto {
        val typ = request.typ ?: throw IllegalArgumentException("Bewegungstyp fehlt.")
        val menge = (request.menge ?: throw IllegalArgumentException("Menge fehlt.")).stripTrailingZeros()
        require(menge > BigDecimal.ZERO) { "Menge muss groesser als 0 sein." }

        val artikel = artikelRepository.findById(request.artikelId ?: throw IllegalArgumentException("Artikel fehlt."))
            .orElseThrow { IllegalArgumentException("Artikel wurde nicht gefunden.") }

        val bewegung = when (typ) {
            LagerbewegungTyp.EINGANG -> bucheEingang(artikel, request, menge)
            LagerbewegungTyp.AUSGANG -> bucheAusgang(artikel, request, menge)
            LagerbewegungTyp.UMLAGERUNG -> bucheUmlagerung(artikel, request, menge)
            LagerbewegungTyp.KORREKTUR -> bucheKorrektur(artikel, request, menge)
        }
        bewegung.grund = request.grund?.trim().takeUnless { it.isNullOrEmpty() }
        bewegung.referenz = request.referenz?.trim().takeUnless { it.isNullOrEmpty() }
        bewegung.verantwortlicher = request.verantwortlicher?.trim().takeUnless { it.isNullOrEmpty() }
        return lagerbewegungRepository.save(bewegung).toDto()
    }

    private fun bucheEingang(artikel: Artikel, request: LagerbewegungRequest, menge: BigDecimal): Lagerbewegung {
        val lagerort = ladeLagerort(request.lagerortId ?: request.nachLagerortId)
        val bestand = findeOderErstelleBestand(artikel, lagerort)
        bestand.menge = bestand.menge + menge
        request.mindestbestand?.let { bestand.mindestbestand = it.coerceAtLeast(BigDecimal.ZERO) }
        bestand.aktualisiertAm = LocalDateTime.now()
        lagerbestandRepository.save(bestand)
        return bewegung(artikel, LagerbewegungTyp.EINGANG, null, lagerort, menge)
    }

    private fun bucheAusgang(artikel: Artikel, request: LagerbewegungRequest, menge: BigDecimal): Lagerbewegung {
        val lagerort = ladeLagerort(request.lagerortId ?: request.vonLagerortId)
        val bestand = lagerbestandRepository.findByArtikelAndLagerort(artikel, lagerort)
            ?: throw IllegalArgumentException("Kein Lagerbestand fuer diesen Artikel am Lagerort.")
        require(bestand.menge >= menge) { "Nicht genug Bestand am Lagerort." }
        bestand.menge = bestand.menge - menge
        bestand.aktualisiertAm = LocalDateTime.now()
        lagerbestandRepository.save(bestand)
        return bewegung(artikel, LagerbewegungTyp.AUSGANG, lagerort, null, menge)
    }

    private fun bucheUmlagerung(artikel: Artikel, request: LagerbewegungRequest, menge: BigDecimal): Lagerbewegung {
        val von = ladeLagerort(request.vonLagerortId)
        val nach = ladeLagerort(request.nachLagerortId)
        require(von.id != nach.id) { "Quell- und Ziel-Lagerort duerfen nicht gleich sein." }
        val quelle = lagerbestandRepository.findByArtikelAndLagerort(artikel, von)
            ?: throw IllegalArgumentException("Kein Lagerbestand am Quell-Lagerort.")
        require(quelle.menge >= menge) { "Nicht genug Bestand am Quell-Lagerort." }
        val ziel = findeOderErstelleBestand(artikel, nach)
        quelle.menge = quelle.menge - menge
        ziel.menge = ziel.menge + menge
        val now = LocalDateTime.now()
        quelle.aktualisiertAm = now
        ziel.aktualisiertAm = now
        lagerbestandRepository.save(quelle)
        lagerbestandRepository.save(ziel)
        return bewegung(artikel, LagerbewegungTyp.UMLAGERUNG, von, nach, menge)
    }

    private fun bucheKorrektur(artikel: Artikel, request: LagerbewegungRequest, menge: BigDecimal): Lagerbewegung {
        val lagerort = ladeLagerort(request.lagerortId ?: request.nachLagerortId)
        val bestand = findeOderErstelleBestand(artikel, lagerort)
        bestand.menge = menge
        request.mindestbestand?.let { bestand.mindestbestand = it.coerceAtLeast(BigDecimal.ZERO) }
        bestand.aktualisiertAm = LocalDateTime.now()
        lagerbestandRepository.save(bestand)
        return bewegung(artikel, LagerbewegungTyp.KORREKTUR, null, lagerort, menge)
    }

    private fun ladeLagerort(id: Long?): Lagerort =
        lagerortRepository.findById(id ?: throw IllegalArgumentException("Lagerort fehlt."))
            .orElseThrow { IllegalArgumentException("Lagerort wurde nicht gefunden.") }

    private fun findeOderErstelleBestand(artikel: Artikel, lagerort: Lagerort): Lagerbestand =
        lagerbestandRepository.findByArtikelAndLagerort(artikel, lagerort) ?: Lagerbestand().apply {
            this.artikel = artikel
            this.lagerort = lagerort
        }

    private fun bewegung(
        artikel: Artikel,
        typ: LagerbewegungTyp,
        von: Lagerort?,
        nach: Lagerort?,
        menge: BigDecimal,
    ) = Lagerbewegung().apply {
        this.artikel = artikel
        this.typ = typ
        this.vonLagerort = von
        this.nachLagerort = nach
        this.menge = menge
        this.erstelltAm = LocalDateTime.now()
    }

    private fun Lagerbestand.toDto() = LagerbestandDto(
        id = id,
        artikelId = artikel?.id,
        produktname = artikel?.produktname,
        produktlinie = artikel?.produktlinie,
        produkttext = artikel?.produkttext,
        externeArtikelnummer = artikel?.getExterneArtikelnummer(),
        lagerort = lagerort?.toDto(),
        menge = menge,
        mindestbestand = mindestbestand,
        unterMindestbestand = menge < mindestbestand,
        charge = charge,
        bemerkung = bemerkung,
        aktualisiertAm = aktualisiertAm,
    )

    private fun Lagerbewegung.toDto() = LagerbewegungDto(
        id = id,
        typ = typ,
        artikelId = artikel?.id,
        produktname = artikel?.produktname,
        vonLagerort = vonLagerort?.toDto(),
        nachLagerort = nachLagerort?.toDto(),
        menge = menge,
        grund = grund,
        referenz = referenz,
        verantwortlicher = verantwortlicher,
        erstelltAm = erstelltAm,
    )

    private fun Lagerort.toDto() = LagerortDto(
        id = id,
        code = code,
        name = name,
        regal = regal,
        fach = fach,
    )
}
