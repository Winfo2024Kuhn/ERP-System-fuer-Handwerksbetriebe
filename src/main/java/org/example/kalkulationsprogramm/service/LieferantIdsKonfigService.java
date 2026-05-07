package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.IdsProtokoll;
import org.example.kalkulationsprogramm.domain.LieferantIdsKonfig;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.LieferantIdsKonfigRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Verwaltung der IDS-Connect-Konfiguration pro Lieferant. Passwörter
 * werden hier verschlüsselt entgegengenommen und nur als Klartext
 * weiter verwendet, wenn der Punchout-Flow das Passwort tatsächlich
 * an den Lieferanten-Shop posten muss. Der Klartext verlässt nicht
 * das Backend.
 */
@Service
@RequiredArgsConstructor
public class LieferantIdsKonfigService {

    private static final String PASSWORT_PLATZHALTER = "********";

    private final LieferantIdsKonfigRepository repository;
    private final LieferantenRepository lieferantenRepository;
    private final IdsCryptoService cryptoService;

    /**
     * Liefert die Konfig zu einem Lieferanten oder eine leere Default-
     * Konfig, falls noch keine angelegt ist. Wird zum Befüllen des
     * Admin-Formulars verwendet.
     */
    @Transactional(readOnly = true)
    public LieferantIdsKonfig findOrEmpty(Long lieferantId) {
        return repository.findByLieferantId(lieferantId)
                .orElseGet(() -> {
                    LieferantIdsKonfig leer = new LieferantIdsKonfig();
                    leer.setProtokoll(IdsProtokoll.IDS_CONNECT_2_5);
                    return leer;
                });
    }

    /**
     * Speichert die Konfig. Das Passwort wird:
     * <ul>
     *   <li>{@code null}/blank → bestehender verschlüsselter Wert bleibt erhalten</li>
     *   <li>{@link #PASSWORT_PLATZHALTER} → Wert bleibt erhalten (Frontend hat
     *       beim Read den Platzhalter bekommen und unverändert zurückgeschickt)</li>
     *   <li>sonst → mit AES-GCM verschlüsselt persistiert</li>
     * </ul>
     */
    @Transactional
    public LieferantIdsKonfig save(Long lieferantId, IdsKonfigUpdate update, Mitarbeiter geaendertVon) {
        Lieferanten lieferant = lieferantenRepository.findById(lieferantId)
                .orElseThrow(() -> new IllegalArgumentException("Lieferant nicht gefunden: " + lieferantId));

        LieferantIdsKonfig konfig = repository.findByLieferantId(lieferantId)
                .orElseGet(LieferantIdsKonfig::new);

        konfig.setLieferant(lieferant);
        konfig.setAktiviert(update.aktiviert);
        konfig.setProtokoll(update.protokoll != null ? update.protokoll : IdsProtokoll.IDS_CONNECT_2_5);
        konfig.setPunchoutUrl(blankToNull(update.punchoutUrl));
        konfig.setKundennummer(blankToNull(update.kundennummer));
        konfig.setLoginName(blankToNull(update.loginName));
        konfig.setNotizen(blankToNull(update.notizen));

        // Passwort-Logik
        if (update.passwortKlartext != null
                && !update.passwortKlartext.isBlank()
                && !PASSWORT_PLATZHALTER.equals(update.passwortKlartext)) {
            konfig.setPasswortVerschluesselt(cryptoService.encrypt(update.passwortKlartext));
        }

        konfig.setGeaendertAm(LocalDateTime.now());
        konfig.setGeaendertVon(geaendertVon);

        return repository.save(konfig);
    }

    /**
     * Liefert das Klartext-Passwort für den Punchout-Flow. Nicht für
     * Anzeige im UI gedacht – der Wert wird direkt in das Punchout-Form
     * geschrieben.
     */
    public String entschluesselePasswort(LieferantIdsKonfig konfig) {
        return cryptoService.decrypt(konfig.getPasswortVerschluesselt());
    }

    public static boolean istPasswortPlatzhalter(String value) {
        return PASSWORT_PLATZHALTER.equals(value);
    }

    public static String passwortPlatzhalter() {
        return PASSWORT_PLATZHALTER;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** Eingabe-Datenstruktur für Save (vermeidet Kopplung an HTTP-DTO). */
    public static class IdsKonfigUpdate {
        public boolean aktiviert;
        public IdsProtokoll protokoll;
        public String punchoutUrl;
        public String kundennummer;
        public String loginName;
        /** Klartext-Passwort oder Platzhalter "********" oder null/blank für unverändert. */
        public String passwortKlartext;
        public String notizen;
    }
}
