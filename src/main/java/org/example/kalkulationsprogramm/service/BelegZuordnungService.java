package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Eine Stelle für „Beleg ist da → passende Bestellung suchen“.
 *
 * <p>Rechnungen, Lieferscheine und Auftragsbestätigungen kommen über viele Wege
 * ins System: Mail-Anhang, Upload beim Lieferanten, „Rechnung hochladen“ in der
 * Bestellübersicht und den Belegscanner am Handy. Jeder Weg ruft am Ende diesen
 * Service auf, damit der Beleg in seine Dokumentenkette findet. Entschieden wird
 * im {@link LieferantDokumentAbgleich}.
 *
 * <p>Fehler beim Zuordnen werden nur protokolliert: Der Beleg selbst ist dann
 * trotzdem gespeichert, und das Neu-Verknüpfen beim Start holt die Zuordnung nach.
 */
@Slf4j
@Service
public class BelegZuordnungService {

    /** Diese Belegarten des Scanners gehören in eine Bestellkette. */
    static final Set<LieferantDokumentTyp> SCANNER_TYPEN = Set.of(
            LieferantDokumentTyp.RECHNUNG, LieferantDokumentTyp.GUTSCHRIFT, LieferantDokumentTyp.LIEFERSCHEIN);

    private final GeminiDokumentAnalyseService analyseService;
    private final LieferantDokumentRepository lieferantDokumentRepository;
    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    private final BelegRepository belegRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate neueTransaktion;
    private final TaskExecutor taskExecutor;

    public BelegZuordnungService(GeminiDokumentAnalyseService analyseService,
            LieferantDokumentRepository lieferantDokumentRepository,
            LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository,
            BelegRepository belegRepository,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Qualifier("taskExecutor") TaskExecutor taskExecutor) {
        this.analyseService = analyseService;
        this.lieferantDokumentRepository = lieferantDokumentRepository;
        this.geschaeftsdokumentRepository = geschaeftsdokumentRepository;
        this.belegRepository = belegRepository;
        // Die gespeicherte KI-Antwort enthält Felder, die das DTO nicht kennt.
        this.objectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.neueTransaktion = new TransactionTemplate(transactionManager);
        this.neueTransaktion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.taskExecutor = taskExecutor;
    }

    /**
     * Beleg ist da → Kette suchen: verknüpft das Dokument mit seinen Vorgängern
     * und mit Nachfolgern, die schon auf es warten. Ohne Lieferant, Geschäftsdaten
     * oder ID passiert nichts.
     *
     * <p>Läuft erst nach dem Commit des Aufrufers und in eigener Transaktion: Ein
     * Fehler beim Einordnen darf weder den Mail-Import noch die KI-Auslesung oder
     * den Upload zurückrollen. Ohne laufende Transaktion sofort.
     */
    public void ordneEin(LieferantDokument dokument) {
        if (dokument == null || dokument.getId() == null || dokument.getLieferant() == null
                || dokument.getGeschaeftsdaten() == null) {
            return;
        }
        Long dokumentId = dokument.getId();
        nachCommit(() -> {
            try {
                neueTransaktion.executeWithoutResult(status -> lieferantDokumentRepository.findById(dokumentId)
                        .ifPresent(analyseService::performRelink));
            } catch (RuntimeException e) {
                // Nur ID und Fehlertyp (DSGVO) – das Neu-Verknüpfen beim Start holt es nach.
                log.warn("[Zuordnung] Dokument {} konnte nicht eingeordnet werden: {}",
                        dokumentId, e.getClass().getSimpleName());
            }
        });
    }

    /**
     * Ein frisch hochgeladenes Dokument im Hintergrund auslesen (ZUGFeRD/XML/KI)
     * und danach einordnen. Startet erst nach dem Speichern, sonst sähe der
     * Hintergrund-Thread das Dokument noch nicht.
     */
    public void analysiereUndOrdneEinNachCommit(Long dokumentId) {
        if (dokumentId == null) {
            return;
        }
        nachCommit(() -> taskExecutor.execute(() -> {
            try {
                LieferantDokument verweis = new LieferantDokument();
                verweis.setId(dokumentId);
                // Liest aus, speichert die Geschäftsdaten und verknüpft in beide Richtungen.
                analyseService.analysiereDokument(verweis);
            } catch (RuntimeException e) {
                log.warn("[Zuordnung] Analyse von Dokument {} fehlgeschlagen: {}",
                        dokumentId, e.getClass().getSimpleName());
            }
        }));
    }

