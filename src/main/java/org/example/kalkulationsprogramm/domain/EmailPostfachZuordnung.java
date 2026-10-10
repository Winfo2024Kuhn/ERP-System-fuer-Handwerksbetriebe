package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Eine Mail liegt in einem Postfach. Dieselbe Mail kann in mehreren Postfächern
 * liegen (gleichzeitig an info@ und max@ geschickt), wird aber nur einmal als
 * {@link Email} gespeichert. Ordner und UID merken sich, wo sie im jeweiligen
 * Postfach auf dem Server liegt – zum endgültigen Löschen.
 */
@Getter
@Setter
@Entity
@Table(
        name = "email_postfach_zuordnung",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_email_postfach_zuordnung", columnNames = { "email_id", "postfach_id" })
        }
)
public class EmailPostfachZuordnung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email_id", nullable = false)
    private Email email;

    /** Postfach (technisch ein {@link EmailAbsender} mit Zugang). Klein und oft gebraucht, daher eager. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "postfach_id", nullable = false)
    private EmailAbsender postfach;

    @Column(name = "imap_ordner", length = 255)
    private String imapOrdner;

    @Column(name = "imap_uid")
    private Long imapUid;
}
