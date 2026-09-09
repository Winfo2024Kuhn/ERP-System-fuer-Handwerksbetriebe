package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonatsabschlussBerechtigungService {
    private final FrontendUserProfileRepository profileRepository;

    public boolean darfMonatAbschliessen(Authentication authentication) {
        return berechtigterAkteur(authentication).isPresent();
    }

    public Mitarbeiter verlangeAkteur(Authentication authentication) {
        return berechtigterAkteur(authentication).orElseThrow(() ->
                new AccessDeniedException("Du darfst keine Monate abschließen oder wieder öffnen"));
    }

    private Optional<Mitarbeiter> berechtigterAkteur(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof FrontendUserPrincipal principal)
                || !principal.isEnabled() || principal.getId() == null) {
            return Optional.empty();
        }
        return profileRepository.findById(principal.getId())
                .filter(p -> p.isActive())
                .map(p -> p.getMitarbeiter())
                .filter(m -> m.getId() != null && m.getArt() == MitarbeiterArt.MENSCH
                        && Boolean.TRUE.equals(m.getAktiv()))
                .filter(m -> m.getAbteilungen().stream()
                        .anyMatch(a -> Boolean.TRUE.equals(a.getDarfMonatAbschliessen())));
    }
}
