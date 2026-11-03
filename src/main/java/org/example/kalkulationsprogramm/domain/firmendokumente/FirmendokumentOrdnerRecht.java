package org.example.kalkulationsprogramm.domain.firmendokumente;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Was eine Abteilung in einem eingeschränkten obersten Ordner der Firmendokumente darf (ansehen oder ändern).
 * Gilt für den ganzen Zweig darunter. Gibt es keinen Eintrag, sieht die Abteilung den Zweig nicht.
 */
@Getter
@Setter
@Entity
@Table(name = "firmendokument_ordner_recht",
        uniqueConstraints = @UniqueConstraint(name = "uk_firmendokument_ordner_recht",
                columnNames = { "ordner_id", "abteilung_id" }))
public class FirmendokumentOrdnerRecht {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordner_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FirmendokumentOrdner ordner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "abteilung_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Abteilung abteilung;

    @Enumerated(EnumType.STRING)
    @Column(name = "stufe", nullable = false, length = 20)
    private FirmendokumentZugriffsStufe stufe;
}
