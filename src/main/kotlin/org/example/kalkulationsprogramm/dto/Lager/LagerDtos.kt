package org.example.kalkulationsprogramm.dto.Lager

import org.example.kalkulationsprogramm.domain.LagerbewegungTyp
import java.math.BigDecimal
import java.time.LocalDateTime

data class LagerortDto(
    var id: Long? = null,
    var code: String? = null,
    var name: String? = null,
    var regal: String? = null,
    var fach: String? = null,
)

data class LagerbestandDto(
    var id: Long? = null,
    var artikelId: Long? = null,
    var produktname: String? = null,
    var produktlinie: String? = null,
    var produkttext: String? = null,
    var externeArtikelnummer: String? = null,
    var lagerort: LagerortDto? = null,
    var menge: BigDecimal = BigDecimal.ZERO,
    var mindestbestand: BigDecimal = BigDecimal.ZERO,
    var unterMindestbestand: Boolean = false,
    var charge: String? = null,
    var bemerkung: String? = null,
    var aktualisiertAm: LocalDateTime? = null,
)

data class LagerbewegungDto(
    var id: Long? = null,
    var typ: LagerbewegungTyp? = null,
    var artikelId: Long? = null,
    var produktname: String? = null,
    var vonLagerort: LagerortDto? = null,
    var nachLagerort: LagerortDto? = null,
    var menge: BigDecimal = BigDecimal.ZERO,
    var grund: String? = null,
    var referenz: String? = null,
    var verantwortlicher: String? = null,
    var erstelltAm: LocalDateTime? = null,
)

data class LagerortRequest(
    var code: String? = null,
    var name: String? = null,
    var regal: String? = null,
    var fach: String? = null,
)

data class LagerbewegungRequest(
    var artikelId: Long? = null,
    var lagerortId: Long? = null,
    var vonLagerortId: Long? = null,
    var nachLagerortId: Long? = null,
    var typ: LagerbewegungTyp? = null,
    var menge: BigDecimal? = null,
    var mindestbestand: BigDecimal? = null,
    var grund: String? = null,
    var referenz: String? = null,
    var verantwortlicher: String? = null,
)