    /**
     * Belegscanner: Ist ein Lieferant dran und hat die KI eine Rechnung, Gutschrift
     * oder einen Lieferschein erkannt, entsteht ein Lieferanten-Dokument zum Beleg,
     * das sofort in seine Kette einsortiert wird. Datei und Vorschau bleiben am
     * Beleg; das Lieferanten-Dokument hält nur Geschäftsdaten und Lieferantenbezug.
     *
     * <p>Idempotent:
     * <ul>
     *   <li>Gibt es zum Beleg schon ein Lieferanten-Dokument, passiert nichts.</li>
     *   <li>Ist die Belegnummer beim Lieferanten schon erfasst (Mail-Import war
     *       schneller), passiert nichts.</li>
     * </ul>
     *
     * @param ergebnis die KI-Auslesung; darf fehlen – dann zählen nur die Belegfelder
     * @return das neue Lieferanten-Dokument, sonst leer
     */
    public Optional<LieferantDokument> uebernehmeScannerBeleg(Beleg beleg, LieferantDokumentDto.AnalyzeResponse ergebnis) {
        if (beleg == null) {
            return Optional.empty();
        }
        LieferantDokumentTyp typ = beleg.getDokumentTyp();
        Lieferanten lieferant = beleg.getLieferant();
        if (typ == null || lieferant == null || !SCANNER_TYPEN.contains(typ)) {
            return Optional.empty();
        }
        // Idempotenz 1: Re-Analyse-Lauf oder Lieferant später gesetzt -> nicht doppelt anlegen
        if (lieferantDokumentRepository.findByBelegId(beleg.getId()).isPresent()) {
            log.debug("LieferantDokument fuer Beleg {} existiert bereits, kein erneutes Anlegen", beleg.getId());
            return Optional.empty();
        }
        // Idempotenz 2: Belegnummer schon beim Lieferanten erfasst
        String dokNr = beleg.getBelegNummer();
        if (dokNr != null && !dokNr.isBlank()
                && geschaeftsdokumentRepository.existsByLieferantIdAndDokumentNummer(lieferant.getId(), dokNr)) {
            log.info("Belegnummer bei Lieferant {} schon vorhanden — Beleg {} bleibt eigenstaendig",
                    lieferant.getId(), beleg.getId());
            return Optional.empty();
        }

        // Datei: kein erneutes Kopieren — wir verweisen relativ auf den Beleg-Pfad.
        // resolveLieferantDokumentPath sucht u.a. uploads/{filename}; mit
        // "belege/<gespeicherterName>" klappt der Lookup als uploads/belege/<gespeicherterName>.
        String gespeicherterFuerLD = beleg.getGespeicherterDateiname() != null
                ? "belege/" + beleg.getGespeicherterDateiname()
                : null;

        LieferantDokument ld = new LieferantDokument();
        ld.setLieferant(lieferant);
        ld.setTyp(typ);
        ld.setOriginalDateiname(beleg.getOriginalDateiname());
        ld.setGespeicherterDateiname(gespeicherterFuerLD);
        ld.setUploadDatum(LocalDateTime.now());
        ld.setUploadedBy(beleg.getUploadedBy());
        ld.setBeleg(beleg);
        ld = lieferantDokumentRepository.save(ld);

        LieferantGeschaeftsdokument lgd = new LieferantGeschaeftsdokument();
        lgd.setDokument(ld);
        lgd.setDokumentNummer(beleg.getBelegNummer());
        lgd.setDokumentDatum(beleg.getBelegDatum());
        lgd.setBetragNetto(beleg.getBetragNetto());
        lgd.setBetragBrutto(beleg.getBetragBrutto());
        lgd.setMwstSatz(beleg.getMwstSatz());
        lgd.setZahlungsart(beleg.getZahlungsart());
        if (typ != LieferantDokumentTyp.LIEFERSCHEIN) {
            // Zahlungsangaben nur für Rechnung/Gutschrift – ein Lieferschein ist nie "bezahlt".
            boolean gezahltLautZahlungsart = ZahlungsartMapper.giltAlsBezahlt(beleg.getKiZahlungsart());
            lgd.setBereitsGezahlt((ergebnis != null && Boolean.TRUE.equals(ergebnis.getBereitsGezahlt()))
                    || gezahltLautZahlungsart);
        }
        if (ergebnis != null) {
            lgd.setSkontoTage(ergebnis.getSkontoTage());
            lgd.setSkontoProzent(ergebnis.getSkontoProzent());
            lgd.setNettoTage(ergebnis.getNettoTage());
            lgd.setZahlungsziel(ergebnis.getZahlungsziel());
            lgd.setLiefertermin(ergebnis.getLiefertermin());
            lgd.setReferenzNummer(ergebnis.getReferenzNummer());
            lgd.setBestellnummer(ergebnis.getBestellnummer());
            if (ergebnis.getAiConfidence() != null) {
                lgd.setAiConfidence(ergebnis.getAiConfidence());
            }
            lgd.setAiRawJson(kiAntwort(ergebnis));
        }
        lgd.setAnalysiertAm(LocalDateTime.now());
        geschaeftsdokumentRepository.save(lgd);
        ld.setGeschaeftsdaten(lgd);

        log.info("Lieferanten-Dokument aus Belegscanner erzeugt: Beleg {} -> LieferantDokument {} ({})",
                beleg.getId(), ld.getId(), typ);
        ordneEin(ld);
        return Optional.of(ld);
    }

