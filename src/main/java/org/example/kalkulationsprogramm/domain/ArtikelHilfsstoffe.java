package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class ArtikelHilfsstoffe extends Artikel
{
    @Column(name = "masse_pro_meter")
    private Long anzugskraefte;

}
