package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "textbaustein")
public class Textbaustein {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "typ", nullable = false, length = 40)
    private TextbausteinTyp typ = TextbausteinTyp.FREITEXT;

    @Column(name = "beschreibung", length = 500)
    private String beschreibung;

    @Column(name = "html", columnDefinition = "longtext")
    private String html;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "textbaustein_placeholder", joinColumns = @JoinColumn(name = "textbaustein_id"))
    @Column(name = "placeholder", length = 120)
    private Set<String> placeholders = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "textbaustein_dokumenttyp_enum", joinColumns = @JoinColumn(name = "textbaustein_id"))
    @Column(name = "dokumenttyp", length = 30)
    private Set<Dokumenttyp> dokumenttypen = new LinkedHashSet<>();

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
