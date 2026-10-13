package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Welcher Aufschlag auf den Einkaufspreis gilt. Die Regel ist <b>„das Genauere gewinnt“</b>: erst der Aufschlag am <b>Artikel</b>, sonst der der
 * <b>Kategorie</b> (hat sie keinen, der der nächsten Oberkategorie), sonst der <b>allgemeine</b> Aufschlag. Ist nirgends einer eingestellt, gibt es keinen,
 * und die Position zeigt den Einkaufspreis mit dem Hinweis „ohne Aufschlag“ (wie bisher).
 */
@Service
@RequiredArgsConstructor
public class AufschlagService {

    static final String ALLGEMEIN_SCHLUESSEL = "kalkulation.aufschlag.allgemein";
    /** Wie beim Aufschlag am Artikel: höchstens 999,99 Prozent. */
    static final BigDecimal MAX_PROZENT = new BigDecimal("999.99");
    private static final int MAX_TIEFE = 30;

    private final SystemSettingsService einstellungen;
    private final KategorieRepository kategorien;

    public enum Quelle { ARTIKEL, KATEGORIE, ALLGEMEIN, KEINER }

    /** Der geltende Aufschlag und woher er kommt; {@code quellenName} ist bei einer Kategorie deren Name. */
    public record Wirksam(BigDecimal prozent, Quelle quelle, String quellenName) { }

    /** Eine Kategorie mit eigenem Aufschlag ({@code prozent}, leer = keiner) und dem, was für ihre Artikel gilt. */
    public record KategorieZeile(Integer id, Integer oberkategorieId, String pfad, BigDecimal prozent, BigDecimal wirksamProzent, Quelle wirksamQuelle,
            String wirksamVon) { }

    public record Uebersicht(BigDecimal allgemein, List<KategorieZeile> kategorien) { }

    @Transactional(readOnly = true)
    public Wirksam wirksamer(Artikel artikel) {
        if (artikel == null) {
            return new Wirksam(null, Quelle.KEINER, null);
        }
        if (artikel.getVerkaufsaufschlagProzent() != null) {
            return new Wirksam(artikel.getVerkaufsaufschlagProzent(), Quelle.ARTIKEL, null);
        }
        return ausKategorieOderAllgemein(artikel.getKategorie(), this::allgemein);
    }

    @Transactional(readOnly = true)
    public BigDecimal allgemein() {
        String wert = einstellungen.get(ALLGEMEIN_SCHLUESSEL, "");
        if (wert == null || wert.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(wert.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Uebersicht uebersicht() {
        List<Kategorie> alle = kategorien.findAll();
        Map<Integer, Kategorie> nachId = new HashMap<>();
        alle.forEach(k -> nachId.put(k.getId(), k));
        List<KategorieZeile> zeilen = new ArrayList<>();
        BigDecimal allgemein = allgemein();
        for (Kategorie k : alle) {
            Wirksam w = ausKategorieOderAllgemein(k, () -> allgemein);
            Kategorie eltern = k.getParentKategorie();
            zeilen.add(new KategorieZeile(k.getId(), eltern == null ? null : eltern.getId(), pfad(k), k.getVerkaufsaufschlagProzent(), w.prozent(), w.quelle(),
                    w.quellenName()));
        }
        zeilen.sort(Comparator.comparing((KategorieZeile z) -> z.pfad().toLowerCase(Locale.ROOT)));
        return new Uebersicht(allgemein, zeilen);
    }

    @Transactional
    public Uebersicht setzeAllgemein(BigDecimal prozent) {
        pruefe(prozent);
        einstellungen.save(ALLGEMEIN_SCHLUESSEL, prozent == null ? "" : prozent.setScale(2, RoundingMode.HALF_UP).toPlainString(), "Allgemeiner Aufschlag in Prozent auf den Einkaufspreis");
        return uebersicht();
    }

    @Transactional
    public Uebersicht setzeKategorie(Integer kategorieId, BigDecimal prozent) {
        pruefe(prozent);
        Kategorie k = kategorien.findById(kategorieId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Die Kategorie gibt es nicht."));
        k.setVerkaufsaufschlagProzent(prozent == null ? null : prozent.setScale(2, RoundingMode.HALF_UP));
        kategorien.save(k);
        return uebersicht();
    }

    private Wirksam ausKategorieOderAllgemein(Kategorie start, Supplier<BigDecimal> allgemeinQuelle) {
        Set<Integer> gesehen = new HashSet<>();
        Kategorie k = start;
        for (int tiefe = 0; k != null && tiefe < MAX_TIEFE && gesehen.add(k.getId()); tiefe++) {
            if (k.getVerkaufsaufschlagProzent() != null) {
                return new Wirksam(k.getVerkaufsaufschlagProzent(), Quelle.KATEGORIE, name(k));
            }
            k = k.getParentKategorie();
        }
        BigDecimal allgemein = allgemeinQuelle.get();
        return allgemein != null ? new Wirksam(allgemein, Quelle.ALLGEMEIN, null) : new Wirksam(null, Quelle.KEINER, null);
    }

    private static String name(Kategorie k) {
        return k.getBeschreibung() == null || k.getBeschreibung().isBlank() ? "Kategorie " + k.getId() : k.getBeschreibung().strip();
    }

    private static String pfad(Kategorie k) {
        List<String> teile = new ArrayList<>();
        Set<Integer> gesehen = new HashSet<>();
        for (Kategorie x = k; x != null && gesehen.add(x.getId()) && teile.size() < MAX_TIEFE; x = x.getParentKategorie()) {
            teile.add(0, name(x));
        }
        return String.join(" › ", teile);
    }

    private static void pruefe(BigDecimal prozent) {
        if (prozent != null && (prozent.signum() < 0 || prozent.compareTo(MAX_PROZENT) > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Aufschlag muss zwischen 0 und 999,99 Prozent liegen.");
        }
    }
}
