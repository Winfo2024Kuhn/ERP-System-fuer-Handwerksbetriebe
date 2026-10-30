package org.example.kalkulationsprogramm.service;

import java.util.function.Predicate;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Gemeinsame Prüfung für Rechte, die in Einstellungen → Abteilungen & Rechte je Abteilung vergeben werden und die
 * Administratoren immer haben (z. B. „Aufschläge ändern“, „Kostenstellen verwalten“). Alle anderen brauchen ein aktives
 * Profil, das mit einem aktiven Mitarbeiter verknüpft ist, der in mindestens einer Abteilung mit dem Recht ist.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AbteilungsRechtPruefung {

    private final FrontendUserProfileRepository profileRepository;

    public boolean adminOderAbteilungDarf(Authentication authentication, Predicate<Abteilung> recht) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof FrontendUserPrincipal principal) || !principal.isEnabled()) {
            return false;
        }
        if (principal.hasRole(FrontendUserRole.ADMIN)) {
            return true;
        }
        if (principal.getId() == null) {
            return false;
        }
        return profileRepository.findById(principal.getId()).filter(p -> hatRecht(p, recht)).isPresent();
    }

    private static boolean hatRecht(FrontendUserProfile p, Predicate<Abteilung> recht) {
        if (!p.isActive() || p.getMitarbeiter() == null) {
            return false;
        }
        var m = p.getMitarbeiter();
        return m.getId() != null && m.getArt() == MitarbeiterArt.MENSCH && Boolean.TRUE.equals(m.getAktiv())
                && m.getAbteilungen().stream().anyMatch(recht);
    }
}
