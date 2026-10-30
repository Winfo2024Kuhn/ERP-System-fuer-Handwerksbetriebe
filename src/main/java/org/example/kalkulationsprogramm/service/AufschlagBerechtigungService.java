package org.example.kalkulationsprogramm.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Recht „Aufschläge ändern“: Administratoren dürfen es immer. Alle anderen nur, wenn ihr aktives Profil mit einem aktiven Mitarbeiter verknüpft ist,
 * der in einer Abteilung mit {@code darfAufschlaegeAendern} ist (Einstellungen → Abteilungen & Rechte).
 */
@Service
@RequiredArgsConstructor
public class AufschlagBerechtigungService {

    private final AbteilungsRechtPruefung rechtPruefung;

    public boolean darfAendern(Authentication authentication) {
        return rechtPruefung.adminOderAbteilungDarf(authentication, a -> Boolean.TRUE.equals(a.getDarfAufschlaegeAendern()));
    }

    /** Wirft 403, wenn der Benutzer die Aufschläge nicht ändern darf. */
    public void verlange(Authentication authentication) {
        if (!darfAendern(authentication)) {
            throw new AccessDeniedException("Du darfst die Aufschläge nicht ändern.");
        }
    }
}
