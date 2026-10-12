package org.example.kalkulationsprogramm.config;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FrontendUserDetailsService implements UserDetailsService {

    private final FrontendUserProfileRepository repository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        FrontendUserProfile profile = repository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Benutzer nicht gefunden."));

        if (profile.getPasswordHash() == null || profile.getPasswordHash().isBlank()) {
            throw new UsernameNotFoundException("Benutzer hat kein Login-Passwort.");
        }

        return new FrontendUserPrincipal(
                profile.getId(),
                profile.getUsername(),
                profile.getDisplayName(),
                profile.getPasswordHash(),
                profile.isActive(),
                mitStandardRolle(profile.getRoleSet())
        );
    }

    /**
     * Der angemeldete Benutzer mit den Rechten, die jetzt in der Datenbank stehen.
     *
     * <p>Die Sitzung merkt sich die Rollen von der Anmeldung. Ändert ein Admin sie danach
     * (Admin-Recht vergeben oder entziehen), gilt das sonst erst nach der nächsten Anmeldung.
     *
     * @return derselbe Principal, wenn sich nichts geändert hat; ein neuer mit den aktuellen
     *         Rollen, wenn doch; leer, wenn der Benutzer gelöscht oder deaktiviert wurde
     */
    public Optional<FrontendUserPrincipal> aktuellerStand(FrontendUserPrincipal angemeldet) {
        boolean aktiv = repository.findAktivById(angemeldet.getId()).orElse(false);
        if (!aktiv) {
            return Optional.empty();
        }
        Set<FrontendUserRole> rollen = mitStandardRolle(repository.findRollenById(angemeldet.getId()));
        if (rollen.equals(angemeldet.getRoles())) {
            return Optional.of(angemeldet);
        }
        return Optional.of(new FrontendUserPrincipal(
                angemeldet.getId(),
                angemeldet.getUsername(),
                angemeldet.getDisplayName(),
                angemeldet.getPassword(),
                true,
                rollen));
    }

    /** Ohne eingetragene Rolle ist ein Benutzer ein normaler Benutzer. */
    private static Set<FrontendUserRole> mitStandardRolle(Set<FrontendUserRole> eingetragen) {
        Set<FrontendUserRole> rollen = new LinkedHashSet<>(eingetragen == null ? Set.of() : eingetragen);
        if (rollen.isEmpty()) {
            rollen.add(FrontendUserRole.USER);
        }
        return rollen;
    }
}
