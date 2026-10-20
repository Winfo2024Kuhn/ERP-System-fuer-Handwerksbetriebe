package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelDokumenttexteRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ArtikelService implements ArtikelServiceContract {

    private static final int MAX_KURZBESCHREIBUNG = 255;
    private static final int MAX_BESCHREIBUNG = 10_000;
    private static final BigDecimal MAX_AUFSCHLAG = new BigDecimal("999.99");

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

    private static final int MAX_ARTIKELNUMMER = 64;

    private final ArtikelRepository artikelRepository;
    private final KategorieRepository kategorieRepository;
    private final WerkstoffRepository werkstoffRepository;
    private final LieferantenRepository lieferantenRepository;

    /**
     * Legt einen Artikel an. Preis und Lieferanten-Artikelnummer gehören immer zu einem
     * Lieferanten: Ohne Lieferant gingen sie verloren, weil Liste und „Wer liefert das?“ nur
     * Lieferantenpreise zeigen (Issue #265). Der erste Preis bekommt Datum und Herkunft und
     * steht damit im Preisverlauf.
     */
    @Transactional
    public Artikel erstelleArtikel(ArtikelCreateDto dto) {
        String externeNummer = leerAlsNull(dto.getExterneArtikelnummer());
        String artikelnummer = leerAlsNull(dto.getArtikelnummer());
        if ((dto.getPreis() != null || externeNummer != null) && dto.getLieferantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Für Preis und Lieferanten-Artikelnummer bitte auch den Lieferanten wählen.");
        }
        if (dto.getPreis() != null && dto.getPreis().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Preis darf nicht negativ sein.");
        }
        if (artikelnummer != null) {
            if (artikelnummer.length() > MAX_ARTIKELNUMMER) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Die Artikelnummer darf höchstens " + MAX_ARTIKELNUMMER + " Zeichen lang sein.");
            }
            if (artikelRepository.existsByArtikelnummerIgnoreCase(artikelnummer)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Die Artikelnummer „" + artikelnummer + "“ ist schon vergeben.");
            }
        }
        Lieferanten lieferant = null;
        if (dto.getLieferantId() != null) {
            lieferant = lieferantenRepository.findById(dto.getLieferantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Der gewählte Lieferant wurde nicht gefunden."));
        }

        Artikel artikel = new Artikel();
        artikel.setArtikelnummer(artikelnummer);
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

        if (lieferant != null && (dto.getPreis() != null || externeNummer != null)) {
            LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
            preis.setArtikel(saved);
            preis.setLieferant(lieferant);
            preis.setPreis(dto.getPreis());
            preis.setExterneArtikelnummer(externeNummer);
            preis.setPreisAenderungsdatum(new java.util.Date());
            preis.setQuelle(PreisQuelle.MANUELL);
            preis.setAktuell(true);
            saved.getArtikelpreis().add(preis);
            artikelRepository.save(saved);
        }

        return saved;
    }

    private static String leerAlsNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
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