    /**
     * Für den Prüf-Dialog: Wurde der Lieferant erst dort gesetzt, entsteht das
     * Lieferanten-Dokument jetzt. Läuft nach dem Speichern des Belegs in einer
     * eigenen Transaktion – ein Fehler beim Einordnen macht das Speichern im
     * Dialog nicht rückgängig.
     */
    public void uebernehmeScannerBelegNachCommit(Long belegId) {
        if (belegId == null) {
            return;
        }
        nachCommit(() -> {
            try {
                neueTransaktion.executeWithoutResult(status -> belegRepository.findById(belegId)
                        .ifPresent(beleg -> uebernehmeScannerBeleg(beleg, gespeicherteKiAuslesung(beleg))));
            } catch (RuntimeException e) {
                log.warn("[Zuordnung] Beleg {} konnte nicht als Lieferanten-Dokument übernommen werden: {}",
                        belegId, e.getClass().getSimpleName());
            }
        });
    }

    /** Die beim Scannen gesicherte KI-Auslesung, falls lesbar. */
    private LieferantDokumentDto.AnalyzeResponse gespeicherteKiAuslesung(Beleg beleg) {
        String json = beleg.getKiExtraktionJson();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, LieferantDokumentDto.AnalyzeResponse.class);
        } catch (Exception e) {
            log.debug("KI-Auslesung von Beleg {} nicht lesbar: {}", beleg.getId(), e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Die rohe KI-Antwort enthält weitere Referenzen, Kommission und Artikel – die
     * braucht der Abgleich. Fehlt sie, das DTO selbst.
     */
    private String kiAntwort(LieferantDokumentDto.AnalyzeResponse ergebnis) {
        if (ergebnis.getAiRawJson() != null && !ergebnis.getAiRawJson().isBlank()) {
            return ergebnis.getAiRawJson();
        }
        try {
            return objectMapper.writeValueAsString(ergebnis);
        } catch (Exception e) {
            return null;
        }
    }

    /** Führt die Aufgabe nach dem Commit der laufenden Transaktion aus – ohne Transaktion sofort. */
    private static void nachCommit(Runnable aufgabe) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    aufgabe.run();
                }
            });
        } else {
            aufgabe.run();
        }
    }
}
