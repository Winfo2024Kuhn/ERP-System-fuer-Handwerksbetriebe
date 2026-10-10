package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "datev_konfiguration")
@Getter @Setter
public class DatevKonfiguration {
 @Id private Long id = 1L;
 @Version private Long version = 0L;
 @Column(nullable = false, length = 10) private String ziel = "LODAS";
 @Column(nullable = false, length = 7) private String beraterNr = "";
 @Column(nullable = false, length = 5) private String mandantenNr = "";
 /** SOLL or IST: which hours go to the tax office as "ARBEIT" unless an employee overrides it. */
 @Column(nullable = false, length = 4) private String stundenart = "SOLL";
 @Column(nullable = false, columnDefinition = "TEXT") private String zuordnungenJson = "[]";
 // Guarantees a dirty singleton even when only employee mappings change.
 @Column(nullable = false) private long aenderungszaehler;
}
