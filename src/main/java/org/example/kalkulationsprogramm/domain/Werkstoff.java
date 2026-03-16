package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
    name = "werkstoff",
    uniqueConstraints = @UniqueConstraint(name = "uk_werkstoff_name", columnNames = "name")
)
public class Werkstoff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", unique = true)
    private String name;

    @OneToMany(mappedBy = "werkstoff")
    private List<Artikel> artikel = new ArrayList<>();
}

