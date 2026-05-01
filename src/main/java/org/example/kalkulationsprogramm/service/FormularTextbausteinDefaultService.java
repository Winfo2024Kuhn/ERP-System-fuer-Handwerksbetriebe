package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.FormularTemplateTextbausteinDefault;
import org.example.kalkulationsprogramm.domain.Textbaustein;
import org.example.kalkulationsprogramm.domain.TextbausteinPosition;
import org.example.kalkulationsprogramm.dto.Formular.FormularTextbausteinDefaultsDto;
import org.example.kalkulationsprogramm.repository.FormularTemplateTextbausteinDefaultRepository;
import org.example.kalkulationsprogramm.repository.TextbausteinRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Verwaltet Default-Textbausteine, die beim Anlegen oder Umwandeln eines
 * Geschaeftsdokuments automatisch vor bzw. nach den Leistungen eingefuegt werden.
 * Konfiguriert wird das pro Vorlage (Briefpapier) und Dokumenttyp im FormularwesenEditor.
 */
@Service
@RequiredArgsConstructor
public class FormularTextbausteinDefaultService {

    private final FormularTemplateTextbausteinDefaultRepository defaultsRepository;
    private final TextbausteinRepository textbausteinRepository;

    /** Liefert alle Defaults einer Vorlage als DTO, gruppiert pro Dokumenttyp. */
    @Transactional(readOnly = true)
    public FormularTextbausteinDefaultsDto loadDefaults(String templateName) {
        FormularTextbausteinDefaultsDto dto = new FormularTextbausteinDefaultsDto();
        if (!StringUtils.hasText(templateName)) {
            return dto;
        }
        List<FormularTemplateTextbausteinDefault> rows =
                defaultsRepository.findByTemplateNameIgnoreCaseOrderByDokumenttypAscPositionAscSortOrderAsc(
                        templateName.trim());

        // Gruppiert: Dokumenttyp -> (Position -> Liste<Long>)
        Map<Dokumenttyp, EnumMap<TextbausteinPosition, List<Long>>> grouped = new EnumMap<>(Dokumenttyp.class);
        for (FormularTemplateTextbausteinDefault row : rows) {
            if (row.getTextbaustein() == null || row.getTextbaustein().getId() == null) continue;
            grouped
                    .computeIfAbsent(row.getDokumenttyp(), k -> new EnumMap<>(TextbausteinPosition.class))
                    .computeIfAbsent(row.getPosition(), k -> new ArrayList<>())
                    .add(row.getTextbaustein().getId());
        }

        grouped.forEach((typ, byPos) -> {
            FormularTextbausteinDefaultsDto.Entry entry = new FormularTextbausteinDefaultsDto.Entry();
            entry.setDokumenttyp(typ.getLabel());
            entry.setVortextIds(byPos.getOrDefault(TextbausteinPosition.VOR, List.of()));
            entry.setNachtextIds(byPos.getOrDefault(TextbausteinPosition.NACH, List.of()));
            dto.getEntries().add(entry);
        });

        return dto;
    }

