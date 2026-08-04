package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Markiert eingehende Rechnungen von Vorauskasse-Lieferanten automatisch als
 * "bereits gezahlt".
 *
 * Bei manchen Lieferanten wird grundsaetzlich im Voraus bezahlt (Vorkasse,
 * Lastschrift, Kreditkarte im Shop). Deren Rechnungen sind beim Eintreffen
 * schon beglichen. Ohne diese Markierung landen sie in den Offenen Posten und
 * koennten versehentlich ein zweites Mal ueberwiesen werden.
 *
 * Wird aus den automatischen Import-Pfaden aufgerufen (E-Mail-Anhang-Analyse,
 * Upload mit KI-Analyse). Beim manuellen Import entscheidet der Nutzer selbst
 * ueber das Haekchen "bereits gezahlt" - dort greift die Automatik bewusst
 * nicht ein.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LieferantVorauskasseAutoAssigner {

    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;

    /**
     * Setzt bei Rechnungen eines Vorauskasse-Lieferanten {@code bereitsGezahlt}.
     * Idempotent: ist das Flag bereits gesetzt, passiert nichts.
     */
    @Transactional
    public void applyIfApplicable(LieferantDokument dokument) {
        if (dokument == null || dokument.getTyp() != LieferantDokumentTyp.RECHNUNG) {
            return;
        }
        Lieferanten lieferant = dokument.getLieferant();
        if (lieferant == null || !Boolean.TRUE.equals(lieferant.getVorauskasse())) {
            return;
        }
        LieferantGeschaeftsdokument gd = dokument.getGeschaeftsdaten();
        if (gd == null || Boolean.TRUE.equals(gd.getBereitsGezahlt())) {
            return;
        }

        gd.setBereitsGezahlt(true);
        // Die Zahlungsart nur ergaenzen, wenn die KI keine konkretere erkannt hat
        // (z. B. SEPA_LASTSCHRIFT) - sonst wuerden wir Wissen ueberschreiben.
        if (gd.getZahlungsart() == null || gd.getZahlungsart().isBlank()) {
            gd.setZahlungsart("VORAUSKASSE");
        }
        geschaeftsdokumentRepository.save(gd);

        log.info("Vorauskasse: Dokument {} von Lieferant {} als bereits gezahlt markiert - "
                + "erscheint nicht in den Offenen Posten", dokument.getId(), lieferant.getId());
    }
}
