package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Ansprechpartner eines Lieferanten (1:n), z. B. Außendienst, Innendienst, Buchhaltung.
 *
 * <p>Hängt nur einseitig am Lieferanten: Ändern oder Löschen eines Ansprechpartners
 * ändert die {@code @Version} des Lieferanten nicht, es braucht also keine Sperre auf
 * den Lieferanten. Wird der Lieferant gelöscht, löscht die Datenbank die
 * Ansprechpartner mit ({@code ON DELETE CASCADE}, DSGVO).</p>
 *
 * <p>Telefon und Handy fließen in die Anruferkennung und ins FRITZ!Box-Telefonbuch
 * (siehe {@code RufnummernZuordnungService}, {@code TelefonbuchSollListe}).</p>
 */
@Getter
@Setter
@Entity
@Table(name = "lieferant_ansprechpartner",
        indexes = @Index(name = "idx_lieferant_ansprechpartner_lieferant", columnList = "lieferant_id"))
public class LieferantAnsprechpartner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lieferant_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Lieferanten lieferant;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Anrede anrede;

    @Column(length = 100)
    private String vorname;

    @Column(nullable = false, length = 255)
    private String nachname;

    /** Freier Text, z. B. „Außendienst“, „Innendienst“, „Buchhaltung“, „Vertreter“. */
    @Column(length = 100)
    private String aufgabe;

    @Column(length = 50)
    private String telefon;

    @Column(length = 50)
    private String mobiltelefon;

    @Column(length = 255)
    private String email;

    @Column(length = 1000)
    private String notiz;
}
