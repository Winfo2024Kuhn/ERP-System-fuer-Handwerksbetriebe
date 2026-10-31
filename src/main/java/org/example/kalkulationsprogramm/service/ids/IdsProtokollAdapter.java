package org.example.kalkulationsprogramm.service.ids;

import org.example.kalkulationsprogramm.domain.ids.IdsProtokoll;

/**
 * Eine Spielart der Shop-Anbindung. Kapselt nur das Protokoll: welches Formular der Browser
 * an den Shop schickt und wie der zurückgeschickte Warenkorb gelesen wird. Zugangsdaten,
 * Rücksprung-Schlüssel und Speicherung erledigt {@link IdsShopBesuchService}.
 */
public interface IdsProtokollAdapter {

    IdsProtokoll protokoll();

    /**
     * Das Anmeldeformular, das der Browser per POST an den Shop schickt.
     *
     * @param zugang       Shop-Zugang mit Klartext-Passwort (nur für diesen Aufruf entschlüsselt)
     * @param ruecksprung  Adresse, an die der Shop den Warenkorb zurückschickt
     */
    ShopFormular anmeldeFormular(IdsZugangsdaten zugang, String ruecksprung);

    /** Liest die Formularfelder der Rückgabe des Shops. */
    IdsWarenkorb leseWarenkorb(IdsRueckgabe rueckgabe);
}
