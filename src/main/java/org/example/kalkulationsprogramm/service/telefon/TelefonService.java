package org.example.kalkulationsprogramm.service.telefon;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.KontaktRufnummer;
import org.example.kalkulationsprogramm.domain.Sprachnachricht;
import org.example.kalkulationsprogramm.domain.TelefonAnruf;
import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.domain.TelefonKontaktZuordenbar;
import org.example.kalkulationsprogramm.domain.TelefonZuordnung;
import org.example.kalkulationsprogramm.dto.Telefon.KontaktKurzDto;
import org.example.kalkulationsprogramm.dto.Telefon.KontaktRufnummerDto;
import org.example.kalkulationsprogramm.dto.Telefon.SprachnachrichtDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonAnrufDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonZuordnenDto;
import org.example.kalkulationsprogramm.repository.KontaktRufnummerRepository;
import org.example.kalkulationsprogramm.repository.SprachnachrichtRepository;
import org.example.kalkulationsprogramm.repository.SteuerberaterKontaktRepository;
import org.example.kalkulationsprogramm.repository.TelefonAnrufRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Anrufliste, Anrufbeantworter und Zuordnung für die Oberfläche.
 * Die Rechteprüfung passiert im Controller über {@link TelefonBerechtigungService}.
 */
@Service
@RequiredArgsConstructor
public class TelefonService {

    static final int MAX_SEITENGROESSE = 100;

    private final TelefonAnrufRepository anrufRepository;
    private final SprachnachrichtRepository nachrichtRepository;
    private final KontaktRufnummerRepository kontaktRufnummerRepository;
    private final SteuerberaterKontaktRepository steuerberaterRepository;
    private final RufnummernZuordnungService zuordnung;
    private final SprachnachrichtDateiablage ablage;
    private final Clock clock;

    /**
     * @param tag        nur Anrufe dieses Tages; null = alle Tage
     * @param kontaktart KUNDE, LIEFERANT oder STEUERBERATER; null = alle
     */
    @Transactional(readOnly = true)
    public Page<TelefonAnrufDto> anrufe(TelefonAnrufArt art, boolean nurUnbekannt, String suche, LocalDate tag,
                                        String kontaktart, Long kundeId, Long lieferantId, int seite, int groesse) {
        PageRequest seitenAnfrage = PageRequest.of(Math.max(0, seite), Math.clamp(groesse, 1, MAX_SEITENGROESSE),
                Sort.by(Sort.Order.desc("zeitpunkt"), Sort.Order.desc("id")));
        return zuDtos(anrufRepository.findAll(
                AnrufSuche.filter(art, nurUnbekannt, kundeId, lieferantId, suche, tag, kontaktart), seitenAnfrage));
    }

    /**
     * Genau die verpassten Anrufe, die die Glocke zählt: noch nicht zurückgerufen
     * bzw. seitdem nicht wieder angenommen, aus den letzten Tagen.
     */
    @Transactional(readOnly = true)
    public Page<TelefonAnrufDto> offeneVerpassteAnrufe(int seite, int groesse) {
        LocalDateTime seit = LocalDateTime.now(clock).minusDays(TelefonBenachrichtigungService.VERPASSTE_TAGE);
        PageRequest seitenAnfrage = PageRequest.of(Math.max(0, seite), Math.clamp(groesse, 1, MAX_SEITENGROESSE));
        return zuDtos(anrufRepository.findeOffeneVerpasste(seit, seitenAnfrage));
    }

