package org.example.kalkulationsprogramm.domain.miete;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "verbrauchsgegenstand", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"raum_id", "name"})
})
public class Verbrauchsgegenstand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raum_id", nullable = false)
    private Raum raum;

    @Column(nullable = false)
    private String name;

    private String seriennummer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Verbrauchsart verbrauchsart;

    private String einheit;

    private boolean aktiv = true;

    @OneToMany(mappedBy = "verbrauchsgegenstand", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Zaehlerstand> zaehlerstaende = new ArrayList<>();
}
