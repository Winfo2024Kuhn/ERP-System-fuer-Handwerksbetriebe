package org.example.kalkulationsprogramm.service.dokumentstand;

import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Schützt ein zurückgeholtes Dokument vor einem veralteten Editor (z. B. derselbe Benutzer auf einem
 * zweiten Gerät): Schickt das Speichern einen Wiederherstellungs-Zähler mit, der nicht mehr zum
 * gespeicherten passt, würde es den zurückgeholten Stand überschreiben → 409. Ohne Zähler (ältere
 * Oberfläche) wird nicht geprüft. Läuft im PUT vor {@code aktualisieren}.
 */
@Component
@RequiredArgsConstructor
public class WiederherstellungsZaehlerPruefung {

    static final String ZURUECKGESETZT = "Dieses Dokument wurde inzwischen auf einen älteren Stand zurückgesetzt. "
            + "Ihre letzte Änderung wurde nicht gespeichert. Bitte das Dokument neu öffnen.";

    private final AusgangsGeschaeftsDokumentRepository dokumentRepository;

    @Transactional(readOnly = true)
    public void pruefe(Long dokumentId, Integer bekannterZaehler) {
        if (bekannterZaehler == null) {
            return;
        }
        dokumentRepository.findById(dokumentId)
                .filter(d -> d.getWiederherstellungsZaehler() != bekannterZaehler)
                .ifPresent(d -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, ZURUECKGESETZT);
                });
    }
}
