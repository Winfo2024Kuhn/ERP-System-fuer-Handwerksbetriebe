package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Bild zu einer Anfrage-Notiz.
 * Ermöglicht 1:n Beziehung zwischen Notiz und Bildern.
 */
@Getter
@Setter
@Entity
@Table(name = "anfrage_notiz_bild")
public class AnfrageNotizBild {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notiz_id", nullable = false)
    private AnfrageNotiz notiz;

    @Column(nullable = false)
    private String gespeicherterDateiname;

    @Column
    private String originalDateiname;

    @Column
    private String dateityp;

    @Column(nullable = false)
    private LocalDateTime erstelltAm;

    @PrePersist
    protected void onCreate() {
        if (erstelltAm == null) {
            erstelltAm = LocalDateTime.now();
        }
    }
}
