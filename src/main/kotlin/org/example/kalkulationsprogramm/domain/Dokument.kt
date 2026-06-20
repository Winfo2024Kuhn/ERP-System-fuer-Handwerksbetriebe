package org.example.kalkulationsprogramm.domain

import java.time.LocalDate

interface Dokument {
    val id: Long?
    val originalDateiname: String?
    val gespeicherterDateiname: String?
    val dateityp: String?
    val dateigroesse: Long?
    val uploadDatum: LocalDate?
    val emailVersandDatum: LocalDate?
    val dokumentGruppe: DokumentGruppe?
}
