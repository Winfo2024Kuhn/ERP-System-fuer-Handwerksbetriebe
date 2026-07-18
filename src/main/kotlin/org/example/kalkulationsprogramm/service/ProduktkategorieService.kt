package org.example.kalkulationsprogramm.service

import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.example.kalkulationsprogramm.domain.Produktkategorie
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit
import org.example.kalkulationsprogramm.dto.Produktkategroie.ArbeitsgangAnalyseDto
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieAnalyseDto
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieErstellenDto
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProjektAnalyseDto
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProjektArbeitsgangAnalyseDto
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.sqrt

@Service
class ProduktkategorieService(
    private val produktkategorieRepository: ProduktkategorieRepository,
    private val projektRepository: ProjektRepository,
    private val produktkategorieMapper: ProduktkategorieMapper,
    private val dateiSpeicherService: DateiSpeicherService,
) {
    fun findeHauptkategorien(light: Boolean): List<ProduktkategorieResponseDto> {
        val counts = if (light) emptyMap() else berechneProjektAnzahlen()
        return produktkategorieRepository.findByUebergeordneteKategorieIsNull()
            .map { kat ->
                produktkategorieMapper.toProduktkategorieResponseDto(kat)!!.also { dto ->
                    if (!light) dto.projektAnzahl = counts.getOrDefault(kat.id, 0L)
                }
            }
    }

    fun findeAlleKategorien(light: Boolean): List<ProduktkategorieResponseDto> {
        val counts = if (light) emptyMap() else berechneProjektAnzahlen()
        val parentIds = HashSet(produktkategorieRepository.findAllParentIds())
        return produktkategorieRepository.findAllWithParent()
            .map { kat ->
                produktkategorieMapper.toProduktkategorieResponseDtoWithLeaf(kat, !parentIds.contains(kat.id))!!.also { dto ->
                    if (!light) dto.projektAnzahl = counts.getOrDefault(kat.id, 0L)
                }
            }
    }

    fun findeUnterkategorie(parentId: Long, light: Boolean): List<ProduktkategorieResponseDto> {
        val counts = if (light) emptyMap() else berechneProjektAnzahlen()
        return produktkategorieRepository.findByUebergeordneteKategorieId(parentId)
            .map { kat ->
                produktkategorieMapper.toProduktkategorieResponseDto(kat)!!.also { dto ->
                    if (!light) dto.projektAnzahl = counts.getOrDefault(kat.id, 0L)
                }
            }
    }

    fun findeKategorieById(id: Long): ProduktkategorieResponseDto {
        val counts = berechneProjektAnzahlen()
        return produktkategorieRepository.findById(id)
            .map { kat ->
                produktkategorieMapper.toProduktkategorieResponseDto(kat)!!.also { dto ->
                    dto.projektAnzahl = counts.getOrDefault(kat.id, 0L)
                }
            }
            .orElseThrow { RuntimeException("Kategorie mit ID $id nicht gefunden.") }
    }

    fun sucheLeafKategorien(suchbegriff: String?): List<ProduktkategorieResponseDto> {
        if (suchbegriff.isNullOrBlank()) {
            return emptyList()
        }
        return produktkategorieRepository.sucheLeafKategorienNachBezeichnung(suchbegriff.trim())
            .map { produktkategorieMapper.toProduktkategorieResponseDto(it)!! }
    }

    @Transactional
    fun erstelleKategorie(
        dto: ProduktkategorieErstellenDto,
        bild: MultipartFile?,
    ): ProduktkategorieResponseDto {
        val neueKategorie = Produktkategorie().apply {
            bezeichnung = dto.bezeichnung
            verrechnungseinheit = dto.verrechnungseinheit
            beschreibung = dto.beschreibung
        }

        val parentId = dto.parentId
        if (parentId != null) {
            val parent = produktkategorieRepository.findById(parentId)
                .orElseThrow { RuntimeException("Eltern-Kategorie nicht gefunden") }
            neueKategorie.uebergeordneteKategorie = parent
        }

        if (bild != null && !bild.isEmpty) {
            neueKategorie.bildUrl = dateiSpeicherService.speichereBild(bild)
        }

        val gespeicherteKategorie = produktkategorieRepository.save(neueKategorie)
        return produktkategorieMapper.toProduktkategorieResponseDto(gespeicherteKategorie)!!
    }

    @Transactional
    fun aktualisiereBeschreibung(kategorieId: Long, beschreibung: String?): ProduktkategorieResponseDto {
        val kategorie = produktkategorieRepository.findById(kategorieId)
            .orElseThrow { RuntimeException("Kategorie mit ID $kategorieId nicht gefunden.") }
        kategorie.beschreibung = beschreibung
        val gespeichert = produktkategorieRepository.save(kategorie)
        return produktkategorieMapper.toProduktkategorieResponseDto(gespeichert)!!
    }

    @Transactional
    fun aktualisiereKategorie(
        id: Long,
        dto: ProduktkategorieErstellenDto,
        bild: MultipartFile?,
    ): ProduktkategorieResponseDto {
        val kategorie = produktkategorieRepository.findById(id)
            .orElseThrow { RuntimeException("Kategorie mit ID $id nicht gefunden.") }

        kategorie.bezeichnung = dto.bezeichnung
        kategorie.verrechnungseinheit = dto.verrechnungseinheit
        kategorie.beschreibung = dto.beschreibung

        if (bild != null && !bild.isEmpty) {
            if (!kategorie.bildUrl.isNullOrEmpty()) {
                dateiSpeicherService.loescheBild(kategorie.bildUrl)
            }
            kategorie.bildUrl = dateiSpeicherService.speichereBild(bild)
        }

        val gespeichert = produktkategorieRepository.save(kategorie)
        return produktkategorieMapper.toProduktkategorieResponseDto(gespeichert)!!
    }

    @Transactional
    fun loescheKategorie(kategorieId: Long) {
        val kategorie = produktkategorieRepository.findById(kategorieId)
            .orElseThrow { RuntimeException("Kategorie mit ID $kategorieId nicht gefunden.") }
        if (kategorie.unterkategorien.isNotEmpty()) {
            throw IllegalStateException("Kategorie kann nicht gelöscht werden, da sie noch Unterkategorien enthält.")
        }
        val projektAnzahl = projektRepository.countByProduktkategorieId(kategorieId)
        if (projektAnzahl > 0) {
            throw IllegalStateException(
                "Kategorie kann nicht gelöscht werden, da ihr noch $projektAnzahl Projekte zugeordnet sind.",
            )
        }
        if (!kategorie.bildUrl.isNullOrEmpty()) {
            dateiSpeicherService.loescheBild(kategorie.bildUrl)
        }
        produktkategorieRepository.delete(kategorie)
    }

    private fun sammleUnterkategorien(
        kategorie: Produktkategorie,
        gesammelteKategorien: MutableList<Produktkategorie>,
        vergleichsEinheit: Verrechnungseinheit?,
    ) {
        if (kategorie.verrechnungseinheit != vergleichsEinheit) {
            throw IllegalStateException(
                "Die Verrechnungseinheiten in den Unterkategorien sind nicht konsistent. Analyse nicht möglich.",
            )
        }
        gesammelteKategorien.add(kategorie)
        val kategorieId = kategorie.id ?: return
        produktkategorieRepository.findById(kategorieId).ifPresent { loaded ->
            loaded.unterkategorien.forEach { unterkategorie ->
                sammleUnterkategorien(unterkategorie, gesammelteKategorien, vergleichsEinheit)
            }
        }
    }

    fun analysiereKategorie(kategorieId: Long, jahr: Int?): ProduktkategorieAnalyseDto {
        val startKategorie = produktkategorieRepository.findById(kategorieId)
            .orElseThrow { EntityNotFoundException("Kategorie mit ID $kategorieId nicht gefunden.") }

        val alleZuAnalysierendenKategorien = ArrayList<Produktkategorie>()
        sammleUnterkategorien(startKategorie, alleZuAnalysierendenKategorien, startKategorie.verrechnungseinheit)
        val kategorieIds = alleZuAnalysierendenKategorien.mapNotNull { it.id }

        val projekte =
            if (jahr != null) {
                val start = LocalDate.of(jahr, 1, 1)
                val end = LocalDate.of(jahr, 12, 31)
                projektRepository.findByProduktkategorieIdsAndAbschlussdatumBetween(kategorieIds, start, end)
            } else {
                projektRepository.findByProduktkategorieIds(kategorieIds)
            }

        val arbeitsgangMap = HashMap<Long, Aggregat>()
        val projektDtos = ArrayList<ProjektAnalyseDto>()
        var gesamtStundenMitZeiten = 0.0
        var gesamtEinheitenMitZeiten = 0.0
        var sumXMitZeiten = 0.0
        var sumYMitZeiten = 0.0
        var sumXYMitZeiten = 0.0
        var sumX2MitZeiten = 0.0
        var datenpunkte = 0

        for (projekt in projekte) {
            val einheiten = projekt.projektProduktkategorien
                .filter { ppk -> kategorieIds.contains(ppk.produktkategorie?.id) }
                .sumOf { ppk -> ppk.menge.toDouble() }

            val projektAgMap = HashMap<Long, ProjektArbeitsgangAnalyseDto>()
            var projektStunden = 0.0
            for (zeit in projekt.zeitbuchungen) {
                val ppk = zeit.projektProduktkategorie
                val produktkategorie = ppk?.produktkategorie
                if (produktkategorie == null || !kategorieIds.contains(produktkategorie.id)) {
                    continue
                }

                val stunden = zeit.anzahlInStunden?.toDouble() ?: 0.0
                projektStunden += stunden
                val ag = zeit.arbeitsgang ?: continue
                val agId = ag.id ?: continue

                val projAgg = projektAgMap.computeIfAbsent(agId) {
                    ProjektArbeitsgangAnalyseDto().apply {
                        arbeitsgangBeschreibung = ag.beschreibung
                    }
                }
                projAgg.stundenProEinheit = projAgg.stundenProEinheit + stunden

                val globalAgg = arbeitsgangMap.computeIfAbsent(agId) {
                    Aggregat(beschreibung = ag.beschreibung)
                }
                globalAgg.stunden += stunden
                globalAgg.einheiten += einheiten
            }

            if (projektStunden <= 0) {
                continue
            }

            val dto = ProjektAnalyseDto().apply {
                id = projekt.id
                projektname = projekt.bauvorhaben
                auftragsnummer = projekt.auftragsnummer
                kunde = projekt.getKunde()
                bildUrl = projekt.bildUrl
                masseinheit = einheiten
            }

            projektAgMap.values.forEach { analyse ->
                analyse.stundenProEinheit = if (einheiten != 0.0) analyse.stundenProEinheit / einheiten else 0.0
            }
            dto.arbeitsgaenge = ArrayList(projektAgMap.values)
            dto.zeitGesamt = projektStunden
            projektDtos.add(dto)

            if (einheiten > 0) {
                gesamtStundenMitZeiten += projektStunden
                gesamtEinheitenMitZeiten += einheiten
                sumXMitZeiten += einheiten
                sumYMitZeiten += projektStunden
                sumXYMitZeiten += einheiten * projektStunden
                sumX2MitZeiten += einheiten * einheiten
                datenpunkte++
            }
        }

        val arbeitsgangAnalysen = arbeitsgangMap.values.map { agg ->
            ArbeitsgangAnalyseDto().apply {
                arbeitsgangBeschreibung = agg.beschreibung
                durchschnittStundenProEinheit = if (agg.einheiten != 0.0) agg.stunden / agg.einheiten else 0.0
            }
        }

        val durchschnitt =
            if (gesamtEinheitenMitZeiten != 0.0) gesamtStundenMitZeiten / gesamtEinheitenMitZeiten else 0.0
        var steigung = 0.0
        var fixzeit = 0.0
        val n = datenpunkte
        if (n > 0) {
            val denominator = n * sumX2MitZeiten - sumXMitZeiten * sumXMitZeiten
            if (denominator != 0.0) {
                steigung = (n * sumXYMitZeiten - sumXMitZeiten * sumYMitZeiten) / denominator
                fixzeit = (sumYMitZeiten - steigung * sumXMitZeiten) / n
            } else if (sumXMitZeiten != 0.0) {
                steigung = sumYMitZeiten / sumXMitZeiten
            }
        }
        if (fixzeit < 0) {
            fixzeit = 0.0
        }

        var rQuadrat = 0.0
        var residualStdAbweichung = 0.0
        if (n >= 2) {
            val yMean = sumYMitZeiten / n
            var ssTot = 0.0
            var ssRes = 0.0
            var sumResiduals2 = 0.0
            for (dto in projektDtos) {
                if (dto.masseinheit <= 0) continue
                val yHat = fixzeit + steigung * dto.masseinheit
                val residual = dto.zeitGesamt - yHat
                ssTot += (dto.zeitGesamt - yMean).pow(2.0)
                ssRes += residual * residual
                sumResiduals2 += residual * residual
            }
            rQuadrat = if (ssTot != 0.0) 1.0 - (ssRes / ssTot) else 0.0
            if (rQuadrat < 0) rQuadrat = 0.0
            residualStdAbweichung = sqrt(sumResiduals2 / (n - 2))
        }

        return ProduktkategorieAnalyseDto().apply {
            projektAnzahl = projektDtos.size.toLong()
            durchschnittlicheZeit = durchschnitt
            this.fixzeit = fixzeit
            this.steigung = steigung
            verrechnungseinheit = startKategorie.verrechnungseinheit?.anzeigename
            this.projekte = projektDtos
            this.arbeitsgangAnalysen = arbeitsgangAnalysen
            datenpunkte = n
            this.rQuadrat = rQuadrat
            this.residualStdAbweichung = residualStdAbweichung
        }
    }

    private fun berechneProjektAnzahlen(): Map<Long, Long> {
        val pairs = projektRepository.getKategorieProjektPairs()
        val mapping = HashMap<Long, MutableSet<Long>>()
        for (pair in pairs) {
            val kategorieId = pair[0] as? Long ?: continue
            val projektId = pair[1] as? Long ?: continue
            mapping.computeIfAbsent(kategorieId) { HashSet() }.add(projektId)
        }

        val alle = produktkategorieRepository.findAll()
        val cumulativeCounts = HashMap<Long, Long>()
        for (kat in alle) {
            val katId = kat.id ?: continue
            val allProjectIds = HashSet<Long>()
            sammleProjektIdsRekursiv(kat, mapping, allProjectIds)
            cumulativeCounts[katId] = allProjectIds.size.toLong()
        }
        return cumulativeCounts
    }

    private fun sammleProjektIdsRekursiv(
        kat: Produktkategorie,
        mapping: Map<Long, Set<Long>>,
        allProjectIds: MutableSet<Long>,
    ) {
        val katId = kat.id
        if (katId != null && mapping.containsKey(katId)) {
            allProjectIds.addAll(mapping[katId].orEmpty())
        }
        kat.unterkategorien.forEach { sub ->
            sammleProjektIdsRekursiv(sub, mapping, allProjectIds)
        }
    }

    private data class Aggregat(
        var stunden: Double = 0.0,
        var einheiten: Double = 0.0,
        var beschreibung: String? = null,
    )
}
