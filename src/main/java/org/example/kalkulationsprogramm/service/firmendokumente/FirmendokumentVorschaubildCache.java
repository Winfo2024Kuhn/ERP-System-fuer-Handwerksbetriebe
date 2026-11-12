package org.example.kalkulationsprogramm.service.firmendokumente;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Hält die Vorschaubilder der Firmendokumente im Speicher, je Dokument-ID. Die Datei eines Dokuments wird nie
 * ersetzt (nur Name, Ordner und Fällig-am ändern sich), deshalb bleibt ein Eintrag gültig, bis das Dokument gelöscht
 * wird ({@link FirmendokumentService#loeschen} ruft dann {@link #vergiss} nach dem Commit). Eigener Cache, kein
 * Schlüssel wird mit anderen Vorschau-Caches geteilt.
 *
 * <p><b>Speicherbedarf:</b> Begrenzt wird nach Bytes, nicht nach Anzahl, weil die Größe der JPEGs schwankt. Ein
 * Vorschaubild mit 512 px Kante ist meist 20–60 KB groß (Foto eher mehr, Textseite weniger). Der Standardrahmen von
 * {@value #STANDARD_MAX_MB} MB reicht damit für rund 600–1.500 Kacheln, also mehrere große Fotoordner, und bleibt auch
 * auf kleinen Servern unkritisch. Jeder Eintrag zählt zusätzlich {@value #AUFSCHLAG_JE_EINTRAG} Bytes
 * Verwaltungsaufschlag; so ist auch die Zahl der leeren Einträge begrenzt.</p>
 *
 * <p><b>Leeres Feld</b> ({@code byte[0]}) heißt „für diese Datei gibt es kein Vorschaubild“ (kaputt, passwortgeschützt
 * …); so wird ein kaputtes 50-MB-PDF nicht bei jedem Scrollen neu geöffnet.</p>
 *
 * <p>Gemerkt werden nur dauerhafte Ergebnisse; ein vorübergehender Lesefehler wird nie abgelegt
 * ({@link FirmendokumentVorschaubildService}).</p>
 *
 * <p><b>Löschen während einer Umrechnung:</b> Eine noch laufende Umrechnung kann ihr Ergebnis nach dem {@link #vergiss}
 * des Löschens ablegen. Das ist kein Rechte-Leck – jeder Abruf prüft vorher, ob es das Dokument noch gibt und wer es
 * sehen darf, und Dokument-IDs werden nie wiederverwendet. Der verwaiste Eintrag belegt nur Platz, bis er verdrängt
 * wird.</p>
 *
 * <p>Ist der Rahmen voll, fliegt das am längsten nicht abgerufene Bild heraus (LRU). Alle Methoden sind
 * synchronisiert; schon ein Lesezugriff ändert die Reihenfolge.</p>
 */
@Component
public class FirmendokumentVorschaubildCache {

    static final int STANDARD_MAX_MB = 32;
    /** Grober Verwaltungsaufwand je Eintrag (Map-Knoten, Long-Schlüssel, Array-Kopf). */
    static final int AUFSCHLAG_JE_EINTRAG = 128;

    private final long maxBytes;
    private final LinkedHashMap<Long, byte[]> eintraege = new LinkedHashMap<>(64, 0.75f, true);
    private long belegt;

    public FirmendokumentVorschaubildCache() {
        this((long) STANDARD_MAX_MB * 1024 * 1024);
    }

    /** Rahmen in Bytes frei wählbar, z. B. für Tests. */
    FirmendokumentVorschaubildCache(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    /** Vorschaubild (leeres Feld = „keins möglich“) oder {@code null}, wenn noch nichts ermittelt wurde. */
    public synchronized byte[] hole(Long dokumentId) {
        return eintraege.get(dokumentId);
    }

    /** Legt das Ergebnis ab; ein Bild, das allein größer als der ganze Rahmen wäre, wird nicht gespeichert. */
    public synchronized void lege(Long dokumentId, byte[] jpeg) {
        long kosten = kosten(jpeg);
        if (kosten > maxBytes) {
            return;
        }
        byte[] vorher = eintraege.put(dokumentId, jpeg);
        if (vorher != null) {
            belegt -= kosten(vorher);
        }
        belegt += kosten;
        verdraengeBisPasst();
    }

    /** Entfernt den Eintrag, etwa weil das Dokument gelöscht wurde. */
    public synchronized void vergiss(Long dokumentId) {
        byte[] vorher = eintraege.remove(dokumentId);
        if (vorher != null) {
            belegt -= kosten(vorher);
        }
    }

    synchronized long belegteBytes() {
        return belegt;
    }

    long maxBytes() {
        return maxBytes;
    }

    private void verdraengeBisPasst() {
        Iterator<Map.Entry<Long, byte[]>> aelteste = eintraege.entrySet().iterator();
        while (belegt > maxBytes && aelteste.hasNext()) {
            belegt -= kosten(aelteste.next().getValue());
            aelteste.remove();
        }
    }

    private static long kosten(byte[] jpeg) {
        return (long) jpeg.length + AUFSCHLAG_JE_EINTRAG;
    }
}
