package org.example.kalkulationsprogramm.dto.Kunde

data class KundeDetailDto(
    var id: Long? = null,
    var kundennummer: String? = null,
    var name: String? = null,
    var anrede: String? = null,
    var ansprechspartner: String? = null,
    var strasse: String? = null,
    var plz: String? = null,
    var ort: String? = null,
    var telefon: String? = null,
    var mobiltelefon: String? = null,
    var kundenEmails: List<String>? = null,
    var statistik: KundeStatistikDto? = null,
    var aggregierteEmails: List<KundeAggregierteEmailDto>? = null,
    var projekte: List<KundeProjektKurzDto>? = null,
    var anfragen: List<KundeAnfrageKurzDto>? = null,
    var kommunikation: List<KundeKommunikationDto>? = null,
    var notizen: List<KundeNotizDto>? = null,
)
