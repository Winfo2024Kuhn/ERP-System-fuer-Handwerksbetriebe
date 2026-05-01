package org.example.kalkulationsprogramm.dto.Formular;

import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Textbaustein;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vom DocumentEditor abgerufen, wenn ein neues Dokument angelegt oder umgewandelt wird.
 * Enthaelt die fertigen Textbaustein-Inhalte (HTML), die in der konfigurierten
 * Reihenfolge vor bzw. nach den Leistungen einzufuegen sind.
 */
@Getter
@Setter
@NoArgsConstructor
public class FormularTextbausteinResolvedDto {

    private List<Item> vortexte = new ArrayList<>();
    private List<Item> nachtexte = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private Long id;
        private String name;
        private String html;
        private String beschreibung;

        public static Item from(Textbaustein tb) {
            Item item = new Item();
            item.setId(tb.getId());
            item.setName(tb.getName());
            item.setHtml(tb.getHtml());
            item.setBeschreibung(tb.getBeschreibung());
            return item;
        }
    }

    public static FormularTextbausteinResolvedDto from(List<Textbaustein> vor, List<Textbaustein> nach) {
        FormularTextbausteinResolvedDto dto = new FormularTextbausteinResolvedDto();
        if (vor != null) vor.forEach(tb -> dto.vortexte.add(Item.from(tb)));
        if (nach != null) nach.forEach(tb -> dto.nachtexte.add(Item.from(tb)));
        return dto;
    }
}
