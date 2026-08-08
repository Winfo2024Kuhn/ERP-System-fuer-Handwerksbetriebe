package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelPreisHinweis;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Leitet aus einem Artikel den Vorschlag fuer eine Dokumentposition ab:
 * Einheit und Einzelpreis.
 *
 * <p>Der Schluessel ist die {@link Verrechnungseinheit}. Sie sagt, worauf sich
 * der gespeicherte Lieferantenpreis bezieht - nicht, was der Kunde liest. Ein
 * Rohr ist mit einem Meterpreis hinterlegt, ein Vollprofil mit einem Kilopreis.
 * Der Bediener gibt in beiden Faellen Meter ein; die Umrechnung passiert hier.
 *
 * <p>Bleche sind der eine Sonderfall: Sie tragen kein Gewicht je Meter, sondern
 * je Quadratmeter, und rechnen deshalb in m2.
 *
 * <p>Der Service ist bewusst zustandslos und ohne Abhaengigkeiten - er rechnet
 * nur. Dadurch laesst er sich ohne Spring-Kontext testen.
 */
@Service
public class ArtikelPositionsPreisService {

    private static final BigDecimal HUNDERT = new BigDecimal("100");

    /** Vorschlag fuer eine Dokumentposition. {@code einzelpreis} ist {@code null}, wenn er nicht ermittelbar war. */
    public record ArtikelPositionsVorschlag(String einheit, BigDecimal einzelpreis, ArtikelPreisHinweis hinweis) {}

    public ArtikelPositionsVorschlag berechne(Artikel artikel) {
        if (artikel == null) {
            return new ArtikelPositionsVorschlag("Stk", null, ArtikelPreisHinweis.KEIN_PREIS);
        }

        Verrechnungseinheit einheit = artikel.getVerrechnungseinheit();
        BigDecimal einkauf = artikel.getGuenstigsterPreis()
                .map(LieferantenArtikelPreise::getPreis)
                .orElse(null);

        // Ohne Preis ist jede Umrechnung gegenstandslos. Die Einheit bestimmen
        // wir trotzdem, damit die Position mit der richtigen Einheit einsteigt
        // und der Bediener nur noch den Preis nachtraegt.
        if (einkauf == null) {
            return new ArtikelPositionsVorschlag(
                    positionsEinheit(artikel, einheit), null, ArtikelPreisHinweis.KEIN_PREIS);
        }

        BigDecimal faktor = umrechnungsfaktor(artikel, einheit);
        if (faktor == null) {
            // Nur bei KILOGRAMM ohne jedes Gewicht erreichbar.
            return new ArtikelPositionsVorschlag("lfm", null, ArtikelPreisHinweis.KEIN_GEWICHT);
        }

        BigDecimal basis = einkauf.multiply(faktor);
        BigDecimal aufschlag = artikel.getVerkaufsaufschlagProzent();
        if (aufschlag == null) {
            return new ArtikelPositionsVorschlag(
                    positionsEinheit(artikel, einheit),
                    basis.setScale(2, RoundingMode.HALF_UP),
                    ArtikelPreisHinweis.KEIN_AUFSCHLAG);
        }

        BigDecimal endpreis = basis
                .multiply(BigDecimal.ONE.add(aufschlag.divide(HUNDERT, 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);
        return new ArtikelPositionsVorschlag(
                positionsEinheit(artikel, einheit), endpreis, ArtikelPreisHinweis.OK);
    }

    /** Einheit, in der die Position gefuehrt wird - unabhaengig davon, ob ein Preis ermittelbar war. */
    private String positionsEinheit(Artikel artikel, Verrechnungseinheit einheit) {
        if (einheit == null) return "Stk";
        return switch (einheit) {
            case LAUFENDE_METER -> "lfm";
            case QUADRATMETER -> "m²";
            case KILOGRAMM -> istBlech(artikel) ? "m²" : "lfm";
            case STUECK -> "Stk";
        };
    }

    /**
     * Womit der gespeicherte Preis multipliziert wird, um auf die
     * Positionseinheit zu kommen. {@code null} bedeutet: nicht ermittelbar.
     */
    private BigDecimal umrechnungsfaktor(Artikel artikel, Verrechnungseinheit einheit) {
        if (einheit != Verrechnungseinheit.KILOGRAMM) {
            return BigDecimal.ONE;
        }
        if (!(artikel instanceof ArtikelWerkstoffe werkstoff)) {
            return null;
        }
        if (werkstoff.getMasse() != null && werkstoff.getMasse().signum() > 0) {
            return werkstoff.getMasse();
        }
        if (werkstoff.getMassePerQm() != null && werkstoff.getMassePerQm().signum() > 0) {
            return werkstoff.getMassePerQm();
        }
        return null;
    }

    /** Ein Blech traegt kein Gewicht je Meter, sondern nur eines je Quadratmeter. */
    private boolean istBlech(Artikel artikel) {
        if (!(artikel instanceof ArtikelWerkstoffe werkstoff)) return false;
        boolean ohneMeterGewicht = werkstoff.getMasse() == null || werkstoff.getMasse().signum() <= 0;
        boolean mitQmGewicht = werkstoff.getMassePerQm() != null && werkstoff.getMassePerQm().signum() > 0;
        return ohneMeterGewicht && mitQmGewicht;
    }
}
