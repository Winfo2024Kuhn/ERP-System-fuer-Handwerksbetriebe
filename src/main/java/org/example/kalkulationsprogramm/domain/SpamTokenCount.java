package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Speichert Token-Frequenzen für den Multinomial Naive Bayes Spam-Klassifikator.
 * Jedes Token hat separate Zähler für Spam- und Ham-Vorkommen.
 */
@Entity
@Table(name = "spam_token_count", indexes = {
        @Index(name = "idx_spam_token_unique", columnList = "token", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class SpamTokenCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @Column(nullable = false)
    private int spamCount = 0;

    @Column(nullable = false)
    private int hamCount = 0;

    public SpamTokenCount(String token) {
        this.token = token;
    }
}
