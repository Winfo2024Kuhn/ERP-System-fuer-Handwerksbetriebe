package org.example.kalkulationsprogramm.dto.Abwesenheitsnotiz;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.OutOfOfficeSchedule;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachRefDto;

/** Abwesenheitsnotiz für die E-Mail-Einstellungen – Feldnamen wie bisher, plus Postfächer. */
public record AbwesenheitsnotizDto(
        Long id,
        String title,
        LocalDate startAt,
        LocalDate endAt,
        boolean active,
        String subjectTemplate,
        String bodyTemplate,
        SignaturRef signature,
        List<PostfachRefDto> postfaecher) {

    /** Kurzform der angehängten Signatur. */
    public record SignaturRef(Long id, String name) {
    }

    public static AbwesenheitsnotizDto von(OutOfOfficeSchedule notiz) {
        SignaturRef signatur = notiz.getSignature() == null ? null
                : new SignaturRef(notiz.getSignature().getId(), notiz.getSignature().getName());
        List<PostfachRefDto> postfaecher = notiz.getPostfaecher().stream()
                .sorted(Comparator.comparingInt(EmailAbsender::getSortierung).thenComparing(EmailAbsender::getId))
                .map(PostfachRefDto::von)
                .toList();
        return new AbwesenheitsnotizDto(notiz.getId(), notiz.getTitle(), notiz.getStartAt(), notiz.getEndAt(),
                notiz.isActive(), notiz.getSubjectTemplate(), notiz.getBodyTemplate(), signatur, postfaecher);
    }
}
