package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.WerkstoffzeugnisNachleseErgebnis;
import org.example.kalkulationsprogramm.repository.LieferantDokumentPositionRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Liest Werkstoffzeugnisse, denen Nummer, Datum oder Positionen fehlen, noch einmal
 * mit der KI. Gedacht für Zeugnisse, die vor Einführung des Typs als „Sonstiges“
 * eingelesen und danach von Hand umgestellt wurden.
 *
 * <p>Die Analyse behält den von Hand gesetzten Typ (überschrieben wird nur
 * „Sonstiges“), liest die Positionen (Werkstoff, Charge, Abmessung) aus und hängt
 * das Zeugnis über die automatische Verknüpfung an seinen Lieferschein.</p>
 *
 * <p>Ohne eigene Transaktion: jedes Dokument läuft über
 * {@link GeminiDokumentAnalyseService#reanalysiereDokumentById(Long)} in einer
 * eigenen – ein Fehler bei einem Zeugnis bricht den Lauf nicht ab. Es läuft
 * höchstens ein Lauf gleichzeitig (KI-Kosten, gleiche Dokumente).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WerkstoffzeugnisNachleseService {

    private final LieferantDokumentRepository dokumentRepository;
    private final LieferantDokumentPositionRepository positionRepository;
    private final GeminiDokumentAnalyseService analyseService;

    private final AtomicBoolean laeuft = new AtomicBoolean(false);

    /** Ein Lauf ist schon gestartet und noch nicht fertig. */
    public static class LaufAktivException extends RuntimeException {
        public LaufAktivException() {
            super("Die Werkstoffzeugnisse werden gerade schon nachgelesen. Bitte warten, bis der Lauf fertig ist.");
        }
    }

    /**
     * @param gestartetVon Benutzer-ID des Admins – nur fürs Protokoll, {@code null} wenn unbekannt
     * @throws LaufAktivException wenn schon ein Lauf aktiv ist
     */
    public WerkstoffzeugnisNachleseErgebnis liesZeugnisseNach(Long gestartetVon) {
        if (!laeuft.compareAndSet(false, true)) {
            throw new LaufAktivException();
        }
        try {
            return lauf(gestartetVon);
        } finally {
            laeuft.set(false);
        }
    }

    private WerkstoffzeugnisNachleseErgebnis lauf(Long gestartetVon) {
        List<Long> ids = dokumentRepository.findIdsOhneVollstaendigeDaten(LieferantDokumentTyp.WERKSTOFFZEUGNIS);
        log.info("[Zeugnisse nachlesen] gestartet von Benutzer-ID {}: {} Werkstoffzeugnisse mit unvollständigen Daten",
                gestartetVon, ids.size());

        int erfolgreich = 0;
        int verknuepft = 0;
        List<Long> fehlgeschlagen = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            log.info("[Zeugnisse nachlesen] Dokument {}/{} (ID {})", i + 1, ids.size(), id);
            try {
                LieferantGeschaeftsdokument gd = analyseService.reanalysiereDokumentById(id);
                if (istVollstaendig(id, gd)) {
                    erfolgreich++;
                } else {
                    fehlgeschlagen.add(id);
                }
            } catch (RuntimeException e) {
                log.warn("[Zeugnisse nachlesen] Dokument {} fehlgeschlagen: {}", id, e.getMessage());
                fehlgeschlagen.add(id);
            }
            if (dokumentRepository.zaehleMitVerknuepfung(id) > 0) {
                verknuepft++;
            }
        }

        log.info("[Zeugnisse nachlesen] fertig: {} gesamt, {} erfolgreich, {} fehlgeschlagen, {} verknüpft",
                ids.size(), erfolgreich, fehlgeschlagen.size(), verknuepft);
        return new WerkstoffzeugnisNachleseErgebnis(ids.size(), erfolgreich, fehlgeschlagen.size(), verknuepft,
                List.copyOf(fehlgeschlagen));
    }

    /** Eigene Geschäftsdaten (nicht die eines anderen Belegs) mit Nummer und mindestens einer Position. */
    private boolean istVollstaendig(Long id, LieferantGeschaeftsdokument gd) {
        return gd != null
                && id.equals(gd.getId())
                && gd.getDokumentNummer() != null && !gd.getDokumentNummer().isBlank()
                && positionRepository.countByGeschaeftsdokumentId(id) > 0;
    }
}
