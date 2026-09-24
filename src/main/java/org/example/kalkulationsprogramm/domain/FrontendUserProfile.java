package org.example.kalkulationsprogramm.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;

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

    @JsonIgnore
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "frontend_user_einkauf_recht", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "recht", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<EinkaufBerechtigung> einkaufBerechtigungen = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "default_signature_id")
    private EmailSignature defaultSignature;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "email_absender_id")
    private EmailAbsender emailAbsender;

    // Links this PC frontend profile to an employee for document upload tracking
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mitarbeiter_id")
    // Nur die von der Benutzerverwaltung genutzten Felder serialisieren – alles andere
    // (Krankenkasse als Lazy-Proxy → 500, Lohn- und Personaldaten) bleibt draußen.
    @JsonIncludeProperties({"id", "vorname", "nachname"})
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

    public Set<EinkaufBerechtigung> getEinkaufBerechtigungen() {
        return einkaufBerechtigungen == null ? Set.of() : Set.copyOf(einkaufBerechtigungen);
    }

    public void setEinkaufBerechtigungen(Set<EinkaufBerechtigung> einkaufBerechtigungen) {
        this.einkaufBerechtigungen = einkaufBerechtigungen == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(einkaufBerechtigungen);
    }

    public EmailSignature getDefaultSignature() {
        return defaultSignature;
    }

    public void setDefaultSignature(EmailSignature defaultSignature) {
        this.defaultSignature = defaultSignature;
    }

    public EmailAbsender getEmailAbsender() {
        return emailAbsender;
    }

    public void setEmailAbsender(EmailAbsender emailAbsender) {
        this.emailAbsender = emailAbsender;
    }

    public Mitarbeiter getMitarbeiter() {
        return mitarbeiter;
    }

    public void setMitarbeiter(Mitarbeiter mitarbeiter) {
        this.mitarbeiter = mitarbeiter;
    }
}
