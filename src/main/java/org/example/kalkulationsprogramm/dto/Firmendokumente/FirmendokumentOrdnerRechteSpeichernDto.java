package org.example.kalkulationsprogramm.dto.Firmendokumente;

import java.util.List;

/**
 * Neue Rechte eines obersten Ordners. Fehlende Abteilungen zählen als {@code KEINE}. Die Stufe kommt als Text,
 * damit ein falscher Wert eine verständliche 400-Meldung bekommt (Prüfung im Service).
 */
public record FirmendokumentOrdnerRechteSpeichernDto(Boolean eingeschraenkt, List<Eintrag> abteilungen) {

    public record Eintrag(Long abteilungId, String stufe) {
    }
}
