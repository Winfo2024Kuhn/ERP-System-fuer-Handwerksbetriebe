package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

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

    /**
     * {@code true} wenn dieser Eintrag automatisch vom System via
     * {@code AusgangsGeschaeftsDokumentService#erstelleOffenenPostenEintrag} erzeugt wurde.
     * Nur systemgenerierte Rechnungen erhalten automatische Zahlungserinnerungen per E-Mail.
     * Manuell im Offene-Posten-Editor erfasste Einträge haben dieses Flag {@code false}.
     */
    @Column(name = "system_generiert", nullable = false)
    private boolean systemGeneriert = false;

    /** Brutto der gebuchten Kunden-Gutschriften zu dieser Rechnung (Issue #223); 0 = keine. */
    @Column(name = "gutgeschrieben", nullable = false, precision = 38, scale = 2)
    private BigDecimal gutgeschrieben = BigDecimal.ZERO;

    /** Was der Kunde noch schuldet: Rechnungsbetrag minus Gutschriften; null ohne Betrag. */
    public BigDecimal getOffenerBetrag() {
        if (bruttoBetrag == null) {
            return null;
        }
        return gutgeschrieben == null ? bruttoBetrag : bruttoBetrag.subtract(gutgeschrieben);
    }

}
