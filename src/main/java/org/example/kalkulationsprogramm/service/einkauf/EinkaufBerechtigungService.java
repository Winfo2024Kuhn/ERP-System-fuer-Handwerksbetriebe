package org.example.kalkulationsprogramm.service.einkauf;

import java.util.LinkedHashSet;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EinkaufBerechtigungService {
    private final FrontendUserProfileRepository profileRepository;

    public Long verlange(Authentication auth, EinkaufBerechtigung recht) {
        FrontendUserProfile profil = aktuellesProfil(auth);
        Set<EinkaufBerechtigung> wirksameRechte = wirksameRechte(profil);
        if (recht == null || !wirksameRechte.contains(recht)) {
            throw new AccessDeniedException("Du hast keine Berechtigung für diese Einkaufsaktion.");
        }
        return profil.getId();
    }

    public Set<EinkaufBerechtigung> rechte(Authentication auth) {
        return Set.copyOf(wirksameRechte(aktuellesProfil(auth)));
    }

    public Set<EinkaufBerechtigung> profilRechte(Long profileId) {
        return Set.copyOf(ladeProfil(profileId).getEinkaufBerechtigungen());
    }

    @Transactional
    public Set<EinkaufBerechtigung> setzeRechte(Long profileId, Set<EinkaufBerechtigung> rechte) {
        FrontendUserProfile profil = ladeProfil(profileId);
        Set<EinkaufBerechtigung> gesetzt = rechte == null ? Set.of() : new LinkedHashSet<>(rechte);
        if (gesetzt.contains(null)) {
            throw new IllegalArgumentException("Die Einkaufsberechtigung ist ungültig.");
        }
        profil.setEinkaufBerechtigungen(gesetzt);
        profileRepository.save(profil);
        return Set.copyOf(gesetzt);
    }

    private FrontendUserProfile aktuellesProfil(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof FrontendUserPrincipal principal)
                || !principal.isEnabled() || principal.getId() == null) {
            throw new AccessDeniedException("Für diese Einkaufsaktion ist eine Anmeldung erforderlich.");
        }
        return profileRepository.findById(principal.getId())
                .filter(FrontendUserProfile::isActive)
                .orElseThrow(() -> new AccessDeniedException("Dein Benutzerprofil ist nicht mehr aktiv."));
    }

    private FrontendUserProfile ladeProfil(Long profileId) {
        if (profileId == null || profileId <= 0) {
            throw new IllegalArgumentException("Das Benutzerprofil ist ungültig.");
        }
        return profileRepository.findById(profileId).orElseThrow(
                () -> new java.util.NoSuchElementException("Benutzerprofil nicht gefunden."));
    }

    private Set<EinkaufBerechtigung> wirksameRechte(FrontendUserProfile profil) {
        if (profil.hasRole(FrontendUserRole.ADMIN)) {
            return Set.of(EinkaufBerechtigung.values());
        }
        return profil.getEinkaufBerechtigungen();
    }
}
