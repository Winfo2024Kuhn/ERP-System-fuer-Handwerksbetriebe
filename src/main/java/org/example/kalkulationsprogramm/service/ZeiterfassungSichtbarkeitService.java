package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Recht „Zeiterfassung im Menü sehen“: Wer bekommt am PC den Menü-Bereich
 * „Zeiterfassung“ angezeigt?
 *
 * <ul>
 * <li>Administratoren immer,</li>
 * <li>die Geschäftsführung immer,</li>
 * <li>sonst, wenn der verknüpfte, aktive Mitarbeiter in mindestens einer
 * Abteilung mit {@code darfZeiterfassungSehen} ist.</li>
 * </ul>
 * Ein PC-Zugang ohne verknüpften Mitarbeiter sieht den Bereich nur als Admin.
 * Die Handy-App (Stempeln, Urlaub beantragen) ist davon nicht betroffen.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZeiterfassungSichtbarkeitService {

    private final FrontendUserProfileRepository profileRepository;

    public boolean darfZeiterfassungSehen(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof FrontendUserPrincipal principal)
                || !principal.isEnabled()) {
            return false;
        }
        if (principal.hasRole(FrontendUserRole.ADMIN)) {
            return true;
        }
        if (principal.getId() == null) {
            return false;
        }
        return profileRepository.findById(principal.getId())
                .filter(FrontendUserProfile::isActive)
                .map(FrontendUserProfile::getMitarbeiter)
                .filter(ZeiterfassungSichtbarkeitService::istAktiverMensch)
                .map(m -> Boolean.TRUE.equals(m.getIstGeschaeftsfuehrer())
                        || m.getAbteilungen().stream()
                                .anyMatch(a -> Boolean.TRUE.equals(a.getDarfZeiterfassungSehen())))
                .orElse(false);
    }

    private static boolean istAktiverMensch(Mitarbeiter m) {
        return m.getId() != null && m.getArt() == MitarbeiterArt.MENSCH && Boolean.TRUE.equals(m.getAktiv());
    }
}
