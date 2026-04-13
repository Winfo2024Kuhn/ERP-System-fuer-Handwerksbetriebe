package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Globale Statistiken für das Naive Bayes Spam-Modell.
 * Speichert Zähler wie total_spam und total_ham (Anzahl trainierter Dokumente).
 */
@Entity
@Table(name = "spam_model_stats")
@Getter
@Setter
@NoArgsConstructor
public class SpamModelStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String statKey;

    @Column(nullable = false)
    private long statValue = 0;
}
