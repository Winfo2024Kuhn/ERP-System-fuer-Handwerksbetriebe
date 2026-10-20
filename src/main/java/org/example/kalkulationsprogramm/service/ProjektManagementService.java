package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.AnfrageNotiz;
import org.example.kalkulationsprogramm.domain.AnfrageNotizBild;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Materialkosten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektArt;
import org.example.kalkulationsprogramm.domain.ProjektNotiz;
import org.example.kalkulationsprogramm.domain.ProjektNotizBild;
import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelMengeDto;
import org.example.kalkulationsprogramm.dto.Materialkosten.MaterialkostenErfassenDto;
import org.example.kalkulationsprogramm.dto.Projekt.MaterialKilogrammDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektResponseDto;
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailDto;
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailFileDto;
import org.example.kalkulationsprogramm.dto.ProjektProduktkategorie.ProjektProduktkategorieErfassenDto;
import org.example.kalkulationsprogramm.exception.FalscheAuftragsnummerException;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.mapper.ProjektMapper;
import org.example.kalkulationsprogramm.repository.AnfrageNotizRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;

@Service
public class

ProjektManagementService {
    private static final Logger log = LoggerFactory.getLogger(ProjektManagementService.class);
    private static final String PROJEKT_KUNDE_PFLICHT_MESSAGE = "Für Projekte muss ein Kunde ausgewählt werden.";
    public static final String ANFRAGE_UEBERNAHME_FEHLGESCHLAGEN =
            "Die Anfrage konnte nicht übernommen werden. Es wurde nichts gespeichert – die Anfrage ist"
                    + " unverändert da. Bitte versuchen Sie es noch einmal.";
    public static final String PROJEKT_NICHT_GEFUNDEN =
            "Dieses Projekt gibt es nicht mehr. Vielleicht wurde es inzwischen gelöscht.";
    /** Spaltenbreite der Texte in {@code materialkosten}. */
    private static final int MAX_TEXTLAENGE_MATERIALKOSTEN = 255;
    /** {@code numeric(19,2)}: höchstens 17 Stellen vor dem Komma. */
    private static final int MAX_VORKOMMASTELLEN_BETRAG = 17;
    private final ProjektRepository projektRepository;
    private final AnfrageNotizRepository anfrageNotizRepository;
    private final ProjektNotizRepository projektNotizRepository;
    private final ProduktkategorieRepository produktkategorieRepository;
    private final KundeRepository kundeRepository;
    private final DateiSpeicherService dateiSpeicherService;
    private final ProjektMapper projektMapper;
    private final AnfrageRepository anfrageRepository;
    private final AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository;
    private final ZeitbuchungRepository ZeitbuchungRepository;
    private final ArtikelRepository artikelRepository;
    private final ArtikelInProjektRepository artikelInProjektRepository;
    private final ProjektPersistenceService projektPersistenceService;
    private final LieferantenRepository lieferantenRepository;
    private final EmailRepository emailRepository;
    private final KalenderEintragRepository kalenderEintragRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @lombok.Setter(onMethod_ = @org.springframework.beans.factory.annotation.Autowired)
    private AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;

    @PersistenceContext
    private EntityManager entityManager;

    public ProjektManagementService(ProjektRepository projektRepository,
            AnfrageNotizRepository anfrageNotizRepository,
            ProjektNotizRepository projektNotizRepository,
            ProduktkategorieRepository produktkategorieRepository,
            KundeRepository kundeRepository,
            DateiSpeicherService dateiSpeicherService,
            ProjektMapper projektMapper,
            AnfrageRepository anfrageRepository,
            AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository,
            ZeitbuchungRepository ZeitbuchungRepository,
            ArtikelRepository artikelRepository,
            ArtikelInProjektRepository artikelInProjektRepository,
            ProjektPersistenceService projektPersistenceService,
            LieferantenRepository lieferantenRepository,
            EmailRepository emailRepository,
            KalenderEintragRepository kalenderEintragRepository,
            org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.projektRepository = projektRepository;
        this.anfrageNotizRepository = anfrageNotizRepository;
        this.projektNotizRepository = projektNotizRepository;
        this.produktkategorieRepository = produktkategorieRepository;
        this.kundeRepository = kundeRepository;
        this.dateiSpeicherService = dateiSpeicherService;
        this.projektMapper = projektMapper;
        this.anfrageRepository = anfrageRepository;
        this.ausgangsGeschaeftsDokumentRepository = ausgangsGeschaeftsDokumentRepository;
        this.ZeitbuchungRepository = ZeitbuchungRepository;
        this.artikelRepository = artikelRepository;
        this.artikelInProjektRepository = artikelInProjektRepository;
        this.projektPersistenceService = projektPersistenceService;
        this.lieferantenRepository = lieferantenRepository;
        this.emailRepository = emailRepository;
        this.kalenderEintragRepository = kalenderEintragRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Überträgt den vollständigen Inhalt einer noch offenen Anfrage in ein
     * bestehendes Projekt und löscht die Anfrage anschließend.
     * <p>
     * Blockiert wird nur, wenn das Projekt bereits eine eigene Dokumentenkette in
     * {@code ausgangs_geschaefts_dokument} hat. Nacherfasste Rechnungen und
     * hochgeladene PDFs (beide landen als
     * {@link org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument} im
     * Dateien-Tab) blockieren bewusst <b>nicht</b> — das war eine Produktentscheidung,
     * weil solche Bestände in der Praxis fast jedes Zusammenführen verhindert haben.
     * Folge: Bringt die Anfrage ein Angebot mit, leitet das abschließende
     * {@code findeProjektById} über
     * {@code AusgangsGeschaeftsDokumentService#aktualisiereProjektPreisAusDokumenten}
     * den Auftragspreis aus diesem Angebot ab statt aus einer nacherfassten Rechnung;
     * der Bezahlt-Status kann dadurch neu bewertet werden.
     */
    @Transactional
    public ProjektResponseDto fuehreAnfrageZusammen(Long projektId, Long anfrageId) {
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new NotFoundException("Projekt mit ID " + projektId + " nicht gefunden."));
        Anfrage anfrage = anfrageRepository.findById(anfrageId)
                .orElseThrow(() -> new NotFoundException("Anfrage mit ID " + anfrageId + " nicht gefunden."));

        // Nur eine bereits laufende Dokumentenkette des Projekts blockiert, weil die
        // Kette der Anfrage sonst mit ihr kollidieren würde. Einträge im Dateien-Tab
        // (ProjektGeschaeftsdokument) zählen bewusst nicht mehr mit — sie entstehen aus
        // dem Offene-Posten-Editor, aus ZUGFeRD-Uploads, aus Zeichnungen/Entwürfen und
        // aus jedem früheren Zusammenführen selbst. Letzteres machte ein zweites
        // Zusammenführen in dasselbe Projekt bisher strukturell unmöglich.
        if (ausgangsGeschaeftsDokumentRepository.existsByProjektId(projektId)) {
            throw new IllegalStateException(
                    "Zusammenführen nicht möglich, da im Projekt bereits Ausgangsgeschäftsdokumente vorhanden sind.");
        }
        Long projektKundeId = projekt.getKundenId() != null ? projekt.getKundenId().getId() : null;
        Long anfrageKundeId = anfrage.getKunde() != null ? anfrage.getKunde().getId() : null;
        if (projektKundeId == null || !projektKundeId.equals(anfrageKundeId)) {
            throw new IllegalStateException(
                    "Zusammenführen nicht möglich, da Projekt und Anfrage nicht demselben Kunden zugewiesen sind.");
        }
        if (anfrage.getProjekt() != null) {
            throw new IllegalStateException("Diese Anfrage wurde bereits einem Projekt zugeordnet.");
        }

        uebernehmeAnfrageInsProjekt(anfrage, projekt);
        anfrageRepository.flush();

        return findeProjektById(projektId);
    }

    /**
     * Gemeinsamer Weg für „Neues Projekt aus Anfrage“ und „mit Anfrage zusammenführen“:
     * Dateien, E-Mails, Notizen, Kontakt-Adressen, Beschreibung, Angebote/Rechnungen
     * und Termine wandern ins Projekt, danach wird die Anfrage gelöscht.
     * <p>
     * Fehler werden bewusst nicht verschluckt. Schlägt ein Schritt fehl, bricht die
     * ganze Übernahme ab und die umgebende Transaktion rollt zurück – die Anfrage
     * bleibt dann vollständig erhalten, statt halb übernommen gelöscht zu werden.
     */
    private void uebernehmeAnfrageInsProjekt(Anfrage anfrage, Projekt projekt) {
        try {
            for (AnfrageDokument dokument : anfrage.getDokumente()) {
                dateiSpeicherService.verschiebeAnfragesDatei(dokument, projekt);
            }
            uebertrageAnfrageEmails(anfrage, projekt);
            copyAnfrageNotizenToProjekt(anfrage, projekt);
            mergeAnfrageKontaktEmails(projekt, anfrage);
            mergeKurzbeschreibung(projekt, anfrage);
            ausgangsGeschaeftsDokumentService.migrateFromAnfrageToProjekt(anfrage.getId(), projekt);
            migrateAnfrageKalenderEintraegeToProjekt(anfrage, projekt);
            projektRepository.save(projekt);
            anfrageRepository.delete(anfrage);
        } catch (RuntimeException e) {
            log.error("Anfrage {} konnte nicht ins Projekt übernommen werden", anfrage.getId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    ANFRAGE_UEBERNAHME_FEHLGESCHLAGEN, e);
        }
    }

    private void uebertrageAnfrageEmails(Anfrage anfrage, Projekt projekt) {
        List<Email> anfrageEmails = emailRepository.findByAnfrageOrderBySentAtDesc(anfrage);
        for (Email email : anfrageEmails) {
            email.assignToProjekt(projekt);
        }
        emailRepository.saveAll(anfrageEmails);
    }

    private void copyAnfrageNotizenToProjekt(Anfrage anfrage, Projekt projekt) {
        List<AnfrageNotiz> anfrageNotizen = anfrageNotizRepository
                .findByAnfrageIdOrderByErstelltAmDesc(anfrage.getId());
        for (AnfrageNotiz anfrageNotiz : anfrageNotizen) {
            ProjektNotiz projektNotiz = new ProjektNotiz();
            projektNotiz.setProjekt(projekt);
            projektNotiz.setMitarbeiter(anfrageNotiz.getMitarbeiter());
            projektNotiz.setNotiz(anfrageNotiz.getNotiz());
            projektNotiz.setErstelltAm(anfrageNotiz.getErstelltAm());
            projektNotiz.setMobileSichtbar(anfrageNotiz.isMobileSichtbar());
            projektNotiz.setNurFuerErsteller(anfrageNotiz.isNurFuerErsteller());

            List<ProjektNotizBild> bilder = new ArrayList<>();
            if (anfrageNotiz.getBilder() != null) {
                for (AnfrageNotizBild anfrageBild : anfrageNotiz.getBilder()) {
                    String neuerDateiname;
                    try {
                        neuerDateiname = dateiSpeicherService
                                .kopiereBildZuDokumenten(anfrageBild.getGespeicherterDateiname());
                    } catch (NotFoundException fehltSchon) {
                        // Die Bilddatei ist schon vorher verloren gegangen. Daran ändert die
                        // Übernahme nichts – ohne Auslassen ließe sich die Anfrage aber nie
                        // mehr übernehmen. Andere Fehler (z. B. Platte voll) brechen ab.
                        log.warn("Notiz-Bild {} fehlt und wird nicht übernommen",
                                anfrageBild.getGespeicherterDateiname());
                        continue;
                    }
                    ProjektNotizBild projektBild = new ProjektNotizBild();
                    projektBild.setNotiz(projektNotiz);
                    projektBild.setOriginalDateiname(anfrageBild.getOriginalDateiname());
                    projektBild.setGespeicherterDateiname(neuerDateiname);
                    projektBild.setDateityp(anfrageBild.getDateityp());
                    projektBild.setErstelltAm(anfrageBild.getErstelltAm());
                    bilder.add(projektBild);
                }
            }
            projektNotiz.setBilder(bilder);
            projektNotizRepository.save(projektNotiz);
        }
    }

    private void mergeKurzbeschreibung(Projekt projekt, Anfrage anfrage) {
        if (!StringUtils.hasText(anfrage.getKurzbeschreibung())) {
            return;
        }
        String anfrageBeschreibung = anfrage.getKurzbeschreibung().trim();
        if (!StringUtils.hasText(projekt.getKurzbeschreibung())) {
            projekt.setKurzbeschreibung(anfrageBeschreibung);
            return;
        }
        if (projekt.getKurzbeschreibung().contains(anfrageBeschreibung)) {
            return;
        }
        String quelle = StringUtils.hasText(anfrage.getBauvorhaben())
                ? anfrage.getBauvorhaben().trim()
                : "Anfrage " + anfrage.getId();
        projekt.setKurzbeschreibung(projekt.getKurzbeschreibung().trim()
                + "\n\n--- Übernommen aus Anfrage: " + quelle + " ---\n"
                + anfrageBeschreibung);
    }

    private void mergeAnfrageKontaktEmails(Projekt projekt, Anfrage anfrage) {
        if (anfrage.getKundenEmails() == null) {
            return;
        }
        for (String email : anfrage.getKundenEmails()) {
            if (!StringUtils.hasText(email)) {
                continue;
            }
            boolean bereitsVorhanden = projekt.getKundenEmails().stream()
                    .anyMatch(vorhanden -> vorhanden != null && vorhanden.equalsIgnoreCase(email.trim()));
            if (!bereitsVorhanden) {
                projekt.getKundenEmails().add(email.trim());
            }
        }
    }

    @Transactional
    public ProjektResponseDto erstelleProjekt(ProjektErstellenDto dto, String strasse, String plz, String ort,
            MultipartFile bild, List<MultipartFile> dokumente, Mitarbeiter uploadedBy)
            throws FalscheAuftragsnummerException {
        // Kundenprüfung erfolgt nach dem Übernehmen von Anfragesdaten.
        Projekt neuesProjekt = new Projekt();
        neuesProjekt.setStrasse(strasse);
        neuesProjekt.setPlz(plz);
        neuesProjekt.setOrt(ort);

        // Wenn Referenzanfragen angegeben wurden, fehlende Felder aus dem ersten übernehmen.
        // Der Bruttopreis wird hier NICHT gesetzt – er kommt aus den Dokumenten des Projekts.
        List<Anfrage> uebernommeneAnfragen = dto.getAnfrageIds() != null && !dto.getAnfrageIds().isEmpty()
                ? anfrageRepository.findAllById(dto.getAnfrageIds())
                : List.of();
        if (!uebernommeneAnfragen.isEmpty()) {
            Anfrage erstes = uebernommeneAnfragen.getFirst();
            if (dto.getBauvorhaben() == null) {
                dto.setBauvorhaben(erstes.getBauvorhaben());
            }
            if (dto.getKundenId() == null && erstes.getKunde() != null) {
                dto.setKundenId(erstes.getKunde().getId());
            }
            if (dto.getKunde() == null && erstes.getKunde() != null) {
                dto.setKunde(erstes.getKunde().getName());
            }
            if (dto.getAuftragsnummer() == null) {
                erstes.getDokumente().stream()
                        .filter(AnfrageGeschaeftsdokument.class::isInstance)
                        .map(AnfrageGeschaeftsdokument.class::cast)
                        .map(AnfrageGeschaeftsdokument::getDokumentid)
                        .findFirst()
                        .ifPresent(dto::setAuftragsnummer);
            }
            if (dto.getKundennummer() == null && erstes.getKunde() != null) {
                dto.setKundennummer(erstes.getKunde().getKundennummer());
            }
            if ((dto.getKundenEmails() == null || dto.getKundenEmails().isEmpty())) {
                dto.setKundenEmails(uebernommeneAnfragen.stream()
                        .flatMap(a -> (a.getKunde() != null && a.getKunde().getKundenEmails() != null)
                                ? a.getKunde().getKundenEmails().stream()
                                : java.util.stream.Stream.empty())
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList()));
            }
        }

        ensureProjektHatZugewiesenenKunden(dto, null);
        Kunde ausgewaehlterKunde = prepareProjektKunde(dto);

        if (ausgewaehlterKunde != null) {
            KundeDatenschutzService.pruefeNichtGesperrt(ausgewaehlterKunde);
            neuesProjekt.setKundenId(ausgewaehlterKunde);
        }

        if (pruefeAuftragsnummer(dto.getAuftragsnummer())) {
            neuesProjekt.setAuftragsnummer(dto.getAuftragsnummer());
        }
        neuesProjekt.setBauvorhaben(dto.getBauvorhaben());
        neuesProjekt.setKurzbeschreibung(dto.getKurzbeschreibung());
        neuesProjekt.setBruttoPreis(java.math.BigDecimal.ZERO);
        neuesProjekt.setBezahlt(false);
        neuesProjekt.setAnlegedatum(dto.getAnlegedatum() != null ? dto.getAnlegedatum() : LocalDate.now());
        neuesProjekt.setAbschlussdatum(dto.getAbschlussdatum());
        
        // Projektart setzen (Default: PAUSCHAL)
        if (dto.getProjektArt() != null && !dto.getProjektArt().isBlank()) {
            try {
                neuesProjekt.setProjektArt(ProjektArt.valueOf(dto.getProjektArt()));
            } catch (IllegalArgumentException e) {
                neuesProjekt.setProjektArt(ProjektArt.PAUSCHAL);
            }
        } else {
            neuesProjekt.setProjektArt(ProjektArt.PAUSCHAL);
        }

        // KORREKTUR: Das separate 'bild'-Objekt verarbeiten
        if (bild != null && !bild.isEmpty()) {
            String bildWebPfad = this.dateiSpeicherService.speichereBild(bild);
            neuesProjekt.setBildUrl(bildWebPfad);
        }

        // Produktkategorien
        List<ProjektProduktkategorie> kategorien = new ArrayList<>();
        if (dto.getProduktkategorien() != null) {
            kategorien = dto.getProduktkategorien().stream()
                    .map(kDto -> {
                        Produktkategorie pk = produktkategorieRepository.findById(kDto.getProduktkategorieID())
                                .orElseThrow(() -> new RuntimeException("Leistung mit der PK ID "
                                        + kDto.getProduktkategorieID() + " konnte nicht gefunden werden."));
                        if (pk.getUnterkategorien() != null && !pk.getUnterkategorien().isEmpty()) {
                            throw new IllegalArgumentException(
                                    "Es können nur Produktkategorien ohne Unterkategorien gewählt werden.");
                        }
                        ProjektProduktkategorie ppk = new ProjektProduktkategorie();
                        ppk.setProjekt(neuesProjekt);
                        ppk.setProduktkategorie(pk);
                        ppk.setMenge(kDto.getMenge());
                        return ppk;
                    }).collect(Collectors.toCollection(ArrayList::new));
        }
        neuesProjekt.setProjektProduktkategorien(new ArrayList<>(kategorien));

        if (dto.getMaterialkosten() != null) {
            List<org.example.kalkulationsprogramm.domain.Materialkosten> materialKosten = dto.getMaterialkosten()
                    .stream()
                    .map(mDto -> {
                        org.example.kalkulationsprogramm.domain.Materialkosten mk = new org.example.kalkulationsprogramm.domain.Materialkosten();
                        mk.setProjekt(neuesProjekt);
                        mk.setBeschreibung(mDto.getBeschreibung());
                        mk.setExterneArtikelnummer(mDto.getExterneArtikelnummer());
                        mk.setBetrag(mDto.getBetrag());
                        return mk;
                    }).collect(Collectors.toCollection(ArrayList::new));
            neuesProjekt.setMaterialkosten(materialKosten);
        }

        // Projekt-spezifische E-Mail-Adressen persistieren
        if (dto.getKundenEmails() != null && !dto.getKundenEmails().isEmpty()) {
            neuesProjekt.getKundenEmails().clear();
            neuesProjekt.getKundenEmails().addAll(dto.getKundenEmails());
        }

        Projekt gespeichertesProjekt = this.projektPersistenceService.saveProjektWithRetry(neuesProjekt);

        // E-Mail-Backfill Trigger
        try {
            List<String> kundenEmails = new ArrayList<>();
            if (gespeichertesProjekt.getKundenId() != null
                    && gespeichertesProjekt.getKundenId().getKundenEmails() != null) {
                kundenEmails.addAll(gespeichertesProjekt.getKundenId().getKundenEmails());
            }
            if (!kundenEmails.isEmpty()) {
                eventPublisher
                        .publishEvent(org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.forNewEntity(
                                org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.EntityType.PROJEKT,
                                gespeichertesProjekt.getId(),
                                kundenEmails));
            }
        } catch (Exception e) {
            // Log but don't fail transaction
            System.err.println("Failed to trigger email backfill: " + e.getMessage());
        }

        for (Anfrage a : uebernommeneAnfragen) {
            // Profilbild der Anfrage übernehmen, falls das Projekt noch keines hat
            if (!StringUtils.hasText(gespeichertesProjekt.getBildUrl()) && StringUtils.hasText(a.getBildUrl())) {
                gespeichertesProjekt.setBildUrl(a.getBildUrl());
            }
            uebernehmeAnfrageInsProjekt(a, gespeichertesProjekt);
        }

        if (dokumente != null) {
            dokumente.stream()
                    .filter(d -> !d.isEmpty())
                    .forEach(d -> dateiSpeicherService.speichereDatei(d, gespeichertesProjekt.getId(),
                            DokumentGruppe.DIVERSE_DOKUMENTE, uploadedBy));
        }

        return findeProjektById(gespeichertesProjekt.getId());
    }

    /**
     * Setzt oder entfernt den Haken "Beendet" auf ausdrücklichen Wunsch des Benutzers.
     * <p>
     * Nur dieser Weg vermerkt die Entscheidung als manuell. Ab dann hat der Benutzer die
     * Oberhand: Die Automatik (komplette Auftragssumme bezahlt → Projekt beenden) fasst
     * den Haken nicht mehr an.
     */
    @Transactional
    public ProjektResponseDto setzeAbgeschlossen(Long id, boolean abgeschlossen) {
        Projekt projekt = findeProjektEntity(id);
        projekt.setAbgeschlossen(abgeschlossen);
        projekt.setAbgeschlossenManuell(true);
        return projektMapper.toProjektResponseDto(projektRepository.save(projekt));
    }

    @Transactional
    public ProjektResponseDto aktualisiereProjekt(Long id, ProjektErstellenDto dto, String strasse, String plz,
            String ort, MultipartFile bild, Mitarbeiter uploadedBy)
            throws FalscheAuftragsnummerException {
        Projekt projekt = findeProjektEntity(id);

        ensureProjektHatZugewiesenenKunden(dto, projekt);
        projekt.setStrasse(strasse);
        projekt.setPlz(plz);
        projekt.setOrt(ort);

        Kunde aktualisierterKunde = prepareProjektKunde(dto);
        if (aktualisierterKunde != null) {
            // Nur der Wechsel auf einen gesperrten Kunden zählt; das aufbewahrte Projekt bleibt bearbeitbar.
            if (projekt.getKundenId() == null || !aktualisierterKunde.getId().equals(projekt.getKundenId().getId())) {
                KundeDatenschutzService.pruefeNichtGesperrt(aktualisierterKunde);
            }
            projekt.setKundenId(aktualisierterKunde);
        }

        if (dto.getAuftragsnummer() != null && !dto.getAuftragsnummer().equals(projekt.getAuftragsnummer())) {
            if (pruefeAuftragsnummer(dto.getAuftragsnummer())) {
                projekt.setAuftragsnummer(dto.getAuftragsnummer());
            }
        }

        if (dto.getBauvorhaben() != null) {
            projekt.setBauvorhaben(dto.getBauvorhaben());
        }
        if (dto.getKurzbeschreibung() != null) {
            projekt.setKurzbeschreibung(dto.getKurzbeschreibung());
        }
        // bruttoPreis wird bewusst NICHT aus dem DTO übernommen: Der Auftragspreis
        // ergibt sich ausschließlich aus den Dokumenten (Angebot/AB/Nachtragsangebot,
        // ersatzweise die Rechnungssumme) – siehe
        // AusgangsGeschaeftsDokumentService.aktualisiereProjektPreisAusDokumenten().
        if (dto.getAbschlussdatum() != null) {
            projekt.setAbschlussdatum(dto.getAbschlussdatum());
        }
        if (dto.getAnlegedatum() != null) {
            projekt.setAnlegedatum(dto.getAnlegedatum());
        }
        if (bild != null && !bild.isEmpty()) {
            String bildWebPfad = this.dateiSpeicherService.speichereBild(bild);
            projekt.setBildUrl(bildWebPfad);
        }

        // Haken "Beendet" nur übernehmen, wenn er mitgeschickt wurde – ein Teil-Update
        // darf ein beendetes Projekt nicht versehentlich wieder aufreißen. Als bewusste
        // Entscheidung des Benutzers zählt ausschließlich {@link #setzeAbgeschlossen}.
        if (dto.getAbgeschlossen() != null) {
            projekt.setAbgeschlossen(dto.getAbgeschlossen());
        }
        
        // Projektart aktualisieren
        if (dto.getProjektArt() != null && !dto.getProjektArt().isBlank()) {
            try {
                projekt.setProjektArt(ProjektArt.valueOf(dto.getProjektArt()));
            } catch (IllegalArgumentException e) {
                // Bei ungültigem Wert Projektart beibehalten
            }
        }

        // Projekt-spezifische E-Mail-Adressen aktualisieren
        if (dto.getKundenEmails() != null) {
            projekt.getKundenEmails().clear();
            projekt.getKundenEmails().addAll(dto.getKundenEmails());
        }

        // Vorhandene Projekt-Produktkategorien: produktkategorie.id -> ProjektProduktkategorie
        java.util.Map<Long, ProjektProduktkategorie> kategorieMap = projekt.getProjektProduktkategorien().stream()
                .collect(Collectors.toMap(ppk -> ppk.getProduktkategorie().getId(), ppk -> ppk));

        if (dto.getProduktkategorien() != null && !dto.getProduktkategorien().isEmpty()) {
            // Map nach ProjektProduktkategorie.id für Matching bei Updates
            java.util.Map<Long, ProjektProduktkategorie> bestehendById = projekt.getProjektProduktkategorien().stream()
                    .filter(ppk -> ppk.getId() != null)
                    .collect(Collectors.toMap(ProjektProduktkategorie::getId, ppk -> ppk));
            java.util.List<ProjektProduktkategorie> aktualisierteKategorien = new java.util.ArrayList<>();
            for (ProjektProduktkategorieErfassenDto kDto : dto.getProduktkategorien()) {
                Long pkId = kDto.getProduktkategorieID();
                if (pkId == null) {
                    continue; // Überspringe ungültige Einträge
                }
                Produktkategorie pk = produktkategorieRepository.findById(pkId)
                        .orElseThrow(() -> new RuntimeException(
                                "Leistung mit der PK ID " + pkId + " konnte nicht gefunden werden."));

                ProjektProduktkategorie ppk = null;

                // Wenn eine ID im DTO vorhanden ist, existierende Entität anhand dieser ID
                // suchen
                if (kDto.getId() != null) {
                    ppk = bestehendById.get(kDto.getId());
                }

                // Fallback: Suche nach produktkategorie.id (für Abwärtskompatibilität)
                if (ppk == null) {
                    ppk = kategorieMap.get(pkId);
                }

                if (ppk == null) {
                    // Neue Kategorie: Prüfen ob sie Unterkategorien hat
                    if (pk.getUnterkategorien() != null && !pk.getUnterkategorien().isEmpty()) {
                        throw new IllegalArgumentException(
                                "Es können nur Produktkategorien ohne Unterkategorien gewählt werden.");
                    }
                    ppk = new ProjektProduktkategorie();
                    ppk.setProjekt(projekt);
                }

                // Produktkategorie und Menge setzen (erlaubt Änderung der Kategorie!)
                ppk.setProduktkategorie(pk);
                ppk.setMenge(kDto.getMenge());
                aktualisierteKategorien.add(ppk);
            }
            // PPKs mit Zeitbuchungen dürfen nicht entfernt werden
            Set<Long> aktualisierteIds = aktualisierteKategorien.stream()
                    .map(ProjektProduktkategorie::getId)
                    .filter(ppkId -> ppkId != null)
                    .collect(Collectors.toSet());
            List<ProjektProduktkategorie> geschuetzteKategorien = projekt.getProjektProduktkategorien().stream()
                    .filter(ppk -> ppk.getId() != null
                            && !aktualisierteIds.contains(ppk.getId())
                            && ZeitbuchungRepository.existsByProjektProduktkategorieId(ppk.getId()))
                    .toList();
            projekt.getProjektProduktkategorien().clear();
            projekt.getProjektProduktkategorien().addAll(aktualisierteKategorien);
            // Geschützte PPKs wieder hinzufügen, die sonst verloren gehen würden
            for (ProjektProduktkategorie geschuetzt : geschuetzteKategorien) {
                if (projekt.getProjektProduktkategorien().stream()
                        .noneMatch(ppk -> ppk.getId() != null && ppk.getId().equals(geschuetzt.getId()))) {
                    projekt.getProjektProduktkategorien().add(geschuetzt);
                }
            }
        }
        // Bei leerer Liste (dto.getProduktkategorien() == []) werden bestehende
        // Kategorien beibehalten

        if (dto.getMaterialkosten() != null) {
            java.util.Map<String, org.example.kalkulationsprogramm.domain.Materialkosten> existierende = projekt
                    .getMaterialkosten().stream()
                    .collect(Collectors.toMap(mk -> (mk.getBeschreibung() == null ? "" : mk.getBeschreibung()) + "|" +
                            (mk.getExterneArtikelnummer() == null ? "" : mk.getExterneArtikelnummer()) + "|" +
                            (mk.getMonat() == null ? "" : mk.getMonat()), mk -> mk));
            java.util.Set<String> dtoKeys = new java.util.HashSet<>();
            for (MaterialkostenErfassenDto mDto : dto.getMaterialkosten()) {
                String key = (mDto.getBeschreibung() == null ? "" : mDto.getBeschreibung()) + "|" +
                        (mDto.getExterneArtikelnummer() == null ? "" : mDto.getExterneArtikelnummer()) + "|" +
                        (mDto.getMonat() == null ? "" : mDto.getMonat());
                dtoKeys.add(key);
                org.example.kalkulationsprogramm.domain.Materialkosten mk = existierende.get(key);
                if (mk != null) {
                    mk.setBeschreibung(mDto.getBeschreibung());
                    mk.setExterneArtikelnummer(mDto.getExterneArtikelnummer());
                    mk.setBetrag(mDto.getBetrag());
                    mk.setMonat(mDto.getMonat());
                } else {
                    org.example.kalkulationsprogramm.domain.Materialkosten neu = new org.example.kalkulationsprogramm.domain.Materialkosten();
                    neu.setProjekt(projekt);
                    neu.setBeschreibung(mDto.getBeschreibung());
                    neu.setExterneArtikelnummer(mDto.getExterneArtikelnummer());
                    neu.setBetrag(mDto.getBetrag());
                    neu.setMonat(mDto.getMonat());
                    projekt.getMaterialkosten().add(neu);
                }
            }
            projekt.getMaterialkosten()
                    .removeIf(mk -> !dtoKeys.contains((mk.getBeschreibung() == null ? "" : mk.getBeschreibung()) + "|" +
                            (mk.getExterneArtikelnummer() == null ? "" : mk.getExterneArtikelnummer()) + "|" +
                            (mk.getMonat() == null ? "" : mk.getMonat())));
        }

        Projekt gespeichertesProjekt = this.projektPersistenceService.saveProjektWithRetry(projekt);
        if (entityManager != null && gespeichertesProjekt != null) {
            try {
                if (!entityManager.contains(gespeichertesProjekt)) {
                    gespeichertesProjekt = entityManager.merge(gespeichertesProjekt);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignorieren: Entity bereits verwaltet oder nicht zusammenführbar.
            }
        }

        // E-Mail-Backfill Trigger
        try {
            List<String> kundenEmails = new ArrayList<>();
            if (gespeichertesProjekt.getKundenId() != null
                    && gespeichertesProjekt.getKundenId().getKundenEmails() != null) {
                kundenEmails.addAll(gespeichertesProjekt.getKundenId().getKundenEmails());
            }
            if (!kundenEmails.isEmpty()) {
                eventPublisher
                        .publishEvent(org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.forAddressChange(
                                org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.EntityType.PROJEKT,
                                gespeichertesProjekt.getId(),
                                kundenEmails, // New (same as all for now)
                                kundenEmails)); // All
            }
        } catch (Exception e) {
            System.err.println("Failed to trigger email backfill update: " + e.getMessage());
        }

        if (dto.getAnfrageIds() != null && !dto.getAnfrageIds().isEmpty()) {
            List<Anfrage> anfragen = anfrageRepository.findAllById(dto.getAnfrageIds());
            boolean imageSetFromAnfrage = false;
            for (Anfrage a : anfragen) {
                if (!imageSetFromAnfrage
                        && (gespeichertesProjekt.getBildUrl() == null || gespeichertesProjekt.getBildUrl().isBlank())) {
                    if (a.getBildUrl() != null && !a.getBildUrl().isBlank()) {
                        gespeichertesProjekt.setBildUrl(a.getBildUrl());
                        imageSetFromAnfrage = true;
                    }
                }
                // if (a.getProjekt() == null) {
                // for (AnfrageDokument doc : a.getDokumente()) {
                // dateiSpeicherService.verschiebeAnfragesDatei(doc, gespeichertesProjekt);
                // }
                // anfrageRepository.delete(a);
                // }
            }
            if (imageSetFromAnfrage) {
                try {
                    projektRepository.save(gespeichertesProjekt);
                } catch (Exception ignored) {
                }
            }
        }

        return mappeMitKilogramm(gespeichertesProjekt);
    }

    /**
     * Hängt einen manuell erfassten Materialkosten-Eintrag an das Projekt an.
     * Bereits erfasste Einträge bleiben unangetastet – früher ersetzte ein
     * PATCH die ganze Liste, und jedes „Kosten erfassen“ löschte so alle
     * älteren Einträge.
     */
    @Transactional
    public ProjektResponseDto fuegeMaterialkostenHinzu(Long projektId, MaterialkostenErfassenDto dto) {
        pruefeMaterialkosten(dto);
        Projekt projekt = findeProjektEntity(projektId);

        Materialkosten mk = new Materialkosten();
        mk.setProjekt(projekt);
        mk.setBeschreibung(dto.getBeschreibung().trim());
        mk.setExterneArtikelnummer(leerAlsNull(dto.getExterneArtikelnummer()));
        mk.setMonat(dto.getMonat());
        mk.setBetrag(dto.getBetrag());
        mk.setRechnungsnummer(leerAlsNull(dto.getRechnungsnummer()));
        if (dto.getLieferantId() != null) {
            Lieferanten lieferant = lieferantenRepository.findById(dto.getLieferantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Der gewählte Lieferant wurde nicht gefunden."));
            mk.setLieferant(lieferant);
            aendereBestellZaehler(lieferant, 1);
        }
        projekt.getMaterialkosten().add(mk);

        Projekt gespeichert = projektRepository.save(projekt);
        return mappeMitKilogramm(gespeichert);
    }

    private static void pruefeMaterialkosten(MaterialkostenErfassenDto dto) {
        if (dto == null || dto.getBeschreibung() == null || dto.getBeschreibung().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte eine Beschreibung angeben.");
        }
        if (dto.getBeschreibung().trim().length() > MAX_TEXTLAENGE_MATERIALKOSTEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Die Beschreibung darf höchstens " + MAX_TEXTLAENGE_MATERIALKOSTEN + " Zeichen lang sein.");
        }
        if (dto.getRechnungsnummer() != null && dto.getRechnungsnummer().trim().length() > MAX_TEXTLAENGE_MATERIALKOSTEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Die Rechnungsnummer darf höchstens " + MAX_TEXTLAENGE_MATERIALKOSTEN + " Zeichen lang sein.");
        }
        if (dto.getExterneArtikelnummer() != null && dto.getExterneArtikelnummer().trim().length() > MAX_TEXTLAENGE_MATERIALKOSTEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Die Artikelnummer darf höchstens " + MAX_TEXTLAENGE_MATERIALKOSTEN + " Zeichen lang sein.");
        }
        if (dto.getBetrag() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen Betrag angeben.");
        }
        if (dto.getBetrag().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Betrag darf nicht negativ sein.");
        }
        BigDecimal betrag = dto.getBetrag().stripTrailingZeros();
        if (betrag.precision() - betrag.scale() > MAX_VORKOMMASTELLEN_BETRAG) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Betrag ist zu groß.");
        }
        if (betrag.scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Betrag darf höchstens zwei Nachkommastellen haben.");
        }
        if (dto.getMonat() != null && (dto.getMonat() < 1 || dto.getMonat() > 12)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Monat muss zwischen 1 und 12 liegen.");
        }
    }

    private static String leerAlsNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    /**
     * Zählt mit, wie oft ein Lieferant in manuell erfassten Materialkosten
     * vorkommt – hoch beim Erfassen, runter beim Entfernen, nie unter 0.
     */
    private void aendereBestellZaehler(Lieferanten lieferant, int delta) {
        int bisher = lieferant.getBestellungen() == null ? 0 : lieferant.getBestellungen();
        lieferant.setBestellungen(Math.max(0, bisher + delta));
        lieferantenRepository.save(lieferant);
    }

    @Transactional
    public ProjektResponseDto fuegeArtikelMaterialkosten(Long projektId, List<ArtikelMengeDto> artikelAuswahl) {
        Projekt projekt = findeProjektEntity(projektId);
        if (artikelAuswahl != null) {
            for (ArtikelMengeDto auswahl : artikelAuswahl) {
                Artikel artikel = artikelRepository.findById(auswahl.getArtikelId())
                        .orElse(null);
                if (artikel == null) {
                    continue;
                }
                // Nur gueltige Preisstaende - die Tabelle enthaelt seit V338 auch
                // vergangene Staende, die hier nicht kalkuliert werden duerfen.
                LieferantenArtikelPreise lap = artikel.getArtikelpreis().stream()
                        .filter(LieferantenArtikelPreise::isAktuell)
                        .filter(p -> p.getPreis() != null)
                        .filter(p -> auswahl.getLieferantId() == null || (p.getLieferant() != null
                                && p.getLieferant().getId().equals(auswahl.getLieferantId())))
                        .max(Comparator.comparing(LieferantenArtikelPreise::getPreisAenderungsdatum))
                        .orElse(null);
                BigDecimal basisPreis = auswahl.getPreis() != null ? auswahl.getPreis()
                        : lap != null ? lap.getPreis() : null;
                ArtikelInProjekt aip = new ArtikelInProjekt();
                aip.setProjekt(projekt);
                aip.setArtikel(artikel);
                BigDecimal kg = null;
                boolean supportsProfileMeasurements = supportsLengthAwareArtikel(artikel);
                boolean meterAuswahl = "METER".equalsIgnoreCase(auswahl.getEinheit());
                Integer stueck = auswahl.getStueckzahl() != null
                        ? auswahl.getStueckzahl()
                        : (meterAuswahl ? null : (auswahl.getMenge() != null ? auswahl.getMenge().intValue() : null));
                aip.setStueckzahl(stueck);

                BigDecimal stueckAsBig = stueck != null ? BigDecimal.valueOf(stueck) : null;
                BigDecimal fallbackVpe = artikel.getVerpackungseinheit() != null
                        ? BigDecimal.valueOf(artikel.getVerpackungseinheit())
                        : BigDecimal.ONE;
                BigDecimal laengeProStueck = auswahl.getLaengeProStueck();
                if (!meterAuswahl && supportsProfileMeasurements) {
                    if (laengeProStueck == null || laengeProStueck.compareTo(BigDecimal.ZERO) <= 0) {
                        laengeProStueck = fallbackVpe;
                    }
                }

                BigDecimal gesamtLaenge = null;
                if (meterAuswahl) {
                    gesamtLaenge = auswahl.getMenge();
                } else if (supportsProfileMeasurements && stueckAsBig != null && laengeProStueck != null) {
                    gesamtLaenge = laengeProStueck.multiply(stueckAsBig);
                }
                if (gesamtLaenge != null && gesamtLaenge.compareTo(BigDecimal.ZERO) > 0) {
                    aip.setMeter(gesamtLaenge);
                } else {
                    aip.setMeter(null);
                }

                if (artikel instanceof ArtikelWerkstoffe aw && aw.getMasse() != null) {
                    if (meterAuswahl && auswahl.getMenge() != null) {
                        kg = aw.getMasse().multiply(auswahl.getMenge());
                    } else if (gesamtLaenge != null) {
                        kg = aw.getMasse().multiply(gesamtLaenge);
                    }
                }
                if (kg == null && artikel.getVerrechnungseinheit() == Verrechnungseinheit.KILOGRAMM
                        && auswahl.getMenge() != null) {
                    kg = auswahl.getMenge();
                }
                if (kg != null && kg.compareTo(BigDecimal.ZERO) > 0) {
                    aip.setKilogramm(kg);
                } else {
                    aip.setKilogramm(null);
                }

                BigDecimal preisProEinheit = determineUnitPrice(artikel, basisPreis, meterAuswahl, laengeProStueck,
                        supportsProfileMeasurements);
                if (preisProEinheit == null) {
                    preisProEinheit = basisPreis;
                }
                aip.setPreisProStueck(preisProEinheit);
                aip.setHinzugefuegtAm(java.time.LocalDate.now());
                // Lagerware muss nicht mehr beschafft werden und faellt damit
                // aus der offenen Bestellliste heraus. Der Bestellzeitpunkt
                // bleibt leer - bestellt wurde sie nie.
                boolean ausLager = Boolean.TRUE.equals(auswahl.getAusLager());
                aip.setAusLager(ausLager);
                aip.setBestellt(ausLager);
                aip.setBestelltAm(null);
                aip.setKommentar(auswahl.getKommentar());
                // Zuschnitt-Daten nur zulassen, wenn Wurzelkategorie 64/65
                if (darfSchnittbildVerwenden(artikel)) {
                    if (auswahl.getSchnittForm() != null)
                        aip.setSchnittForm(auswahl.getSchnittForm());
                    if (auswahl.getAnschnittWinkelLinks() != null)
                        aip.setAnschnittWinkelLinks(auswahl.getAnschnittWinkelLinks());
                    if (auswahl.getAnschnittWinkelRechts() != null)
                        aip.setAnschnittWinkelRechts(auswahl.getAnschnittWinkelRechts());
                } else {
                    aip.setSchnittForm(null);
                    aip.setAnschnittWinkelLinks(null);
                    aip.setAnschnittWinkelRechts(null);
                }
                aip.setLieferantenArtikelPreis(lap);
                if (lap != null && lap.getLieferant() != null) {
                    aip.setLieferant(lap.getLieferant());
                }
                projekt.getArtikelInProjekt().add(aip);
            }
        }
        Projekt gespeichert = projektRepository.save(projekt);
        return mappeMitKilogramm(gespeichert);
    }

    private boolean supportsLengthAwareArtikel(Artikel artikel) {
        if (artikel == null) {
            return false;
        }
        return (artikel instanceof ArtikelWerkstoffe) || istKategorieEinsOderUnterkategorie(artikel.getKategorie());
    }

    private BigDecimal determineUnitPrice(Artikel artikel,
            BigDecimal basisPreis,
            boolean meterAuswahl,
            BigDecimal laengeProStueck,
            boolean supportsProfileMeasurements) {
        if (basisPreis == null) {
            return null;
        }
        Verrechnungseinheit verrechnungseinheit = artikel != null ? artikel.getVerrechnungseinheit() : null;
        if (verrechnungseinheit == null) {
            return basisPreis;
        }
        if (meterAuswahl) {
            if (verrechnungseinheit == Verrechnungseinheit.KILOGRAMM) {
                BigDecimal masse = getMassPerMeter(artikel);
                if (masse != null) {
                    return basisPreis.multiply(masse);
                }
            }
            return basisPreis;
        }
        if (!supportsProfileMeasurements || laengeProStueck == null
                || laengeProStueck.compareTo(BigDecimal.ZERO) <= 0) {
            return basisPreis;
        }
        if (verrechnungseinheit == Verrechnungseinheit.KILOGRAMM) {
            BigDecimal masse = getMassPerMeter(artikel);
            if (masse != null) {
                return basisPreis.multiply(masse.multiply(laengeProStueck));
            }
        } else if (verrechnungseinheit == Verrechnungseinheit.LAUFENDE_METER
                || verrechnungseinheit == Verrechnungseinheit.QUADRATMETER) {
            return basisPreis.multiply(laengeProStueck);
        }
        return basisPreis;
    }

    private BigDecimal getMassPerMeter(Artikel artikel) {
        if (artikel instanceof ArtikelWerkstoffe aw) {
            return aw.getMasse();
        }
        return null;
    }

    private boolean istKategorieEinsOderUnterkategorie(Kategorie k) {
        Kategorie current = k;
        while (current != null) {
            if (current.getId() != null && current.getId() == 1) {
                return true;
            }
            current = current.getParentKategorie();
        }
        return false;
    }

    @Transactional
    public ProjektResponseDto entferneArtikelMaterialkosten(Long projektId, Long artikelInProjektId) {
        Projekt projekt = findeProjektEntity(projektId);

        ArtikelInProjekt artikelInProjekt = artikelInProjektRepository.findById(artikelInProjektId)
                .orElseThrow(() -> new RuntimeException("Artikel im Projekt konnte nicht gefunden werden."));

        if (!artikelInProjekt.getProjekt().getId().equals(projektId)) {
            throw new RuntimeException("Artikel gehört nicht zum Projekt.");
        }

        projekt.getArtikelInProjekt().removeIf(aip -> aip.getId().equals(artikelInProjektId));
        Projekt gespeichert = projektRepository.save(projekt);
        return mappeMitKilogramm(gespeichert);
    }

    @Transactional
    public ProjektResponseDto aktualisiereArtikelInProjekt(Long projektId, Long artikelInProjektId,
            org.example.kalkulationsprogramm.dto.Artikel.ArtikelInProjektUpdateDto dto) {
        Projekt projekt = findeProjektEntity(projektId);

        ArtikelInProjekt artikelInProjekt = artikelInProjektRepository.findById(artikelInProjektId)
                .orElseThrow(() -> new RuntimeException("Artikel im Projekt konnte nicht gefunden werden."));

        if (!artikelInProjekt.getProjekt().getId().equals(projektId)) {
            throw new RuntimeException("Artikel gehört nicht zum Projekt.");
        }

        boolean wantsCutData = (dto.getSchnittForm() != null) || (dto.getAnschnittWinkelLinks() != null)
                || (dto.getAnschnittWinkelRechts() != null);
        if (wantsCutData && !darfSchnittbildVerwenden(artikelInProjekt.getArtikel())) {
            throw new IllegalArgumentException("Schnittbilder sind nur fuer Kategorien 64/65 erlaubt.");
        }
        if (dto.getSchnittForm() != null)
            artikelInProjekt.setSchnittForm(dto.getSchnittForm());
        if (dto.getAnschnittWinkelLinks() != null)
            artikelInProjekt.setAnschnittWinkelLinks(dto.getAnschnittWinkelLinks());
        if (dto.getAnschnittWinkelRechts() != null)
            artikelInProjekt.setAnschnittWinkelRechts(dto.getAnschnittWinkelRechts());
        if (dto.getKommentar() != null)
            artikelInProjekt.setKommentar(dto.getKommentar());

        artikelInProjektRepository.save(artikelInProjekt);
        Projekt gespeichert = projektRepository.save(projekt);
        return mappeMitKilogramm(gespeichert);
    }

    private boolean darfSchnittbildVerwenden(Artikel artikel) {
        if (artikel == null || artikel.getKategorie() == null)
            return false;
        if (artikel.getWerkstoff() == null || artikel.getWerkstoff().getId() == null) {
            return false;
        }
        long werkstoffId = artikel.getWerkstoff().getId();
        if (werkstoffId != 6L) {
            return false;
        }
        Kategorie cur = artikel.getKategorie();
        while (cur.getParentKategorie() != null) {
            cur = cur.getParentKategorie();
        }
        Integer root = cur.getId();
        return root != null && (root == 64 || root == 65);
    }

    @Transactional
    public ProjektResponseDto entferneMaterialkosten(Long projektId, Long materialId) {
        Projekt projekt = findeProjektEntity(projektId);
        Materialkosten eintrag = projekt.getMaterialkosten().stream()
                .filter(mk -> materialId != null && materialId.equals(mk.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Diese Materialkosten gibt es in diesem Projekt nicht mehr."));
        projekt.getMaterialkosten().remove(eintrag);
        if (eintrag.getLieferant() != null) {
            aendereBestellZaehler(eintrag.getLieferant(), -1);
        }
        Projekt gespeichert = projektRepository.save(projekt);
        return mappeMitKilogramm(gespeichert);
    }

    @Transactional
    public ProjektResponseDto fuegeProduktkategorienHinzu(Long projektId,
            List<ProjektProduktkategorieErfassenDto> kategorienDtos) {
        Projekt projekt = findeProjektEntity(projektId);
        if (kategorienDtos != null) {
            for (ProjektProduktkategorieErfassenDto dto : kategorienDtos) {
                Produktkategorie pk = produktkategorieRepository.findById(dto.getProduktkategorieID())
                        .orElseThrow(() -> new RuntimeException("Produktkategorie nicht gefunden."));
                if (pk.getUnterkategorien() != null && !pk.getUnterkategorien().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Es können nur Produktkategorien ohne Unterkategorien gewählt werden.");
                }
                boolean vorhanden = projekt.getProjektProduktkategorien().stream()
                        .anyMatch(ppk -> ppk.getProduktkategorie().getId().equals(pk.getId()));
                if (vorhanden) {
                    throw new IllegalArgumentException("Produktkategorie bereits dem Projekt zugeordnet.");
                }
                ProjektProduktkategorie ppk = new ProjektProduktkategorie();
                ppk.setProjekt(projekt);
                ppk.setProduktkategorie(pk);
                ppk.setMenge(dto.getMenge());
                projekt.getProjektProduktkategorien().add(ppk);
            }
        }
        Projekt gespeichert = projektRepository.save(projekt);
        return mappeMitKilogramm(gespeichert);
    }

    @Transactional
    public ProjektResponseDto aktualisiereProjektProduktkategorie(Long projektId, Long ppkId,
            ProjektProduktkategorieErfassenDto dto) {
        Projekt projekt = findeProjektEntity(projektId);
        ProjektProduktkategorie ppk = projekt.getProjektProduktkategorien().stream()
                .filter(k -> k.getId().equals(ppkId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Produktkategorie im Projekt nicht gefunden."));
        if (dto.getProduktkategorieID() != null
                && !dto.getProduktkategorieID().equals(ppk.getProduktkategorie().getId())) {
            Produktkategorie pk = produktkategorieRepository.findById(dto.getProduktkategorieID())
                    .orElseThrow(() -> new RuntimeException("Produktkategorie nicht gefunden."));
            if (pk.getUnterkategorien() != null && !pk.getUnterkategorien().isEmpty()) {
                throw new IllegalArgumentException(
                        "Es kÃ¶nnen nur Produktkategorien ohne Unterkategorien gewÃ¤hlt werden.");
            }
            boolean vorhanden = projekt.getProjektProduktkategorien().stream()
                    .anyMatch(k -> k.getProduktkategorie().getId().equals(pk.getId()) && !k.getId().equals(ppkId));
            if (vorhanden) {
                throw new IllegalArgumentException("Produktkategorie bereits dem Projekt zugeordnet.");
            }
            ppk.setProduktkategorie(pk);
        }
        if (dto.getMenge() != null) {
            ppk.setMenge(dto.getMenge());
        }
        Projekt gespeichert = projektRepository.save(projekt);
        return mappeMitKilogramm(gespeichert);
    }

    @Transactional
    public ProjektResponseDto loescheProjektProduktkategorie(Long projektId, Long ppkId) {
        Projekt projekt = findeProjektEntity(projektId);
        if (ZeitbuchungRepository.existsByProjektProduktkategorieId(ppkId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Die Produktkategorie kann nicht gelöscht werden, da bereits Zeitbuchungen dafür existieren.");
        }
        boolean removed = projekt.getProjektProduktkategorien().removeIf(ppk -> ppk.getId().equals(ppkId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Diese Produktkategorie gehört nicht mehr zum Projekt. Bitte die Seite neu laden.");
        }
        Projekt gespeichert = projektRepository.save(projekt);
        return mappeMitKilogramm(gespeichert);
    }

    public List<Projekt> findeAlle() {
        return this.projektRepository.findAll();
    }

    public static boolean pruefeAuftragsnummer(String auftragsnummer) throws FalscheAuftragsnummerException {
        if (auftragsnummer == null || auftragsnummer.isBlank()) {
            throw new FalscheAuftragsnummerException(
                    "Falsche Auftragsnummer - es muss eine Auftragsnummer angegeben werden.");
        }
        return true;
    }

    @Transactional
    public void loescheProjekt(Long projektID) {
        Projekt projekt = findeProjektEntity(projektID);
        for (var dokument : new ArrayList<>(projekt.getProjektDokument())) {
            dateiSpeicherService.loescheDatei(dokument.getId());
        }
        ZeitbuchungRepository.deleteByProjektId(projektID);
        if (projekt.getBildUrl() != null && !projekt.getBildUrl().isBlank()) {
            dateiSpeicherService.loescheBild(projekt.getBildUrl());
        }
        this.projektRepository.delete(projekt);
    }

    private List<Long> sammleKategorieIds(Long id) {
        List<Long> ids = new ArrayList<>();
        ids.add(id);
        produktkategorieRepository.findByUebergeordneteKategorieId(id)
                .forEach(k -> ids.addAll(sammleKategorieIds(k.getId())));
        return ids;
    }

    @Transactional
    public Page<ProjektResponseDto> findeProjekteMitFilter(String q,
            Long kategorieId,
            String kunde,
            String kundenummer,
            String auftragsnummer,
            LocalDate datum,
            Integer jahr,
            Boolean bezahlt,
            Boolean abgeschlossen,
            int page,
            int size) {
        Specification<Projekt> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Free text search: OR across bauvorhaben, kunde, kundennummer, auftragsnummer
            if (q != null && !q.isBlank()) {
                String searchTerm = "%" + q.toLowerCase(java.util.Locale.ROOT) + "%";
                Predicate bauvorhabenLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("bauvorhaben")),
                        searchTerm);
                // kunde und kundennummer sind über kundenId Beziehung erreichbar
                Predicate kundeLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("kundenId").get("name")),
                        searchTerm);
                Predicate kundennummerLike = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("kundenId").get("kundennummer")),
                        searchTerm);
                Predicate auftragsnummerLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("auftragsnummer")),
                        searchTerm);
                predicates.add(criteriaBuilder.or(bauvorhabenLike, kundeLike, kundennummerLike, auftragsnummerLike));
            }

            if (kategorieId != null) {
                List<Long> ids = sammleKategorieIds(kategorieId);
                jakarta.persistence.criteria.Join<Projekt, ProjektProduktkategorie> join = root
                        .join("projektProduktkategorien");
                predicates.add(join.get("produktkategorie").get("id").in(ids));
                query.distinct(true);
            }
            if (kunde != null && !kunde.isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("kundenId").get("name")),
                        "%" + kunde.toLowerCase(java.util.Locale.ROOT) + "%"));
            }
            if (kundenummer != null && !kundenummer.isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("kundenId").get("kundennummer")),
                        "%" + kundenummer.toLowerCase() + "%"));
            }
            if (auftragsnummer != null && !auftragsnummer.isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("auftragsnummer")),
                        "%" + auftragsnummer.toLowerCase() + "%"));
            }
            if (datum != null) {
                predicates.add(criteriaBuilder.equal(root.get("abschlussdatum"), datum));
            }
            // Jahresfilter auf dem Anlegedatum. Achtung: Das ist nicht zwingend das Jahr
            // aus der Auftragsnummer – Anlegedatum und Nummern-Vorschlag sind getrennt
            // setzbar, und Bestandsdaten stammen aus dem Alt-Import.
            if (jahr != null) {
                predicates.add(criteriaBuilder.between(root.get("anlegedatum"),
                        LocalDate.of(jahr, 1, 1), LocalDate.of(jahr, 12, 31)));
            }
            if (bezahlt != null) {
                predicates.add(criteriaBuilder.equal(root.get("bezahlt"), bezahlt));
            }
            if (abgeschlossen != null) {
                predicates.add(criteriaBuilder.equal(root.get("abgeschlossen"), abgeschlossen));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Sort sort = Sort.by(Sort.Direction.DESC, "anlegedatum");
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        Page<Projekt> result = projektRepository.findAll(spec, pageRequest);
        List<ProjektResponseDto> projekte = result.stream()
                .map(this::mappeFuerListe)
                .collect(Collectors.toList());
        return new PageImpl<>(projekte, pageRequest, result.getTotalElements());
    }

    /** Alle Jahre mit angelegten Projekten – füllt das Jahres-Dropdown der Projektsuche. */
    public List<Integer> verfuegbareAnlegeJahre() {
        List<Integer> jahre = projektRepository.findDistinctAnlegedatumJahre();
        return jahre != null ? jahre : List.of();
    }

    /**
     * Optimierte Mapping-Methode für Listenansichten.
     * Vermeidet N+1 Queries für E-Mails und Kilogramm-Statistiken,
     * die in der Übersicht nicht benötigt werden.
     */
    private ProjektResponseDto mappeFuerListe(Projekt projekt) {
        return projektMapper.toProjektListeDto(projekt);
    }

    @Transactional
    public ProjektResponseDto findeProjektById(Long id) {
        // Beim Öffnen eines Projekts: Preis on-the-fly aus Dokumenten berechnen
        ausgangsGeschaeftsDokumentService.aktualisiereProjektPreisAusDokumenten(id);

        Projekt projekt = findeProjektEntity(id);
        return mappeMitKilogramm(projekt);
    }

    /**
     * Lädt ein Projekt oder antwortet mit 404 und verständlicher Meldung – über den
     * RestExceptionHandler, damit die Controller keinen Sammel-Catch brauchen.
     */
    public Projekt findeProjektEntity(Long id) {
        return this.projektRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PROJEKT_NICHT_GEFUNDEN));
    }

    @Transactional
    public ProjektResponseDto updateProjektKurzbeschreibung(Long projektId, String kurzbeschreibung) {
        Projekt projekt = findeProjektEntity(projektId);
        projekt.setKurzbeschreibung(kurzbeschreibung);
        Projekt saved = projektRepository.save(projekt);
        return mappeMitKilogramm(saved);
    }

    private ProjektResponseDto mappeMitKilogramm(Projekt projekt) {
        ProjektResponseDto dto = projektMapper.toProjektResponseDto(projekt);

        // Emails laden und mappen
        List<Email> emails = emailRepository.findByProjektOrderBySentAtDesc(projekt);
        List<ProjektEmailDto> emailDtos = emails.stream().map(e -> {
            ProjektEmailDto ed = new ProjektEmailDto();
            ed.setId(e.getId());
            ed.setDirection(e.getDirection());
            ed.setFrom(e.getFromAddress());
            ed.setTo(e.getRecipient());
            ed.setSubject(e.getSubject());
            ed.setSentAt(e.getSentAt());
            ed.setBodyHtml(e.getHtmlBody() != null ? e.getHtmlBody() : e.getBody());
            if (ed.getBodyHtml() == null)
                ed.setBodyHtml(e.getRawBody());

            // Thread-Info
            ed.setParentEmailId(e.getParentEmail() != null ? e.getParentEmail().getId() : null);
            ed.setReplyCount(countAncestors(e) + countAllReplies(e));

            // Zustellstatus: Angebote und Rechnungen haengen am Projekt — hier
            // muss eine nicht angekommene Mail sichtbar sein.
            ed.setZustellStatus(e.getZustellStatus() != null ? e.getZustellStatus().name() : null);
            ed.setZustellFehler(e.getZustellFehler());

            if (e.getAttachments() != null) {
                final Long emailId = e.getId();
                ed.setAttachments(e.getAttachments().stream().map(att -> {
                    ProjektEmailFileDto ad = new ProjektEmailFileDto();
                    ad.setId(att.getId());
                    ad.setOriginalFilename(att.getOriginalFilename());
                    ad.setStoredFilename(att.getStoredFilename());
                    ad.setContentId(att.getContentId());
                    ad.setInline(Boolean.TRUE.equals(att.getInlineAttachment()));
                    // Download-URL setzen
                    ad.setUrl("/api/emails/" + emailId + "/attachments/" + att.getId());
                    return ad;
                }).collect(Collectors.toList()));
            } else {
                ed.setAttachments(new ArrayList<>());
            }
            return ed;
        }).collect(Collectors.toList());
        dto.setEmails(emailDtos);

        List<MaterialKilogrammDto> kg = artikelInProjektRepository
                .sumKilogrammByProjektGroupedByWerkstoff(projekt.getId());
        dto.setKilogrammProMaterial(kg);
        java.math.BigDecimal total = kg.stream()
                .map(MaterialKilogrammDto::getKilogramm)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        dto.setGesamtKilogramm(total.compareTo(java.math.BigDecimal.ZERO) > 0 ? total : null);
        return dto;
    }

    private int countAllReplies(Email email) {
        if (email.getReplies() == null || email.getReplies().isEmpty()) return 0;
        int count = email.getReplies().size();
        for (Email reply : email.getReplies()) {
            count += countAllReplies(reply);
        }
        return count;
    }

    private int countAncestors(Email email) {
        int count = 0;
        java.util.Set<Long> visited = new java.util.HashSet<>();
        Email current = email.getParentEmail();
        while (current != null && !visited.contains(current.getId())) {
            visited.add(current.getId());
            count++;
            current = current.getParentEmail();
        }
        return count;
    }

    private void ensureProjektHatZugewiesenenKunden(ProjektErstellenDto dto, Projekt projekt) {
        boolean dtoHatKunde = dto != null && dto.getKundenId() != null;
        boolean projektHatKunde = projekt != null && projekt.getKundenId() != null;
        if (!dtoHatKunde && !projektHatKunde) {
            throw new IllegalArgumentException(PROJEKT_KUNDE_PFLICHT_MESSAGE);
        }
    }

    private Kunde prepareProjektKunde(ProjektErstellenDto dto) {
        if (dto == null || dto.getKundenId() == null) {
            return null;
        }
        Kunde kunde = kundeRepository.findById(dto.getKundenId())
                .orElseThrow(() -> new NotFoundException("Kunde mit ID " + dto.getKundenId() + " nicht gefunden."));
        if (!StringUtils.hasText(dto.getKunde())) {
            dto.setKunde(kunde.getName());
        }
        if (!StringUtils.hasText(dto.getKundennummer())) {
            dto.setKundennummer(kunde.getKundennummer());
        }
        if ((dto.getKundenEmails() == null || dto.getKundenEmails().isEmpty()) && kunde.getKundenEmails() != null) {
            dto.setKundenEmails(new ArrayList<>(kunde.getKundenEmails()));
        }
        return kunde;
    }

    private void runAfterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        runnable.run();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } else {
            runnable.run();
        }
    }

    /**
     * Generiert die nächste Auftragsnummer im Format YYYY/MM/XXXXX
     * basierend auf dem Anlegedatum.
     * 
     * @param anlegedatum Das Datum für die Auftragsnummer (bestimmt Jahr und
     *                    Monat)
     * @return Die nächste verfügbare Auftragsnummer
     */
    public String generiereNaechsteAuftragsnummer(LocalDate anlegedatum) {
        if (anlegedatum == null) {
            anlegedatum = LocalDate.now();
        }

        String prefix = "%d/%02d/".formatted(anlegedatum.getYear(), anlegedatum.getMonthValue());

        List<String> existingNumbers = projektRepository.findAuftragsnummernByPrefix(prefix);

        long nextCounter = 1;
        if (!existingNumbers.isEmpty()) {
            // Die höchste Nummer finden und um 1 erhöhen
            String highest = existingNumbers.getFirst(); // Bereits absteigend sortiert
            String counterPart = highest.substring(prefix.length());
            try {
                nextCounter = Long.parseLong(counterPart) + 1;
            } catch (NumberFormatException e) {
                // Falls das Parsen fehlschlägt, bei 1 anfangen
                nextCounter = 1;
            }
        }

        return "%s%05d".formatted(prefix, nextCounter);
    }

    /**
     * Generiert eine kundenspezifische Auftragsnummer für die Anfrage→Projekt-Konvertierung
     * nach digitaler Angebots-Annahme.
     *
     * <p>Format: {@code YYYY/MM/NNNCC}
     * <ul>
     *   <li>{@code YYYY} – Jahr aus {@code anlegedatum}</li>
     *   <li>{@code MM}   – Monat aus {@code anlegedatum}</li>
     *   <li>{@code NNN}  – Kunden-Slot innerhalb des Jahres (001–999):
     *       Erste Bestellung eines Kunden im Jahr → neuer Slot. Folgebestellungen
     *       desselben Kunden im selben Jahr behalten den Slot.</li>
     *   <li>{@code CC}   – Laufende Auftragsnummer dieses Kunden im Jahr (00–99).</li>
     * </ul>
     *
     * <p>Beispiele:
     * <ul>
     *   <li>Erster Auftrag von Kunde A (neu im Jahr) im Januar 2026 → {@code 2026/01/00100}</li>
     *   <li>Zweiter Auftrag von Kunde A im Mai 2026 → {@code 2026/05/00101}</li>
     *   <li>Erster Auftrag von Kunde B im Mai 2026 → {@code 2026/05/00200}</li>
     * </ul>
     *
     * <p>Fallback auf {@link #generiereNaechsteAuftragsnummer(LocalDate)}, wenn:
     * <ul>
     *   <li>{@code kundeId == null} (kein Kunde verknüpft)</li>
     *   <li>Slot- oder Auftragszähler die Grenzen überschreitet (NNN > 999 / CC > 99)</li>
     *   <li>Die berechnete Nummer bereits vergeben ist (Race-Condition-Schutz)</li>
     * </ul>
     */
    public String generiereKundenAuftragsnummer(LocalDate anlegedatum, Long kundeId) {
        return ermittleKundenAuftragsnummer(anlegedatum, kundeId).auftragsnummer();
    }

    /**
     * Ergebnis der kundenspezifischen Auftragsnummern-Vergabe — inklusive der Herleitung,
     * damit das Frontend dem Benutzer erklären kann, warum die vorgeschlagene Nummer so lautet.
     *
     * @param auftragsnummer   die vorgeschlagene Nummer
     * @param kundenLogik      {@code true}, wenn die Nummer nach dem Schema {@code YYYY/MM/NNNCC}
     *                         vergeben wurde; {@code false} beim Fallback auf die rein
     *                         fortlaufende Nummer (kein Kunde, Überlauf oder Kollision)
     * @param neuerKundeImJahr {@code true}, wenn der Kunde in diesem Jahr noch keinen Auftrag hatte
     * @param kundenSlot       die Kundennummer im Jahr (NNN), {@code 0} ohne Kundenlogik
     * @param auftragImJahr    der wievielte Auftrag des Kunden in diesem Jahr (1-basiert,
     *                         also {@code CC + 1}), {@code 0} ohne Kundenlogik
     */
    public record AuftragsnummerVorschlag(
            String auftragsnummer,
            boolean kundenLogik,
            boolean neuerKundeImJahr,
            int kundenSlot,
            int auftragImJahr) {

        static AuftragsnummerVorschlag fallback(String auftragsnummer) {
            return new AuftragsnummerVorschlag(auftragsnummer, false, false, 0, 0);
        }
    }

    /**
     * Prüft, ob es den Kunden gibt. Der Auftragsnummer-Vorschlag darf für eine unbekannte
     * ID keinen Kunden-Slot verbrennen und keinen Hinweistext über einen Kunden erfinden,
     * den es nicht gibt.
     */
    // Vollqualifiziert, weil in dieser Datei jakarta.transaction.Transactional
    // importiert ist – die kennt kein readOnly.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean existiertKunde(Long kundeId) {
        return kundeId != null && kundeId > 0 && kundeRepository.existsById(kundeId);
    }

    /**
     * Wie {@link #generiereKundenAuftragsnummer(LocalDate, Long)}, liefert zusätzlich die
     * Herleitung der Nummer für den Hinweistext im Anlege-Dialog.
     */
    public AuftragsnummerVorschlag ermittleKundenAuftragsnummer(LocalDate anlegedatum, Long kundeId) {
        if (anlegedatum == null) {
            anlegedatum = LocalDate.now();
        }
        if (kundeId == null) {
            return AuftragsnummerVorschlag.fallback(generiereNaechsteAuftragsnummer(anlegedatum));
        }

        String jahrPrefix = "%d/".formatted(anlegedatum.getYear());
        String monatPrefix = "%d/%02d/".formatted(anlegedatum.getYear(), anlegedatum.getMonthValue());

        List<String> kundenAuftraege =
                projektRepository.findAuftragsnummernByKundeAndYearPrefix(kundeId, jahrPrefix);

        int nnn;
        int cc;
        boolean neuerKundeImJahr;

        if (!kundenAuftraege.isEmpty()) {
            // Kunde hat in diesem Jahr bereits Aufträge → existierenden Slot wiederverwenden,
            // höchstes CC ermitteln und +1.
            //
            // Der Slot stammt aus der höchsten Nummer des Kunden (Liste kommt absteigend sortiert
            // aus dem Repository). Weichen mehrere Nummern eines Kunden im Slot voneinander ab —
            // etwa weil eine Nummer von Hand vergeben wurde — gewinnt so immer dieselbe, statt
            // je nach DB-Reihenfolge zu springen. Das höchste CC wird über alle Nummern gesucht,
            // damit auch bei gemischten Slots keine Nummer doppelt vergeben wird.
            Integer slotFromKunde = null;
            int hoechstesCc = -1;
            for (String nr : kundenAuftraege) {
                int[] parsed = parseSlotUndCc(nr, jahrPrefix);
                if (parsed == null) {
                    continue;
                }
                if (slotFromKunde == null) {
                    slotFromKunde = parsed[0];
                }
                if (parsed[1] > hoechstesCc) {
                    hoechstesCc = parsed[1];
                }
            }
            if (slotFromKunde == null || hoechstesCc < 0 || hoechstesCc >= 99) {
                return AuftragsnummerVorschlag.fallback(generiereNaechsteAuftragsnummer(anlegedatum));
            }
            nnn = slotFromKunde;
            cc = hoechstesCc + 1;
            neuerKundeImJahr = false;
        } else {
            // Neuer Kunden-Slot im Jahr → höchste vorhandene NNN-Komponente + 1.
            // Hinweis: Bestandsdaten aus der alten reinen Fortlauf-Logik (z.B. "2026/01/00007")
            // werden vom Parser als Slot=000, CC=07 interpretiert. Das ist bewusst akzeptiert:
            // - Slot-Vergabe für neue Kunden bleibt korrekt (Start bei 001, Konflikte verhindert
            //   der existsByAuftragsnummer-Fallback unten und der UNIQUE-Constraint der DB).
            // - Bestandskunden bleiben semantisch im "Legacy-Slot 000" — eine Migration der
            //   Alt-Auftragsnummern ist GoBD-relevant und daher nicht gewünscht.
            List<String> alleAuftraegeImJahr = projektRepository.findAuftragsnummernByYearPrefix(jahrPrefix);
            int hoechsterSlot = 0;
            for (String nr : alleAuftraegeImJahr) {
                int[] parsed = parseSlotUndCc(nr, jahrPrefix);
                if (parsed != null && parsed[0] > hoechsterSlot) {
                    hoechsterSlot = parsed[0];
                }
            }
            if (hoechsterSlot >= 999) {
                return AuftragsnummerVorschlag.fallback(generiereNaechsteAuftragsnummer(anlegedatum));
            }
            nnn = hoechsterSlot + 1;
            cc = 0;
            neuerKundeImJahr = true;
        }

        String kandidat = "%s%03d%02d".formatted(monatPrefix, nnn, cc);
        if (projektRepository.existsByAuftragsnummer(kandidat)) {
            // Sehr unwahrscheinlich (Race oder Altdaten-Kollision) – sicherer Fallback.
            return AuftragsnummerVorschlag.fallback(generiereNaechsteAuftragsnummer(anlegedatum));
        }
        return new AuftragsnummerVorschlag(kandidat, true, neuerKundeImJahr, nnn, cc + 1);
    }

    /**
     * Parst eine Auftragsnummer im Format {@code YYYY/MM/NNNCC} und liefert
     * {@code int[]{NNN, CC}} oder {@code null}, wenn das Format nicht passt
     * (z.B. Bestandsdaten mit abweichender Stellenzahl).
     */
    private static int[] parseSlotUndCc(String auftragsnummer, String jahrPrefix) {
        if (auftragsnummer == null || jahrPrefix == null || !auftragsnummer.startsWith(jahrPrefix)) {
            return null;
        }
        String rest = auftragsnummer.substring(jahrPrefix.length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return null;
        }
        String zaehler = rest.substring(slash + 1);
        if (zaehler.length() != 5) {
            return null;
        }
        try {
            int nnn = Integer.parseInt(zaehler.substring(0, 3));
            int cc = Integer.parseInt(zaehler.substring(3, 5));
            return new int[]{nnn, cc};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Gibt nur den Zähler-Teil (XXXXX) für eine Auftragsnummer zurück.
     */
    public long getNaechsterAuftragsnummerZaehler(LocalDate anlegedatum) {
        if (anlegedatum == null) {
            anlegedatum = LocalDate.now();
        }

        String prefix = "%d/%02d/".formatted(anlegedatum.getYear(), anlegedatum.getMonthValue());

        List<String> existingNumbers = projektRepository.findAuftragsnummernByPrefix(prefix);

        if (existingNumbers.isEmpty()) {
            return 1;
        }

        String highest = existingNumbers.getFirst();
        String counterPart = highest.substring(prefix.length());
        try {
            return Long.parseLong(counterPart) + 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Prüft ob eine Auftragsnummer bereits vergeben ist.
     */
    public boolean istAuftragsnummerVergeben(String auftragsnummer) {
        if (auftragsnummer == null || auftragsnummer.isBlank()) {
            return false;
        }
        return projektRepository.existsByAuftragsnummer(auftragsnummer);
    }

    /**
     * Prüft ob eine Auftragsnummer von einem anderen Projekt (nicht dem mit
     * projektId) verwendet wird.
     */
    public boolean istAuftragsnummerVergebenFuerAnderesProjekt(String auftragsnummer, Long projektId) {
        if (auftragsnummer == null || auftragsnummer.isBlank()) {
            return false;
        }
        if (projektId == null) {
            return istAuftragsnummerVergeben(auftragsnummer);
        }
        return projektRepository.existsByAuftragsnummerAndIdNot(auftragsnummer, projektId);
    }

    private void migrateAnfrageKalenderEintraegeToProjekt(Anfrage anfrage, Projekt projekt) {
        List<KalenderEintrag> eintraege = kalenderEintragRepository.findByAnfrageIdOrderByDatumDesc(anfrage.getId());
        for (KalenderEintrag e : eintraege) {
            e.setAnfrage(null);
            e.setProjekt(projekt);
        }
        if (!eintraege.isEmpty()) {
            kalenderEintragRepository.saveAll(eintraege);
        }
    }
}
