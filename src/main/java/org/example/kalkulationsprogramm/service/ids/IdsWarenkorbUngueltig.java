package org.example.kalkulationsprogramm.service.ids;

/** Der Shop hat etwas geschickt, das sich nicht als Warenkorb lesen lässt. Die Meldung ist für Nutzer. */
public class IdsWarenkorbUngueltig extends RuntimeException {

    public IdsWarenkorbUngueltig(String meldung) {
        super(meldung);
    }

    public IdsWarenkorbUngueltig(String meldung, Throwable ursache) {
        super(meldung, ursache);
    }
}
