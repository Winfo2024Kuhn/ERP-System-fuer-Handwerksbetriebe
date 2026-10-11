package org.example.kalkulationsprogramm.dto.Abwesenheitsnotiz;

import java.time.LocalDate;
import java.util.List;

/**
 * Anlegen ({@code id == null}) oder Ändern einer Abwesenheitsnotiz. Feldnamen wie bisher,
 * neu sind die {@code postfachIds}.
 */
public record AbwesenheitsnotizSpeichernRequest(
        Long id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        Long signatureId,
        Boolean active,
        String subject,
        String message,
        List<Long> postfachIds) {
}
