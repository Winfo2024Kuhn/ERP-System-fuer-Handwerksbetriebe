package org.example.kalkulationsprogramm.service;

/**
 * Die Positionen eines Dokuments konnten nicht (nach)gelesen werden. Die
 * Meldung ist für den Nutzer bestimmt und enthält nie Pfade oder Interna.
 */
public class PositionenNichtLesbarException extends RuntimeException {

    public PositionenNichtLesbarException(String meldung) {
        super(meldung);
    }
}
