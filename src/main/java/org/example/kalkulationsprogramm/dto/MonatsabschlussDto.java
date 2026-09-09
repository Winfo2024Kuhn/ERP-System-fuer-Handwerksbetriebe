package org.example.kalkulationsprogramm.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

public record MonatsabschlussDto(Long mitarbeiterId, int jahr, int monat, boolean festgeschrieben,
        Long version, LocalDateTime festgeschriebenAm, Long festgeschriebenVonMitarbeiterId,
        BigDecimal istStunden, BigDecimal sollStunden, BigDecimal abwesenheitsStunden,
        BigDecimal feiertagsStunden, BigDecimal korrekturStunden, BigDecimal gesamtIst, BigDecimal differenz,
        List<Audit> audit) {
    public record Audit(Long id, String aktion, Long akteurMitarbeiterId, String akteurName, LocalDateTime zeitpunkt) {}
    public record Berechtigung(boolean darfMonatAbschliessen) {}
}
