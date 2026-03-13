package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "anfrage_geschaeftsdokument")
@Getter
@Setter
public class AnfrageGeschaeftsdokument extends AnfrageDokument {
    @Column(nullable = false)
    private String dokumentid;
    @Column(nullable = false)
    private String geschaeftsdokumentart;
    private BigDecimal bruttoBetrag;
}
