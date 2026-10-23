package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieLoeschPruefungDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieLoeschenDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Ordner (Produktkategorien) umsortieren und löschen – auch wenn noch Unterordner,
 * Leistungen oder Projekte daran hängen. Gebuchte Arbeitszeiten werden dabei nie gelöscht;
 * ändert sich ihre Kategorie, entsteht ein GoBD-Protokolleintrag.
 */
@Service
@RequiredArgsConstructor
public class ProduktkategorieVerwaltungService {

    /** So viele Einträge je Liste zeigt der Lösch-Dialog; die Zähler nennen den Rest. */
    static final int MAX_LISTE = 20;

    private static final String NICHT_GEFUNDEN = "Diesen Ordner gibt es nicht mehr.";
    private static final String ZIEL_FEHLT = "Der gewählte Ziel-Ordner existiert nicht mehr.";

    private final ProduktkategorieRepository produktkategorieRepository;
    private final LeistungRepository leistungRepository;
    private final ProjektProduktkategorieRepository projektProduktkategorieRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;
    private final ZeitbuchungAuditService zeitbuchungAuditService;
    private final AngemeldeterMitarbeiterService angemeldeterMitarbeiterService;
    private final ProduktkategorieService produktkategorieService;
    private final ProduktkategorieMapper produktkategorieMapper;

    enum Modus { UMZIEHEN, ALLES }

