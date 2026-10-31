package org.example.kalkulationsprogramm.service.ids;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.ids.IdsVorgang;
import org.example.kalkulationsprogramm.domain.ids.IdsVorgangStatus;
import org.example.kalkulationsprogramm.domain.ids.IdsWarenkorbPosition;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsArtikelstammAnfrageDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsArtikelstammErgebnisDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsVorgangDto;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.service.ArtikelServiceContract;
import org.example.kalkulationsprogramm.service.AufschlagService;
import org.example.kalkulationsprogramm.service.LieferantArtikelpreisService;
import org.example.kalkulationsprogramm.util.HtmlKlartext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Verbindet einen Shop-Warenkorb mit dem eigenen Artikelstamm: findet bekannte Artikel über die
 * Artikelnummer des Lieferanten, nennt den Aufschlag, der für sie gilt, und schreibt auf Wunsch neue
 * Einkaufspreise (Quelle {@link PreisQuelle#IDS_SHOP}) oder legt unbekannte Artikel an.
 * Nutzbar für jeden Verwender eines Warenkorbs (Dokumenteditor, später Beschaffung).
 */
@Service
@RequiredArgsConstructor
public class IdsArtikelstammAbgleich {

    static final BigDecimal MAX_PREIS = new BigDecimal("1000000000");
    static final int MAX_BEZEICHNUNG = 255;
    private static final String NOTIZ = "Aus Warenkorb im Lieferanten-Shop";
    private static final java.util.regex.Pattern LEERRAUM = java.util.regex.Pattern.compile("\\s++");

    private final LieferantenArtikelPreiseRepository preiseRepository;
    private final LieferantArtikelpreisService preisService;
    private final ArtikelServiceContract artikelService;
    private final AufschlagService aufschlagService;
    private final IdsShopBesuchService shopBesuch;

    /** Shop-Besuch des Benutzers laden und als Ansicht liefern – in einer Transaktion. */
    @Transactional
    public IdsVorgangDto ansicht(Long vorgangId, Long frontendUserId) {
        return ansicht(shopBesuch.lade(vorgangId, frontendUserId));
    }

    /** Shop-Besuch des Benutzers laden und den Artikelstamm abgleichen – in einer Transaktion. */
    @Transactional
    public IdsArtikelstammErgebnisDto uebernimm(Long vorgangId, Long frontendUserId, IdsArtikelstammAnfrageDto anfrage) {
        return uebernimm(shopBesuch.lade(vorgangId, frontendUserId), anfrage);
    }

    /**
     * Shop-Besuch samt Warenkorb für die Oberfläche, mit Treffern im Artikelstamm und geltendem Aufschlag.
     * Nur innerhalb einer laufenden Transaktion aufrufen (Lieferant und Positionen werden nachgeladen) –
     * von außen {@link #ansicht(Long, Long)} nutzen.
     */
    IdsVorgangDto ansicht(IdsVorgang vorgang) {
        Long lieferantId = vorgang.getLieferant().getId();
        List<IdsWarenkorbPosition> positionen = vorgang.getPositionen();
        Map<String, LieferantenArtikelPreise> treffer =
                stammTreffer(lieferantId, positionen.stream().map(IdsWarenkorbPosition::getArtikelnummer).toList());
        BigDecimal allgemein = aufschlagService.allgemein();
        Function<Artikel, AufschlagService.Wirksam> aufschlag = aufschlagService.fuerListe();

        List<IdsVorgangDto.Position> zeilen = positionen.stream().map(p -> {
            LieferantenArtikelPreise stamm = treffer.get(schluessel(p.getArtikelnummer()));
            AufschlagService.Wirksam wirksam = stamm != null
                    ? aufschlag.apply(stamm.getArtikel())
                    : new AufschlagService.Wirksam(allgemein,
                            allgemein != null ? AufschlagService.Quelle.ALLGEMEIN : AufschlagService.Quelle.KEINER, null);
            return new IdsVorgangDto.Position(p.getPositionIndex(), p.getArtikelnummer(), p.getBezeichnung(),
                    p.getLangtext(), p.getMenge(), p.getEinheit(), p.getEinkaufspreis(), p.getHinweis(),
                    wirksam.prozent(), wirksam.quelle(),
                    stamm == null ? null : new IdsVorgangDto.Stamm(stamm.getArtikel().getId(),
                            anzeigename(stamm.getArtikel()), stamm.getPreis()));
        }).toList();

        return new IdsVorgangDto(vorgang.getId(), lieferantId, vorgang.getLieferant().getLieferantenname(),
                vorgang.getStatus(), vorgang.getFehlertext(), vorgang.isMitBestellung(), vorgang.getEmpfangenAm(),
                allgemein, zeilen);
    }

    /**
     * Schreibt die gewünschten Änderungen in den Artikelstamm. Zeilen, die inzwischen bekannt sind,
     * werden aktualisiert statt doppelt angelegt.
     */
    /** Nur innerhalb einer laufenden Transaktion aufrufen; von außen {@link #uebernimm(Long, Long, IdsArtikelstammAnfrageDto)}. */
    IdsArtikelstammErgebnisDto uebernimm(IdsVorgang vorgang, IdsArtikelstammAnfrageDto anfrage) {
        if (vorgang.getStatus() != IdsVorgangStatus.EMPFANGEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Für diesen Shop-Besuch ist kein Warenkorb angekommen.");
        }
        if (anfrage == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte angeben, was in den Artikelstamm soll.");
        }
        Long lieferantId = vorgang.getLieferant().getId();
        // Werte jetzt kopieren: das Schreiben eines Preisstands leert den Persistence Context.
        Map<Integer, Zeile> zeilen = new HashMap<>();
        for (IdsWarenkorbPosition p : vorgang.getPositionen()) {
            zeilen.put(p.getPositionIndex(), new Zeile(p.getArtikelnummer(), p.getLangtext(), p.getEinheit(), p.getEinkaufspreis()));
        }

        Map<Integer, Aenderung> aenderungen = new LinkedHashMap<>();
        for (IdsArtikelstammAnfrageDto.Aktualisieren a : nichtNull(anfrage.aktualisieren())) {
            aenderungen.putIfAbsent(gueltigerIndex(a.index(), zeilen), new Aenderung(preis(a.einkaufspreis(), true), null, false));
        }
        for (IdsArtikelstammAnfrageDto.NeuAnlegen n : nichtNull(anfrage.neuAnlegen())) {
            aenderungen.putIfAbsent(gueltigerIndex(n.index(), zeilen),
                    new Aenderung(preis(n.einkaufspreis(), false), bezeichnung(n.bezeichnung()), true));
        }
        Set<String> nummern = new LinkedHashSet<>();
        aenderungen.keySet().forEach(i -> nummern.add(zeilen.get(i).artikelnummer()));
        Map<String, LieferantenArtikelPreise> treffer = stammTreffer(lieferantId, nummern);
        Map<String, Long> bekannteArtikel = new HashMap<>();
        Map<String, BigDecimal> bekanntePreise = new HashMap<>();
        treffer.forEach((k, v) -> {
            bekannteArtikel.put(k, v.getArtikel().getId());
            bekanntePreise.put(k, v.getPreis());
        });

        int aktualisiert = 0;
        int angelegt = 0;
        int unveraendert = 0;
        Map<Integer, Long> artikelIds = new LinkedHashMap<>();
        for (Map.Entry<Integer, Aenderung> e : aenderungen.entrySet()) {
            Zeile zeile = zeilen.get(e.getKey());
            Aenderung aenderung = e.getValue();
            String key = schluessel(zeile.artikelnummer());
            Long artikelId = bekannteArtikel.get(key);
            if (artikelId != null) {
                artikelIds.put(e.getKey(), artikelId);
                if (aenderung.preis() == null || gleich(bekanntePreise.get(key), aenderung.preis())) {
                    unveraendert++;
                    continue;
                }
                preisService.schreibePreisstand(lieferantId, artikelId, aenderung.preis(), zeile.artikelnummer(),
                        zeile.quelleFuer(aenderung.preis()), NOTIZ);
                bekanntePreise.put(key, aenderung.preis());
                aktualisiert++;
            } else if (aenderung.neu()) {
                ArtikelCreateDto dto = new ArtikelCreateDto();
                dto.setProduktname(aenderung.bezeichnung());
                dto.setProdukttext(zeile.langtext());
                dto.setExterneArtikelnummer(zeile.artikelnummer());
                dto.setLieferantId(lieferantId);
                dto.setPreis(aenderung.preis());
                dto.setPreiseinheit(zeile.einheit());
                dto.setVerrechnungseinheit(IdsEinheiten.verrechnungseinheit(zeile.einheit()));
                Long neueId = artikelService.erstelleArtikel(dto, zeile.quelleFuer(aenderung.preis())).getId();
                bekannteArtikel.put(key, neueId);
                bekanntePreise.put(key, aenderung.preis());
                artikelIds.put(e.getKey(), neueId);
                angelegt++;
            } else {
                // „Preis aktualisieren“ für einen Artikel, den es im Stamm (nicht mehr) gibt.
                unveraendert++;
            }
        }
        return new IdsArtikelstammErgebnisDto(aktualisiert, angelegt, unveraendert, artikelIds);
    }

    /** Aktuelle Preiseinträge dieses Lieferanten zu den Artikelnummern; Schlüssel = normalisierte Nummer. */
    Map<String, LieferantenArtikelPreise> stammTreffer(Long lieferantId, Collection<String> artikelnummern) {
        Set<String> gesucht = new LinkedHashSet<>();
        for (String n : artikelnummern) {
            String k = schluessel(n);
            if (k != null) {
                gesucht.add(k);
            }
        }
        Map<String, LieferantenArtikelPreise> ergebnis = new HashMap<>();
        if (gesucht.isEmpty()) {
            return ergebnis;
        }
        for (LieferantenArtikelPreise p : preiseRepository.findByLieferant_IdAndAktuellTrueAndExterneArtikelnummerIn(lieferantId, gesucht)) {
            String k = schluessel(p.getExterneArtikelnummer());
            // Altbestand kann mehrere aktuelle Zeilen je Nummer haben: der kleinste Artikel gewinnt, damit es stabil bleibt.
            ergebnis.merge(k, p, (alt, neu) -> alt.getArtikel().getId() <= neu.getArtikel().getId() ? alt : neu);
        }
        return ergebnis;
    }

    /** Vergleichsschlüssel einer Artikelnummer (siehe Repository: nur ASCII wird zuverlässig gefunden). */
    static String schluessel(String artikelnummer) {
        if (artikelnummer == null || artikelnummer.isBlank()) {
            return null;
        }
        return LEERRAUM.matcher(artikelnummer.trim()).replaceAll(" ").toUpperCase(Locale.ROOT);
    }

    private static String anzeigename(Artikel a) {
        if (a.getKurzbeschreibung() != null && !a.getKurzbeschreibung().isBlank()) {
            return a.getKurzbeschreibung();
        }
        return a.getProduktname() != null ? a.getProduktname() : a.getArtikelnummer();
    }

    private static int gueltigerIndex(Integer index, Map<Integer, Zeile> zeilen) {
        if (index == null || !zeilen.containsKey(index)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diese Zeile gibt es im Warenkorb nicht.");
        }
        return index;
    }

    private static BigDecimal preis(BigDecimal preis, boolean pflicht) {
        if (preis == null) {
            if (pflicht) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zum Aktualisieren bitte einen Einkaufspreis angeben.");
            }
            return null;
        }
        if (preis.signum() < 0 || preis.compareTo(MAX_PREIS) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Einkaufspreis ist ungültig.");
        }
        return preis;
    }

    private static String bezeichnung(String roh) {
        String text = roh == null ? "" : HtmlKlartext.entferneTags(roh).trim();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Für einen neuen Artikel bitte eine Bezeichnung angeben.");
        }
        if (text.length() > MAX_BEZEICHNUNG) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Die Bezeichnung darf höchstens " + MAX_BEZEICHNUNG + " Zeichen lang sein.");
        }
        return text;
    }

    private static boolean gleich(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private static <T> List<T> nichtNull(List<T> liste) {
        return liste == null ? List.of() : liste.stream().filter(Objects::nonNull).toList();
    }

    /** @param shopPreis Einkaufspreis, wie ihn der Shop geschickt hat ({@code null} ohne Preis) */
    private record Zeile(String artikelnummer, String langtext, String einheit, BigDecimal shopPreis) {

        /** Stammt der Preis aus dem Shop oder hat ihn der Nutzer im Dialog geändert/eingetragen? */
        PreisQuelle quelleFuer(BigDecimal preis) {
            return preis == null || gleich(shopPreis, preis) ? PreisQuelle.IDS_SHOP : PreisQuelle.MANUELL;
        }
    }

    private record Aenderung(BigDecimal preis, String bezeichnung, boolean neu) {
    }
}
