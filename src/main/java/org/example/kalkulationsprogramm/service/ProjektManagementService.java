package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.AnfrageNotiz;
import org.example.kalkulationsprogramm.domain.AnfrageNotizBild;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektArt;
import org.example.kalkulationsprogramm.domain.ProjektNotiz;
import org.example.kalkulationsprogramm.domain.ProjektNotizBild;
import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
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
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;

@Service
public class

ProjektManagementService {
    private static final String PROJEKT_KUNDE_PFLICHT_MESSAGE = "Für Projekte muss ein Kunde ausgewählt werden.";
    private final ProjektRepository projektRepository;
    private final AnfrageNotizRepository anfrageNotizRepository;
    private final ProjektNotizRepository projektNotizRepository;
    private final ProduktkategorieRepository produktkategorieRepository;
    private final ArbeitsgangRepository arbeitsgangRepository;
    private final KundeRepository kundeRepository;
    private final DateiSpeicherService dateiSpeicherService;
    private final ProjektMapper projektMapper;
    private final AnfrageRepository anfrageRepository;
    private final ZeitbuchungRepository ZeitbuchungRepository;
    private final ArbeitsgangStundensatzRepository stundensatzRepository;
    private final ArtikelRepository artikelRepository;
    private final ArtikelInProjektRepository artikelInProjektRepository;
    private final ProjektPersistenceService projektPersistenceService;
    private final LieferantenRepository lieferantenRepository;
    private final EmailRepository emailRepository;
    private final org.example.kalkulationsprogramm.repository.SchnittbilderRepository schnittbilderRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @lombok.Setter(onMethod_ = @org.springframework.beans.factory.annotation.Autowired)
    private AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;

    @PersistenceContext
    private EntityManager entityManager;

    public ProjektManagementService(ProjektRepository projektRepository,
            AnfrageNotizRepository anfrageNotizRepository,
            ProjektNotizRepository projektNotizRepository,
            ProduktkategorieRepository produktkategorieRepository,
            ArbeitsgangRepository arbeitsgangRepository,
            KundeRepository kundeRepository,
            DateiSpeicherService dateiSpeicherService,
            ProjektMapper projektMapper,
            AnfrageRepository anfrageRepository,
            ZeitbuchungRepository ZeitbuchungRepository,
            ArbeitsgangStundensatzRepository stundensatzRepository,
            ArtikelRepository artikelRepository,
            ArtikelInProjektRepository artikelInProjektRepository,
            ProjektPersistenceService projektPersistenceService,
            LieferantenRepository lieferantenRepository,
            EmailRepository emailRepository,
            org.example.kalkulationsprogramm.repository.SchnittbilderRepository schnittbilderRepository,
            org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.projektRepository = projektRepository;
        this.anfrageNotizRepository = anfrageNotizRepository;
        this.projektNotizRepository = projektNotizRepository;
        this.produktkategorieRepository = produktkategorieRepository;
        this.arbeitsgangRepository = arbeitsgangRepository;
        this.kundeRepository = kundeRepository;
        this.dateiSpeicherService = dateiSpeicherService;
        this.projektMapper = projektMapper;
        this.anfrageRepository = anfrageRepository;
        this.ZeitbuchungRepository = ZeitbuchungRepository;
        this.stundensatzRepository = stundensatzRepository;
        this.artikelRepository = artikelRepository;
        this.artikelInProjektRepository = artikelInProjektRepository;
        this.projektPersistenceService = projektPersistenceService;
        this.lieferantenRepository = lieferantenRepository;
        this.emailRepository = emailRepository;
        this.schnittbilderRepository = schnittbilderRepository;
        this.eventPublisher = eventPublisher;
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

        // Wenn Referenzanfragen angegeben wurden, fehlende Felder aus dem ersten
        // übernehmen und Bruttopreis summieren
        if (dto.getAnfrageIds() != null && !dto.getAnfrageIds().isEmpty()) {
            List<Anfrage> anfragen = anfrageRepository.findAllById(dto.getAnfrageIds());
            if (!anfragen.isEmpty()) {
                Anfrage erstes = anfragen.getFirst();
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
                    dto.setKundenEmails(anfragen.stream()
                            .flatMap(a -> (a.getKunde() != null && a.getKunde().getKundenEmails() != null)
                                    ? a.getKunde().getKundenEmails().stream()
                                    : java.util.stream.Stream.empty())
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toList()));
                }
                java.math.BigDecimal summe = anfragen.stream()
                        .map(Anfrage::getBetrag)
                        .filter(java.util.Objects::nonNull)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                dto.setBruttoPreis(summe);
            }
        }

        ensureProjektHatZugewiesenenKunden(dto, null);
        Kunde ausgewaehlterKunde = prepareProjektKunde(dto);

        if (ausgewaehlterKunde != null) {
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

        // EN 1090 Ausführungsklasse (optional)
        neuesProjekt.setExcKlasse(dto.getExcKlasse());

        // KORREKTUR: Das separate 'bild'-Objekt verarbeiten
        if (bild != null && !bild.isEmpty()) {
            String bildWebPfad = this.dateiSpeicherService.speichereBild(bild);
            neuesProjekt.setBildUrl(bildWebPfad);
        }

        // Produktkategorien
        List<ProjektProduktkategorie> kategorien = new ArrayList<>();
        java.util.Map<Long, ProjektProduktkategorie> kategorieMap;
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
            kategorieMap = kategorien.stream()
                    .collect(Collectors.toMap(ppk -> ppk.getProduktkategorie().getId(), ppk -> ppk));
        } else {
            kategorieMap = Collections.emptyMap();
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

        // Sicherstellen, dass die Liste nicht null ist
        List<Zeitbuchung> zeitPositionen = new ArrayList<>();
        if (dto.getZeitPositionen() != null) {
            java.util.Set<String> ids = new java.util.HashSet<>();
            dto.getZeitPositionen().forEach(z -> {
                String key = z.getArbeitsgangID() + "_" + z.getProduktkategorieID();
                if (!ids.add(key)) {
                    throw new IllegalArgumentException(
                            "Ein Arbeitsgang darf pro Produktkategorie nur einmal gewählt werden.");
                }
            });
            zeitPositionen = dto.getZeitPositionen().stream()
                    .map(zeitDto -> {
                        Arbeitsgang arbeitsgang = arbeitsgangRepository.findById(zeitDto.getArbeitsgangID())
                                .orElseThrow(() -> new RuntimeException("Zeitart konnte nicht gefunden werden."));
                        ProjektProduktkategorie ppk = kategorieMap.get(zeitDto.getProduktkategorieID());
                        if (ppk == null) {
                            throw new RuntimeException("Produktkategorie ist nicht dem Projekt zugeordnet.");
                        }
                        Zeitbuchung Zeitbuchung = new Zeitbuchung();
                        Zeitbuchung.setProjekt(neuesProjekt);
                        Zeitbuchung.setArbeitsgang(arbeitsgang);
                        Zeitbuchung.setProjektProduktkategorie(ppk);
                        Zeitbuchung.setAnzahlInStunden(zeitDto.getAnzahlInStunden());
                        ArbeitsgangStundensatz satz = stundensatzRepository
                                .findTopByArbeitsgangIdAndJahrOrderByIdDesc(arbeitsgang.getId(),
                                        neuesProjekt.getAnlegedatum().getYear())
                                .or(() -> stundensatzRepository
                                        .findTopByArbeitsgangIdOrderByJahrDesc(arbeitsgang.getId()))
                                .orElseThrow(() -> new RuntimeException("Kein Stundensatz für Arbeitsgang"));
                        Zeitbuchung.setArbeitsgangStundensatz(satz);
                        return Zeitbuchung;
                    }).collect(Collectors.toCollection(ArrayList::new));
        }
        neuesProjekt.setZeitbuchungen(new ArrayList<>(zeitPositionen));

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

        if (dto.getAnfrageIds() != null && !dto.getAnfrageIds().isEmpty()) {
            List<Anfrage> anfragen = anfrageRepository.findAllById(dto.getAnfrageIds());
            boolean imageSetFromAnfrage = false;
            for (Anfrage a : anfragen) {
                // Übernehme Profilbild vom Anfrage, falls Projekt noch keines hat
                if (!imageSetFromAnfrage
                        && (gespeichertesProjekt.getBildUrl() == null || gespeichertesProjekt.getBildUrl().isBlank())) {
                    if (a.getBildUrl() != null && !a.getBildUrl().isBlank()) {
                        gespeichertesProjekt.setBildUrl(a.getBildUrl());
                        imageSetFromAnfrage = true;
                    }
                }
                for (AnfrageDokument doc : a.getDokumente()) {
                    dateiSpeicherService.verschiebeAnfragesDatei(doc, gespeichertesProjekt);
                }
                // E-Mail-Verlauf vom Anfrage in das Projekt kopieren (inkl. Anhänge)
                try {
                    copyAnfrageEmailsToProjekt(a, gespeichertesProjekt);
                } catch (Exception ignored) {
                }
                // Notizen vom Anfrage in das Projekt kopieren (inkl. Bilder)
                try {
                    List<AnfrageNotiz> aNotizen = anfrageNotizRepository
                            .findByAnfrageIdOrderByErstelltAmDesc(a.getId());
                    for (AnfrageNotiz aNotiz : aNotizen) {
                        ProjektNotiz pNotiz = new ProjektNotiz();
                        pNotiz.setProjekt(gespeichertesProjekt);
                        pNotiz.setMitarbeiter(aNotiz.getMitarbeiter());
                        pNotiz.setNotiz(aNotiz.getNotiz());
                        pNotiz.setErstelltAm(aNotiz.getErstelltAm());
                        pNotiz.setMobileSichtbar(aNotiz.isMobileSichtbar());

                        List<ProjektNotizBild> pBilder = new ArrayList<>();
                        if (aNotiz.getBilder() != null) {
                            for (AnfrageNotizBild aBild : aNotiz.getBilder()) {
                                ProjektNotizBild pBild = new ProjektNotizBild();
                                pBild.setNotiz(pNotiz);
                                pBild.setOriginalDateiname(aBild.getOriginalDateiname());
                                pBild.setGespeicherterDateiname(aBild.getGespeicherterDateiname());
                                pBild.setDateityp(aBild.getDateityp());
                                pBild.setErstelltAm(aBild.getErstelltAm());
                                pBilder.add(pBild);
                            }
                        }
                        pNotiz.setBilder(pBilder);
                        projektNotizRepository.save(pNotiz);
                    }
                } catch (Exception e) {
                    System.err.println("Fehler beim Kopieren der Anfragesnotizen: " + e.getMessage());
                }

                // Ausgangsgeschäftsdokumente vom Anfrage zum Projekt migrieren
                try {
                    ausgangsGeschaeftsDokumentService.migrateFromAnfrageToProjekt(a.getId(), gespeichertesProjekt);
                } catch (Exception e) {
                    System.err.println("Fehler beim Migrieren der Ausgangsgeschäftsdokumente: " + e.getMessage());
                }

                anfrageRepository.delete(a);
            }
            if (imageSetFromAnfrage) {
                try {
                    projektRepository.save(gespeichertesProjekt);
                } catch (Exception ignored) {
                }
            }
        }

        if (dokumente != null) {
            dokumente.stream()
                    .filter(d -> !d.isEmpty())
                    .forEach(d -> dateiSpeicherService.speichereDatei(d, gespeichertesProjekt.getId(),
                            DokumentGruppe.DIVERSE_DOKUMENTE, uploadedBy));
        }

        return findeProjektById(gespeichertesProjekt.getId());
    }

    @Transactional
    public ProjektResponseDto aktualisiereProjekt(Long id, ProjektErstellenDto dto, String strasse, String plz,
            String ort, MultipartFile bild, Mitarbeiter uploadedBy)
            throws FalscheAuftragsnummerException {
        Projekt projekt = projektRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Projekt konnte nicht gefunden werden."));

        ensureProjektHatZugewiesenenKunden(dto, projekt);
        projekt.setStrasse(strasse);
        projekt.setPlz(plz);
        projekt.setOrt(ort);

        Kunde aktualisierterKunde = prepareProjektKunde(dto);
        if (aktualisierterKunde != null) {
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
        if (dto.getBruttoPreis() != null) {
            projekt.setBruttoPreis(dto.getBruttoPreis());
        }
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

        // Manuelles Beenden/Schließen des Projekts
        projekt.setAbgeschlossen(dto.isAbgeschlossen());
        
        // Projektart aktualisieren
        if (dto.getProjektArt() != null && !dto.getProjektArt().isBlank()) {
            try {
                projekt.setProjektArt(ProjektArt.valueOf(dto.getProjektArt()));
            } catch (IllegalArgumentException e) {
                // Bei ungültigem Wert Projektart beibehalten
            }
        }

        // EN 1090 Ausführungsklasse aktualisieren (null = kein EN 1090 Projekt)
        projekt.setExcKlasse(dto.getExcKlasse());

        // Projekt-spezifische E-Mail-Adressen aktualisieren
        if (dto.getKundenEmails() != null) {
            projekt.getKundenEmails().clear();
            projekt.getKundenEmails().addAll(dto.getKundenEmails());
        }

        // Map für Zeitbuchungen: produktkategorie.id -> ProjektProduktkategorie
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
            kategorieMap = projekt.getProjektProduktkategorien().stream()
                    .collect(Collectors.toMap(ppk -> ppk.getProduktkategorie().getId(), ppk -> ppk));
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

        if (dto.getZeitPositionen() != null) {
            java.util.Map<String, Zeitbuchung> existierend = new java.util.LinkedHashMap<>();
            java.util.Iterator<Zeitbuchung> iterator = projekt.getZeitbuchungen().iterator();
            while (iterator.hasNext()) {
                Zeitbuchung zip = iterator.next();
                String key = zip.getArbeitsgang().getId() + "_"
                        + zip.getProjektProduktkategorie().getProduktkategorie().getId();
                Zeitbuchung bereitsVorhanden = existierend.get(key);
                if (bereitsVorhanden == null) {
                    existierend.put(key, zip);
                } else {
                    java.math.BigDecimal stunden = zip.getAnzahlInStunden();
                    if (stunden != null) {
                        java.math.BigDecimal vorhandeneStunden = bereitsVorhanden.getAnzahlInStunden();
                        bereitsVorhanden.setAnzahlInStunden(
                                vorhandeneStunden == null ? stunden : vorhandeneStunden.add(stunden));
                    }
                    iterator.remove();
                }
            }
            java.util.Map<String, org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitErfassenDto> aggregierteDtos = new java.util.LinkedHashMap<>();
            for (org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitErfassenDto zeitDtoOriginal : dto
                    .getZeitPositionen()) {
                String key = zeitDtoOriginal.getArbeitsgangID() + "_" + zeitDtoOriginal.getProduktkategorieID();
                org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitErfassenDto aggregiert = aggregierteDtos.get(key);
                java.math.BigDecimal aktuelleStunden = zeitDtoOriginal.getAnzahlInStunden() != null
                        ? zeitDtoOriginal.getAnzahlInStunden()
                        : java.math.BigDecimal.ZERO;
                if (aggregiert == null) {
                    org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitErfassenDto neu = new org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitErfassenDto();
                    neu.setArbeitsgangID(zeitDtoOriginal.getArbeitsgangID());
                    neu.setProduktkategorieID(zeitDtoOriginal.getProduktkategorieID());
                    neu.setAnzahlInStunden(aktuelleStunden);
                    aggregierteDtos.put(key, neu);
                } else {
                    java.math.BigDecimal vorhandeneStunden = aggregiert.getAnzahlInStunden();
                    if (vorhandeneStunden == null) {
                        aggregiert.setAnzahlInStunden(aktuelleStunden);
                    } else {
                        aggregiert.setAnzahlInStunden(vorhandeneStunden.add(aktuelleStunden));
                    }
                }
            }

            java.util.Map<String, Zeitbuchung> vorhandeneZeiten = projekt.getZeitbuchungen().stream()
                    .collect(
                            Collectors.toMap(
                                    zip -> zip.getArbeitsgang().getId() + "_"
                                            + zip.getProjektProduktkategorie().getProduktkategorie().getId(),
                                    zip -> zip));
            java.util.List<Zeitbuchung> aktualisierteZeiten = new java.util.ArrayList<>();
            for (org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitErfassenDto zeitDto : aggregierteDtos.values()) {
                String key = zeitDto.getArbeitsgangID() + "_" + zeitDto.getProduktkategorieID();
                Zeitbuchung zip = vorhandeneZeiten.get(key);
                Arbeitsgang arbeitsgang = arbeitsgangRepository.findById(zeitDto.getArbeitsgangID())
                        .orElseThrow(() -> new RuntimeException("Zeitart konnte nicht gefunden werden."));
                ProjektProduktkategorie ppk = kategorieMap.get(zeitDto.getProduktkategorieID());
                if (ppk == null) {
                    throw new RuntimeException("Produktkategorie ist nicht dem Projekt zugeordnet.");
                }
                if (zip == null && projekt.getId() != null) {
                    zip = ZeitbuchungRepository
                            .findByProjektIdAndArbeitsgangIdAndProjektProduktkategorieId(
                                    projekt.getId(), arbeitsgang.getId(), ppk.getId())
                            .orElse(null);
                }
                if (zip == null) {
                    zip = new Zeitbuchung();
                    zip.setProjekt(projekt);
                }
                ArbeitsgangStundensatz satz = stundensatzRepository
                        .findTopByArbeitsgangIdAndJahrOrderByIdDesc(arbeitsgang.getId(),
                                projekt.getAnlegedatum().getYear())
                        .or(() -> stundensatzRepository.findTopByArbeitsgangIdOrderByJahrDesc(arbeitsgang.getId()))
                        .orElseThrow(() -> new RuntimeException("Kein Stundensatz für Arbeitsgang"));
                java.math.BigDecimal stundenwert = zeitDto.getAnzahlInStunden() != null
                        ? zeitDto.getAnzahlInStunden()
                        : java.math.BigDecimal.ZERO;
                zip.setArbeitsgang(arbeitsgang);
                zip.setProjektProduktkategorie(ppk);
                zip.setAnzahlInStunden(stundenwert);
                zip.setArbeitsgangStundensatz(satz);
                aktualisierteZeiten.add(zip);
            }
            projekt.getZeitbuchungen().clear();
            projekt.getZeitbuchungen().addAll(aktualisierteZeiten);
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

    @Transactional
    public ProjektResponseDto aktualisiereMaterialkosten(Long projektId, List<MaterialkostenErfassenDto> materialDtos) {
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new RuntimeException("Projekt konnte nicht gefunden werden."));

        // Dekrementiere Bestellungen für entfernte Materialkosten (oder alle, da wir
        // gleich clearen)
        for (org.example.kalkulationsprogramm.domain.Materialkosten alt : projekt.getMaterialkosten()) {
            if (alt.getLieferant() != null) {
                Lieferanten l = alt.getLieferant();
                if (l.getBestellungen() != null && l.getBestellungen() > 0) {
                    l.setBestellungen(l.getBestellungen() - 1);
                    lieferantenRepository.save(l);
                }
            }
        }

        projekt.getMaterialkosten().clear();
        if (materialDtos != null) {
            List<org.example.kalkulationsprogramm.domain.Materialkosten> materialKosten = materialDtos.stream()
                    .map(dto -> {
                        org.example.kalkulationsprogramm.domain.Materialkosten mk = new org.example.kalkulationsprogramm.domain.Materialkosten();
                        mk.setProjekt(projekt);
                        mk.setBeschreibung(dto.getBeschreibung());
                        mk.setExterneArtikelnummer(dto.getExterneArtikelnummer());
                        mk.setMonat(dto.getMonat());
                        mk.setBetrag(dto.getBetrag());
                        mk.setRechnungsnummer(dto.getRechnungsnummer());

                        if (dto.getLieferantId() != null) {
                            Lieferanten lieferant = lieferantenRepository.findById(dto.getLieferantId()).orElse(null);
                            if (lieferant != null) {
                                mk.setLieferant(lieferant);
                                // Increment bestellungen counter
                                lieferant.setBestellungen(
                                        lieferant.getBestellungen() == null ? 1 : lieferant.getBestellungen() + 1);
                                lieferantenRepository.save(lieferant);
                            }
                        }

                        return mk;
                    }).collect(Collectors.toCollection(ArrayList::new));
            projekt.getMaterialkosten().addAll(materialKosten);
        }
        Projekt gespeichert = projektRepository.save(projekt);
        return mappeMitKilogramm(gespeichert);
    }

    @Transactional
    public ProjektResponseDto fuegeArtikelMaterialkosten(Long projektId, List<ArtikelMengeDto> artikelAuswahl) {
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new RuntimeException("Projekt konnte nicht gefunden werden."));
        if (artikelAuswahl != null) {
            for (ArtikelMengeDto auswahl : artikelAuswahl) {
                Artikel artikel = artikelRepository.findById(auswahl.getArtikelId())
                        .orElse(null);
                if (artikel == null) {
                    continue;
                }
                LieferantenArtikelPreise lap = artikel.getArtikelpreis().stream()
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

                aip.setHinzugefuegtAm(java.time.LocalDate.now());
                boolean ausLager = Boolean.TRUE.equals(auswahl.getAusLager());
                if (ausLager) {
                    // Nachkalkulationspreis kommt aus dem Lager-Ø (bzw. manueller
                    // Eingabe) — siehe LagerAbgleichService. Als Initialwert den
                    // im Request mitgegebenen Preis nehmen, damit die Kalkulation
                    // direkt steht; der Lager-Haken kann später überschreiben.
                    BigDecimal preisProEinheit = determineUnitPrice(artikel, basisPreis, meterAuswahl, laengeProStueck,
                            supportsProfileMeasurements);
                    if (preisProEinheit == null) {
                        preisProEinheit = basisPreis;
                    }
                    aip.setPreisProStueck(preisProEinheit);
                    aip.setQuelle(org.example.kalkulationsprogramm.domain.BestellQuelle.AUS_LAGER);
                    aip.setLagerAbgleichAm(java.time.LocalDateTime.now());
                } else {
                    // OFFEN: Bedarf steht fest, Preis wird erst durch die
                    // Eingangsrechnung (über die interne Bestellnummer) oder
                    // beim späteren Lager-Haken gesetzt.
                    aip.setPreisProStueck(null);
                    aip.setQuelle(org.example.kalkulationsprogramm.domain.BestellQuelle.OFFEN);
                    aip.setLagerAbgleichAm(null);
                }
                aip.setKommentar(auswahl.getKommentar());
                // Zuschnitt-Daten nur zulassen, wenn Wurzelkategorie 64/65
                if (darfSchnittbildVerwenden(artikel)) {
                    if (auswahl.getSchnittbildId() != null)
                        aip.setSchnittbild(resolveSchnittbild(auswahl.getSchnittbildId()));
                    if (auswahl.getAnschnittWinkelLinks() != null)
                        aip.setAnschnittWinkelLinks(auswahl.getAnschnittWinkelLinks());
                    if (auswahl.getAnschnittWinkelRechts() != null)
                        aip.setAnschnittWinkelRechts(auswahl.getAnschnittWinkelRechts());
                } else {
                    aip.setSchnittbild(null);
                    aip.setAnschnittWinkelLinks(null);
                    aip.setAnschnittWinkelRechts(null);
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
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new RuntimeException("Projekt konnte nicht gefunden werden."));

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
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new RuntimeException("Projekt konnte nicht gefunden werden."));

        ArtikelInProjekt artikelInProjekt = artikelInProjektRepository.findById(artikelInProjektId)
                .orElseThrow(() -> new RuntimeException("Artikel im Projekt konnte nicht gefunden werden."));

        if (!artikelInProjekt.getProjekt().getId().equals(projektId)) {
            throw new RuntimeException("Artikel gehört nicht zum Projekt.");
        }

        boolean wantsCutData = (dto.getSchnittbildId() != null) || (dto.getAnschnittWinkelLinks() != null)
                || (dto.getAnschnittWinkelRechts() != null);
        if (wantsCutData && !darfSchnittbildVerwenden(artikelInProjekt.getArtikel())) {
            throw new IllegalArgumentException("Schnittbilder sind nur fuer Kategorien 64/65 erlaubt.");
        }
        if (dto.getSchnittbildId() != null)
            artikelInProjekt.setSchnittbild(resolveSchnittbild(dto.getSchnittbildId()));
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

    /**
     * Loest eine Schnittbild-ID auf die Entity auf. Null-In, Null-Out.
     * Gueltigkeit (Wurzelkategorie 64/65 etc.) wird vom Controller beim
     * Laden der Picker-Liste sichergestellt; hier reicht ein einfacher Lookup.
     */
    private org.example.kalkulationsprogramm.domain.Schnittbilder resolveSchnittbild(Long id) {
        if (id == null) return null;
        return schnittbilderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schnittbild nicht gefunden: " + id));
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
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new RuntimeException("Projekt konnte nicht gefunden werden."));
        projekt.getMaterialkosten().removeIf(mk -> mk.getId().equals(materialId));
        Projekt gespeichert = projektRepository.save(projekt);
        return mappeMitKilogramm(gespeichert);
    }

    @Transactional
    public ProjektResponseDto fuegeProduktkategorienHinzu(Long projektId,
            List<ProjektProduktkategorieErfassenDto> kategorienDtos) {
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new RuntimeException("Projekt konnte nicht gefunden werden."));
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
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new RuntimeException("Projekt konnte nicht gefunden werden."));
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
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new RuntimeException("Projekt konnte nicht gefunden werden."));
        if (ZeitbuchungRepository.existsByProjektProduktkategorieId(ppkId)) {
            throw new IllegalStateException(
                    "Die Produktkategorie kann nicht gelöscht werden, da bereits Zeitbuchungen dafür existieren.");
        }
        boolean removed = projekt.getProjektProduktkategorien().removeIf(ppk -> ppk.getId().equals(ppkId));
        if (!removed) {
            throw new RuntimeException("Produktkategorie im Projekt nicht gefunden.");
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
        Projekt projekt = this.projektRepository.findById(projektID)
                .orElseThrow(() -> new RuntimeException("Das Projekt konnte nicht gefunden werden."));
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
            Boolean bezahlt,
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
            if (bezahlt != null) {
                predicates.add(criteriaBuilder.equal(root.get("bezahlt"), bezahlt));
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

        Projekt projekt = this.projektRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Projekt konnte nicht gefunden werden!"));
        return mappeMitKilogramm(projekt);
    }

    public Projekt findeProjektEntity(Long id) {
        return this.projektRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Projekt konnte nicht gefunden werden!"));
    }

    @Transactional
    public ProjektResponseDto updateProjektKurzbeschreibung(Long projektId, String kurzbeschreibung) {
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new RuntimeException("Projekt konnte nicht gefunden werden."));
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

    private void copyAnfrageEmailsToProjekt(Anfrage anfrage, Projekt projekt) {
        if (anfrage == null || projekt == null) {
            return;
        }
        try {
            // Alle E-Mails vom Anfrage abrufen und dem Projekt zuordnen
            List<Email> anfrageEmails = emailRepository.findByAnfrageOrderBySentAtDesc(anfrage);
            for (Email email : anfrageEmails) {
                // E-Mail vom Anfrage zum Projekt übertragen
                email.assignToProjekt(projekt);
                emailRepository.save(email);
            }
            if (!anfrageEmails.isEmpty()) {
                System.out.println("Übertragen: " + anfrageEmails.size() + " E-Mails von Anfrage "
                        + anfrage.getId() + " zu Projekt " + projekt.getId());
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Übertragen der E-Mails von Anfrage zu Projekt: " + e.getMessage());
        }
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

    @Transactional
    public ProjektResponseDto fuegeZeitenHinzu(Long projektId,
            List<org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitErfassenDto> zeitenDtos) {
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new NotFoundException("Projekt mit ID " + projektId + " nicht gefunden."));

        java.util.Map<Long, ProjektProduktkategorie> kategorieMap = projekt.getProjektProduktkategorien().stream()
                .collect(Collectors.toMap(ppk -> ppk.getProduktkategorie().getId(), ppk -> ppk));

        List<Zeitbuchung> neueZeiten = new ArrayList<>();
        for (org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitErfassenDto zeitDto : zeitenDtos) {
            Arbeitsgang arbeitsgang = arbeitsgangRepository.findById(zeitDto.getArbeitsgangID())
                    .orElseThrow(() -> new NotFoundException(
                            "Arbeitsgang mit ID " + zeitDto.getArbeitsgangID() + " nicht gefunden."));

            ProjektProduktkategorie ppk = kategorieMap.get(zeitDto.getProduktkategorieID());
            if (ppk == null) {
                throw new IllegalArgumentException("Produktkategorie mit ID " + zeitDto.getProduktkategorieID()
                        + " ist dem Projekt nicht zugeordnet.");
            }

            Zeitbuchung Zeitbuchung = new Zeitbuchung();
            Zeitbuchung.setProjekt(projekt);
            Zeitbuchung.setArbeitsgang(arbeitsgang);
            Zeitbuchung.setProjektProduktkategorie(ppk);
            Zeitbuchung.setAnzahlInStunden(zeitDto.getAnzahlInStunden());

            ArbeitsgangStundensatz satz = stundensatzRepository
                    .findTopByArbeitsgangIdAndJahrOrderByIdDesc(arbeitsgang.getId(), projekt.getAnlegedatum().getYear())
                    .or(() -> stundensatzRepository.findTopByArbeitsgangIdOrderByJahrDesc(arbeitsgang.getId()))
                    .orElseThrow(() -> new RuntimeException(
                            "Kein Stundensatz für Arbeitsgang " + arbeitsgang.getBeschreibung() + " gefunden."));
            Zeitbuchung.setArbeitsgangStundensatz(satz);

            neueZeiten.add(Zeitbuchung);
        }

        projekt.getZeitbuchungen().addAll(neueZeiten);
        Projekt gespeichert = projektRepository.save(projekt);
        return mappeMitKilogramm(gespeichert);
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

}
