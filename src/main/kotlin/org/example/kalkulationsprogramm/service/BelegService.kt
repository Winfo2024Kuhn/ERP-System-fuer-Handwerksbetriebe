package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal
import org.example.kalkulationsprogramm.domain.Beleg
import org.example.kalkulationsprogramm.domain.BelegAufteilungsModus
import org.example.kalkulationsprogramm.domain.BelegKategorie
import org.example.kalkulationsprogramm.domain.BelegStatus
import org.example.kalkulationsprogramm.domain.FrontendUserRole
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.dto.BelegDto
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository
import org.example.kalkulationsprogramm.repository.BelegPositionRepository
import org.example.kalkulationsprogramm.repository.BelegRepository
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Path
import java.time.LocalDate

@Service
open class BelegService(
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val frontendUserProfileRepository: FrontendUserProfileRepository,
    private val berechtigungRepository: AbteilungDokumentBerechtigungRepository,
    private val belegRepository: BelegRepository,
    private val belegPositionRepository: BelegPositionRepository,
    private val belegKostenstellenAnteilRepository: BelegKostenstellenAnteilRepository,
) {
    fun getPermissions(mitarbeiter: Mitarbeiter?): BelegDto.PermissionResponse {
        if (isAdminCaller(mitarbeiter)) {
            return BelegDto.PermissionResponse.builder().darfScannen(true).darfSehen(true).build()
        }
        val abteilungIds = mitarbeiter?.abteilungen
            ?.mapNotNull { it.id }
            ?.distinct()
            .orEmpty()
        if (abteilungIds.isEmpty()) {
            return BelegDto.PermissionResponse.builder().darfScannen(false).darfSehen(false).build()
        }
        val sichtbareTypen = berechtigungRepository.findSichtbareTypenByAbteilungIds(abteilungIds)
        val scanbareTypen = berechtigungRepository.findScanbarTypenByAbteilungIds(abteilungIds)
        val darfScannen = scanbareTypen.any { it.equals(BELEG_TYP, ignoreCase = true) }
        val darfSehen = darfScannen || sichtbareTypen.any { it.equals(BELEG_TYP, ignoreCase = true) }
        return BelegDto.PermissionResponse.builder()
            .darfScannen(darfScannen)
            .darfSehen(darfSehen)
            .build()
    }

    fun darfScannen(mitarbeiter: Mitarbeiter?): Boolean = getPermissions(mitarbeiter).isDarfScannen

    fun darfSehen(mitarbeiter: Mitarbeiter?): Boolean = getPermissions(mitarbeiter).isDarfSehen

    @Throws(IOException::class)
    open fun uploadBeleg(datei: MultipartFile, uploader: Mitarbeiter?): Beleg =
        uploadBeleg(datei, null, BelegAufteilungsModus.VOLLSTAENDIG, uploader)

    @Throws(IOException::class)
    open fun uploadBeleg(datei: MultipartFile, lieferantId: Long?, uploader: Mitarbeiter?): Beleg =
        uploadBeleg(datei, lieferantId, BelegAufteilungsModus.VOLLSTAENDIG, uploader)

    @Throws(IOException::class)
    open fun uploadBeleg(
        datei: MultipartFile,
        lieferantId: Long?,
        aufteilungsModus: BelegAufteilungsModus?,
        uploader: Mitarbeiter?,
    ): Beleg = Beleg().apply { uploadedBy = uploader }

    fun listBelege(statusFilter: BelegStatus?, kategorieFilter: BelegKategorie?): List<BelegDto.Response> {
        val belege = when {
            statusFilter != null && kategorieFilter != null ->
                belegRepository.findByStatusAndBelegKategorieOrderByBelegDatumDesc(statusFilter, kategorieFilter)
            statusFilter != null ->
                belegRepository.findByStatusOrderByUploadDatumDesc(statusFilter)
            else ->
                belegRepository.findAllByOrderByUploadDatumDesc()
        }
        return belege
            .asSequence()
            .filter { kategorieFilter == null || it.belegKategorie == kategorieFilter }
            .map { toDto(it) }
            .toList()
    }

    fun listBelegeFuerMobile(uploader: Mitarbeiter?): List<BelegDto.Response> {
        val belege = if (uploader == null || isAdminCaller(uploader)) {
            belegRepository.findAllByOrderByUploadDatumDesc().take(20)
        } else {
            belegRepository.findTop20ByUploadedByOrderByUploadDatumDesc(uploader)
        }
        return belege.map { toDto(it) }
    }

    fun getBeleg(id: Long): BelegDto.Response? =
        belegRepository.findById(id).map { toDto(it, true) }.orElse(null)

    fun getRawBeleg(id: Long): Beleg? = belegRepository.findById(id).orElse(null)

    fun getBelegDatei(beleg: Beleg): Path = Path.of("uploads", "belege", beleg.gespeicherterDateiname ?: "")

    fun updateBeleg(id: Long, req: BelegDto.UpdateRequest, validierer: Mitarbeiter?): BelegDto.Response =
        BelegDto.Response.builder().id(id).build()

    fun setzePositionsAuswahl(belegId: Long, firmaPositionIds: List<Long>?): BelegDto.Response =
        BelegDto.Response.builder().id(belegId).build()

    fun deleteBeleg(id: Long): Boolean = false

    fun createUmbuchung(req: BelegDto.UmbuchungCreateRequest, ersteller: Mitarbeiter?): Beleg =
        Beleg().apply { uploadedBy = ersteller }

    fun getKassenbuch(von: LocalDate?, bis: LocalDate?): BelegDto.KassenbuchResponse {
        val relevanteKategorien = listOf(
            BelegKategorie.KASSE_EINNAHME,
            BelegKategorie.KASSE_AUSGABE,
            BelegKategorie.PRIVATEINLAGE,
            BelegKategorie.PRIVATENTNAHME,
        )
        val bewegungen = belegRepository.findValidierteByKategorien(BelegStatus.VALIDIERT, relevanteKategorien)
            .asSequence()
            .filter { b -> von == null || b.belegDatum?.let { !it.isBefore(von) } == true }
            .filter { b -> bis == null || b.belegDatum?.let { !it.isAfter(bis) } == true }
            .sortedWith(compareBy<Beleg> { it.belegDatum ?: LocalDate.MIN }.thenBy { it.id ?: 0L })
            .map { beleg ->
                val betrag = signedKassenBetrag(beleg)
                BelegDto.KassenBewegung.builder()
                    .belegId(beleg.id)
                    .datum(beleg.belegDatum)
                    .kategorie(beleg.belegKategorie.name)
                    .beschreibung(beleg.beschreibung)
                    .lieferantName(beleg.lieferant?.lieferantenname)
                    .betrag(betrag)
                    .build()
            }
            .toList()
        var saldo = BigDecimal.ZERO
        bewegungen.forEach { bewegung ->
            saldo = saldo.add(bewegung.betrag ?: BigDecimal.ZERO)
            bewegung.saldoNachher = saldo
        }
        val einnahmen = bewegungen.filter { (it.betrag ?: BigDecimal.ZERO) > BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, b -> acc.add(b.betrag ?: BigDecimal.ZERO) }
        val ausgaben = bewegungen.filter { (it.betrag ?: BigDecimal.ZERO) < BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, b -> acc.add((b.betrag ?: BigDecimal.ZERO).abs()) }
        return BelegDto.KassenbuchResponse.builder()
            .saldoStart(BigDecimal.ZERO)
            .saldoEnde(saldo)
            .summeEinnahmen(einnahmen)
            .summeAusgaben(ausgaben)
            .summePrivateinlagen(sumByKategorie(bewegungen, BelegKategorie.PRIVATEINLAGE))
            .summePrivatentnahmen(sumByKategorie(bewegungen, BelegKategorie.PRIVATENTNAHME))
            .bewegungen(bewegungen)
            .build()
    }

    fun listeFuerSteuerberaterExport(von: LocalDate?, bis: LocalDate?): List<BelegDto.SteuerberaterExportEntry> =
        belegRepository.findValidierteImZeitraumFuerExport(von, bis).map { beleg ->
            val positionen = belegPositionRepository.findByBelegIdOrderBySortierungAsc(beleg.id)
            BelegDto.SteuerberaterExportEntry.builder()
                .belegId(beleg.id)
                .belegDatum(beleg.belegDatum)
                .belegNummer(beleg.belegNummer)
                .lieferantName(beleg.lieferant?.lieferantenname)
                .belegKategorie(beleg.belegKategorie.name)
                .dokumentTyp(beleg.dokumentTyp?.name)
                .sachkontoNummer(beleg.sachkonto?.nummer)
                .sachkontoBezeichnung(beleg.sachkonto?.bezeichnung)
                .betragNetto(beleg.betragNetto)
                .betragBrutto(beleg.betragBrutto)
                .betragMwst(beleg.betragBrutto?.subtract(beleg.betragNetto ?: BigDecimal.ZERO))
                .mwstSatz(beleg.mwstSatz)
                .notiz(beleg.notiz)
                .beschreibung(beleg.beschreibung)
                .aufteilungsModus(beleg.aufteilungsModus.name)
                .gesamtBruttoOriginal(beleg.betragBrutto)
                .anzahlPositionenGesamt(positionen.size)
                .anzahlPositionenFirma(positionen.count { it.istFuerFirma })
                .positionenHinweis(if (positionen.isEmpty()) null else "Positionen vorhanden")
                .build()
        }

    fun toDto(b: Beleg): BelegDto.Response = toDto(b, false)

    fun toDto(b: Beleg, mitPositionen: Boolean): BelegDto.Response {
        val response = BelegDto.Response.builder()
            .id(b.id)
            .belegKategorie(b.belegKategorie.name)
            .dokumentTyp(b.dokumentTyp?.name)
            .istUmbuchung(b.istUmbuchung)
            .status(b.status.name)
            .kiAnalyseStatus(b.kiAnalyseStatus.name)
            .belegDatum(b.belegDatum)
            .belegNummer(b.belegNummer)
            .beschreibung(b.beschreibung)
            .betragNetto(b.betragNetto)
            .betragBrutto(b.betragBrutto)
            .mwstSatz(b.mwstSatz)
            .zahlungsart(b.zahlungsart)
            .lieferantId(b.lieferant?.id)
            .lieferantName(b.lieferant?.lieferantenname)
            .sachkontoId(b.sachkonto?.id)
            .sachkontoBezeichnung(b.sachkonto?.bezeichnung)
            .sachkontoNummer(b.sachkonto?.nummer)
            .sachkontoTyp(b.sachkonto?.kontoTyp?.name)
            .kostenstelleId(b.kostenstelle?.id)
            .kostenstelleBezeichnung(b.kostenstelle?.bezeichnung)
            .kostenstelleTyp(b.kostenstelle?.typ?.name)
            .kostenstelleIstFixkosten(b.kostenstelle?.istFixkosten)
            .kiVorgeschlagenerLieferant(b.kiVorgeschlagenerLieferant)
            .kiConfidence(b.kiConfidence)
            .kiVorgeschlagenerKostenstelleId(b.kiVorgeschlagenerKostenstelleId)
            .kiVorgeschlagenerSachkontoId(b.kiVorgeschlagenerSachkontoId)
            .kiKostenkontoConfidence(b.kiKostenkontoConfidence)
            .kiKostenkontoBegruendung(b.kiKostenkontoBegruendung)
            .kiFehlerText(b.kiFehlerText)
            .originalDateiname(b.originalDateiname)
            .mimeType(b.mimeType)
            .uploadDatum(b.uploadDatum)
            .uploadedById(b.uploadedBy?.id)
            .uploadedByName(b.uploadedBy?.let { "${it.vorname.orEmpty()} ${it.nachname.orEmpty()}".trim().ifBlank { it.email } })
            .validiertAm(b.validiertAm)
            .validiertVonId(b.validiertVon?.id)
            .validiertVonName(b.validiertVon?.let { "${it.vorname.orEmpty()} ${it.nachname.orEmpty()}".trim().ifBlank { it.email } })
            .notiz(b.notiz)
            .aufteilungsModus(b.aufteilungsModus.name)
            .betragFirmaNetto(b.betragFirmaNetto)
            .betragFirmaBrutto(b.betragFirmaBrutto)
            .betragFirmaMwst(b.betragFirmaMwst)
            .build()
        if (mitPositionen) {
            response.positionen = belegPositionRepository.findByBelegIdOrderBySortierungAsc(b.id).map { position ->
                BelegDto.PositionResponse.builder()
                    .id(position.id)
                    .sortierung(position.sortierung)
                    .beschreibung(position.beschreibung)
                    .menge(position.menge)
                    .einheit(position.einheit)
                    .einzelpreis(position.einzelpreis)
                    .betragNetto(position.betragNetto)
                    .betragBrutto(position.betragBrutto)
                    .mwstSatz(position.mwstSatz)
                    .istFuerFirma(position.istFuerFirma)
                    .build()
            }
            response.kostenstellenSplits = belegKostenstellenAnteilRepository.findByBelegId(b.id).map { anteil ->
                BelegDto.KostenstellenSplitDto.builder()
                    .id(anteil.id)
                    .kostenstelleId(anteil.kostenstelle?.id)
                    .kostenstelleBezeichnung(anteil.kostenstelle?.bezeichnung)
                    .kostenstelleIstFixkosten(anteil.kostenstelle?.istFixkosten)
                    .prozent(anteil.prozent)
                    .absoluterBetrag(anteil.absoluterBetrag)
                    .berechneterBetrag(anteil.berechneterBetrag)
                    .beschreibung(anteil.beschreibung)
                    .streckungJahre(anteil.streckungJahre)
                    .streckungStartJahr(anteil.streckungStartJahr)
                    .build()
            }
        }
        return response
    }

    private fun signedKassenBetrag(beleg: Beleg): BigDecimal {
        val betrag = beleg.betragBrutto ?: BigDecimal.ZERO
        return when (beleg.belegKategorie) {
            BelegKategorie.KASSE_EINNAHME,
            BelegKategorie.PRIVATEINLAGE,
                -> betrag.abs()
            BelegKategorie.KASSE_AUSGABE,
            BelegKategorie.PRIVATENTNAHME,
                -> betrag.abs().negate()
            else -> betrag
        }
    }

    private fun sumByKategorie(bewegungen: List<BelegDto.KassenBewegung>, kategorie: BelegKategorie): BigDecimal =
        bewegungen
            .filter { it.kategorie == kategorie.name }
            .fold(BigDecimal.ZERO) { acc, bewegung -> acc.add((bewegung.betrag ?: BigDecimal.ZERO).abs()) }

    fun findByToken(token: String?): Mitarbeiter? {
        if (token.isNullOrBlank()) return null
        return mitarbeiterRepository.findByLoginTokenAndAktivTrue(token.trim()).orElse(null)
    }

    fun findCaller(token: String?, auth: Authentication?): Mitarbeiter? {
        findByToken(token)?.let { return it }
        val principal = auth?.principal as? FrontendUserPrincipal ?: return null
        if (principal.hasRole(FrontendUserRole.ADMIN)) {
            return ADMIN_CALLER
        }
        val profileId = principal.id ?: return null
        return frontendUserProfileRepository.findById(profileId).orElse(null)?.mitarbeiter
    }

    companion object {
        private const val BELEG_TYP = "BELEG"
        private const val ADMIN_CALLER_MARKER = "__ADMIN_BELEG_CALLER__"

        private val ADMIN_CALLER = Mitarbeiter().apply {
            email = ADMIN_CALLER_MARKER
        }

        private fun isAdminCaller(mitarbeiter: Mitarbeiter?): Boolean =
            mitarbeiter?.email == ADMIN_CALLER_MARKER
    }
}
