package org.example.kalkulationsprogramm.service.telefon;

import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;

import java.time.LocalDateTime;

/**
 * Ein Anruf, wie ihn die Telefonanlage meldet.
 *
 * @param gegenNummer  Nummer des Gegenübers, "" wenn unterdrückt
 * @param eigeneNummer beteiligte eigene Rufnummer (angerufen bzw. verwendet)
 * @param anrufbeantworter Index des AB, der den Anruf entgegengenommen hat, sonst null
 */
public record AnlagenAnruf(LocalDateTime zeitpunkt,
                           TelefonAnrufArt art,
                           String gegenNummer,
                           String eigeneNummer,
                           int dauerMinuten,
                           String name,
                           Integer anrufbeantworter) {
}
