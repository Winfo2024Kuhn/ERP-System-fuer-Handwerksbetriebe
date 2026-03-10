package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "email_signature")
public class EmailSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Lob
    @Column(name = "html", nullable = false, columnDefinition = "longtext")
    private String html;

    @Transient
    private boolean defaultSignature;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @com.fasterxml.jackson.annotation.JsonManagedReference
    @OneToMany(mappedBy = "signature", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EmailSignatureImage> images = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHtml() { return html; }
    public void setHtml(String html) { this.html = html; }
    public boolean isDefaultSignature() { return defaultSignature; }
    public void setDefaultSignature(boolean defaultSignature) { this.defaultSignature = defaultSignature; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<EmailSignatureImage> getImages() { return images; }
    public void setImages(List<EmailSignatureImage> images) { this.images = images; }
}
