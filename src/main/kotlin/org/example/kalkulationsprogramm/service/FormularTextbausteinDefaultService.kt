package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Dokumenttyp
import org.example.kalkulationsprogramm.domain.FormularTemplateTextbausteinDefault
import org.example.kalkulationsprogramm.domain.Textbaustein
import org.example.kalkulationsprogramm.domain.TextbausteinPosition
import org.example.kalkulationsprogramm.dto.Formular.FormularTextbausteinDefaultsDto
import org.example.kalkulationsprogramm.repository.FormularTemplateTextbausteinDefaultRepository
import org.example.kalkulationsprogramm.repository.TextbausteinRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import org.springframework.web.server.ResponseStatusException
import java.util.EnumMap

@Service
open class FormularTextbausteinDefaultService(
    private val defaultsRepository: FormularTemplateTextbausteinDefaultRepository,
    private val textbausteinRepository: TextbausteinRepository,
) {

    @Transactional(readOnly = true)
    open fun loadDefaults(templateName: String?): FormularTextbausteinDefaultsDto {
        val dto = FormularTextbausteinDefaultsDto()
        if (!StringUtils.hasText(templateName)) {
            return dto
        }
        val rows = defaultsRepository
            .findByTemplateNameIgnoreCaseOrderByDokumenttypAscPositionAscSortOrderAsc(templateName!!.trim())

        val grouped = EnumMap<Dokumenttyp, EnumMap<TextbausteinPosition, MutableList<Long>>>(Dokumenttyp::class.java)
        for (row in rows) {
            val textbausteinId = row.textbaustein?.id ?: continue
            val typ = row.dokumenttyp ?: continue
            val position = row.position ?: continue
            grouped
                .computeIfAbsent(typ) { EnumMap(TextbausteinPosition::class.java) }
                .computeIfAbsent(position) { mutableListOf() }
                .add(textbausteinId)
        }

        grouped.forEach { (typ, byPos) ->
            val entry = FormularTextbausteinDefaultsDto.Entry()
            entry.dokumenttyp = typ.label
            entry.vortextIds = byPos[TextbausteinPosition.VOR] ?: mutableListOf()
            entry.nachtextIds = byPos[TextbausteinPosition.NACH] ?: mutableListOf()
            dto.entries.add(entry)
        }

        return dto
    }

    @Transactional
    open fun replaceDefaults(templateName: String?, dto: FormularTextbausteinDefaultsDto?) {
        if (!StringUtils.hasText(templateName)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Vorlagenname fehlt.")
        }
        val name = templateName!!.trim()
        defaultsRepository.deleteByTemplateNameIgnoreCase(name)
        val entries = dto?.entries ?: return

        for (entry in entries) {
            if (!StringUtils.hasText(entry.dokumenttyp)) continue
            val typ = Dokumenttyp.fromLabel(entry.dokumenttyp) ?: continue
            saveAll(name, typ, TextbausteinPosition.VOR, entry.vortextIds)
            saveAll(name, typ, TextbausteinPosition.NACH, entry.nachtextIds)
        }
    }

    @Transactional
    open fun deleteForTemplate(templateName: String?) {
        if (!StringUtils.hasText(templateName)) return
        defaultsRepository.deleteByTemplateNameIgnoreCase(templateName!!.trim())
    }

    @Transactional
    open fun renameTemplate(oldName: String?, newName: String?) {
        if (!StringUtils.hasText(oldName) || !StringUtils.hasText(newName)) return
        if (oldName!!.equals(newName, ignoreCase = true)) return

        val oldTrimmed = oldName.trim()
        val newTrimmed = newName!!.trim()
        val rows = defaultsRepository
            .findByTemplateNameIgnoreCaseOrderByDokumenttypAscPositionAscSortOrderAsc(oldTrimmed)
        defaultsRepository.deleteByTemplateNameIgnoreCase(newTrimmed)
        for (row in rows) {
            val copy = FormularTemplateTextbausteinDefault()
            copy.templateName = newTrimmed
            copy.dokumenttyp = row.dokumenttyp
            copy.position = row.position
            copy.textbaustein = row.textbaustein
            copy.sortOrder = row.sortOrder
            defaultsRepository.save(copy)
        }
        defaultsRepository.deleteByTemplateNameIgnoreCase(oldTrimmed)
    }

    @Transactional(readOnly = true)
    open fun loadForDokumenttyp(templateName: String?, dokumenttyp: String?): DefaultsForDokumenttyp {
        if (!StringUtils.hasText(templateName) || !StringUtils.hasText(dokumenttyp)) {
            return DefaultsForDokumenttyp(emptyList(), emptyList())
        }
        val typ = try {
            Dokumenttyp.fromLabel(dokumenttyp!!.trim())
        } catch (_: IllegalArgumentException) {
            null
        } ?: return DefaultsForDokumenttyp(emptyList(), emptyList())

        val vor = defaultsRepository
            .findByTemplateNameIgnoreCaseAndDokumenttypAndPositionOrderBySortOrderAsc(
                templateName!!.trim(),
                typ,
                TextbausteinPosition.VOR,
            )
            .mapNotNull { it.textbaustein }

        val nach = defaultsRepository
            .findByTemplateNameIgnoreCaseAndDokumenttypAndPositionOrderBySortOrderAsc(
                templateName.trim(),
                typ,
                TextbausteinPosition.NACH,
            )
            .mapNotNull { it.textbaustein }

        return DefaultsForDokumenttyp(vor, nach)
    }

    private fun saveAll(templateName: String, typ: Dokumenttyp, pos: TextbausteinPosition, ids: List<Long>?) {
        if (ids.isNullOrEmpty()) return
        var order = 0
        for (id in ids) {
            val textbaustein = textbausteinRepository.findById(id).orElse(null) ?: continue
            val row = FormularTemplateTextbausteinDefault()
            row.templateName = templateName
            row.dokumenttyp = typ
            row.position = pos
            row.textbaustein = textbaustein
            row.sortOrder = order++
            defaultsRepository.save(row)
        }
    }

    data class DefaultsForDokumenttyp(
        val vortexte: List<Textbaustein>,
        val nachtexte: List<Textbaustein>,
    ) {
        fun vortexte(): List<Textbaustein> = vortexte

        fun nachtexte(): List<Textbaustein> = nachtexte
    }
}