    @Transactional
    public ProduktkategorieResponseDto verschiebe(Long id, Long neuerParentId) {
        KategorieBaum baum = ladeBaum();
        Produktkategorie kategorie = ordner(baum, id);
        Produktkategorie ziel = null;
        if (neuerParentId != null) {
            ziel = Optional.ofNullable(baum.get(neuerParentId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, ZIEL_FEHLT));
            if (baum.mitUnterordnern(id).contains(neuerParentId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ein Ordner kann nicht in sich selbst oder in einen eigenen Unterordner verschoben werden.");
            }
            if (leistungRepository.existsByKategorieId(neuerParentId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "In „" + ziel.getBezeichnung() + "“ liegen Leistungen. Dort kann kein Unterordner hinein.");
            }
        }
        kategorie.setUebergeordneteKategorie(ziel);
        Produktkategorie gespeichert = produktkategorieRepository.save(kategorie);
        return produktkategorieMapper.toProduktkategorieResponseDtoWithLeaf(gespeichert, !baum.hatKinder(id));
    }

    @Transactional(readOnly = true)
    public KategorieLoeschPruefungDto pruefeLoeschen(Long id) {
        KategorieBaum baum = ladeBaum();
        Produktkategorie kategorie = ordner(baum, id);
        List<Long> ids = baum.mitUnterordnern(id);
        List<Long> unterordnerIds = ids.subList(1, ids.size());

        List<Leistung> leistungen = new ArrayList<>(leistungRepository.findByKategorieIdIn(ids));
        leistungen.sort(Comparator.comparing(Leistung::getBezeichnung, String.CASE_INSENSITIVE_ORDER));

        List<ProjektProduktkategorie> zuordnungen = projektProduktkategorieRepository.findeMitProjekt(ids);
        Map<Long, Projekt> projekte = new LinkedHashMap<>();
        zuordnungen.forEach(ppk -> projekte.putIfAbsent(ppk.getProjekt().getId(), ppk.getProjekt()));
        List<Projekt> projektListe = new ArrayList<>(projekte.values());
        projektListe.sort(Comparator.comparing(Projekt::getId, Comparator.reverseOrder()));
        long zeitbuchungen = zuordnungen.isEmpty() ? 0
                : zeitbuchungRepository.countByProjektProduktkategorieIdIn(
                        zuordnungen.stream().map(ProjektProduktkategorie::getId).toList());

        return new KategorieLoeschPruefungDto(
                id,
                kategorie.getBezeichnung(),
                baum.pfad(id),
                !unterordnerIds.isEmpty() || !leistungen.isEmpty() || !projekte.isEmpty(),
                unterordnerIds.size(),
                leistungen.size(),
                projekte.size(),
                zeitbuchungen,
                unterordnerIds.stream().limit(MAX_LISTE)
                        .map(u -> new KategorieLoeschPruefungDto.Unterordner(u, baum.get(u).getBezeichnung(),
                                baum.pfad(u)))
                        .toList(),
                leistungen.stream().limit(MAX_LISTE)
                        .map(l -> new KategorieLoeschPruefungDto.LeistungEintrag(l.getId(), l.getBezeichnung(),
                                l.getKategorie() != null ? l.getKategorie().getId() : null))
                        .toList(),
                projektListe.stream().limit(MAX_LISTE)
                        .map(p -> new KategorieLoeschPruefungDto.ProjektEintrag(p.getId(), p.getBauvorhaben(),
                                p.getAuftragsnummer(), p.getKunde()))
                        .toList());
    }

    @Transactional
    public void loesche(Long id, KategorieLoeschenDto auftrag, Authentication authentication) {
        Modus modus = leseModus(auftrag);
        KategorieBaum baum = ladeBaum();
        Produktkategorie kategorie = ordner(baum, id);
        BearbeiterQuelle bearbeiter = new BearbeiterQuelle(authentication);
        if (modus == Modus.UMZIEHEN) {
            ziehUm(baum, kategorie, auftrag.zielKategorieId(), bearbeiter);
        } else {
            loescheMitInhalt(baum, kategorie, bearbeiter);
        }
    }

    private void ziehUm(KategorieBaum baum, Produktkategorie kategorie, Long zielId, BearbeiterQuelle bearbeiter) {
        if (zielId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte wählen Sie einen Ziel-Ordner.");
        }
        Produktkategorie ziel = Optional.ofNullable(baum.get(zielId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, ZIEL_FEHLT));
        Long id = kategorie.getId();
        if (baum.mitUnterordnern(id).contains(zielId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Ziel-Ordner darf nicht der Ordner selbst oder einer seiner Unterordner sein.");
        }
        List<Long> direkteUnterordner = baum.kinder(id);
        List<Leistung> direkteLeistungen = leistungRepository.findByKategorieId(id);
        if (!direkteUnterordner.isEmpty() && leistungRepository.existsByKategorieId(zielId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "In „" + ziel.getBezeichnung() + "“ liegen Leistungen. Dort können keine Unterordner hinein.");
        }
        if (!direkteLeistungen.isEmpty() && baum.hatKinder(zielId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "„" + ziel.getBezeichnung()
                            + "“ hat Unterordner. Leistungen können nur in einem Ordner ohne Unterordner liegen.");
        }

        List<Produktkategorie> unterordner = direkteUnterordner.stream().map(baum::get).toList();
        unterordner.forEach(u -> u.setUebergeordneteKategorie(ziel));
        produktkategorieRepository.saveAll(unterordner);
        direkteLeistungen.forEach(l -> l.setKategorie(ziel));
        leistungRepository.saveAll(direkteLeistungen);

        String grund = "Ordner „" + kategorie.getBezeichnung() + "“ gelöscht, Zeit auf „"
                + ziel.getBezeichnung() + "“ umgehängt";
        for (ProjektProduktkategorie zuordnung : projektProduktkategorieRepository.findeMitProjekt(List.of(id))) {
            Optional<ProjektProduktkategorie> vorhanden = projektProduktkategorieRepository
                    .findFirstByProjektIdAndProduktkategorieId(zuordnung.getProjekt().getId(), zielId);
            if (vorhanden.isEmpty()) {
                // Die Zuordnung samt gebuchter Zeiten wechselt den Ordner. Die Buchungen selbst
                // ändern sich technisch nicht, fachlich aber schon – deshalb auch hier protokollieren.
                zuordnung.setProduktkategorie(ziel);
                projektProduktkategorieRepository.save(zuordnung);
                haengeZeitenUm(List.of(zuordnung.getId()), zuordnung, grund, bearbeiter);
                continue;
            }
            // Das Projekt hat den Ziel-Ordner schon: Mengen zusammenlegen, Zeiten umhängen.
            ProjektProduktkategorie zielZuordnung = vorhanden.get();
            zielZuordnung.setMenge(zielZuordnung.getMenge().add(zuordnung.getMenge()));
            projektProduktkategorieRepository.save(zielZuordnung);
            haengeZeitenUm(List.of(zuordnung.getId()), zielZuordnung, grund, bearbeiter);
            entferneZuordnung(zuordnung);
        }

        produktkategorieService.loescheBildDatei(kategorie.getBildUrl());
        produktkategorieRepository.delete(kategorie);
    }

    private void loescheMitInhalt(KategorieBaum baum, Produktkategorie kategorie, BearbeiterQuelle bearbeiter) {
        List<Long> ids = baum.mitUnterordnern(kategorie.getId());
        List<ProjektProduktkategorie> zuordnungen = projektProduktkategorieRepository.findeMitProjekt(ids);
        if (!zuordnungen.isEmpty()) {
            haengeZeitenUm(zuordnungen.stream().map(ProjektProduktkategorie::getId).toList(), null,
                    "Ordner „" + kategorie.getBezeichnung() + "“ mit Inhalt gelöscht, Kategorie entfernt",
                    bearbeiter);
            zuordnungen.forEach(this::entferneZuordnung);
        }
        leistungRepository.deleteAll(leistungRepository.findByKategorieIdIn(ids));
        // Unterste Ebene zuerst, damit kein Ordner vor seinen Unterordnern verschwindet.
        List<Long> vonUnten = new ArrayList<>(ids);
        Collections.reverse(vonUnten);
        for (Long ordnerId : vonUnten) {
            Produktkategorie ordner = baum.get(ordnerId);
            produktkategorieService.loescheBildDatei(ordner.getBildUrl());
            produktkategorieRepository.delete(ordner);
        }
    }

    /**
     * Hängt die Zeitbuchungen dieser Projekt-Zuordnungen um ({@code ziel == null}: ohne Kategorie).
     * Jede Änderung bekommt einen Protokolleintrag mit neuer Version (wie im Zeitbuchungs-Editor).
     */
    private void haengeZeitenUm(List<Long> zuordnungIds, ProjektProduktkategorie ziel, String grund,
            BearbeiterQuelle bearbeiter) {
        List<Zeitbuchung> buchungen = zeitbuchungRepository.findByProjektProduktkategorieIdIn(zuordnungIds);
        for (Zeitbuchung buchung : buchungen) {
            Mitarbeiter wer = bearbeiter.get();
            buchung.setProjektProduktkategorie(ziel);
            buchung.markiereAlsGeaendert(wer);
            zeitbuchungAuditService.protokolliereAenderung(buchung, wer, ErfassungsQuelle.DESKTOP, grund);
        }
        zeitbuchungRepository.saveAll(buchungen);
    }

    /**
     * Löscht die Zuordnung sofort. Nur aus {@code Projekt.projektProduktkategorien} zu entfernen
     * (orphanRemoval) reichte nicht: Hibernate reiht dieses DELETE erst beim Flush ein – hinter das
     * DELETE des Ordners, und der Fremdschlüssel {@code projekt_produktkategorie → produktkategorie}
     * schlüge an.
     */
    private void entferneZuordnung(ProjektProduktkategorie zuordnung) {
        zuordnung.getProjekt().getProjektProduktkategorien().remove(zuordnung);
        projektProduktkategorieRepository.delete(zuordnung);
    }

    private static Modus leseModus(KategorieLoeschenDto auftrag) {
        if (auftrag != null && auftrag.modus() != null) {
            for (Modus m : Modus.values()) {
                if (m.name().equalsIgnoreCase(auftrag.modus().strip())) {
                    return m;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Bitte wählen Sie, ob der Inhalt umziehen oder mitgelöscht werden soll.");
    }

    private KategorieBaum ladeBaum() {
        return KategorieBaum.aus(produktkategorieRepository.findAllWithParent());
    }

    private static Produktkategorie ordner(KategorieBaum baum, Long id) {
        Produktkategorie kategorie = id != null ? baum.get(id) : null;
        if (kategorie == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NICHT_GEFUNDEN);
        }
        return kategorie;
    }

    /**
     * Den angemeldeten Mitarbeiter nur ermitteln, wenn wirklich Zeiten geändert werden –
     * ein leerer Ordner lässt sich auch ohne Mitarbeiter-Zuordnung löschen.
     */
    private final class BearbeiterQuelle {
        private final Authentication authentication;
        private Mitarbeiter mitarbeiter;

        BearbeiterQuelle(Authentication authentication) {
            this.authentication = authentication;
        }

        Mitarbeiter get() {
            if (mitarbeiter == null) {
                mitarbeiter = angemeldeterMitarbeiterService.bearbeiter(authentication);
            }
            return mitarbeiter;
        }
    }
}
