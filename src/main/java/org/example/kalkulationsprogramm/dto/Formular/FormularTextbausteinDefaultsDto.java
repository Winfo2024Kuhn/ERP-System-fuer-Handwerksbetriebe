package org.example.kalkulationsprogramm.dto.Formular;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Default-Textbausteine fuer eine bestimmte Vorlage.
 * Pro Dokumenttyp je eine Liste von Vortext-IDs (vor den Leistungen) und
 * Nachtext-IDs (nach den Leistungen). Die Reihenfolge in den Listen
 * bestimmt die Reihenfolge der Bausteine im erzeugten Dokument.
 */
@Getter
@Setter
public class FormularTextbausteinDefaultsDto {

    /** Liste je Dokumenttyp. */
    private List<Entry> entries = new ArrayList<>();

    @Getter
    @Setter
    public static class Entry {
        /** Dokumenttyp-Label (z.B. "Angebot", "Rechnung"). */
        private String dokumenttyp;
        /** Textbaustein-IDs vor den Leistungen, in gewuenschter Reihenfolge. */
        private List<Long> vortextIds = new ArrayList<>();
        /** Textbaustein-IDs nach den Leistungen, in gewuenschter Reihenfolge. */
        private List<Long> nachtextIds = new ArrayList<>();
    }
}
