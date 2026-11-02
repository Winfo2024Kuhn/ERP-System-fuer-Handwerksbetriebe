package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentEingaben.fehler;
import static org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentEingaben.nichtGefunden;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import org.example.kalkulationsprogramm.domain.firmendokumente.Firmendokument;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdner;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentAendernDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentSeiteDto;
import org.example.kalkulationsprogramm.mapper.FirmendokumentMapper;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentRepository;
import org.example.kalkulationsprogramm.util.LikeMuster;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Firmen-PDFs: auflisten, suchen, fällige zeigen, hochladen, ändern, löschen und ausliefern. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirmendokumentService {

    /** So viele Tage vor dem Fälligkeitsdatum gilt ein Dokument als „bald fällig“. */
    public static final int BALD_FAELLIG_TAGE = 30;
    static final int SUCHE_MIN = 2;
    static final int SUCHE_MAX = 100;

    private static final String DOKUMENT_FEHLT = "Das Dokument gibt es nicht mehr.";
    private static final String NAME_FEHLT = "Bitte einen Namen für das Dokument eingeben.";

    private final FirmendokumentRepository dokumentRepository;
    private final FirmendokumentOrdnerRepository ordnerRepository;
    private final FirmendokumentDateiablage ablage;
    private final Clock clock;

    /** Datei zum Ausliefern: Pfad auf dem Server und Dateiname für den Browser. */
    public record Datei(Path pfad, String dateiname) {
    }

    @Transactional(readOnly = true)
    public FirmendokumentSeiteDto imOrdner(Long ordnerId, int page, int size) {
        if (!ordnerRepository.existsById(ordnerId)) {
            throw nichtGefunden("Den Ordner gibt es nicht mehr.");
        }
        return FirmendokumentMapper.zuSeite(dokumentRepository.findeImOrdner(ordnerId, seite(page, size)));
    }

    @Transactional(readOnly = true)
    public FirmendokumentSeiteDto suche(String suchtext, int page, int size) {
        String text = suchtext == null ? "" : suchtext.strip();
        if (text.length() < SUCHE_MIN) {
            throw fehler("Bitte mindestens " + SUCHE_MIN + " Zeichen eingeben.");
        }
        if (text.length() > SUCHE_MAX) {
            throw fehler("Der Suchbegriff darf höchstens " + SUCHE_MAX + " Zeichen lang sein.");
        }
        return FirmendokumentMapper.zuSeite(dokumentRepository.suche(LikeMuster.enthaelt(text), seite(page, size)));
    }

    @Transactional(readOnly = true)
    public FirmendokumentSeiteDto faellig(int page, int size) {
        LocalDate grenze = LocalDate.now(clock).plusDays(BALD_FAELLIG_TAGE);
        return FirmendokumentMapper.zuSeite(dokumentRepository.findeFaelligBis(grenze, seite(page, size)));
    }

    @Transactional
    public FirmendokumentDto hochladen(MultipartFile datei, Long ordnerId, String name, LocalDate faelligAm,
            String hochgeladenVon) {
        FirmendokumentPdfPruefung.pruefe(datei);
        String sauberName = FirmendokumentEingaben.name(name, NAME_FEHLT);
        FirmendokumentOrdner ordner = zielOrdner(ordnerId);

        String gespeichert = speichere(datei);
        try {
            Firmendokument dokument = new Firmendokument();
            dokument.setOrdner(ordner);
            dokument.setName(sauberName);
            dokument.setOriginalDateiname(FirmendokumentPdfPruefung.anzeigeName(datei.getOriginalFilename()));
            dokument.setGespeicherterDateiname(gespeichert);
            dokument.setGroesseBytes(datei.getSize());
            dokument.setHochgeladenAm(LocalDateTime.now(clock));
            dokument.setHochgeladenVon(hochgeladenVon);
            dokument.setFaelligAm(faelligAm);
            return FirmendokumentMapper.zuDto(dokumentRepository.saveAndFlush(dokument));
        } catch (RuntimeException e) {
            ablage.loesche(gespeichert);
            throw e;
        }
    }

    @Transactional
    public FirmendokumentDto aendern(Long id, FirmendokumentAendernDto eingabe) {
        Firmendokument dokument = finde(id);
        dokument.setName(FirmendokumentEingaben.name(eingabe.name(), NAME_FEHLT));
        dokument.setOrdner(zielOrdner(eingabe.ordnerId()));
        dokument.setFaelligAm(eingabe.faelligAm());
        return FirmendokumentMapper.zuDto(dokument);
    }

    /**
     * Löscht Eintrag und Datei endgültig. Die Datei verschwindet erst nach dem Commit: Scheitert die
     * Transaktion, bleiben Eintrag und Datei zusammen erhalten.
     */
    @Transactional
    public void loeschen(Long id) {
        Firmendokument dokument = finde(id);
        dokumentRepository.delete(dokument);
        String gespeichert = dokument.getGespeicherterDateiname();
        nachCommit(() -> ablage.loesche(gespeichert));
    }

    @Transactional(readOnly = true)
    public Datei datei(Long id) {
        Firmendokument dokument = finde(id);
        Path pfad = ablage.pfad(dokument.getGespeicherterDateiname());
        if (!Files.isReadable(pfad)) {
            throw nichtGefunden("Die Datei zu diesem Dokument wurde nicht gefunden.");
        }
        return new Datei(pfad, downloadName(dokument.getName()));
    }

    /** Dateiname für den Browser: Dokumentname ohne Sonderzeichen, mit „.pdf“. */
    static String downloadName(String name) {
        String sauber = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        if (sauber.isEmpty()) {
            sauber = "dokument";
        }
        return sauber.toLowerCase(Locale.ROOT).endsWith(".pdf") ? sauber : sauber + ".pdf";
    }

    private Firmendokument finde(Long id) {
        return dokumentRepository.findById(id).orElseThrow(() -> nichtGefunden(DOKUMENT_FEHLT));
    }

    private FirmendokumentOrdner zielOrdner(Long ordnerId) {
        if (ordnerId == null) {
            throw fehler("Bitte einen Ordner auswählen.");
        }
        return ordnerRepository.findById(ordnerId).orElseThrow(() -> fehler("Den Ordner gibt es nicht mehr."));
    }

    private String speichere(MultipartFile datei) {
        try {
            return ablage.speichere(datei.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("Hochgeladene Datei konnte nicht gelesen werden", e);
        }
    }

    /** Führt die Aktion nach erfolgreichem Commit aus (ohne laufende Transaktion sofort). */
    private static void nachCommit(Runnable aktion) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            aktion.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    aktion.run();
                } catch (RuntimeException e) {
                    log.warn("Datei eines gelöschten Firmendokuments konnte nicht entfernt werden: {}", e.getMessage());
                }
            }
        });
    }

    private static PageRequest seite(int page, int size) {
        return PageRequest.of(FirmendokumentEingaben.seite(page), FirmendokumentEingaben.groesse(size));
    }
}
