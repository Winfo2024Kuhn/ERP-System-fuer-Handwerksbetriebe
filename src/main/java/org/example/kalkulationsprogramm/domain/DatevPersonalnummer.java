package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "datev_personalnummer", uniqueConstraints = @UniqueConstraint(name = "uk_datev_personalnummer_normalisiert", columnNames = "normalisiert"))
@Getter @Setter
public class DatevPersonalnummer {
 @Id private Long mitarbeiterId;
 @Column(nullable = false, length = 5) private String personalnummer;
 @Column(nullable = false, length = 5) private String normalisiert;
}
