package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
public class AngebotGeschaeftsdokument extends AngebotDokument {
    @Column(nullable = false)
    private String dokumentid;
    @Column(nullable = false)
    private String geschaeftsdokumentart;
    private BigDecimal bruttoBetrag;
}
