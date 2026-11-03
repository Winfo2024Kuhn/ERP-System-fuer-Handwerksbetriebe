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
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.example.kalkulationsprogramm.domain.firmendokumente.Firmendokument;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdner;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentAendernDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentSeiteDto;
import org.example.kalkulationsprogramm.mapper.FirmendokumentMapper;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentRepository;
import org.example.kalkulationsprogramm.util.LikeMuster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Firmendokumente (alle Formate außer Programmdateien): auflisten, suchen, fällige zeigen, hochladen, ändern,
 * löschen und ausliefern – jeweils nur in Ordnern, die der Angemeldete sehen bzw. ändern darf
 * ({@link FirmendokumentZugriff}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirmendokumentService {

    /** So viele Tage vor dem Fälligkeitsdatum gilt ein Dokument als „bald fällig“. */
    public static final int BALD_FAELLIG_TAGE = 30;
    static final int SUCHE_MIN = 2;
    static final int SUCHE_MAX = 100;

    private static final String DOKUMENT_FEHLT = "Das Dokument gibt es nicht mehr.";
    private static final String ORDNER_FEHLT = "Den Ordner gibt es nicht mehr.";
    private static final String NAME_FEHLT = "Bitte einen Namen für das Dokument eingeben.";

    private final FirmendokumentRepository dokumentRepository;
    private final FirmendokumentOrdnerRepository ordnerRepository;
    private final FirmendokumentDateiablage ablage;
    private final FirmendokumentZugriff zugriff;
    private final Clock clock;

    /**
     * Datei zum Ausliefern: Pfad auf dem Server, Dateiname für den Browser, Content-Type und ob sie nie im Browser
     * geöffnet werden darf (alles außer PDF und Bild).
     */
    public record Datei(Path pfad, String dateiname, MediaType mediaType, boolean immerAlsAnhang) {
    }

    /** Dokumente direkt in einem Ordner; unsichtbarer Ordner → 404 wie „gibt es nicht“. */
    @Transactional(readOnly = true)
    public FirmendokumentSeiteDto imOrdner(Long ordnerId, int page, int size, Authentication authentication) {
        FirmendokumentOrdner ordner = ordnerRepository.findById(ordnerId)
                .orElseThrow(() -> nichtGefunden(ORDNER_FEHLT));
        boolean darfAendern = zugriff.verlangeAnsehen(ordner, authentication, () -> nichtGefunden(ORDNER_FEHLT))
                .darfAendern();
        return FirmendokumentMapper.zuSeite(dokumentRepository.findeImOrdner(ordnerId, seite(page, size)),
                d -> darfAendern);
    }

    @Transactional(readOnly = true)
    public FirmendokumentSeiteDto suche(String suchtext, int page, int size, Authentication authentication) {
        String text = suchtext == null ? "" : suchtext.strip();
        if (text.length() < SUCHE_MIN) {
            throw fehler("Bitte mindestens " + SUCHE_MIN + " Zeichen eingeben.");
        }
        if (text.length() > SUCHE_MAX) {
            throw fehler("Der Suchbegriff darf höchstens " + SUCHE_MAX + " Zeichen lang sein.");
        }
        String muster = LikeMuster.enthaelt(text);
        return inSichtbarenOrdnern(authentication, seite(page, size),
                s -> dokumentRepository.suche(muster, s),
                (ids, s) -> dokumentRepository.sucheInOrdnern(muster, ids, s));
    }

    @Transactional(readOnly = true)
    public FirmendokumentSeiteDto faellig(int page, int size, Authentication authentication) {
        LocalDate grenze = LocalDate.now(clock).plusDays(BALD_FAELLIG_TAGE);
        return inSichtbarenOrdnern(authentication, seite(page, size),
                s -> dokumentRepository.findeFaelligBis(grenze, s),
                (ids, s) -> dokumentRepository.findeFaelligBisInOrdnern(grenze, ids, s));
    }

    /** Hochladen braucht „Ändern“ am Zielordner; ein unsichtbarer Ordner gilt als „gibt es nicht mehr“ (400). */
    @Transactional
    public FirmendokumentDto hochladen(MultipartFile datei, Long ordnerId, String name, LocalDate faelligAm,
            String hochgeladenVon, Authentication authentication) {
        FirmendokumentDateiPruefung.pruefe(datei);
        String sauberName = FirmendokumentEingaben.name(name, NAME_FEHLT);
        FirmendokumentDateiPruefung.pruefeName(sauberName);
        FirmendokumentOrdner ordner = zielOrdner(ordnerId, authentication);

        String gespeichert = speichere(datei);
        try {
            Firmendokument dokument = new Firmendokument();
            dokument.setOrdner(ordner);
            dokument.setName(sauberName);
            dokument.setOriginalDateiname(FirmendokumentDateinamen.anzeigeName(datei.getOriginalFilename()));
            dokument.setGespeicherterDateiname(gespeichert);
            dokument.setGroesseBytes(datei.getSize());
            dokument.setHochgeladenAm(LocalDateTime.now(clock));
            dokument.setHochgeladenVon(hochgeladenVon);
            dokument.setFaelligAm(faelligAm);
            return FirmendokumentMapper.zuDto(dokumentRepository.saveAndFlush(dokument), true);
        } catch (RuntimeException e) {
            ablage.loesche(gespeichert);
            throw e;
        }
    }

    /**
     * Umbenennen, Verschieben und Datum: braucht „Ändern“ am bisherigen und am neuen Ordner; aus einem eingeschränkten
     * Zweig unter einen anderen obersten Ordner nur als Admin.
     */
    @Transactional
    public FirmendokumentDto aendern(Long id, FirmendokumentAendernDto eingabe, Authentication authentication) {
        Firmendokument dokument = aenderbaresDokument(id, authentication);
        String name = FirmendokumentEingaben.name(eingabe.name(), NAME_FEHLT);
        FirmendokumentDateiPruefung.pruefeName(name);
        FirmendokumentOrdner ziel = dokument.getOrdner();
        if (!ziel.getId().equals(eingabe.ordnerId())) {
            ziel = zielOrdner(eingabe.ordnerId(), authentication);
            zugriff.verlangeVerschieben(dokument.getOrdner(), ziel, authentication);
        }
        dokument.setName(name);
        dokument.setOrdner(ziel);
        dokument.setFaelligAm(eingabe.faelligAm());
        return FirmendokumentMapper.zuDto(dokument, true);
    }

    /**
     * Löscht Eintrag und Datei endgültig (braucht „Ändern“). Die Datei verschwindet erst nach dem Commit: Scheitert
     * die Transaktion, bleiben Eintrag und Datei zusammen erhalten.
     */
    @Transactional
    public void loeschen(Long id, Authentication authentication) {
        Firmendokument dokument = aenderbaresDokument(id, authentication);
        dokumentRepository.delete(dokument);
        String gespeichert = dokument.getGespeicherterDateiname();
        nachCommit(() -> ablage.loesche(gespeichert));
    }

    /** Datei zum Anzeigen/Herunterladen; „Ansehen“ reicht, unsichtbar → 404. */
    @Transactional(readOnly = true)
    public Datei datei(Long id, Authentication authentication) {
        Firmendokument dokument = finde(id);
        zugriff.verlangeAnsehen(dokument.getOrdner(), authentication, () -> nichtGefunden(DOKUMENT_FEHLT));
        Path pfad = ablage.pfad(dokument.getGespeicherterDateiname());
        if (!Files.isReadable(pfad)) {
            throw nichtGefunden("Die Datei zu diesem Dokument wurde nicht gefunden.");
        }
        String endung = FirmendokumentDateinamen.endungDesDokuments(dokument);
        return new Datei(pfad, FirmendokumentDateinamen.downloadName(dokument.getName(), endung),
                FirmendokumentDateiart.mediaType(endung), FirmendokumentDateiart.aus(endung).immerAlsAnhang());
    }

    /**
     * Seite über alle Ordner, wenn der Angemeldete alles sieht; sonst nur aus den sichtbaren Ordnern (keiner
     * sichtbar → leere Seite ohne Abfrage). Das Ändern-Recht je Dokument kommt aus derselben Berechnung.
     */
    private FirmendokumentSeiteDto inSichtbarenOrdnern(Authentication authentication, PageRequest seite,
            Function<PageRequest, Page<Firmendokument>> ueberAlle,
            BiFunction<Set<Long>, PageRequest, Page<Firmendokument>> inOrdnern) {
        FirmendokumentSichtbarkeit sichtbar = zugriff.sichtbarkeit(authentication);
        if (sichtbar.allesSichtbar()) {
            return FirmendokumentMapper.zuSeite(ueberAlle.apply(seite), d -> true);
        }
        Set<Long> ordnerIds = sichtbar.sichtbareOrdnerIds();
        if (ordnerIds.isEmpty()) {
            return new FirmendokumentSeiteDto(List.of(), 0, seite.getPageNumber(), seite.getPageSize());
        }
        return FirmendokumentMapper.zuSeite(inOrdnern.apply(ordnerIds, seite),
                d -> sichtbar.stufe(d.getOrdner().getId()).darfAendern());
    }

    private Firmendokument finde(Long id) {
        return dokumentRepository.findById(id).orElseThrow(() -> nichtGefunden(DOKUMENT_FEHLT));
    }

    /** Dokument, dessen Ordner der Angemeldete ändern darf; unsichtbar → 404, nur ansehen → 403. */
    private Firmendokument aenderbaresDokument(Long id, Authentication authentication) {
        Firmendokument dokument = finde(id);
        zugriff.verlangeAendern(dokument.getOrdner(), authentication, () -> nichtGefunden(DOKUMENT_FEHLT));
        return dokument;
    }

    /** Zielordner, in dem der Angemeldete ändern darf; fehlt oder unsichtbar → 400, nur ansehen → 403. */
    private FirmendokumentOrdner zielOrdner(Long ordnerId, Authentication authentication) {
        if (ordnerId == null) {
            throw fehler("Bitte einen Ordner auswählen.");
        }
        FirmendokumentOrdner ordner = ordnerRepository.findById(ordnerId).orElseThrow(() -> fehler(ORDNER_FEHLT));
        zugriff.verlangeAendern(ordner, authentication, () -> fehler(ORDNER_FEHLT));
        return ordner;
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
