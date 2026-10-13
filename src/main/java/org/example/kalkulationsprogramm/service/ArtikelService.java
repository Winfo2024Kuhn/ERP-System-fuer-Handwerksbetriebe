package org.example.kalkulationsprogramm.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelDokumenttexteRequest;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelStammdatenRequest;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.PortableSortierung;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ArtikelService implements ArtikelServiceContract {

    private static final int MAX_KURZBESCHREIBUNG = 255;
    private static final int MAX_BESCHREIBUNG = 10_000;
    private static final BigDecimal MAX_AUFSCHLAG = new BigDecimal("999.99");
    private static final BigDecimal MAX_MASS = new BigDecimal("100000");
    private static final int MAX_STANDARDLAENGE = 30_000;
    /** So lang ist die Spalte {@code artikel.produkttext}; längere Texte würden erst in der Datenbank scheitern. */
    private static final int MAX_PRODUKTTEXT = 255;

    /**
     * Tags, die der TiptapEditor fuer die Beschreibung tatsaechlich erzeugt.
     * Bewusst kein {@code a} (Phishing-Link), kein {@code blockquote}/{@code
     * code}/{@code pre} und keine Bilder - der Text landet unveraendert im
     * Kunden-PDF und auf der oeffentlichen Freigabe-Seite. Ueberschriften,
     * Blockquotes, Code und Trennlinien sind nicht dabei: Das Projekt
     * deaktiviert diese Features im Editor (siehe {@code TiptapEditor.tsx},
     * {@code StarterKit.configure({ heading/blockquote/codeBlock/code/
     * horizontalRule: false })}) - der Editor kann sie nicht erzeugen, die
     * Safelist muss sie also auch nicht erlauben. Sanitisiert wird trotzdem:
     * Der Endpoint wird auch vom Backfill-Skript und potenziell von Hand
     * beschickt.
     */
    private static final Safelist BESCHREIBUNG_SAFELIST = new Safelist()
            .addTags("p", "br", "strong", "b", "em", "i", "u", "s", "ul", "ol", "li", "span")
            .addAttributes("span", "style")
            .addAttributes("p", "style");

    /** Einzige CSS-Eigenschaften, die im style-Attribut ueberleben duerfen. */
    private static final Set<String> ERLAUBTE_CSS_EIGENSCHAFTEN = Set.of("color", "text-align");

    /**
     * Jsoup prueft das style-Attribut nur dem Namen nach, nicht dem
     * CSS-Inhalt. Dieses Muster faengt zusaetzlich Werte ab, die selbst
     * innerhalb einer erlaubten Eigenschaft gefaehrlich waeren.
     */
    private static final Pattern GEFAEHRLICHES_CSS_MUSTER = Pattern.compile(
            "url\\(|expression\\(|position|@import|javascript:", Pattern.CASE_INSENSITIVE);

    private final ArtikelRepository artikelRepository;
    private final KategorieRepository kategorieRepository;
    private final WerkstoffRepository werkstoffRepository;
    private final LieferantenRepository lieferantenRepository;

    @Transactional
    public Artikel erstelleArtikel(ArtikelCreateDto dto) {
        Artikel artikel = new Artikel();
        artikel.setProduktname(dto.getProduktname());
        artikel.setProduktlinie(dto.getProduktlinie());
        artikel.setProdukttext(dto.getProdukttext());
        artikel.setVerpackungseinheit(dto.getVerpackungseinheit());
        artikel.setPreiseinheit(dto.getPreiseinheit());
        artikel.setVerrechnungseinheit(dto.getVerrechnungseinheit());

        if (dto.getKategorieId() != null) {
            kategorieRepository.findById(Math.toIntExact(dto.getKategorieId()))
                    .ifPresent(artikel::setKategorie);
        }

        if (dto.getWerkstoffId() != null) {
            werkstoffRepository.findById(dto.getWerkstoffId())
                    .ifPresent(artikel::setWerkstoff);
        }

        Artikel saved = artikelRepository.save(artikel);

        if (dto.getPreis() != null || (dto.getExterneArtikelnummer() != null && !dto.getExterneArtikelnummer().isBlank())) {
            LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
            preis.setArtikel(saved);
            preis.setPreis(dto.getPreis());
            preis.setExterneArtikelnummer(dto.getExterneArtikelnummer());
            if (dto.getLieferantId() != null) {
                lieferantenRepository.findById(dto.getLieferantId()).ifPresent(preis::setLieferant);
            }
            saved.getArtikelpreis().add(preis);
            artikelRepository.save(saved);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Artikel> findeAlleByIds(List<Long> ids) {
        return artikelRepository.findAllById(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Artikel> findeById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return artikelRepository.findById(id);
    }

    /**
     * Die Sortierspalte waehlt der Nutzer; leere Werte stehen dabei wie gewohnt
     * ({@link PortableSortierung}).
     */
    @Transactional(readOnly = true)
    public Page<Artikel> suche(Specification<Artikel> specification, Pageable pageable) {
        return artikelRepository.findAll(PortableSortierung.mit(Specification.where(specification), pageable.getSort()),
                PortableSortierung.ohneSortierung(pageable));
    }

    @Transactional(readOnly = true)
    public List<String> findeProduktlinienOhneLieferant(Long lieferantId) {
        return artikelRepository.findDistinctProduktlinieExcludingLieferant(lieferantId);
    }

    @Transactional
    public void fuegeExterneNummerHinzu(Long artikelId, Lieferanten lieferant, String nummer) {
        artikelRepository.findById(artikelId).ifPresent(a -> {
            a.addExterneArtikelnummer(lieferant, nummer);
            artikelRepository.save(a);
        });
    }

    /**
     * Setzt die Felder, mit denen ein Artikel als Position in einem
     * Kundendokument auftauchen kann. Echtes Teil-Update: Ein Feld, das im
     * Request gar nicht vorkommt ({@code xGesetzt == false}), bleibt am
     * Artikel unangetastet. Ein ausdruecklich mitgesendetes {@code null}
     * loescht das Feld.
     *
     * <p>Die Beschreibung wird serverseitig gesaeubert: Sie landet spaeter
     * unveraendert im PDF und auf der oeffentlichen Freigabe-Seite, deshalb darf
     * ueber diesen Endpunkt kein Skript-Markup und kein Link hereinkommen.
     *
     * @throws NotFoundException bei unbekannter ID
     * @throws IllegalArgumentException bei unzulaessigen Werten
     */
    @Override
    @Transactional
    public Artikel aktualisiereDokumenttexte(Long id, ArtikelDokumenttexteRequest request) {
        Artikel artikel = artikelRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Artikel " + id + " nicht gefunden"));

        if (request.isKurzbeschreibungGesetzt()) {
            String kurz = request.getKurzbeschreibung();
            if (kurz != null && kurz.length() > MAX_KURZBESCHREIBUNG) {
                throw new IllegalArgumentException(
                        "Die Kurzbeschreibung darf hoechstens " + MAX_KURZBESCHREIBUNG + " Zeichen lang sein.");
            }
            artikel.setKurzbeschreibung(kurz == null || kurz.isBlank() ? null : kurz.trim());
        }

        if (request.isBeschreibungGesetzt()) {
            String beschreibung = request.getBeschreibung();
            if (beschreibung != null && beschreibung.length() > MAX_BESCHREIBUNG) {
                throw new IllegalArgumentException(
                        "Die Beschreibung darf hoechstens " + MAX_BESCHREIBUNG + " Zeichen lang sein.");
            }
            artikel.setBeschreibung(saeubereHtml(beschreibung));
        }

        if (request.isVerkaufsaufschlagProzentGesetzt()) {
            BigDecimal aufschlag = request.getVerkaufsaufschlagProzent();
            if (aufschlag != null && (aufschlag.signum() < 0 || aufschlag.compareTo(MAX_AUFSCHLAG) > 0)) {
                throw new IllegalArgumentException("Der Aufschlag muss zwischen 0 und 999,99 Prozent liegen.");
            }
            artikel.setVerkaufsaufschlagProzent(aufschlag);
        }

        return artikelRepository.save(artikel);
    }

    /**
     * Ändert alle Stammdaten eines Artikels (vollständiges Ersetzen: Was fehlt, wird geleert). Texte für Angebote, Aufschlag,
     * Lieferanten, Preise und Dokumente bleiben unberührt. Der Suchtext wird neu aufgebaut.
     *
     * @throws NotFoundException bei unbekannter ID
     * @throws ResponseStatusException 400 bei ungültigen Werten, 409 bei doppelter Artikelnummer/EAN oder veraltetem Stand
     */
    @Override
    @Transactional
    public Artikel aktualisiereStammdaten(Long id, ArtikelStammdatenRequest r) {
        Artikel artikel = artikelRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Artikel " + id + " nicht gefunden"));
        // Der Stand ist Pflicht, sonst würde ein vollständiges Ersetzen still fremde Änderungen überschreiben
        if (artikel.getVersion() != null && !artikel.getVersion().equals(r.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Der Artikel wurde inzwischen von jemand anderem geändert. Bitte die Seite neu laden und noch einmal versuchen.");
        }
        String name = text(r.getProduktname(), 255, "Der Produktname");
        if (name == null) {
            throw ungueltig("Bitte einen Produktnamen eingeben.");
        }
        String nummer = text(r.getArtikelnummer(), 64, "Die Artikelnummer");
        if (nummer != null && artikelRepository.findByArtikelnummer(nummer).filter(a -> !a.getId().equals(id)).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Die Artikelnummer " + nummer + " gibt es schon bei einem anderen Artikel.");
        }
        String ean = EanPruefung.normalisiert(r.getEan());
        pruefeEan(ean, id);
        if (r.getVerpackungseinheit() != null && r.getVerpackungseinheit() < 0) {
            throw ungueltig("Die Verpackungseinheit darf nicht negativ sein.");
        }

        artikel.setProduktname(name);
        artikel.setProduktlinie(text(r.getProduktlinie(), 255, "Die Produktlinie"));
        artikel.setProdukttext(text(r.getProdukttext(), MAX_PRODUKTTEXT, "Die Beschreibung"));
        artikel.setArtikelnummer(nummer);
        artikel.setEan(ean);
        artikel.setVerpackungseinheit(r.getVerpackungseinheit());
        artikel.setPreiseinheit(text(r.getPreiseinheit(), 255, "Die Preiseinheit"));
        artikel.setVerrechnungseinheit(r.getVerrechnungseinheit());
        artikel.setKategorie(kategorie(r.getKategorieId()));
        artikel.setWerkstoff(r.getWerkstoffId() == null ? null : werkstoffRepository.findById(r.getWerkstoffId())
                .orElseThrow(() -> ungueltig("Der gewählte Werkstoff gibt es nicht.")));
        artikel.setProfilform(r.getProfilform());
        artikel.setHerstellverfahren(r.getHerstellverfahren());
        artikel.setFertigungszustand(r.getFertigungszustand());
        artikel.setMassnorm(text(r.getMassnorm(), 64, "Die Maßnorm"));
        artikel.setWerkstoffnorm(text(r.getWerkstoffnorm(), 64, "Die Werkstoffnorm"));
        // Leer = vom Werkstoff übernehmen; sonst gilt die Eignung nur für diesen Artikel
        artikel.setVerzinkungsgeeignet(r.getVerzinkungsgeeignet());
        artikel.setPulverbeschichtungsgeeignet(r.getPulverbeschichtungsgeeignet());

        // Maße gibt es nur bei Halbzeug; andere Artikel ignorieren sie
        if (artikel instanceof ArtikelWerkstoffe aw) {
            aw.setDurchmesser(mass(r.getDurchmesser(), "Der Durchmesser"));
            aw.setHoehe(mass(r.getHoehe(), "Die Höhe"));
            aw.setBreite(mass(r.getBreite(), "Die Breite"));
            aw.setWandstaerke(mass(r.getWandstaerke(), "Die Wandstärke"));
            aw.setStegdicke(mass(r.getStegdicke(), "Die Stegdicke"));
            aw.setFlanschdicke(mass(r.getFlanschdicke(), "Die Flanschdicke"));
            Integer laenge = r.getStandardlaenge();
            if (laenge != null && (laenge < 1 || laenge > MAX_STANDARDLAENGE)) {
                throw ungueltig("Die Standardlänge muss zwischen 1 und " + MAX_STANDARDLAENGE + " mm liegen.");
            }
            aw.setStandardlaenge(laenge);
        }
        artikel.setSuchtext(ArtikelSuchtext.aufbauen(artikel));
        return artikelRepository.save(artikel);
    }

    /** Kategorie zur ID; eine unbekannte oder zu große ID ist ein Eingabefehler (400), kein Serverfehler. */
    private Kategorie kategorie(Long id) {
        if (id == null) {
            return null;
        }
        if (id < 0 || id > Integer.MAX_VALUE) {
            throw ungueltig("Die gewählte Kategorie gibt es nicht.");
        }
        return kategorieRepository.findById(id.intValue())
                .orElseThrow(() -> ungueltig("Die gewählte Kategorie gibt es nicht."));
    }

    /** Prüft Prüfziffer und Eindeutigkeit einer EAN; {@code eigeneId} ist der Artikel, der sie schon tragen darf. */
    private void pruefeEan(String ean, Long eigeneId) {
        String fehler = EanPruefung.fehler(ean);
        if (fehler != null) {
            throw ungueltig(fehler);
        }
        if (ean != null && artikelRepository.findByEan(ean).filter(a -> !a.getId().equals(eigeneId)).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Die EAN " + ean + " gehört schon zu einem anderen Artikel.");
        }
    }

    /** Text bereinigen: leer wird {@code null}, zu lang wird abgelehnt. */
    private static String text(String wert, int max, String name) {
        if (wert == null || wert.isBlank()) {
            return null;
        }
        String s = wert.strip();
        if (s.length() > max) {
            throw ungueltig(name + " darf höchstens " + max + " Zeichen lang sein.");
        }
        return s;
    }

    /** Maß in mm: leer ist erlaubt, sonst muss es zwischen 0 und 100000 liegen. */
    private static BigDecimal mass(BigDecimal wert, String name) {
        if (wert == null) {
            return null;
        }
        if (wert.signum() < 0 || wert.compareTo(MAX_MASS) > 0) {
            throw ungueltig(name + " muss zwischen 0 und " + MAX_MASS.toPlainString() + " mm liegen.");
        }
        return wert;
    }

    private static ResponseStatusException ungueltig(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }

    /** Laesst nur die Formatierung durch, die der TiptapEditor erzeugt. */
    private String saeubereHtml(String html) {
        if (html == null || html.isBlank()) return null;
        String bereinigt = Jsoup.clean(html, BESCHREIBUNG_SAFELIST);

        Document dokument = Jsoup.parseBodyFragment(bereinigt);
        for (Element element : dokument.body().select("[style]")) {
            String gefiltertesStyle = saeubereCssStyle(element.attr("style"));
            if (gefiltertesStyle == null) {
                element.removeAttr("style");
            } else {
                element.attr("style", gefiltertesStyle);
            }
        }
        return dokument.body().html();
    }

    /**
     * Beschraenkt ein style-Attribut auf {@link #ERLAUBTE_CSS_EIGENSCHAFTEN}.
     * Jsoup selbst prueft nur, dass das Attribut "style" heissen darf - nicht,
     * was darin steht. Ohne diesen Filter koennte ein Angebot einen
     * Tracking-Pixel ({@code background: url(...)}) oder eine Ueberdeckung der
     * oeffentlichen Freigabe-Seite ({@code position: fixed}) transportieren.
     *
     * @return gefilterter Wert oder {@code null}, wenn nichts Erlaubtes uebrig bleibt
     */
    private static String saeubereCssStyle(String style) {
        if (style == null || style.isBlank()) {
            return null;
        }
        String gefiltert = java.util.Arrays.stream(style.split(";"))
                .map(String::trim)
                .filter(deklaration -> !deklaration.isEmpty())
                .filter(ArtikelService::istErlaubteCssDeklaration)
                .collect(Collectors.joining("; "));
        return gefiltert.isBlank() ? null : gefiltert;
    }

    private static boolean istErlaubteCssDeklaration(String deklaration) {
        int trenner = deklaration.indexOf(':');
        if (trenner < 0) {
            return false;
        }
        String eigenschaft = deklaration.substring(0, trenner).trim().toLowerCase(Locale.ROOT);
        String wert = deklaration.substring(trenner + 1).trim();
        return ERLAUBTE_CSS_EIGENSCHAFTEN.contains(eigenschaft) && !GEFAEHRLICHES_CSS_MUSTER.matcher(wert).find();
    }
}
