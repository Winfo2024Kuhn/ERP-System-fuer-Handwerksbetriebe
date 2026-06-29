package org.example.kalkulationsprogramm.mapper

import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto
import org.springframework.stereotype.Component

@Component
class AnfrageMapper {

    fun toAnfrageResponseDto(a: Anfrage?): AnfrageResponseDto? {
        if (a == null) {
            return null
        }
        return AnfrageResponseDto().apply {
            id = a.id
            a.kunde?.let { kunde ->
                kundenId = kunde.id
                kundenName = sanitize(kunde.name)
                kundennummer = kunde.kundennummer

                val allEmails = mutableListOf<String>()
                kunde.kundenEmails?.let { allEmails.addAll(it) }
                a.kundenEmails?.let { allEmails.addAll(it) }
                kundenEmails = allEmails.distinct()

                kundenStrasse = kunde.strasse
                kundenPlz = kunde.plz
                kundenOrt = kunde.ort
                kundenTelefon = kunde.telefon
                kundenMobiltelefon = kunde.mobiltelefon
                kundenAnsprechpartner = kunde.ansprechspartner
                kundenAnrede = kunde.anrede?.name
            }
        }
    }

    private fun sanitize(s: String?): String? =
        s?.replace("�", "ss")?.replace("?", "ss")?.replace("?", "")
}
