package org.example.kalkulationsprogramm.dto.Produktkategroie;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;

import lombok.Getter;
import lombok.Setter;

/**
 * Vorschlag einer Produktkategorie inkl. aggregierter Menge,
 * abgeleitet aus den Leistungen einer Auftragsbestätigung oder eines Angebots.
 *
 * Wird beim Anlegen eines Projekts aus einer Anfrage als Vorbelegung verwendet,
 * damit der Nutzer die Kategorien nicht erneut manuell auswählen muss.
 */
@Getter
@Setter
public class KategorieVorschlagDto {
    private Long kategorieId;
    private String bezeichnung;
    private String pfad;
    private Verrechnungseinheit verrechnungseinheit;
    private BigDecimal menge;
    /** "AUFTRAGSBESTAETIGUNG" wenn AB existiert, sonst "ANGEBOT" */
    private String quelle;
}
