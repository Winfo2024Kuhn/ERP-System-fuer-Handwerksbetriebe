package org.example.kalkulationsprogramm.service.telefon;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Sprachnachricht;
import org.example.kalkulationsprogramm.domain.TelefonAnruf;
import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.domain.TelefonKontaktZuordenbar;
import org.example.kalkulationsprogramm.dto.Telefon.AnrufbeantworterDto;
import org.example.kalkulationsprogramm.dto.Telefon.KontaktKurzDto;
import org.example.kalkulationsprogramm.repository.SprachnachrichtRepository;
import org.example.kalkulationsprogramm.repository.TelefonAnrufRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Telefon-Einträge für die Benachrichtigungs-Glocke: neue Nachrichten auf dem
 * Anrufbeantworter und verpasste Anrufe, die noch niemand zurückgerufen hat.
 */
@Service
@RequiredArgsConstructor
public class TelefonBenachrichtigungService {

    static final int VERPASSTE_TAGE = 7;
    private static final DateTimeFormatter UHRZEIT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.");

    private final SprachnachrichtRepository nachrichtRepository;
    private final TelefonAnrufRepository anrufRepository;
    private final TelefonEinstellungenService einstellungen;
    private final Clock clock;

    /** Ein Eintrag für die Glocke (Typ SPRACHNACHRICHT oder VERPASSTER_ANRUF). */
    public record Eintrag(String typ, String titel, String untertitel, LocalDateTime zeitpunkt, String link) {
    }

    @Transactional(readOnly = true)
    public List<Eintrag> neueSprachnachrichten() {
        List<AnrufbeantworterDto> abs = einstellungen.anrufbeantworter();
        return nachrichtRepository.findeNeue().stream().map(s -> new Eintrag(
                "SPRACHNACHRICHT",
                wer(s, s.getNummerRoh()),
                abName(abs, s.getAnrufbeantworter()) + " · " + wann(s.getZeitpunkt()) + " · " + dauer(s.getDauerSekunden()),
                s.getZeitpunkt(),
                "/telefon/anrufbeantworter?nachricht=" + s.getId())).toList();
    }

    @Transactional(readOnly = true)
    public List<Eintrag> offeneVerpassteAnrufe() {
        LocalDateTime seit = LocalDateTime.now(clock).minusDays(VERPASSTE_TAGE);
        List<AnrufbeantworterDto> abs = einstellungen.anrufbeantworter();
        return anrufRepository.findeOffeneVerpasste(seit).stream().map(a -> new Eintrag(
                "VERPASSTER_ANRUF",
                wer(a, a.getNummerRoh()),
                (a.getArt() == TelefonAnrufArt.ANRUFBEANTWORTER && a.getAnrufbeantworter() != null
                        ? abName(abs, a.getAnrufbeantworter()) + ", ohne Nachricht"
                        : "Verpasst") + " · " + wann(a.getZeitpunkt()),
                a.getZeitpunkt(),
                "/telefon/anrufe?anruf=" + a.getId())).toList();
    }

    private static String wer(TelefonKontaktZuordenbar e, String nummer) {
        KontaktKurzDto k = RufnummernZuordnungService.kontaktVon(e);
        if (k != null && k.name() != null && !k.name().isBlank()) {
            return k.name();
        }
        return nummer == null || nummer.isBlank() ? "Unbekannte Nummer" : nummer;
    }

    private String wann(LocalDateTime z) {
        LocalDate heute = LocalDate.now(clock);
        if (z.toLocalDate().equals(heute)) {
            return "heute " + UHRZEIT.format(z);
        }
        if (z.toLocalDate().equals(heute.minusDays(1))) {
            return "gestern " + UHRZEIT.format(z);
        }
        return DATUM.format(z) + " " + UHRZEIT.format(z);
    }

    static String abName(List<AnrufbeantworterDto> abs, int index) {
        return abs.stream().filter(a -> a.index() == index).map(AnrufbeantworterDto::name)
                .findFirst().orElse("AB " + (index + 1));
    }

    static String dauer(int sekunden) {
        int s = Math.max(0, sekunden);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
