package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ein einzelner Lieferant einer Preisanfrage. Hält den individuellen Token,
 * die Versand-Metadaten und den Status des Angebots dieses Lieferanten.
 */
@Entity
@Table(name = "preisanfrage_lieferant")
@Getter
@Setter
@NoArgsConstructor
public class PreisanfrageLieferant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preisanfrage_id", nullable = false)
    private Preisanfrage preisanfrage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lieferant_id", nullable = false)
    private Lieferanten lieferant;

    @Column(nullable = false, unique = true, length = 40)
    private String token;

    @Column(name = "versendet_an", length = 255)
    private String versendetAn;

    @Column(name = "versendet_am")
    private LocalDateTime versendetAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outgoing_email_id")
    private Email outgoingEmail;

    @Column(name = "outgoing_message_id", length = 512)
    private String outgoingMessageId;

    @Column(name = "antwort_erhalten_am")
    private LocalDateTime antwortErhaltenAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "antwort_email_id")
    private Email antwortEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PreisanfrageLieferantStatus status = PreisanfrageLieferantStatus.VORBEREITET;

    @OneToMany(mappedBy = "preisanfrageLieferant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PreisanfrageAngebot> angebote = new ArrayList<>();
}
