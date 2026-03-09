package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class AngebotDokument implements Dokument
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalDateiname;

    @Column(nullable = false, unique = true)
    private String gespeicherterDateiname;

    private String dateityp;
    private Long dateigroesse;
    private LocalDate uploadDatum;
    private LocalDate emailVersandDatum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DokumentGruppe dokumentGruppe = DokumentGruppe.DIVERSE_DOKUMENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Angebot")
    private Angebot angebot;
}
