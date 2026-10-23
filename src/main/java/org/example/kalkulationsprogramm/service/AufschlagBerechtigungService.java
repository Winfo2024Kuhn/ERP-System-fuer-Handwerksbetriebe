package org.example.kalkulationsprogramm.service;

import java.util.Optional;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Recht „Aufschläge ändern“: Administratoren dürfen es immer. Alle anderen nur, wenn ihr aktives Profil mit einem aktiven Mitarbeiter verknüpft ist,
 * der in einer Abteilung mit {@code darfAufschlaegeAendern} ist (Einstellungen → Abteilungen & Rechte).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AufschlagBerechtigungService {

    private final FrontendUserProfileRepository profileRepository;

    public boolean darfAendern(Authentication authentication) {
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
        Optional<FrontendUserProfile> profil = profileRepository.findById(principal.getId());
        return profil.filter(AufschlagBerechtigungService::hatRecht).isPresent();
    }

    /** Wirft 403, wenn der Benutzer die Aufschläge nicht ändern darf. */
    public void verlange(Authentication authentication) {
        if (!darfAendern(authentication)) {
            throw new AccessDeniedException("Du darfst die Aufschläge nicht ändern.");
        }
    }

    private static boolean hatRecht(FrontendUserProfile p) {
        if (!p.isActive() || p.getMitarbeiter() == null) {
            return false;
        }
        var m = p.getMitarbeiter();
        return m.getId() != null && m.getArt() == MitarbeiterArt.MENSCH && Boolean.TRUE.equals(m.getAktiv())
                && m.getAbteilungen().stream().anyMatch(a -> Boolean.TRUE.equals(a.getDarfAufschlaegeAendern()));
    }
}
