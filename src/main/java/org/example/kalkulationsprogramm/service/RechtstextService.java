package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.RechtstextArt;
import org.example.kalkulationsprogramm.domain.RechtstextFassung;
import org.example.kalkulationsprogramm.repository.RechtstextFassungRepository;
import org.example.kalkulationsprogramm.util.RechtstextHtml;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bearbeitbare Rechtstexte mit Fassungen. Gilt immer die jüngste gespeicherte Fassung; ohne
 * Fassung der mitgelieferte Standardtext (Fassung 0). Jedes Speichern mit geändertem Text legt
 * eine neue Fassung mit dem heutigen Datum als Stand an – alte Fassungen bleiben unverändert.
 */
@Service
@RequiredArgsConstructor
public class RechtstextService
{
    /** Höchstlänge des HTML, das gespeichert werden darf. */
    static final int MAX_LAENGE = 50_000;

    private final RechtstextFassungRepository repository;
    private final Clock clock;

    private final Map<RechtstextArt, String> standardTexte = new EnumMap<>(RechtstextArt.class);

    /** Der gültige Text einer Art. {@code fassung} 0 = mitgelieferter Standardtext. */
    public record Rechtstext(RechtstextArt art, int fassung, LocalDate stand, LocalDateTime geaendertAm,
            String html, boolean standardtext)
    {
    }

    /** Art aus dem Pfad, Groß-/Kleinschreibung egal; unbekannt = 404. */
    public static RechtstextArt artAus(String wert)
    {
        if (wert != null)
        {
            String name = wert.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            for (RechtstextArt art : RechtstextArt.values())
            {
                if (art.name().equals(name)) return art;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diesen Text gibt es nicht.");
    }

    /** Gültiger Text jeder Art, in der Reihenfolge der Arten. */
    @Transactional(readOnly = true)
    public List<Rechtstext> alle()
    {
        return Arrays.stream(RechtstextArt.values()).map(this::aktuell).toList();
    }

    @Transactional(readOnly = true)
    public Rechtstext aktuell(RechtstextArt art)
    {
        return repository.findFirstByArtOrderByFassungDesc(art)
                .map(RechtstextService::alsRechtstext)
                .orElseGet(() -> new Rechtstext(art, 0, art.getStandardStand(), null, standardHtml(art), true));
    }

    /**
     * Speichert den Text als neue Fassung. Ist er nach dem Bereinigen gleich dem gültigen Text,
     * entsteht keine neue Fassung – sonst hätte jede Fassung ohne Änderung einen neuen Stand.
     */
    @Transactional
    public Rechtstext speichere(RechtstextArt art, String html)
    {
        String bereinigt = pruefeUndBereinige(html);
        Rechtstext gueltig = aktuell(art);
        if (bereinigt.equals(gueltig.html())) return gueltig;
        RechtstextFassung neu = new RechtstextFassung();
        neu.setArt(art);
        neu.setFassung(gueltig.fassung() + 1);
        neu.setStand(LocalDate.now(clock));
        neu.setInhaltHtml(bereinigt);
        neu.setGeaendertAm(LocalDateTime.now(clock));
        try
        {
            // Sofort schreiben: Speichern zwei Admins gleichzeitig, bekommen beide dieselbe Nummer –
            // die Eindeutigkeit (art, fassung) lässt nur einen durch, der andere soll neu laden.
            return alsRechtstext(repository.saveAndFlush(neu));
        }
        catch (DataIntegrityViolationException e)
        {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Der Text wurde gerade von jemand anderem gespeichert. Bitte neu laden und noch einmal ändern.");
        }
    }

    /** Setzt den mitgelieferten Standardtext als neue Fassung ein (alte bleiben erhalten). */
    @Transactional
    public Rechtstext setzeStandard(RechtstextArt art)
    {
        return speichere(art, standardHtml(art));
    }

    @Transactional(readOnly = true)
    public List<RechtstextFassung> fassungen(RechtstextArt art)
    {
        return repository.findByArtOrderByFassungDesc(art);
    }

    @Transactional(readOnly = true)
    public RechtstextFassung fassung(RechtstextArt art, int nummer)
    {
        return repository.findByArtAndFassung(art, nummer)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Diese Fassung gibt es nicht."));
    }

    /**
     * Prüft Länge und Inhalt und liefert das bereinigte HTML. Wirft 400 mit einer Meldung, die der
     * Nutzer versteht.
     */
    public String pruefeUndBereinige(String html)
    {
        if (html != null && html.length() > MAX_LAENGE)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Text ist zu lang. Bitte kürzen Sie ihn auf höchstens 50.000 Zeichen.");
        }
        String bereinigt = RechtstextHtml.bereinige(html);
        if (RechtstextHtml.istLeer(bereinigt))
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Text ist leer. Ohne Text kann keine Belehrung verschickt werden.");
        }
        return bereinigt;
    }

    /** Mitgelieferter Standardtext (bereinigt, einmal geladen). */
    public String standardHtml(RechtstextArt art)
    {
        synchronized (standardTexte)
        {
            return standardTexte.computeIfAbsent(art, RechtstextService::ladeStandard);
        }
    }

    private static String ladeStandard(RechtstextArt art)
    {
        try (InputStream in = new ClassPathResource(art.getStandardDatei()).getInputStream())
        {
            return RechtstextHtml.bereinige(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Standardtext für " + art + " fehlt: " + art.getStandardDatei(), e);
        }
    }

    private static Rechtstext alsRechtstext(RechtstextFassung f)
    {
        return new Rechtstext(f.getArt(), f.getFassung(), f.getStand(), f.getGeaendertAm(), f.getInhaltHtml(), false);
    }
}
