package org.example.kalkulationsprogramm.config;

import java.util.LinkedHashSet;
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

        Set<FrontendUserRole> roles = new LinkedHashSet<>(profile.getRoleSet());
        if (roles.isEmpty()) {
            roles.add(FrontendUserRole.USER);
        }

        return new FrontendUserPrincipal(
                profile.getId(),
                profile.getUsername(),
                profile.getDisplayName(),
                profile.getPasswordHash(),
                profile.isActive(),
                roles
        );
    }
}
