package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Kunde
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatGrund
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatResponseDto
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatTrefferDto
import org.example.kalkulationsprogramm.repository.KundeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import java.util.EnumSet
import java.util.Locale

@Service
open class KundeDuplikatService(
    private val kundeRepository: KundeRepository,
) {

    @Transactional(readOnly = true)
    open fun findeDuplikate(
        email: String?,
        telefon: String?,
        mobiltelefon: String?,
        name: String?,
        plz: String?,
        strasse: String?,
    ): KundeDuplikatResponseDto {
        val emailNorm = normalisiereEmail(email)
        val telDigits = normalisiereTelefon(telefon)
        val mobilDigits = normalisiereTelefon(mobiltelefon)
        val nameNorm = normalisiereName(name)
        val plzNorm = if (StringUtils.hasText(plz)) plz!!.trim() else null
        val strasseNorm = normalisiereStrasse(strasse)

        if (emailNorm == null && telDigits == null && mobilDigits == null &&
            (nameNorm == null || (plzNorm == null && strasseNorm == null))
        ) {
            return KundeDuplikatResponseDto(emptyList(), false)
        }

        val kandidaten = kundeRepository.findePotenzielleDuplikate(
            emailNorm,
            telDigits,
            mobilDigits,
            nameNorm,
            plzNorm,
            strasseNorm,
        )

        val trefferProKunde = linkedMapOf<Long?, KundeDuplikatTrefferDto>()
        var harterTreffer = false

        for (kunde in kandidaten) {
            val gruende = ermittleGruende(kunde, emailNorm, telDigits, mobilDigits, nameNorm, plzNorm, strasseNorm)
            if (gruende.isEmpty()) {
                continue
            }
            val treffer = toTreffer(kunde, gruende)
            trefferProKunde[invokeLong(kunde, "getId")] = treffer
            if (gruende.any { it.isHart() }) {
                harterTreffer = true
            }
        }

        val sortiert = trefferProKunde.values.sortedByDescending { it.score }
        return KundeDuplikatResponseDto(sortiert, harterTreffer)
    }

    private fun ermittleGruende(
        kunde: Kunde,
        emailNorm: String?,
        telDigits: String?,
        mobilDigits: String?,
        nameNorm: String?,
        plz: String?,
        strasse: String?,
    ): EnumSet<KundeDuplikatGrund> {
        val gruende = EnumSet.noneOf(KundeDuplikatGrund::class.java)

        val kundenEmails = invokeStringList(kunde, "getKundenEmails")
        if (emailNorm != null && kundenEmails != null) {
            for (email in kundenEmails) {
                if (email != null && email.trim().lowercase(Locale.GERMAN) == emailNorm) {
                    gruende.add(KundeDuplikatGrund.EMAIL_GLEICH)
                    break
                }
            }
        }
        if (telDigits != null && telDigits == normalisiereTelefon(invokeString(kunde, "getTelefon"))) {
            gruende.add(KundeDuplikatGrund.TELEFON_GLEICH)
        }
        if (mobilDigits != null && mobilDigits == normalisiereTelefon(invokeString(kunde, "getMobiltelefon"))) {
            gruende.add(KundeDuplikatGrund.MOBILTELEFON_GLEICH)
        }
        if (nameNorm != null && nameNorm == normalisiereName(invokeString(kunde, "getName"))) {
            if (plz != null && plz == invokeString(kunde, "getPlz")) {
                gruende.add(KundeDuplikatGrund.NAME_PLZ_GLEICH)
            }
            if (strasse != null && strasse == normalisiereStrasse(invokeString(kunde, "getStrasse"))) {
                gruende.add(KundeDuplikatGrund.NAME_STRASSE_GLEICH)
            }
        }
        return gruende
    }

    private fun toTreffer(kunde: Kunde, gruende: Set<KundeDuplikatGrund>): KundeDuplikatTrefferDto {
        val sorted = gruende.sorted()
        return KundeDuplikatTrefferDto(
            id = invokeLong(kunde, "getId"),
            kundennummer = invokeString(kunde, "getKundennummer"),
            name = invokeString(kunde, "getName"),
            ansprechspartner = invokeString(kunde, "getAnsprechspartner"),
            strasse = invokeString(kunde, "getStrasse"),
            plz = invokeString(kunde, "getPlz"),
            ort = invokeString(kunde, "getOrt"),
            telefon = invokeString(kunde, "getTelefon"),
            mobiltelefon = invokeString(kunde, "getMobiltelefon"),
            kundenEmails = invokeStringList(kunde, "getKundenEmails")?.filterNotNull() ?: emptyList(),
            gruende = sorted,
            score = sorted.sumOf { it.score },
        )
    }

    private fun invokeLong(target: Any, methodName: String): Long? =
        target.javaClass.getMethod(methodName).invoke(target) as? Long

    private fun invokeString(target: Any, methodName: String): String? =
        target.javaClass.getMethod(methodName).invoke(target) as? String

    @Suppress("UNCHECKED_CAST")
    private fun invokeStringList(target: Any, methodName: String): List<String?>? =
        target.javaClass.getMethod(methodName).invoke(target) as? List<String?>

    companion object {
        @JvmStatic
        fun normalisiereEmail(email: String?): String? {
            if (!StringUtils.hasText(email)) {
                return null
            }
            return email!!.trim().lowercase(Locale.GERMAN)
        }

        @JvmStatic
        fun normalisiereTelefon(telefon: String?): String? {
            if (!StringUtils.hasText(telefon)) {
                return null
            }
            var value = telefon!!.trim()
            if (value.startsWith("+49")) {
                value = "0" + value.substring(3)
            } else if (value.startsWith("0049")) {
                value = "0" + value.substring(4)
            }
            val digits = value.replace("\\D".toRegex(), "")
            if (digits.length < 7) {
                return null
            }
            return digits
        }

        @JvmStatic
        fun normalisiereName(name: String?): String? {
            if (!StringUtils.hasText(name)) {
                return null
            }
            return name!!.trim().lowercase(Locale.GERMAN).replace("\\s+".toRegex(), " ")
        }

        @JvmStatic
        fun normalisiereStrasse(strasse: String?): String? {
            if (!StringUtils.hasText(strasse)) {
                return null
            }
            return strasse!!.trim().lowercase(Locale.GERMAN)
                .replace("\\s+".toRegex(), " ")
                .replace("straße", "str.")
                .replace("strasse", "str.")
        }
    }
}
