package org.example.kalkulationsprogramm.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class FrontendUserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final boolean active;
    private final Set<FrontendUserRole> roles;
    private final Set<GrantedAuthority> authorities;

    public FrontendUserPrincipal(Long id,
                                 String username,
                                 String displayName,
                                 String passwordHash,
                                 boolean active,
                                 Set<FrontendUserRole> roles) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.active = active;
        this.roles = roles == null ? Set.of() : Set.copyOf(roles);
        this.authorities = mapAuthorities(this.roles);
    }

    /**
     * Der angemeldete PC-Benutzer aus der Spring-Security-Anmeldung, sonst {@code null}.
     */
    public static FrontendUserPrincipal aus(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof FrontendUserPrincipal p) {
            return p;
        }
        return null;
    }

    /**
     * ID des angemeldeten Benutzers (FrontendUserProfile) für Audit-Einträge, sonst {@code null}.
     * Der Bearbeiter im GoBD-Protokoll kommt immer von hier, nie aus einem Request-Parameter.
     */
    public static Long idAus(Authentication authentication) {
        FrontendUserPrincipal p = aus(authentication);
        return p != null ? p.getId() : null;
    }

    private Set<GrantedAuthority> mapAuthorities(Set<FrontendUserRole> roles) {
        LinkedHashSet<GrantedAuthority> mapped = new LinkedHashSet<>();
        for (FrontendUserRole role : roles) {
            mapped.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        }
        return mapped;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<FrontendUserRole> getRoles() {
        return roles;
    }

    public boolean hasRole(FrontendUserRole role) {
        return role != null && roles.contains(role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
