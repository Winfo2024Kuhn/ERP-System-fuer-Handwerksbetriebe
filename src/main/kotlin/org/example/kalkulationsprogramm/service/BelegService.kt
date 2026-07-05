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
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Path
import java.time.LocalDate

@Service
open class BelegService(
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val frontendUserProfileRepository: FrontendUserProfileRepository,
    private val berechtigungRepository: AbteilungDokumentBerechtigungRepository,
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

    fun listBelege(statusFilter: BelegStatus?, kategorieFilter: BelegKategorie?): List<BelegDto.Response> = emptyList()

    fun listBelegeFuerMobile(uploader: Mitarbeiter?): List<BelegDto.Response> = emptyList()

    fun getBeleg(id: Long): BelegDto.Response = BelegDto.Response.builder().id(id).build()

    fun getRawBeleg(id: Long): Beleg? = null

    fun getBelegDatei(beleg: Beleg): Path = Path.of("uploads", "belege", beleg.gespeicherterDateiname ?: "")

    fun updateBeleg(id: Long, req: BelegDto.UpdateRequest, validierer: Mitarbeiter?): BelegDto.Response =
        BelegDto.Response.builder().id(id).build()

    fun setzePositionsAuswahl(belegId: Long, firmaPositionIds: List<Long>?): BelegDto.Response =
        BelegDto.Response.builder().id(belegId).build()

    fun deleteBeleg(id: Long): Boolean = false

    fun createUmbuchung(req: BelegDto.UmbuchungCreateRequest, ersteller: Mitarbeiter?): Beleg =
        Beleg().apply { uploadedBy = ersteller }

    fun getKassenbuch(von: LocalDate?, bis: LocalDate?): BelegDto.KassenbuchResponse =
        BelegDto.KassenbuchResponse.builder().bewegungen(emptyList()).build()

    fun listeFuerSteuerberaterExport(von: LocalDate?, bis: LocalDate?): List<BelegDto.SteuerberaterExportEntry> = emptyList()

    fun toDto(b: Beleg): BelegDto.Response = toDto(b, false)

    fun toDto(b: Beleg, mitPositionen: Boolean): BelegDto.Response =
        BelegDto.Response.builder().id(b.id).status(b.status?.name).build()

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
