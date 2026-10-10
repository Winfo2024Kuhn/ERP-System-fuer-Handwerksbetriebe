package org.example.kalkulationsprogramm.dto.Anfrage;

import java.time.LocalDate;

/**
 * Anfrage, wie sie die Handy-App braucht: Bauvorhaben, Kunde zum Anrufen und Anfahren, Baustelle.
 * Bewusst ohne Betrag, E-Mails und interne Nummern – die App ist auch übers Internet erreichbar.
 */
public record AnfrageMobilDto(
        Long id,
        String bauvorhaben,
        String kundenName,
        String kundenStrasse,
        String kundenPlz,
        String kundenOrt,
        String kundenTelefon,
        String kundenMobiltelefon,
        String projektStrasse,
        String projektPlz,
        String projektOrt,
        String bildUrl,
        LocalDate anlegedatum,
        boolean abgeschlossen) {

    public static AnfrageMobilDto von(AnfrageResponseDto dto) {
        return new AnfrageMobilDto(dto.getId(), dto.getBauvorhaben(), dto.getKundenName(), dto.getKundenStrasse(),
                dto.getKundenPlz(), dto.getKundenOrt(), dto.getKundenTelefon(), dto.getKundenMobiltelefon(),
                dto.getProjektStrasse(), dto.getProjektPlz(), dto.getProjektOrt(), dto.getBildUrl(),
                dto.getAnlegedatum(), dto.isAbgeschlossen());
    }
}
