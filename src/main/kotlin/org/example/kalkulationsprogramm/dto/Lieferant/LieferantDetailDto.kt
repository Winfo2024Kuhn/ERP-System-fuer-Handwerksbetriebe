package org.example.kalkulationsprogramm.dto.Lieferant

import org.example.kalkulationsprogramm.domain.LieferantRolle
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailDto
import java.time.LocalDate

data class LieferantDetailDto(
    var id: Long? = null,
    var lieferantenname: String? = null,
    var eigeneKundennummer: String? = null,
    var lieferantenTyp: String? = null,
    var rollen: Set<LieferantRolle>? = null,
    var vertreter: String? = null,
    var strasse: String? = null,
    var plz: String? = null,
    var ort: String? = null,
    var telefon: String? = null,
    var mobiltelefon: String? = null,
    var istAktiv: Boolean? = null,
    var startZusammenarbeit: LocalDate? = null,
    var kundenEmails: List<String>? = null,
    var standardKostenstelleId: Long? = null,
    var standardKostenstelleName: String? = null,
    var statistik: LieferantStatistikDto? = null,
    var artikelpreise: List<LieferantArtikelpreisDto>? = null,
    var kommunikation: List<LieferantKommunikationDto>? = null,
    var dokumente: List<LieferantDokumentDto.Response>? = null,
    var emails: List<ProjektEmailDto>? = null,
    var notizen: List<LieferantNotizDto>? = null,
)
