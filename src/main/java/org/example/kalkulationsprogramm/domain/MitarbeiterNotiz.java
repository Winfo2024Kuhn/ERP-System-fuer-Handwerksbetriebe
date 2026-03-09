package org.example.kalkulationsprogramm.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class MitarbeiterNotiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String inhalt;

    @Column(nullable = false)
    private LocalDateTime erstelltAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id")
    @JsonBackReference
    private Mitarbeiter mitarbeiter;

    @PrePersist
    public void prePersist() {
        if (erstelltAm == null) {
            erstelltAm = LocalDateTime.now();
        }
    }
}
