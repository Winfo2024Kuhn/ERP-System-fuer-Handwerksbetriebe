package org.example.kalkulationsprogramm.dto;

import java.util.List;

import lombok.Data;

/**
 * Repräsentiert den vollständigen Konversationsverlauf (Thread) einer E-Mail.
 * Enthält alle E-Mails chronologisch sortiert (älteste zuerst).
 */
@Data
public class EmailThreadDto {
    /** ID der Wurzel-Email (älteste im Thread). */
    private Long rootEmailId;

    /** ID der ursprünglich angeklickten Email (wird im UI hervorgehoben und auto-expandiert). */
    private Long focusedEmailId;

    /** Alle E-Mails des Threads, chronologisch sortiert (sentAt ASC). */
    private List<EmailThreadEntryDto> emails;
}