    /** Ersetzt alle Defaults einer Vorlage durch die im DTO angegebenen Werte. */
    @Transactional
    public void replaceDefaults(String templateName, FormularTextbausteinDefaultsDto dto) {
        if (!StringUtils.hasText(templateName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vorlagenname fehlt.");
        }
        String name = templateName.trim();
        defaultsRepository.deleteByTemplateNameIgnoreCase(name);
        if (dto == null || dto.getEntries() == null) {
            return;
        }

        for (FormularTextbausteinDefaultsDto.Entry entry : dto.getEntries()) {
            if (entry == null || !StringUtils.hasText(entry.getDokumenttyp())) continue;
            Dokumenttyp typ = Dokumenttyp.fromLabel(entry.getDokumenttyp());
            saveAll(name, typ, TextbausteinPosition.VOR, entry.getVortextIds());
            saveAll(name, typ, TextbausteinPosition.NACH, entry.getNachtextIds());
        }
    }

    /** Wird beim Loeschen oder Umbenennen einer Vorlage aufgerufen. */
    @Transactional
    public void deleteForTemplate(String templateName) {
        if (!StringUtils.hasText(templateName)) return;
        defaultsRepository.deleteByTemplateNameIgnoreCase(templateName.trim());
    }

    /**
     * Kopiert alle Defaults einer Vorlage auf einen neuen Namen
     * (z.B. nach Rename oder Copy einer Briefpapier-Vorlage).
     */
    @Transactional
    public void renameTemplate(String oldName, String newName) {
        if (!StringUtils.hasText(oldName) || !StringUtils.hasText(newName)) return;
        if (oldName.equalsIgnoreCase(newName)) return;

        List<FormularTemplateTextbausteinDefault> rows =
                defaultsRepository.findByTemplateNameIgnoreCaseOrderByDokumenttypAscPositionAscSortOrderAsc(oldName.trim());
        defaultsRepository.deleteByTemplateNameIgnoreCase(newName.trim());
        for (FormularTemplateTextbausteinDefault row : rows) {
            FormularTemplateTextbausteinDefault copy = new FormularTemplateTextbausteinDefault();
            copy.setTemplateName(newName.trim());
            copy.setDokumenttyp(row.getDokumenttyp());
            copy.setPosition(row.getPosition());
            copy.setTextbaustein(row.getTextbaustein());
            copy.setSortOrder(row.getSortOrder());
            defaultsRepository.save(copy);
        }
        defaultsRepository.deleteByTemplateNameIgnoreCase(oldName.trim());
    }

    /**
     * Liefert die Default-Textbausteine fuer einen konkreten Dokumenttyp einer Vorlage,
     * mit den vollstaendigen Textbaustein-Objekten in der definierten Reihenfolge.
     * Wird vom DocumentEditor beim Neuanlegen / Umwandeln aufgerufen.
     */
    @Transactional(readOnly = true)
    public DefaultsForDokumenttyp loadForDokumenttyp(String templateName, String dokumenttyp) {
        if (!StringUtils.hasText(templateName) || !StringUtils.hasText(dokumenttyp)) {
            return new DefaultsForDokumenttyp(List.of(), List.of());
        }
        Dokumenttyp typ;
        try {
            typ = Dokumenttyp.fromLabel(dokumenttyp.trim());
        } catch (IllegalArgumentException e) {
            return new DefaultsForDokumenttyp(List.of(), List.of());
        }

        List<Textbaustein> vor = defaultsRepository
                .findByTemplateNameIgnoreCaseAndDokumenttypAndPositionOrderBySortOrderAsc(
                        templateName.trim(), typ, TextbausteinPosition.VOR)
                .stream().map(FormularTemplateTextbausteinDefault::getTextbaustein)
                .filter(Objects::nonNull).toList();

        List<Textbaustein> nach = defaultsRepository
                .findByTemplateNameIgnoreCaseAndDokumenttypAndPositionOrderBySortOrderAsc(
                        templateName.trim(), typ, TextbausteinPosition.NACH)
                .stream().map(FormularTemplateTextbausteinDefault::getTextbaustein)
                .filter(Objects::nonNull).toList();

        return new DefaultsForDokumenttyp(vor, nach);
    }

    private void saveAll(String templateName, Dokumenttyp typ, TextbausteinPosition pos, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        int order = 0;
        for (Long id : ids) {
            if (id == null) continue;
            Textbaustein tb = textbausteinRepository.findById(id).orElse(null);
            if (tb == null) continue; // unbekannte ID stillschweigend ueberspringen
            FormularTemplateTextbausteinDefault row = new FormularTemplateTextbausteinDefault();
            row.setTemplateName(templateName);
            row.setDokumenttyp(typ);
            row.setPosition(pos);
            row.setTextbaustein(tb);
            row.setSortOrder(order++);
            defaultsRepository.save(row);
        }
    }

    public record DefaultsForDokumenttyp(List<Textbaustein> vortexte, List<Textbaustein> nachtexte) {}
}
