package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantArtikelpreisDto
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.util.Date

@Component
class LieferantArtikelpreisMapper {
    fun toDto(entity: LieferantenArtikelPreise?): LieferantArtikelpreisDto? {
        if (entity == null) {
            return null
        }

        val dto = LieferantArtikelpreisDto()
        val artikel = entity.artikel
        if (artikel != null) {
            dto.artikelId = longValue(artikel, "getId")
            dto.produktname = stringValue(artikel, "getProduktname")
            dto.produkttext = stringValue(artikel, "getProdukttext")
            val werkstoff = invokeGetter(artikel, "getWerkstoff")
            if (werkstoff != null) {
                dto.werkstoff = stringValue(werkstoff, "getName")
            }
        }
        dto.externeArtikelnummer = entity.externeArtikelnummer
        dto.preis = entity.preis
        dto.preisAenderungsdatum = toLocalDate(entity.preisAenderungsdatum)
        return dto
    }

    fun toDtoList(entities: List<LieferantenArtikelPreise>?): List<LieferantArtikelpreisDto> =
        entities.orEmpty().mapNotNull { toDto(it) }

    private fun toLocalDate(date: Date?) =
        date?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()

    private fun longValue(target: Any, getter: String): Long? =
        invokeGetter(target, getter) as? Long

    private fun stringValue(target: Any, getter: String): String? =
        invokeGetter(target, getter) as? String

    private fun invokeGetter(target: Any, getter: String): Any? =
        target.javaClass.getMethod(getter).invoke(target)
}