    private Page<TelefonAnrufDto> zuDtos(Page<TelefonAnruf> treffer) {
        List<Long> ids = treffer.getContent().stream().map(TelefonAnruf::getId).toList();
        Map<Long, Long> nachrichtZuAnruf = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Sprachnachricht s : nachrichtRepository.findByAnrufIdIn(ids)) {
                nachrichtZuAnruf.putIfAbsent(s.getAnruf().getId(), s.getId());
            }
        }
        RufnummernZuordnungService.Verzeichnis verzeichnis = zuordnung.verzeichnis();
        return treffer.map(a -> new TelefonAnrufDto(
                a.getId(), a.getZeitpunkt(), a.getArt().name(), a.getAnrufbeantworter(),
                a.getNummerRoh(), a.getEigeneNummer(), a.getDauerMinuten(), a.getNameFritzbox(),
                a.getZuordnung().name(), RufnummernZuordnungService.kontaktVon(a),
                kandidaten(a, verzeichnis), nachrichtZuAnruf.get(a.getId())));
    }

    /** @param tag nur Nachrichten dieses Tages; null = alle Tage */
    @Transactional(readOnly = true)
    public List<SprachnachrichtDto> sprachnachrichten(boolean nurNeue, Integer anrufbeantworter, LocalDate tag,
                                                      Long kundeId, Long lieferantId) {
        RufnummernZuordnungService.Verzeichnis verzeichnis = zuordnung.verzeichnis();
        return nachrichtRepository.suche(nurNeue, anrufbeantworter, kundeId, lieferantId,
                        tag == null ? null : tag.atStartOfDay(),
                        tag == null ? null : tag.plusDays(1).atStartOfDay()).stream()
                .map(s -> toDto(s, verzeichnis))
                .toList();
    }

    @Transactional(readOnly = true)
    public long anzahlNeueSprachnachrichten() {
        return nachrichtRepository.countByAbgehoertAmIsNull();
    }

    @Transactional
    public SprachnachrichtDto setzeAbgehoert(Long id, boolean abgehoert, FrontendUserProfile von) {
        Sprachnachricht s = nachricht(id);
        if (abgehoert) {
            if (s.getAbgehoertAm() == null) {
                s.setAbgehoertAm(LocalDateTime.now(clock));
                s.setAbgehoertVon(von);
            }
        } else {
            s.setAbgehoertAm(null);
            s.setAbgehoertVon(null);
        }
        return toDto(s, zuordnung.verzeichnis());
    }

    /** Pfad der Audiodatei einer Nachricht. */
    @Transactional(readOnly = true)
    public Path audio(Long id) {
        return ablage.pfad(nachricht(id).getDateiName());
    }

    @Transactional
    public TelefonAnrufDto ordneAnrufZu(Long id, TelefonZuordnenDto dto) {
        TelefonAnruf a = anruf(id);
        ordneZu(a, a.getNummerRoh(), dto);
        return einzelnerAnruf(a);
    }

    @Transactional
    public TelefonAnrufDto hebeAnrufZuordnungAuf(Long id) {
        TelefonAnruf a = anruf(id);
        zuordnung.hebeZuordnungAuf(a);
        return einzelnerAnruf(a);
    }

    @Transactional
    public SprachnachrichtDto ordneNachrichtZu(Long id, TelefonZuordnenDto dto) {
        Sprachnachricht s = nachricht(id);
        ordneZu(s, s.getNummerRoh(), dto);
        return toDto(s, zuordnung.verzeichnis());
    }

    @Transactional
    public SprachnachrichtDto hebeNachrichtZuordnungAuf(Long id) {
        Sprachnachricht s = nachricht(id);
        zuordnung.hebeZuordnungAuf(s);
        return toDto(s, zuordnung.verzeichnis());
    }

    @Transactional(readOnly = true)
    public List<KontaktRufnummerDto> kontaktRufnummern(Long kundeId, Long lieferantId) {
        List<KontaktRufnummer> liste = kundeId != null
                ? kontaktRufnummerRepository.findByKundeIdOrderByAngelegtAmAsc(kundeId)
                : kontaktRufnummerRepository.findByLieferantIdOrderByAngelegtAmAsc(lieferantId);
        return liste.stream().map(r -> new KontaktRufnummerDto(r.getId(), r.getNummerRoh())).toList();
    }

    /** Kanzleien zur Auswahl beim Zuordnen – es sind nur wenige, deshalb ohne Suche. */
    @Transactional(readOnly = true)
    public List<KontaktKurzDto> steuerberaterAuswahl() {
        return steuerberaterRepository.findAllFuerAuswahl().stream()
                .map(s -> new KontaktKurzDto(KontaktKurzDto.STEUERBERATER, s.getId(), s.getName(), null, null))
                .toList();
    }

    @Transactional
    public void loescheKontaktRufnummer(Long id) {
        KontaktRufnummer r = kontaktRufnummerRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Rufnummer nicht gefunden"));
        kontaktRufnummerRepository.delete(r);
        zuordnung.verwerfeCache();
    }

    /**
     * Von Hand zuordnen. Mit "Nummer merken" wird die Nummer am Kontakt gespeichert
     * und alle bisher unbekannten Anrufe/Nachrichten dieser Nummer werden nachgezogen.
     */
    private void ordneZu(TelefonKontaktZuordenbar eintrag, String nummerRoh, TelefonZuordnenDto dto) {
        zuordnung.ordneManuellZu(eintrag, dto.kundeId(), dto.lieferantId(), dto.steuerberaterId());
        String normalisiert = eintrag.getNummerNormalisiert();
        if (dto.nummerMerken() && normalisiert != null) {
            zuordnung.merkeNummer(nummerRoh, normalisiert, dto.kundeId(), dto.lieferantId(), dto.steuerberaterId());
            RufnummernZuordnungService.Verzeichnis verzeichnis = zuordnung.frischesVerzeichnis();
            for (TelefonAnruf a : anrufRepository.findByZuordnungAndNummerNormalisiert(TelefonZuordnung.KEINE, normalisiert)) {
                zuordnung.ordneAutomatischZu(a, verzeichnis);
            }
            for (Sprachnachricht s : nachrichtRepository.findByZuordnungAndNummerNormalisiert(TelefonZuordnung.KEINE, normalisiert)) {
                zuordnung.ordneAutomatischZu(s, verzeichnis);
            }
        }
    }

    private TelefonAnrufDto einzelnerAnruf(TelefonAnruf a) {
        Long nachrichtId = nachrichtRepository.findByAnrufId(a.getId()).stream()
                .map(Sprachnachricht::getId).findFirst().orElse(null);
        return new TelefonAnrufDto(a.getId(), a.getZeitpunkt(), a.getArt().name(), a.getAnrufbeantworter(),
                a.getNummerRoh(), a.getEigeneNummer(), a.getDauerMinuten(), a.getNameFritzbox(),
                a.getZuordnung().name(), RufnummernZuordnungService.kontaktVon(a),
                kandidaten(a, zuordnung.verzeichnis()), nachrichtId);
    }

    private SprachnachrichtDto toDto(Sprachnachricht s, RufnummernZuordnungService.Verzeichnis verzeichnis) {
        return new SprachnachrichtDto(s.getId(), s.getAnrufbeantworter(), s.getZeitpunkt(), s.getNummerRoh(),
                s.getDauerSekunden(), s.getAbgehoertAm() == null, s.getAbgehoertAm(),
                s.getAbgehoertVon() != null ? s.getAbgehoertVon().getDisplayName() : null,
                s.getZuordnung().name(), RufnummernZuordnungService.kontaktVon(s), kandidaten(s, verzeichnis),
                s.getAnruf() != null ? s.getAnruf().getNameFritzbox() : null);
    }

    private static List<KontaktKurzDto> kandidaten(TelefonKontaktZuordenbar e,
                                                   RufnummernZuordnungService.Verzeichnis verzeichnis) {
        if (e.getZuordnung() != TelefonZuordnung.KEINE) {
            return List.of();
        }
        RufnummernZuordnungService.Treffer t = verzeichnis.finde(e.getNummerNormalisiert());
        return t.mehrdeutig() ? t.kontakte() : List.of();
    }

    private TelefonAnruf anruf(Long id) {
        return anrufRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Anruf nicht gefunden"));
    }

    private Sprachnachricht nachricht(Long id) {
        return nachrichtRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Sprachnachricht nicht gefunden"));
    }
}
