package org.example.kalkulationsprogramm.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "frontend_user_profile")
public class FrontendUserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "short_code", length = 50)
    private String shortCode;

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
        if (mitarbeiter != null) {
            return mitarbeiter.getAbteilungen().stream()
                    .map(Abteilung::getName)
                    .toList();
        }
        return new ArrayList<>();
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
