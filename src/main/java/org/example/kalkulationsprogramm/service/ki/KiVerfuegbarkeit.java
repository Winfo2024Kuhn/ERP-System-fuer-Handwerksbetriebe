package org.example.kalkulationsprogramm.service.ki;

import java.util.List;

/**
 * Was die Oberfläche beim Anmelden über die KI wissen muss ({@link KiDienst#verfuegbarkeit()}).
 *
 * @param irgendeinAnbieterEingerichtet hat irgendein Anbieter einen lesbaren Schlüssel? Dann sind
 *                                      KI-Knöpfe grundsätzlich an
 * @param funktionen                    die Funktionen, die gerade laufen (selbst gewählt oder als Ersatz),
 *                                      in der Reihenfolge von {@link KiFunktion}
 */
public record KiVerfuegbarkeit(boolean irgendeinAnbieterEingerichtet, List<KiFunktion> funktionen) {

    public KiVerfuegbarkeit {
        funktionen = funktionen == null ? List.of() : List.copyOf(funktionen);
    }
}
