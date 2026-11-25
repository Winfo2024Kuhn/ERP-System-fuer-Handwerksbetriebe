package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.LieferantAnsprechpartner;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAnsprechpartnerDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAnsprechpartnerKurzDto;
import org.example.kalkulationsprogramm.service.lieferantansprechpartner.LieferantAnsprechpartnerEingabe;
import org.example.kalkulationsprogramm.util.PersonenName;

/** Wandelt Lieferanten-Ansprechpartner zwischen Entity, Eingabe und DTO (rein, ohne Spring). */
public final class LieferantAnsprechpartnerMapper {

    private LieferantAnsprechpartnerMapper() {
    }

    public static LieferantAnsprechpartnerDto toDto(LieferantAnsprechpartner a) {
        LieferantAnsprechpartnerDto dto = new LieferantAnsprechpartnerDto();
        dto.setId(a.getId());
        dto.setAnrede(a.getAnrede() != null ? a.getAnrede().name() : null);
        dto.setVorname(a.getVorname());
        dto.setNachname(a.getNachname());
        dto.setAufgabe(a.getAufgabe());
        dto.setTelefon(a.getTelefon());
        dto.setMobiltelefon(a.getMobiltelefon());
        dto.setEmail(a.getEmail());
        dto.setNotiz(a.getNotiz());
        return dto;
    }

    /** Kurzform für die Handy-App. */
    public static LieferantAnsprechpartnerKurzDto toKurzDto(LieferantAnsprechpartner a) {
        return new LieferantAnsprechpartnerKurzDto(a.getId(), anzeigename(a), a.getAufgabe(), a.getTelefon(),
                a.getMobiltelefon(), a.getEmail());
    }

    /** „Vorname Nachname“, ohne Vorname nur der Nachname. */
    public static String anzeigename(LieferantAnsprechpartner a) {
        return PersonenName.vollerName(a.getVorname(), a.getNachname());
    }

    /** Überschreibt alle Felder mit den geprüften Werten (leere Felder werden geleert). */
    public static void uebernehme(LieferantAnsprechpartnerEingabe.Werte w, LieferantAnsprechpartner a) {
        a.setAnrede(w.anrede());
        a.setVorname(w.vorname());
        a.setNachname(w.nachname());
        a.setAufgabe(w.aufgabe());
        a.setTelefon(w.telefon());
        a.setMobiltelefon(w.mobiltelefon());
        a.setEmail(w.email());
        a.setNotiz(w.notiz());
    }
}
