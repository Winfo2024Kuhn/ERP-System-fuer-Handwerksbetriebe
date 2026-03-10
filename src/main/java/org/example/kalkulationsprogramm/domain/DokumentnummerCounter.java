package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dokumentnummer_counter", uniqueConstraints = @UniqueConstraint(columnNames = "month_key"))
@Getter
@Setter
public class DokumentnummerCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_key", nullable = false, length = 10)
    private String monthKey;

    @Column(name = "counter", nullable = false)
    private Long counter;
}
