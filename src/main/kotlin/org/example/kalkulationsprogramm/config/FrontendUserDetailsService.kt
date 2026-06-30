package org.example.kalkulationsprogramm.config

import org.example.kalkulationsprogramm.domain.FrontendUserRole
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class FrontendUserDetailsService(
    private val repository: FrontendUserProfileRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val profile = repository.findByUsernameIgnoreCase(username)
            .orElseThrow { UsernameNotFoundException("Benutzer nicht gefunden.") }

        if (profile.passwordHash.isNullOrBlank()) {
            throw UsernameNotFoundException("Benutzer hat kein Login-Passwort.")
        }

        val roles = LinkedHashSet(profile.roleSet)
        if (roles.isEmpty()) {
            roles.add(FrontendUserRole.USER)
        }

        return FrontendUserPrincipal(
            profile.id,
            profile.username,
            profile.displayName,
            profile.passwordHash,
            profile.active,
            roles
        )
    }
}
