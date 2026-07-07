package org.example.kalkulationsprogramm.dto.Lieferant

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.example.kalkulationsprogramm.domain.LieferantRolle
import java.time.LocalDate

open class LieferantCreateRequestDto(
    @field:NotBlank(message = "Lieferantenname darf nicht leer sein.")
    @field:Size(max = 255, message = "Lieferantenname ist zu lang.")
    var lieferantenname: String? = null,
    @field:Size(max = 50, message = "Kundennummer ist zu lang.")
    var eigeneKundennummer: String? = null,
    @field:Size(max = 100, message = "Lieferantentyp ist zu lang.")
    var lieferantenTyp: String? = null,
    var rollen: Set<LieferantRolle>? = null,
    @field:Size(max = 255, message = "Vertreter ist zu lang.")
    var vertreter: String? = null,
    @field:Size(max = 255, message = "Straße ist zu lang.")
    var strasse: String? = null,
    @field:Size(max = 20, message = "PLZ ist zu lang.")
    var plz: String? = null,
    @field:Size(max = 255, message = "Ort ist zu lang.")
    var ort: String? = null,
    @field:Size(max = 50, message = "Telefonnummer ist zu lang.")
    var telefon: String? = null,
    @field:Size(max = 50, message = "Mobiltelefon ist zu lang.")
    var mobiltelefon: String? = null,
    var istAktiv: Boolean? = true,
    @field:JsonFormat(pattern = "yyyy-MM-dd")
    var startZusammenarbeit: LocalDate? = null,
    var kundenEmails: List<@Email(message = "Ungültige E-Mail-Adresse.") @Size(max = 255, message = "E-Mail ist zu lang.") String>? = null,
    var standardKostenstelleId: Long? = null,
)
