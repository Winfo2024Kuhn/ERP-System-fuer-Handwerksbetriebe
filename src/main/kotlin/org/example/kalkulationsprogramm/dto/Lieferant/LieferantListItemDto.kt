package org.example.kalkulationsprogramm.dto.Lieferant

import org.example.kalkulationsprogramm.domain.LieferantRolle

data class LieferantListItemDto(
    var id: Long? = null,
    var lieferantenname: String? = null,
    var lieferantenTyp: String? = null,
    var rollen: Set<LieferantRolle>? = null,
    var vertreter: String? = null,
    var strasse: String? = null,
    var plz: String? = null,
    var ort: String? = null,
    var telefon: String? = null,
    var mobiltelefon: String? = null,
    var istAktiv: Boolean? = null,
    var kundenEmails: List<String>? = null,
    var lieferzeit: Int? = null,
    var bestellungen: Int? = null,
    var standardKostenstelleId: Long? = null,
    var standardKostenstelleName: String? = null,
)
