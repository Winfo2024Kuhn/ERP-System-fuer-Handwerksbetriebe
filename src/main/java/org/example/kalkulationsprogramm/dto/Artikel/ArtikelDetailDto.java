package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.example.kalkulationsprogramm.domain.PreisQuelle;

/**
 * Vollstaendige Sicht auf einen Artikel fuer die Detailseite.
 *
 * <p>Enthaelt neben den Stammdaten alle Lieferanten mit ihrer jeweiligen
 * Artikelnummer und ihrem aktuellen Preis sowie den Preisverlauf. Damit laesst
 * sich nachvollziehen, wie sich ein Preis entwickelt hat und worauf er beruht.
 */
@Getter
@Setter
public class ArtikelDetailDto {

    /** Stammdaten des Artikels, einschliesslich der technischen Merkmale. */
    private ArtikelResponseDto artikel;

    /** Je Lieferant ein Eintrag mit dessen Artikelnummer und aktuellem Preis. */
    private List<LieferantEintragDto> lieferanten = new ArrayList<>();

    /** Alle Preisstaende, juengster zuerst - Grundlage der Verlaufsanzeige. */
    private List<PreisstandDto> preisverlauf = new ArrayList<>();

    @Getter
    @Setter
    public static class LieferantEintragDto {
        private Long preisId;
        private Long lieferantId;
        private String lieferantName;
        /** Die Artikelnummer, unter der dieser Lieferant den Artikel fuehrt. */
        private String externeArtikelnummer;
        private BigDecimal preis;
        private Date preisDatum;
        private PreisQuelle quelle;
        private String notiz;
        /** Kennzeichnet den derzeit guenstigsten Lieferanten. */
        private boolean guenstigster;
        /** Abweichung zum guenstigsten Preis in Prozent - 0 beim guenstigsten. */
        private BigDecimal aufschlagProzent;
    }

    @Getter
    @Setter
    public static class PreisstandDto {
        private Long preisId;
        private Long lieferantId;
        private String lieferantName;
        private BigDecimal preis;
        private Date preisDatum;
        private PreisQuelle quelle;
        private String notiz;
        /** Ob dieser Stand derzeit gilt. */
        private boolean aktuell;
    }
}
