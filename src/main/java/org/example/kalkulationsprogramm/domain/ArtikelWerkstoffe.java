package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
public class ArtikelWerkstoffe extends Artikel
{
    @Column(name = "masse_pro_meter")
    private BigDecimal masse;
    private BigDecimal mantelflaeche;
    private boolean geschliffen;
    int hoehe;
    int breite;

}
