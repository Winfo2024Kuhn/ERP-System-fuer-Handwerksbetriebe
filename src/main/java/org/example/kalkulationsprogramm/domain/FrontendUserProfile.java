package org.example.kalkulationsprogramm.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "frontend_user_profile",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_frontend_user_profile_username", columnNames = "username")
    }
)
public class FrontendUserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "short_code", length = 50)
    private String shortCode;

    @Column(name = "username", length = 120)
    private String username;

    @JsonIgnore
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @JsonIgnore
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "frontend_user_profile_role", joinColumns = @JoinColumn(name = "frontend_user_profile_id"))
    @Column(name = "role_name", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Set<FrontendUserRole> roleSet = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "default_signature_id")
    private EmailSignature defaultSignature;

    // Links this PC frontend profile to an employee for document upload tracking
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mitarbeiter_id")
    @JsonIgnoreProperties({"abteilungen", "zeitkonten", "buchungen", "antraege", "dokumente", "hibernateLazyInitializer", "handler"})
    private Mitarbeiter mitarbeiter;

    @Transient
    @JsonProperty("roles")
    public List<String> getRoles() {
        LinkedHashSet<String> roles = new LinkedHashSet<>();

        if (roleSet != null) {
            roleSet.stream()
                    .map(Enum::name)
                    .forEach(roles::add);
        }

        if (mitarbeiter != null) {
            mitarbeiter.getAbteilungen().stream()
                    .map(Abteilung::getName)
                    .forEach(roles::add);
        }

        return new ArrayList<>(roles);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<FrontendUserRole> getRoleSet() {
        return roleSet;
    }

    public void setRoleSet(Set<FrontendUserRole> roleSet) {
        this.roleSet = roleSet == null ? new LinkedHashSet<>() : new LinkedHashSet<>(roleSet);
    }

    public boolean hasRole(FrontendUserRole role) {
        return role != null && roleSet != null && roleSet.contains(role);
    }

    public EmailSignature getDefaultSignature() {
        return defaultSignature;
    }

    public void setDefaultSignature(EmailSignature defaultSignature) {
        this.defaultSignature = defaultSignature;
    }

    public Mitarbeiter getMitarbeiter() {
        return mitarbeiter;
    }

    public void setMitarbeiter(Mitarbeiter mitarbeiter) {
        this.mitarbeiter = mitarbeiter;
    }
}
