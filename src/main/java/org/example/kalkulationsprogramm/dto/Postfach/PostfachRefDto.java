package org.example.kalkulationsprogramm.dto.Postfach;

import org.example.kalkulationsprogramm.domain.EmailAbsender;

/** Kurzform eines Postfachs für Mail-Listen (Schild an der Mail) und den festen Absender. */
public record PostfachRefDto(Long id, String emailAdresse, String anzeigename) {

    public static PostfachRefDto von(EmailAbsender postfach) {
        return postfach == null ? null
                : new PostfachRefDto(postfach.getId(), postfach.getEmailAdresse(), postfach.getAnzeigename());
    }
}
