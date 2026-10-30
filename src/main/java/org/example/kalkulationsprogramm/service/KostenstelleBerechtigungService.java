package org.example.kalkulationsprogramm.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Recht „Kostenstellen verwalten“ (anlegen, ändern, löschen, wiederherstellen): Administratoren dürfen es immer. Alle anderen nur,
 * wenn ihr aktives Profil mit einem aktiven Mitarbeiter verknüpft ist, der in einer Abteilung mit {@code darfKostenstellenVerwalten}
 * ist (Einstellungen → Abteilungen & Rechte). Ansehen dürfen alle Angemeldeten.
 */
@Service
@RequiredArgsConstructor
public class KostenstelleBerechtigungService {

    private final AbteilungsRechtPruefung rechtPruefung;

    public boolean darfVerwalten(Authentication authentication) {
        return rechtPruefung.adminOderAbteilungDarf(authentication, a -> Boolean.TRUE.equals(a.getDarfKostenstellenVerwalten()));
    }

    /** Wirft 403, wenn der Benutzer Kostenstellen nicht verwalten darf. */
    public void verlange(Authentication authentication) {
        if (!darfVerwalten(authentication)) {
            throw new AccessDeniedException("Du darfst Kostenstellen nicht verwalten.");
        }
    }
}
