package org.example.kalkulationsprogramm.service.telefon;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException.Grund;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Anrufen aus dem ERP ("Zurückrufen"): Die Telefonanlage lässt erst das
 * Telefon am Arbeitsplatz klingeln und wählt nach dem Abnehmen die Nummer.
 * Welches Telefon klingelt, entscheidet der Arbeitsplatz – der Server prüft
 * nur, dass es das Telefon in der Anlage wirklich gibt.
 */
@Service
@RequiredArgsConstructor
public class TelefonWaehlService {

    private static final int MIN_ZIFFERN = 2;
    private static final int MAX_ZIFFERN = 30;
    private static final int MAX_EINGABE = 60;
    private static final int MAX_TELEFON_NAME = 100;

    private final TelefonAnlage anlage;
    private final TelefonEinstellungenService einstellungen;

    public List<String> telefone() {
        return anlage.ladeTelefone(zugang());
    }

    public void anrufen(String telefon, String nummer) {
        String waehlbar = waehlbareNummer(nummer);
        if (telefon == null || telefon.isBlank()) {
            throw new IllegalArgumentException("Bitte ein Telefon auswählen.");
        }
        if (telefon.length() > MAX_TELEFON_NAME) {
            throw unbekanntesTelefon();
        }
        TelefonZugang zugang = zugang();
        if (!anlage.ladeTelefone(zugang).contains(telefon)) {
            throw unbekanntesTelefon();
        }
        anlage.anrufen(zugang, telefon, waehlbar);
    }

    /**
     * Macht aus einer geschriebenen Nummer eine wählbare: Leerzeichen, Klammern,
     * Striche, Punkte und Schrägstriche fallen weg, ein führendes "+" wird "00".
     * Erlaubt sind danach nur Ziffern – "*" und "#" nicht, weil damit
     * Steuercodes der Telefonanlage ausgelöst werden könnten.
     */
    static String waehlbareNummer(String roh) {
        if (roh == null || roh.length() > MAX_EINGABE) {
            throw ungueltigeNummer();
        }
        String s = roh.strip();
        StringBuilder ziffern = new StringBuilder(s.length());
        boolean international = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                ziffern.append(c);
            } else if (c == '+' && i == 0) {
                international = true;
            } else if (!istTrenner(c)) {
                throw ungueltigeNummer();
            }
        }
        if (ziffern.length() < MIN_ZIFFERN || ziffern.length() > MAX_ZIFFERN) {
            throw ungueltigeNummer();
        }
        return international ? "00" + ziffern : ziffern.toString();
    }

    private static boolean istTrenner(char c) {
        return c == ' ' || c == '\u00A0' || c == '/' || c == '-' || c == '(' || c == ')' || c == '.';
    }

    private TelefonZugang zugang() {
        if (!einstellungen.istAktiv()) {
            throw new TelefonAnlageException(Grund.NICHT_EINGERICHTET);
        }
        return einstellungen.zugang().orElseThrow(() -> new TelefonAnlageException(Grund.NICHT_EINGERICHTET));
    }

    private static IllegalArgumentException ungueltigeNummer() {
        return new IllegalArgumentException("Diese Nummer kann nicht gewählt werden.");
    }

    private static IllegalArgumentException unbekanntesTelefon() {
        return new IllegalArgumentException("Dieses Telefon kennt die FRITZ!Box nicht. Bitte ein anderes Telefon auswählen.");
    }
}
