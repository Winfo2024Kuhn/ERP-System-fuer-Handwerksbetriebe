package org.example.kalkulationsprogramm.service.telefon;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Recht "Anrufe & Anrufbeantworter": Ein Benutzer darf Telefondaten sehen und
 * bekommt das Anruf-Fenster, wenn sein aktives Profil mit einem aktiven
 * Mitarbeiter verknüpft ist, der in einer Abteilung mit {@code darfTelefonSehen}
 * ist. ADMIN bekommt das Recht nicht automatisch.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TelefonBerechtigungService {

    private final FrontendUserProfileRepository profileRepository;

    public boolean darfTelefonSehen(Authentication authentication) {
        return berechtigtesProfil(authentication).isPresent();
    }

    public boolean darfTelefonSehen(Long profilId) {
        if (profilId == null) {
            return false;
        }
        return profileRepository.findById(profilId).filter(TelefonBerechtigungService::hatRecht).isPresent();
    }

    /** Liefert das Profil oder wirft 403. */
    public FrontendUserProfile verlange(Authentication authentication) {
        return berechtigtesProfil(authentication).orElseThrow(() ->
                new AccessDeniedException("Du darfst Anrufe und Anrufbeantworter nicht sehen."));
    }

    private Optional<FrontendUserProfile> berechtigtesProfil(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof FrontendUserPrincipal principal)
                || !principal.isEnabled() || principal.getId() == null) {
            return Optional.empty();
        }
        return profileRepository.findById(principal.getId()).filter(TelefonBerechtigungService::hatRecht);
    }

    private static boolean hatRecht(FrontendUserProfile p) {
        if (!p.isActive() || p.getMitarbeiter() == null) {
            return false;
        }
        var m = p.getMitarbeiter();
        return m.getId() != null && m.getArt() == MitarbeiterArt.MENSCH && Boolean.TRUE.equals(m.getAktiv())
                && m.getAbteilungen().stream().anyMatch(a -> Boolean.TRUE.equals(a.getDarfTelefonSehen()));
    }
}
