package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter

public class ProjektGeschaeftsdokument extends ProjektDokument
{
    @Column(nullable = false)
    private String dokumentid;
    @Column(nullable = false)
    private String geschaeftsdokumentart;
    private LocalDate rechnungsdatum;
    private LocalDate faelligkeitsdatum;
    private BigDecimal bruttoBetrag;
    @Column(nullable = false)
    private boolean bezahlt = false;
    @Enumerated(EnumType.STRING)
    private Mahnstufe mahnstufe;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referenz_dokument_id")
    private ProjektGeschaeftsdokument referenzDokument;
    @OneToMany(mappedBy = "referenzDokument", fetch = FetchType.LAZY)
    private List<ProjektGeschaeftsdokument> mahnungen = new ArrayList<>();

}
