package org.example.kalkulationsprogramm.domain.firmendokumente;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** Ein Ordner im Baum der Firmendokumente; {@code parent == null} = oberste Ebene. */
@Getter
@Setter
@Entity
@Table(name = "firmendokument_ordner")
public class FirmendokumentOrdner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private FirmendokumentOrdner parent;

    /**
     * Nur am obersten Ordner von Bedeutung: {@code true} = nur die eingetragenen Abteilungen
     * ({@link FirmendokumentOrdnerRecht}) und Admins sehen bzw. ändern diesen Zweig. {@code false} = alle.
     */
    @Column(name = "rechte_eingeschraenkt", nullable = false)
    private boolean rechteEingeschraenkt;

    @Column(name = "angelegt_am", nullable = false)
    private LocalDateTime angelegtAm;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
