package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentCounter;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AbrechnungsverlaufDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentErstellenDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentUpdateDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentCounterRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AusgangsGeschaeftsDokumentService {

    private final Path dokumentenSpeicherplatz;

    private final AusgangsGeschaeftsDokumentRepository dokumentRepository;
    private final AusgangsGeschaeftsDokumentCounterRepository counterRepository;
    private final ProjektRepository projektRepository;
    private final AnfrageRepository anfrageRepository;
    private final KundeRepository kundeRepository;
    private final FrontendUserProfileRepository frontendUserProfileRepository;
    private final LeistungRepository leistungRepository;
    private final ProduktkategorieRepository produktkategorieRepository;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;
    private final LeistungWpsAutoAssignService leistungWpsAutoAssignService;

    public AusgangsGeschaeftsDokumentService(
            @Value("${file.upload-dir}") String uploadDir,
            AusgangsGeschaeftsDokumentRepository dokumentRepository,
            AusgangsGeschaeftsDokumentCounterRepository counterRepository,
            ProjektRepository projektRepository,
            AnfrageRepository anfrageRepository,
            KundeRepository kundeRepository,
            FrontendUserProfileRepository frontendUserProfileRepository,
            LeistungRepository leistungRepository,
            ProduktkategorieRepository produktkategorieRepository,
            ProjektDokumentRepository projektDokumentRepository,
            ZeitbuchungRepository zeitbuchungRepository,
            LeistungWpsAutoAssignService leistungWpsAutoAssignService) {
        this.dokumentenSpeicherplatz = Path.of(uploadDir).toAbsolutePath().normalize();
        this.dokumentRepository = dokumentRepository;
        this.counterRepository = counterRepository;
        this.projektRepository = projektRepository;
        this.anfrageRepository = anfrageRepository;
        this.kundeRepository = kundeRepository;
        this.frontendUserProfileRepository = frontendUserProfileRepository;
        this.leistungRepository = leistungRepository;
        this.produktkategorieRepository = produktkategorieRepository;
        this.projektDokumentRepository = projektDokumentRepository;
        this.zeitbuchungRepository = zeitbuchungRepository;
        this.leistungWpsAutoAssignService = leistungWpsAutoAssignService;
    }

    /** Rechnungstypen, die im Abrechnungsverlauf berücksichtigt werden */
    private static final Set<AusgangsGeschaeftsDokumentTyp> RECHNUNGSTYPEN = EnumSet.of(
            AusgangsGeschaeftsDokumentTyp.RECHNUNG,
            AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG
    );

    /** Dokumenttypen, die für die Produktkategorie-Zuordnung relevant sind */
    private static final Set<AusgangsGeschaeftsDokumentTyp> KATEGORIE_RELEVANTE_TYPEN = EnumSet.of(
            AusgangsGeschaeftsDokumentTyp.ANGEBOT,
            AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG
    );

    /**
     * Erstellt ein neues Dokument mit automatisch generierter Nummer.
     */
    @Transactional
    public AusgangsGeschaeftsDokument erstellen(AusgangsGeschaeftsDokumentErstellenDto dto) {
        // Nur ein Basisdokument (ohne Vorgänger) pro Projekt/Anfrage erlaubt
        if (dto.getVorgaengerId() == null) {
            if (dto.getProjektId() != null && dokumentRepository.existsByProjektIdAndVorgaengerIsNull(dto.getProjektId())) {
                throw new IllegalStateException("Es existiert bereits ein Basisdokument für dieses Projekt.");
            }
            if (dto.getAnfrageId() != null && dokumentRepository.existsByAnfrageIdAndVorgaengerIsNull(dto.getAnfrageId())) {
                throw new IllegalStateException("Es existiert bereits ein Basisdokument für dieses Anfrage.");
            }
        }

        AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();

        dokument.setTyp(dto.getTyp());
        dokument.setDatum(dto.getDatum() != null ? dto.getDatum() : LocalDate.now());
        dokument.setBetreff(dto.getBetreff());
        dokument.setBetragNetto(dto.getBetragNetto());
        dokument.setMwstSatz(dto.getMwstSatz() != null ? dto.getMwstSatz() : new BigDecimal("0.19"));
        dokument.setZahlungszielTage(dto.getZahlungszielTage());
        dokument.setHtmlInhalt(dto.getHtmlInhalt());
        dokument.setPositionenJson(dto.getPositionenJson());
        dokument.setRechnungsadresseOverride(dto.getRechnungsadresseOverride());

        // Bruttobetrag berechnen
        if (dto.getBetragNetto() != null && dokument.getMwstSatz() != null) {
            BigDecimal mwst = dto.getBetragNetto().multiply(dokument.getMwstSatz());
            dokument.setBetragBrutto(dto.getBetragNetto().add(mwst).setScale(2, RoundingMode.HALF_UP));
        }

        // Verknüpfungen setzen
        if (dto.getProjektId() != null) {
            Projekt projekt = projektRepository.findById(dto.getProjektId()).orElse(null);
            dokument.setProjekt(projekt);
            // Kunde aus Projekt übernehmen falls nicht explizit gesetzt
            if (dto.getKundeId() == null && projekt != null && projekt.getKundenId() != null) {
                dokument.setKunde(projekt.getKundenId());
            }
        }

        if (dto.getAnfrageId() != null) {
            Anfrage anfrage = anfrageRepository.findById(dto.getAnfrageId()).orElse(null);
            dokument.setAnfrage(anfrage);
            // Kunde aus Anfrage übernehmen falls nicht explizit gesetzt
            if (dto.getKundeId() == null && anfrage != null && anfrage.getKunde() != null) {
                dokument.setKunde(anfrage.getKunde());
            }
            // Projekt aus Anfrage übernehmen falls nicht explizit gesetzt
            if (dokument.getProjekt() == null && anfrage != null && anfrage.getProjekt() != null) {
                dokument.setProjekt(anfrage.getProjekt());
            }
        }

        if (dto.getKundeId() != null) {
            dokument.setKunde(kundeRepository.findById(dto.getKundeId()).orElse(null));
        }

        if (dto.getVorgaengerId() != null) {
            AusgangsGeschaeftsDokument vorgaenger = dokumentRepository.findById(dto.getVorgaengerId()).orElse(null);
            dokument.setVorgaenger(vorgaenger);

            // Bei Umwandlung: Inhalte vom Vorgänger übernehmen wenn nicht explizit gesetzt
            if (vorgaenger != null) {
                if (dto.getHtmlInhalt() == null && vorgaenger.getHtmlInhalt() != null) {
                    dokument.setHtmlInhalt(vorgaenger.getHtmlInhalt());
                }
                if (dto.getPositionenJson() == null && vorgaenger.getPositionenJson() != null) {
                    dokument.setPositionenJson(vorgaenger.getPositionenJson());
                }
                // Kunde vom Vorgänger übernehmen falls nicht gesetzt
                if (dokument.getKunde() == null && vorgaenger.getKunde() != null) {
                    dokument.setKunde(vorgaenger.getKunde());
                }
                // Anfrage vom Vorgänger übernehmen falls nicht gesetzt
                if (dokument.getAnfrage() == null && vorgaenger.getAnfrage() != null) {
                    dokument.setAnfrage(vorgaenger.getAnfrage());
                }
                // Projekt vom Vorgänger übernehmen falls nicht gesetzt
                if (dokument.getProjekt() == null && vorgaenger.getProjekt() != null) {
                    dokument.setProjekt(vorgaenger.getProjekt());
                }

                // Validierung: Rechnungsbetrag darf Restbetrag nicht übersteigen
                if (RECHNUNGSTYPEN.contains(dto.getTyp())) {
                    BigDecimal zuPruefenderBetrag = dto.getBetragNetto();
                    // Fallback: Betrag aus positionenJson berechnen (z.B. Teilrechnung)
                    if (zuPruefenderBetrag == null && dto.getPositionenJson() != null) {
                        zuPruefenderBetrag = berechneNettoAusPositionenJson(dto.getPositionenJson());
                    }
                    if (zuPruefenderBetrag != null) {
                        validateRechnungsbetrag(vorgaenger.getId(), zuPruefenderBetrag);
                    }
                }
            }

            // Bei Abschlagsrechnung: Nummer ermitteln
            if (dto.getTyp() == AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG && vorgaenger != null) {
                int anzahl = dokumentRepository.countByVorgaengerIdAndTyp(
                        vorgaenger.getId(), AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG);
                dokument.setAbschlagsNummer(anzahl + 1);
            }
        }

        // Zahlungsziel aus Kunde übernehmen falls nicht explizit gesetzt
        if (dokument.getZahlungszielTage() == null && dokument.getKunde() != null
                && dokument.getKunde().getZahlungsziel() != null) {
            dokument.setZahlungszielTage(dokument.getKunde().getZahlungsziel());
        }

        // Ersteller setzen
        if (dto.getErstelltVonId() != null) {
            frontendUserProfileRepository.findById(dto.getErstelltVonId())
                    .ifPresent(dokument::setErstelltVon);
        }

        // Dokumentnummer generieren
        dokument.setDokumentNummer(generiereNummer(dokument.getTyp()));

        AusgangsGeschaeftsDokument saved = dokumentRepository.save(dokument);

        // Projekt-Preis aktualisieren
        if (saved.getProjekt() != null) {
            aktualisiereProjektPreisAusDokumenten(saved.getProjekt().getId());
        }

        // Anfrage-Preis aktualisieren
        if (saved.getAnfrage() != null) {
            aktualisiereAnfragePreisAusDokumenten(saved.getAnfrage().getId());
        }

        // ProjektProduktkategorien automatisch zuweisen bei Anfrage/AB
        if (saved.getProjekt() != null && KATEGORIE_RELEVANTE_TYPEN.contains(saved.getTyp())) {
            aktualisiereProjektProduktkategorienAusDokumenten(saved.getProjekt().getId());
        } else if (saved.getAnfrage() != null && saved.getAnfrage().getProjekt() != null
                && KATEGORIE_RELEVANTE_TYPEN.contains(saved.getTyp())) {
            aktualisiereProjektProduktkategorienAusDokumenten(saved.getAnfrage().getProjekt().getId());
        }

        saved.setAutoZugewieseneWps(autoAssignEn1090Wps(saved));
        return saved;
    }

    /**
     * Stellt sicher, dass ein ANFRAGE-Dokument für das gegebene Anfrage existiert.
     * Wird automatisch aufgerufen, wenn der AnfrageEditor geöffnet wird.
     * Pro Anfrage darf nur ein ANFRAGE-Dokument existieren.
     *
     * @return die dokumentNummer des (ggf. neu erstellten) ANFRAGE-Dokuments, oder null
     */
    @Transactional
    public String ensureAnfrageDokument(Long anfrageId) {
        if (anfrageId == null) return null;

        // Prüfen ob bereits ein ANFRAGE-Dokument existiert
        Optional<AusgangsGeschaeftsDokument> existing = dokumentRepository
                .findFirstByAnfrageIdAndTyp(anfrageId, AusgangsGeschaeftsDokumentTyp.ANGEBOT);
        if (existing.isPresent()) {
            return existing.get().getDokumentNummer();
        }

        // Anfrage laden
        Anfrage anfrage = anfrageRepository.findById(anfrageId).orElse(null);
        if (anfrage == null) return null;

        // Neues ANFRAGE-Dokument automatisch erstellen
        AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
        dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
        dto.setAnfrageId(anfrageId);
        dto.setBetreff(anfrage.getBauvorhaben());
        if (anfrage.getKunde() != null) {
            dto.setKundeId(anfrage.getKunde().getId());
        }

        AusgangsGeschaeftsDokument created = erstellen(dto);
        return created.getDokumentNummer();
    }

    /**
     * Gibt die Anfragesnummer (= dokumentNummer des ANFRAGE-Dokuments) zurück, falls vorhanden.
     */
    public String resolveAnfragesnummer(Long anfrageId) {
        if (anfrageId == null) return null;
        return dokumentRepository
                .findFirstByAnfrageIdAndTyp(anfrageId, AusgangsGeschaeftsDokumentTyp.ANGEBOT)
                .map(AusgangsGeschaeftsDokument::getDokumentNummer)
                .orElse(null);
    }

    /**
     * Stub: Angebotsnummer aus AusgangsGeschaeftsDokumenten ableiten.
     * Aktuell kein Angebot-FK in AusgangsGeschaeftsDokument vorhanden.
     */
    public String resolveAngebotsnummer(Long angebotId) {
        return null;
    }

    /**
     * Stub: Angebotspreis aus Dokumenten aktualisieren.
     * Aktuell kein Angebot-FK in AusgangsGeschaeftsDokument vorhanden.
     */
    public void aktualisiereAngebotPreisAusDokumenten(Long angebotId) {
        // No-op: AusgangsGeschaeftsDokument hat noch keine Angebot-Relation
    }

    /**
     * Aktualisiert ein Dokument (nur wenn nicht gebucht).
     */
    @Transactional
    public AusgangsGeschaeftsDokument aktualisieren(Long id, AusgangsGeschaeftsDokumentUpdateDto dto) {
        AusgangsGeschaeftsDokument dokument = dokumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dokument nicht gefunden: " + id));

        if (!dokument.istBearbeitbar()) {
            throw new RuntimeException("Dokument ist gesperrt und kann nicht mehr bearbeitet werden.");
        }

        if (dto.getBetreff() != null) dokument.setBetreff(dto.getBetreff());
        if (dto.getDatum() != null) dokument.setDatum(dto.getDatum());
        if (dto.getBetragNetto() != null) dokument.setBetragNetto(dto.getBetragNetto());
        if (dto.getMwstSatz() != null) dokument.setMwstSatz(dto.getMwstSatz());
        if (dto.getZahlungszielTage() != null) dokument.setZahlungszielTage(dto.getZahlungszielTage());
        if (dto.getHtmlInhalt() != null) dokument.setHtmlInhalt(dto.getHtmlInhalt());
        if (dto.getPositionenJson() != null) dokument.setPositionenJson(dto.getPositionenJson());
        // rechnungsadresseOverride darf auch auf null gesetzt werden (Reset auf Kundenadresse)
        dokument.setRechnungsadresseOverride(dto.getRechnungsadresseOverride());

        // Bruttobetrag neu berechnen
        if (dokument.getBetragNetto() != null && dokument.getMwstSatz() != null) {
            BigDecimal mwst = dokument.getBetragNetto().multiply(dokument.getMwstSatz());
            dokument.setBetragBrutto(dokument.getBetragNetto().add(mwst).setScale(2, RoundingMode.HALF_UP));
        }

        AusgangsGeschaeftsDokument saved = dokumentRepository.save(dokument);

        // Projekt-Preis aktualisieren
        if (saved.getProjekt() != null) {
            aktualisiereProjektPreisAusDokumenten(saved.getProjekt().getId());
        }

        // Anfrage-Preis aktualisieren
        if (saved.getAnfrage() != null) {
            aktualisiereAnfragePreisAusDokumenten(saved.getAnfrage().getId());
        }

        // ProjektProduktkategorien automatisch aktualisieren bei Anfrage/AB-Änderung
        if (saved.getProjekt() != null && KATEGORIE_RELEVANTE_TYPEN.contains(saved.getTyp())) {
            aktualisiereProjektProduktkategorienAusDokumenten(saved.getProjekt().getId());
        } else if (saved.getAnfrage() != null && saved.getAnfrage().getProjekt() != null
                && KATEGORIE_RELEVANTE_TYPEN.contains(saved.getTyp())) {
            aktualisiereProjektProduktkategorienAusDokumenten(saved.getAnfrage().getProjekt().getId());
        }

        saved.setAutoZugewieseneWps(autoAssignEn1090Wps(saved));
        return saved;
    }

    /**
     * Hängt alle Schweißanweisungen, die an den im Dokument enthaltenen
     * Leistungen hinterlegt sind, automatisch an das Projekt des Dokuments.
     * Reichweite: nur Dokumente mit Projekt-Bezug; für reine Anfrage-Dokumente
     * (ohne konkretes Projekt) ist EN-1090-Zuordnung nicht sinnvoll.
     */
    private int autoAssignEn1090Wps(AusgangsGeschaeftsDokument dokument) {
        if (dokument == null || dokument.getProjekt() == null
                || dokument.getPositionenJson() == null) {
            return 0;
        }
        Set<Long> leistungIds = extractLeistungIdsFromPositionenJson(dokument.getPositionenJson());
        if (leistungIds.isEmpty()) return 0;
        return leistungWpsAutoAssignService.autoAssignWpsFromLeistungen(
                dokument.getProjekt().getId(), leistungIds);
    }

    /** Dokumenttypen die beim Buchen/Versand gesperrt werden (nur Rechnungen + Gutschrift + Storno) */
    private static final Set<AusgangsGeschaeftsDokumentTyp> SPERRBARE_TYPEN = EnumSet.of(
            AusgangsGeschaeftsDokumentTyp.RECHNUNG,
            AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT,
            AusgangsGeschaeftsDokumentTyp.STORNO
    );

    /** Dokumenttypen die beim Export/Versand NICHT gebucht werden (nachträglich anpassbar) */
    private static final Set<AusgangsGeschaeftsDokumentTyp> NICHT_BUCHBARE_TYPEN = EnumSet.of(
            AusgangsGeschaeftsDokumentTyp.ANGEBOT,
            AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG
    );

    /**
     * Bucht ein Dokument (nach Export).
     * Nur Rechnungstypen werden dadurch gesperrt (GoBD).
     * Anfragen/ABs werden NICHT gebucht, da sie nachträglich angepasst werden können.
     */
    @Transactional
    public AusgangsGeschaeftsDokument buchen(Long id) {
        AusgangsGeschaeftsDokument dokument = dokumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dokument nicht gefunden: " + id));

        if (dokument.isGebucht()) {
            return dokument;
        }

        if (dokument.isStorniert()) {
            throw new RuntimeException("Storniertes Dokument kann nicht gebucht werden.");
        }

        // Anfragen und ABs werden nicht gebucht – sie sollen nachträglich anpassbar bleiben
        if (NICHT_BUCHBARE_TYPEN.contains(dokument.getTyp())) {
            log.info("Dokument {} (Typ: {}) wird nicht gebucht – Anfragen/ABs bleiben immer bearbeitbar",
                    dokument.getDokumentNummer(), dokument.getTyp());
            return dokument;
        }

        dokument.setGebucht(true);
        dokument.setGebuchtAm(LocalDate.now());

        AusgangsGeschaeftsDokument saved = dokumentRepository.save(dokument);
        erstelleOffenenPostenEintrag(saved);
        return saved;
    }

    /**
     * Bucht ein Dokument nach E-Mail-Versand (GoBD-konform).
     * Setzt Versanddatum. Nur Rechnungstypen werden dadurch gesperrt.
     * Anfragen/ABs bekommen das Versanddatum, werden aber NICHT gebucht.
     */
    @Transactional
    public AusgangsGeschaeftsDokument buchenNachEmailVersand(Long id) {
        AusgangsGeschaeftsDokument dokument = dokumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dokument nicht gefunden: " + id));

        if (dokument.isStorniert()) {
            throw new RuntimeException("Storniertes Dokument kann nicht versendet werden.");
        }

        dokument.setVersandDatum(LocalDate.now());

        // Anfragen und ABs werden nicht gebucht – nur Versanddatum setzen
        boolean istNichtBuchbar = NICHT_BUCHBARE_TYPEN.contains(dokument.getTyp());
        boolean warBereitsGebucht = dokument.isGebucht();
        if (!warBereitsGebucht && !istNichtBuchbar) {
            dokument.setGebucht(true);
            dokument.setGebuchtAm(LocalDate.now());
        }

        AusgangsGeschaeftsDokument saved = dokumentRepository.save(dokument);
        if (!warBereitsGebucht && !istNichtBuchbar) {
            erstelleOffenenPostenEintrag(saved);
        }

        // Versanddatum auch auf dem Offene-Posten-Eintrag setzen
        aktualisiereOffenenPostenVersandDatum(saved);

        // Fälligkeitsdatum nachträglich setzen falls es fehlt (z.B. bei bereits gebuchten Dokumenten)
        aktualisiereOffenenPostenFaelligkeitsdatum(saved);

        return saved;
    }

    /**
     * Setzt das Fälligkeitsdatum auf dem zugehörigen ProjektGeschaeftsdokument,
     * falls es noch fehlt (Reparatur für bestehende Einträge).
     */
    private void aktualisiereOffenenPostenFaelligkeitsdatum(AusgangsGeschaeftsDokument dokument) {
        if (dokument.getDokumentNummer() == null || dokument.getDatum() == null) return;
        Integer zahlungsziel = dokument.getZahlungszielTage();
        if (zahlungsziel == null && dokument.getKunde() != null && dokument.getKunde().getZahlungsziel() != null) {
            zahlungsziel = dokument.getKunde().getZahlungsziel();
        }
        if (zahlungsziel == null) return;

        final Integer effectiveZahlungsziel = zahlungsziel;
        projektDokumentRepository.findAllGeschaeftsdokumente().stream()
                .filter(g -> dokument.getDokumentNummer().equals(g.getDokumentid()))
                .filter(g -> g.getFaelligkeitsdatum() == null)
                .findFirst()
                .ifPresent(g -> {
                    g.setFaelligkeitsdatum(dokument.getDatum().plusDays(effectiveZahlungsziel));
                    projektDokumentRepository.save(g);
                    log.info("Fälligkeitsdatum {} auf Offenen Posten {} nachgetragen",
                            g.getFaelligkeitsdatum(), g.getDokumentid());
                });
    }

    /**
     * Setzt das emailVersandDatum auf dem zugehörigen ProjektGeschaeftsdokument (Offener Posten).
     */
    private void aktualisiereOffenenPostenVersandDatum(AusgangsGeschaeftsDokument dokument) {
        if (dokument.getDokumentNummer() == null || dokument.getVersandDatum() == null) return;
        projektDokumentRepository.findAllGeschaeftsdokumente().stream()
                .filter(g -> dokument.getDokumentNummer().equals(g.getDokumentid()))
                .findFirst()
                .ifPresent(g -> {
                    g.setEmailVersandDatum(dokument.getVersandDatum());
                    projektDokumentRepository.save(g);
                    log.info("Versanddatum {} auf Offenen Posten {} übertragen",
                            dokument.getVersandDatum(), g.getDokumentid());
                });
    }

    /**
     * Storniert ein Dokument. Erstellt ein Storno-Gegendokument.
     * Die Stornorechnung übernimmt Positionen und Inhalt vom Original.
     */
    @Transactional
    public AusgangsGeschaeftsDokument stornieren(Long id) {
        AusgangsGeschaeftsDokument original = dokumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dokument nicht gefunden: " + id));

        if (original.isStorniert()) {
            throw new RuntimeException("Dokument ist bereits storniert.");
        }

        // Nur Rechnungstypen dürfen storniert werden
        if (!RECHNUNGSTYPEN.contains(original.getTyp()) && original.getTyp() != AusgangsGeschaeftsDokumentTyp.STORNO) {
            throw new RuntimeException("Nur Rechnungen können storniert werden.");
        }

        // Original als storniert markieren
        original.setStorniert(true);
        original.setStorniertAm(LocalDate.now());
        dokumentRepository.save(original);

        // Storno-Dokument erstellen
        AusgangsGeschaeftsDokument storno = new AusgangsGeschaeftsDokument();
        storno.setTyp(AusgangsGeschaeftsDokumentTyp.STORNO);
        storno.setDatum(LocalDate.now());
        storno.setDokumentNummer(generiereNummer(AusgangsGeschaeftsDokumentTyp.STORNO));

        // Betreff: "Stornorechnung XXXX (zu Rechnung YYYY)"
        String originalTypLabel = switch (original.getTyp()) {
            case RECHNUNG -> "Rechnung";
            case TEILRECHNUNG -> "Teilrechnung";
            case ABSCHLAGSRECHNUNG -> "Abschlagsrechnung";
            case SCHLUSSRECHNUNG -> "Schlussrechnung";
            default -> "Dokument";
        };
        storno.setBetreff("Stornorechnung " + storno.getDokumentNummer()
                + " (zu " + originalTypLabel + " " + original.getDokumentNummer() + ")");

        storno.setVorgaenger(original);
        storno.setProjekt(original.getProjekt());
        storno.setAnfrage(original.getAnfrage());
        storno.setKunde(original.getKunde());
        storno.setRechnungsadresseOverride(original.getRechnungsadresseOverride());

        // Beträge vom Original negieren (Stornorechnung = Gutschrift)
        storno.setBetragNetto(original.getBetragNetto() != null ? original.getBetragNetto().negate() : null);
        storno.setBetragBrutto(original.getBetragBrutto() != null ? original.getBetragBrutto().negate() : null);
        storno.setMwstSatz(original.getMwstSatz());

        // Inhalt und Positionen vom Original übernehmen für PDF-Generierung
        storno.setHtmlInhalt(original.getHtmlInhalt());
        storno.setPositionenJson(original.getPositionenJson());

        storno.setGebucht(true);
        storno.setGebuchtAm(LocalDate.now());

        AusgangsGeschaeftsDokument savedStorno = dokumentRepository.save(storno);

        // Offenen-Posten-Eintrag des Originals als bezahlt markieren (storniert)
        markiereOffenenPostenAlsBezahlt(original);

        // Kaskadierende Stornierung: Wenn eine Abschlagsrechnung storniert wird,
        // müssen abhängige Schlussrechnungen (gleicher Vorgänger) ebenfalls storniert werden,
        // da deren Betrag auf Basis der Abschlagsrechnungen berechnet wurde.
        if (original.getTyp() == AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG
                && original.getVorgaenger() != null) {
            List<AusgangsGeschaeftsDokument> geschwister = dokumentRepository
                    .findByVorgaengerIdOrderByErstelltAmAsc(original.getVorgaenger().getId());
            for (AusgangsGeschaeftsDokument geschwisterDok : geschwister) {
                if (geschwisterDok.getTyp() == AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG
                        && !geschwisterDok.isStorniert()) {
                    log.info("Kaskadierende Stornierung: Schlussrechnung {} wird mitstorniert (Abschlagsrechnung {} storniert)",
                            geschwisterDok.getDokumentNummer(), original.getDokumentNummer());
                    stornieren(geschwisterDok.getId());
                }
            }
        }

        // Projekt-Preis aktualisieren
        if (original.getProjekt() != null) {
            aktualisiereProjektPreisAusDokumenten(original.getProjekt().getId());
        }

        // Anfrage-Preis aktualisieren
        if (original.getAnfrage() != null) {
            aktualisiereAnfragePreisAusDokumenten(original.getAnfrage().getId());
        }

        return savedStorno;
    }

    // ==================== OFFENE POSTEN INTEGRATION ====================

    /**
     * Erstellt einen ProjektGeschaeftsdokument-Eintrag für ein gebuchtes AusgangsGeschaeftsDokument,
     * damit es automatisch in den Offenen Posten (Ausgangsrechnungen) erscheint.
     * Nur für Rechnungstypen relevant.
     */
    private void erstelleOffenenPostenEintrag(AusgangsGeschaeftsDokument dokument) {
        if (!RECHNUNGSTYPEN.contains(dokument.getTyp())) {
            return;
        }
        if (dokument.getProjekt() == null) {
            return;
        }

        // Prüfen ob bereits ein Eintrag mit dieser Dokumentnummer existiert
        if (projektDokumentRepository.existsByDokumentid(dokument.getDokumentNummer())) {
            log.info("Offener-Posten-Eintrag für {} existiert bereits, überspringe.",
                    dokument.getDokumentNummer());
            return;
        }

        ProjektGeschaeftsdokument offenerPosten = new ProjektGeschaeftsdokument();
        offenerPosten.setProjekt(dokument.getProjekt());
        offenerPosten.setDokumentid(dokument.getDokumentNummer());
        offenerPosten.setGeschaeftsdokumentart(mapTypZuGeschaeftsdokumentart(dokument.getTyp()));
        offenerPosten.setRechnungsdatum(dokument.getDatum());
        offenerPosten.setBruttoBetrag(dokument.getBetragBrutto());
        offenerPosten.setBezahlt(false);

        // Fälligkeitsdatum berechnen aus Zahlungsziel
        Integer zahlungsziel = dokument.getZahlungszielTage();
        if (zahlungsziel == null && dokument.getKunde() != null && dokument.getKunde().getZahlungsziel() != null) {
            zahlungsziel = dokument.getKunde().getZahlungsziel();
        }
        if (zahlungsziel != null && dokument.getDatum() != null) {
            offenerPosten.setFaelligkeitsdatum(
                    dokument.getDatum().plusDays(zahlungsziel));
        }

        // Synthetischer Dateiname – wird per mappeDokumentZuDto zu einer URL auf den DocumentEditor
        String syntheticFilename = "ausgangs-dok-" + dokument.getId() + ".pdf";
        offenerPosten.setOriginalDateiname(dokument.getDokumentNummer() + ".pdf");
        offenerPosten.setGespeicherterDateiname(syntheticFilename);
        offenerPosten.setDateityp("application/pdf");
        offenerPosten.setUploadDatum(LocalDate.now());
        offenerPosten.setDokumentGruppe(DokumentGruppe.GESCHAEFTSDOKUMENTE);

        // Versanddatum übernehmen falls bereits vorhanden
        if (dokument.getVersandDatum() != null) {
            offenerPosten.setEmailVersandDatum(dokument.getVersandDatum());
        }

        projektDokumentRepository.save(offenerPosten);
        log.info("Offener-Posten-Eintrag erstellt für Dokument {} (Typ: {})",
                dokument.getDokumentNummer(), dokument.getTyp());
    }

    /**
     * Speichert die PDF-Bytes eines gebuchten Dokuments auf der Festplatte
     * und aktualisiert den zugehörigen Offene-Posten-Eintrag, damit dieser
     * direkt auf die PDF-Datei verweist (statt auf den Document-Editor).
     */
    @Transactional
    public void speicherePdfFuerDokument(Long dokumentId, byte[] pdfBytes) {
        AusgangsGeschaeftsDokument dokument = dokumentRepository.findById(dokumentId)
                .orElseThrow(() -> new RuntimeException("Dokument nicht gefunden: " + dokumentId));

        if (dokument.getDokumentNummer() == null) {
            throw new RuntimeException("Dokument hat keine Dokumentnummer");
        }

        // PDF auf Festplatte speichern
        String gespeicherterDateiname = UUID.randomUUID() + ".pdf";
        Path zielPfad = dokumentenSpeicherplatz.resolve(gespeicherterDateiname).normalize();
        if (!zielPfad.startsWith(dokumentenSpeicherplatz)) {
            throw new RuntimeException("Ungültiger Dateipfad");
        }
        try {
            Files.createDirectories(dokumentenSpeicherplatz);
            Files.write(zielPfad, pdfBytes);
        } catch (IOException e) {
            throw new RuntimeException("PDF konnte nicht gespeichert werden", e);
        }

        // Offene-Posten-Eintrag aktualisieren: gespeicherterDateiname auf die echte PDF setzen
        projektDokumentRepository.findAllGeschaeftsdokumente().stream()
                .filter(g -> dokument.getDokumentNummer().equals(g.getDokumentid()))
                .findFirst()
                .ifPresent(g -> {
                    g.setGespeicherterDateiname(gespeicherterDateiname);
                    g.setOriginalDateiname(dokument.getDokumentNummer() + ".pdf");
                    g.setDateityp("application/pdf");
                    projektDokumentRepository.save(g);
                    log.info("PDF für Offenen Posten {} gespeichert: {}", g.getDokumentid(), gespeicherterDateiname);
                });
    }

    /**
     * Markiert den zugehörigen Offene-Posten-Eintrag als bezahlt (z.B. bei Stornierung).
     */
    private void markiereOffenenPostenAlsBezahlt(AusgangsGeschaeftsDokument dokument) {
        projektDokumentRepository.findAllGeschaeftsdokumente().stream()
                .filter(g -> dokument.getDokumentNummer().equals(g.getDokumentid()))
                .findFirst()
                .ifPresent(g -> {
                    g.setBezahlt(true);
                    projektDokumentRepository.save(g);
                    log.info("Offener-Posten-Eintrag {} als bezahlt markiert (Stornierung)",
                            g.getDokumentid());
                });
    }

    /**
     * Mappt den AusgangsGeschaeftsDokumentTyp auf den geschaeftsdokumentart-String
     * für ProjektGeschaeftsdokument (Offene Posten).
     */
    private String mapTypZuGeschaeftsdokumentart(AusgangsGeschaeftsDokumentTyp typ) {
        return switch (typ) {
            case RECHNUNG -> "Rechnung";
            case TEILRECHNUNG -> "Teilrechnung";
            case ABSCHLAGSRECHNUNG -> "Abschlagsrechnung";
            case SCHLUSSRECHNUNG -> "Schlussrechnung";
            default -> typ.name();
        };
    }

    /**
     * Findet alle Dokumente für ein Projekt.
     */
    public List<AusgangsGeschaeftsDokumentResponseDto> findByProjekt(Long projektId) {
        return dokumentRepository.findByProjektIdOrderByDatumDesc(projektId).stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Findet alle Dokumente für ein Anfrage.
     */
    public List<AusgangsGeschaeftsDokumentResponseDto> findByAnfrage(Long anfrageId) {
        return dokumentRepository.findByAnfrageIdOrderByDatumDesc(anfrageId).stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Migriert alle AusgangsGeschaeftsDokumente von einem Anfrage zum Projekt.
     * Wird aufgerufen, wenn ein Anfrage in ein Projekt überführt wird.
     * Setzt das Projekt und entfernt die Anfrage-Referenz, damit das Anfrage gelöscht werden kann.
     */
    @Transactional
    public void migrateFromAnfrageToProjekt(Long anfrageId, Projekt projekt) {
        List<AusgangsGeschaeftsDokument> dokumente = dokumentRepository.findByAnfrageIdOrderByDatumDesc(anfrageId);
        for (AusgangsGeschaeftsDokument dok : dokumente) {
            dok.setProjekt(projekt);
            dok.setAnfrage(null);
            dokumentRepository.save(dok);
        }
        if (!dokumente.isEmpty()) {
            log.info("Migrierte {} Ausgangsgeschäftsdokumente von Anfrage {} zu Projekt {}",
                    dokumente.size(), anfrageId, projekt.getId());
            // ProjektProduktkategorien aus den migrierten Dokumenten ableiten
            aktualisiereProjektProduktkategorienAusDokumenten(projekt.getId());
        }
    }

    /**
     * Findet ein Dokument nach ID.
     */
    public AusgangsGeschaeftsDokumentResponseDto findById(Long id) {
        return dokumentRepository.findById(id)
                .map(this::toResponseDto)
                .orElse(null);
    }

    /**
     * Löscht ein Dokument (nur Entwürfe).
     *
     * GoBD-konforme Löschregeln (§147 AO, GoBD Rz. 58-59):
     * - Gebuchte Dokumente sind unveränderbar und dürfen nicht gelöscht werden.
     * - Versandte Dokumente gelten als "in den Geschäftsverkehr gebracht" und dürfen nicht gelöscht werden.
     * - Stornierte Dokumente müssen als Nachweis der Korrektur erhalten bleiben.
     * - STORNO-Dokumente sind selbst Korrekturbuchungen und dürfen nie gelöscht werden.
     * - Nur Entwürfe (nicht gebucht, nicht versandt, nicht storniert) dürfen mit Begründung gelöscht werden.
     */
    @Transactional
    public void loeschen(Long id, String begruendung) {
        AusgangsGeschaeftsDokument dokument = dokumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dokument nicht gefunden: " + id));

        // GoBD: Gebuchte Dokumente sind unveränderbar (Grundsatz der Unveränderbarkeit)
        if (dokument.isGebucht()) {
            throw new RuntimeException("Gebuchte Dokumente dürfen gemäß GoBD nicht gelöscht werden. Bitte erstellen Sie stattdessen eine Stornierung.");
        }

        // GoBD: Versandte Dokumente gelten als in den Geschäftsverkehr gebracht
        if (dokument.getVersandDatum() != null) {
            throw new RuntimeException("Bereits versandte Dokumente dürfen gemäß GoBD nicht gelöscht werden. Bitte erstellen Sie stattdessen eine Stornierung.");
        }

        // GoBD: Stornierte Dokumente müssen als Nachweis erhalten bleiben
        if (dokument.isStorniert()) {
            throw new RuntimeException("Stornierte Dokumente dürfen nicht gelöscht werden, da sie als Korrekturnachweis aufbewahrt werden müssen.");
        }

        // GoBD: Storno-Dokumente sind selbst Korrekturbuchungen und dürfen nicht gelöscht werden
        if (dokument.getTyp() == AusgangsGeschaeftsDokumentTyp.STORNO) {
            throw new RuntimeException("Stornorechnungen dürfen nicht gelöscht werden, da sie als Korrekturbuchung aufbewahrt werden müssen.");
        }

        if (begruendung == null || begruendung.isBlank()) {
            throw new RuntimeException("Eine Begründung für das Löschen ist erforderlich.");
        }

        log.info("Dokument gelöscht: {} (Typ: {}, Nr: {}) – Begründung: {}",
                dokument.getId(), dokument.getTyp(), dokument.getDokumentNummer(), begruendung);

        Long projektId = dokument.getProjekt() != null ? dokument.getProjekt().getId() : null;
        Long anfrageId = dokument.getAnfrage() != null ? dokument.getAnfrage().getId() : null;
        boolean kategorieRelevant = KATEGORIE_RELEVANTE_TYPEN.contains(dokument.getTyp());
        // Für den Fall dass das Dokument über ein Anfrage mit einem Projekt verknüpft ist
        Long anfrageProjektId = (dokument.getAnfrage() != null && dokument.getAnfrage().getProjekt() != null)
                ? dokument.getAnfrage().getProjekt().getId() : null;
        dokumentRepository.delete(dokument);

        // Projekt-Preis aktualisieren
        if (projektId != null) {
            aktualisiereProjektPreisAusDokumenten(projektId);
        }

        // Anfrage-Preis aktualisieren
        if (anfrageId != null) {
            aktualisiereAnfragePreisAusDokumenten(anfrageId);
        }

        // ProjektProduktkategorien dynamisch aktualisieren nach Löschung
        if (kategorieRelevant) {
            if (projektId != null) {
                aktualisiereProjektProduktkategorienAusDokumenten(projektId);
            } else if (anfrageProjektId != null) {
                aktualisiereProjektProduktkategorienAusDokumenten(anfrageProjektId);
            }
        }
    }

    // --- Abrechnungsverlauf ---

    /**
     * Berechnet den Abrechnungsverlauf für ein Basisdokument.
     * Listet alle Rechnungen auf, die aus diesem Dokument erstellt wurden,
     * und berechnet den verbleibenden Restbetrag.
     */
    public AbrechnungsverlaufDto getAbrechnungsverlauf(Long basisdokumentId) {
        AusgangsGeschaeftsDokument basis = dokumentRepository.findById(basisdokumentId)
                .orElseThrow(() -> new RuntimeException("Basisdokument nicht gefunden: " + basisdokumentId));

        AbrechnungsverlaufDto verlauf = new AbrechnungsverlaufDto();
        verlauf.setBasisdokumentId(basis.getId());
        verlauf.setBasisdokumentNummer(basis.getDokumentNummer());
        verlauf.setBasisdokumentTyp(basis.getTyp());
        verlauf.setBasisdokumentDatum(basis.getDatum());
        verlauf.setBasisdokumentBetragNetto(
                basis.getBetragNetto() != null ? basis.getBetragNetto() : BigDecimal.ZERO);

        // Alle Nachfolger-Dokumente laden (sortiert nach Erstellungszeitpunkt)
        List<AusgangsGeschaeftsDokument> nachfolger = dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(basisdokumentId);

        // Nur Rechnungstypen filtern
        List<AbrechnungsverlaufDto.AbrechnungspositionDto> positionen = new ArrayList<>();
        BigDecimal bereitsAbgerechnet = BigDecimal.ZERO;

        for (AusgangsGeschaeftsDokument dok : nachfolger) {
            if (!RECHNUNGSTYPEN.contains(dok.getTyp())) {
                continue;
            }

            AbrechnungsverlaufDto.AbrechnungspositionDto pos = new AbrechnungsverlaufDto.AbrechnungspositionDto();
            pos.setId(dok.getId());
            pos.setDokumentNummer(dok.getDokumentNummer());
            pos.setTyp(dok.getTyp());
            pos.setDatum(dok.getDatum());
            pos.setErstelltAm(dok.getErstelltAm());
            pos.setAbschlagsNummer(dok.getAbschlagsNummer());
            pos.setStorniert(dok.isStorniert());

            // Betrag ermitteln: gespeicherter Wert oder aus positionenJson berechnen
            BigDecimal effektiverBetrag = dok.getBetragNetto();
            if (effektiverBetrag == null && dok.getPositionenJson() != null) {
                effektiverBetrag = berechneNettoAusPositionenJson(dok.getPositionenJson());
            }
            pos.setBetragNetto(effektiverBetrag != null ? effektiverBetrag : BigDecimal.ZERO);
            positionen.add(pos);

            // Nur nicht-stornierte Rechnungen in die Summe
            if (!dok.isStorniert() && effektiverBetrag != null) {
                bereitsAbgerechnet = bereitsAbgerechnet.add(effektiverBetrag);
            }
        }

        verlauf.setPositionen(positionen);
        verlauf.setBereitsAbgerechnet(bereitsAbgerechnet);
        verlauf.setRestbetrag(verlauf.getBasisdokumentBetragNetto().subtract(bereitsAbgerechnet));

        // Block-IDs aus nicht-stornierten Teilrechnungen sammeln
        Set<String> abgerechneteBlockIds = new HashSet<>();
        for (AusgangsGeschaeftsDokument dok : nachfolger) {
            if (dok.getTyp() == AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG
                    && !dok.isStorniert()
                    && dok.getPositionenJson() != null) {
                abgerechneteBlockIds.addAll(extractAbgerechneteBlockIds(dok.getPositionenJson()));
            }
        }
        verlauf.setBereitsAbgerechneteBlockIds(abgerechneteBlockIds);

        return verlauf;
    }

    /**
     * Validiert, dass ein neuer Rechnungsbetrag den Restbetrag des Basisdokuments nicht übersteigt.
     *
     * @throws RuntimeException wenn der Betrag den Restbetrag übersteigt
     */
    private void validateRechnungsbetrag(Long vorgaengerId, BigDecimal neuerBetrag) {
        AbrechnungsverlaufDto verlauf = getAbrechnungsverlauf(vorgaengerId);
        BigDecimal restbetrag = verlauf.getRestbetrag();

        // Toleranz von 0.01 für Rundungsdifferenzen
        if (neuerBetrag.compareTo(restbetrag.add(new BigDecimal("0.01"))) > 0) {
            throw new RuntimeException(
                    String.format("Der Rechnungsbetrag (%.2f €) übersteigt den verfügbaren Restbetrag (%.2f €) " +
                                    "des Basisdokuments %s.",
                            neuerBetrag, restbetrag, verlauf.getBasisdokumentNummer())
            );
        }
    }

    // --- Private Helpers ---

    /**
     * Berechnet den Nettobetrag aus dem positionenJson eines Dokuments.
     * Summiert (quantity * price) aller SERVICE-Blöcke (auch in SECTION_HEADER verschachtelt).
     */
    private BigDecimal berechneNettoAusPositionenJson(String positionenJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(positionenJson);

            JsonNode blocks;
            if (root.isArray()) {
                blocks = root;
            } else if (root.has("blocks") && root.get("blocks").isArray()) {
                blocks = root.get("blocks");
            } else {
                return null;
            }

            BigDecimal summe = BigDecimal.ZERO;
            for (JsonNode block : blocks) {
                summe = summe.add(summeServiceBlock(block));
            }
            return summe.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("Fehler beim Berechnen des Nettobetrags aus positionenJson: {}", e.getMessage());
            return null;
        }
    }

    private BigDecimal summeServiceBlock(JsonNode block) {
        BigDecimal summe = BigDecimal.ZERO;
        String type = block.has("type") ? block.get("type").asText() : "";

        if ("SERVICE".equals(type)) {
            double quantity = block.has("quantity") ? block.get("quantity").asDouble(0) : 0;
            double price = block.has("price") ? block.get("price").asDouble(0) : 0;
            summe = summe.add(BigDecimal.valueOf(quantity).multiply(BigDecimal.valueOf(price)));
        }

        if ("SECTION_HEADER".equals(type) && block.has("children") && block.get("children").isArray()) {
            for (JsonNode child : block.get("children")) {
                summe = summe.add(summeServiceBlock(child));
            }
        }

        return summe;
    }

    /**
     * Extrahiert die IDs aller SERVICE-Blöcke mit nicht-null quantity > 0 aus einem positionenJson.
     * Damit werden die tatsächlich abgerechneten Positionen einer Teilrechnung identifiziert.
     */
    private Set<String> extractAbgerechneteBlockIds(String positionenJson) {
        Set<String> ids = new HashSet<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(positionenJson);

            JsonNode blocks;
            if (root.isArray()) {
                blocks = root;
            } else if (root.has("blocks") && root.get("blocks").isArray()) {
                blocks = root.get("blocks");
            } else {
                return ids;
            }

            for (JsonNode block : blocks) {
                collectAbgerechneteServiceIds(block, ids);
            }
        } catch (Exception e) {
            log.warn("Fehler beim Extrahieren der abgerechneten Block-IDs: {}", e.getMessage());
        }
        return ids;
    }

    private void collectAbgerechneteServiceIds(JsonNode block, Set<String> ids) {
        String type = block.has("type") ? block.get("type").asText() : "";

        if ("SERVICE".equals(type)) {
            double quantity = block.has("quantity") ? block.get("quantity").asDouble(0) : 0;
            double price = block.has("price") ? block.get("price").asDouble(0) : 0;
            if (quantity > 0 && price > 0 && block.has("id")) {
                ids.add(block.get("id").asText());
            }
        }

        if ("SECTION_HEADER".equals(type) && block.has("children") && block.get("children").isArray()) {
            for (JsonNode child : block.get("children")) {
                collectAbgerechneteServiceIds(child, ids);
            }
        }
    }

    /**
     * Generiert eine neue Dokumentnummer im Format {PREFIX}-YYYY/MM/NNNNN.
     * Thread-sicher durch pessimistisches Locking. Gemeinsamer Monatszähler für alle Typen.
     */
    private String generiereNummer(AusgangsGeschaeftsDokumentTyp typ) {
        YearMonth now = YearMonth.now();
        String monatKey = now.format(DateTimeFormatter.ofPattern("yyyy/MM"));

        // Counter mit Lock holen oder neu anlegen
        AusgangsGeschaeftsDokumentCounter counter = counterRepository.findByMonatKeyForUpdate(monatKey)
                .orElseGet(() -> {
                    AusgangsGeschaeftsDokumentCounter neuerCounter = new AusgangsGeschaeftsDokumentCounter();
                    neuerCounter.setMonatKey(monatKey);
                    neuerCounter.setZaehler(0L);
                    return counterRepository.save(neuerCounter);
                });

        // Zähler erhöhen
        counter.setZaehler(counter.getZaehler() + 1);
        counterRepository.save(counter);

        // Nummer formatieren: {PREFIX}-YYYY/MM/NNNNN
        return String.format("%s-%s/%05d", praefixFuer(typ), monatKey, counter.getZaehler());
    }

    /**
     * Liefert das Nummernkreis-Präfix für den Dokumenttyp.
     * AG = Angebot, AB = Auftragsbestätigung, RE = Rechnung, TR = Teilrechnung,
     * AR = Abschlagsrechnung, SR = Schlussrechnung, GU = Gutschrift, ST = Storno.
     */
    private String praefixFuer(AusgangsGeschaeftsDokumentTyp typ) {
        return switch (typ) {
            case ANGEBOT -> "AG";
            case AUFTRAGSBESTAETIGUNG -> "AB";
            case RECHNUNG -> "RE";
            case TEILRECHNUNG -> "TR";
            case ABSCHLAGSRECHNUNG -> "AR";
            case SCHLUSSRECHNUNG -> "SR";
            case GUTSCHRIFT -> "GU";
            case STORNO -> "ST";
        };
    }

    /**
     * Konvertiert Entity zu Response DTO.
     */
    private AusgangsGeschaeftsDokumentResponseDto toResponseDto(AusgangsGeschaeftsDokument dokument) {
        AusgangsGeschaeftsDokumentResponseDto dto = new AusgangsGeschaeftsDokumentResponseDto();
        dto.setId(dokument.getId());
        dto.setDokumentNummer(dokument.getDokumentNummer());
        dto.setTyp(dokument.getTyp());
        dto.setDatum(dokument.getDatum());
        dto.setBetreff(dokument.getBetreff());
        dto.setHtmlInhalt(dokument.getHtmlInhalt());
        dto.setPositionenJson(dokument.getPositionenJson());

        // Betrag Netto: gespeicherter Wert oder aus positionenJson berechnen
        BigDecimal netto = dokument.getBetragNetto();
        if (netto == null && dokument.getPositionenJson() != null) {
            netto = berechneNettoAusPositionenJson(dokument.getPositionenJson());
        }

        // Schlussrechnung: Betrag ist Restbetrag (Basisdokument minus bereits abgerechnete Rechnungen,
        // aber OHNE die Schlussrechnung selbst, da sie sonst sich selbst abzieht und immer 0 ergibt).
        // Stornierte Schlussrechnungen: gespeicherten Betrag verwenden, da der Abrechnungsverlauf
        // sie bereits ausschließt und das Addieren von eigenBetrag den Betrag verdoppeln würde.
        if (dokument.getTyp() == AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG
                && dokument.getVorgaenger() != null
                && !dokument.isStorniert()) {
            try {
                AbrechnungsverlaufDto verlauf = getAbrechnungsverlauf(dokument.getVorgaenger().getId());
                // Restbetrag = Basisbetrag - bereitsAbgerechnet (ALLE Nachfolger inkl. dieser Schlussrechnung)
                // Wir müssen den Betrag dieser Schlussrechnung wieder addieren, um ihn nicht doppelt abzuziehen
                BigDecimal eigenBetrag = dokument.getBetragNetto() != null ? dokument.getBetragNetto() : BigDecimal.ZERO;
                netto = verlauf.getRestbetrag().add(eigenBetrag);
            } catch (Exception e) {
                log.warn("Fehler beim Berechnen des Schlussrechnungsbetrags: {}", e.getMessage());
            }
        }

        dto.setBetragNetto(netto);

        // Bruttobetrag: immer aus aktuellem Netto berechnen für Konsistenz
        BigDecimal mwstSatz = dokument.getMwstSatz() != null ? dokument.getMwstSatz() : new BigDecimal("0.19");
        BigDecimal brutto;
        if (netto != null) {
            BigDecimal mwst = netto.multiply(mwstSatz);
            brutto = netto.add(mwst).setScale(2, RoundingMode.HALF_UP);
        } else {
            brutto = dokument.getBetragBrutto();
        }
        dto.setBetragBrutto(brutto);

        dto.setMwstSatz(dokument.getMwstSatz());
        dto.setMwstBetrag(dokument.getMwstBetrag());
        dto.setAbschlagsNummer(dokument.getAbschlagsNummer());
        dto.setGebucht(dokument.isGebucht());
        dto.setGebuchtAm(dokument.getGebuchtAm());
        dto.setStorniert(dokument.isStorniert());
        dto.setStorniertAm(dokument.getStorniertAm());
        dto.setZahlungszielTage(dokument.getZahlungszielTage());
        dto.setVersandDatum(dokument.getVersandDatum());
        dto.setBearbeitbar(dokument.istBearbeitbar());

        // Verknüpfungen
        if (dokument.getProjekt() != null) {
            dto.setProjektId(dokument.getProjekt().getId());
            dto.setProjektBauvorhaben(dokument.getProjekt().getBauvorhaben());
            dto.setProjektnummer(dokument.getProjekt().getAuftragsnummer());
        }

        if (dokument.getAnfrage() != null) {
            dto.setAnfrageId(dokument.getAnfrage().getId());
        }

        if (dokument.getKunde() != null) {
            dto.setKundeId(dokument.getKunde().getId());
            dto.setKundennummer(dokument.getKunde().getKundennummer());
            dto.setKundenName(dokument.getKunde().getName());
            // Override hat Vorrang vor berechneter Kundenadresse
            if (dokument.getRechnungsadresseOverride() != null && !dokument.getRechnungsadresseOverride().isBlank()) {
                dto.setRechnungsadresse(dokument.getRechnungsadresseOverride());
            } else {
                dto.setRechnungsadresse(buildRechnungsadresse(dokument.getKunde()));
            }
        } else if (dokument.getRechnungsadresseOverride() != null && !dokument.getRechnungsadresseOverride().isBlank()) {
            dto.setRechnungsadresse(dokument.getRechnungsadresseOverride());
        }

        if (dokument.getVorgaenger() != null) {
            dto.setVorgaengerId(dokument.getVorgaenger().getId());
            dto.setVorgaengerNummer(dokument.getVorgaenger().getDokumentNummer());
        }

        // Ersteller
        if (dokument.getErstelltVon() != null) {
            dto.setErstelltVonId(dokument.getErstelltVon().getId());
            dto.setErstelltVonName(dokument.getErstelltVon().getDisplayName());
        }

        return dto;
    }

    /**
     * Baut die Rechnungsadresse aus den Kundendaten.
     */
    private String buildRechnungsadresse(Kunde kunde) {
        StringBuilder sb = new StringBuilder();
        if (kunde.getName() != null) sb.append(kunde.getName());
        if (kunde.getAnsprechspartner() != null) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(kunde.getAnsprechspartner());
        }
        if (kunde.getStrasse() != null) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(kunde.getStrasse());
        }
        if (kunde.getPlz() != null || kunde.getOrt() != null) {
            if (sb.length() > 0) sb.append("\n");
            if (kunde.getPlz() != null) sb.append(kunde.getPlz()).append(" ");
            if (kunde.getOrt() != null) sb.append(kunde.getOrt());
        }
        return sb.toString().trim();
    }

    // --- Projekt-Preis Berechnung aus Dokumenten ---

    /**
     * Berechnet bruttoPreis und bezahlt-Status eines Projekts on-the-fly
     * aus den AusgangsGeschaeftsDokumenten.
     *
     * Logik:
     * - Auftragsbestätigungen haben Priorität vor Anfragenn für den Brutto-Preis.
     * - Falls keine ABs, wird die Anfragessumme verwendet.
     * - bezahlt = true wenn Summe der (nicht-stornierten) Rechnungen >= Projekt-Preis UND alle Offene-Posten-Rechnungen bezahlt.
     * - abgeschlossen = true wenn bezahlt = true.
     */
    @Transactional
    public void aktualisiereProjektPreisAusDokumenten(Long projektId) {
        if (projektId == null) return;

        Projekt projekt = projektRepository.findById(projektId).orElse(null);
        if (projekt == null) return;

        List<AusgangsGeschaeftsDokument> alleDokumente =
                dokumentRepository.findByProjektIdOrderByDatumDesc(projektId);

        // Stornierte Dokumente ausfiltern
        List<AusgangsGeschaeftsDokument> aktive = alleDokumente.stream()
                .filter(d -> !d.isStorniert())
                .toList();

        // Kategorisieren
        BigDecimal summeAB = aktive.stream()
                .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG)
                .map(d -> d.getBetragBrutto() != null ? d.getBetragBrutto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal summeAnfragen = aktive.stream()
                .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.ANGEBOT)
                .map(d -> d.getBetragBrutto() != null ? d.getBetragBrutto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal summeRechnungen = aktive.stream()
                .filter(d -> RECHNUNGSTYPEN.contains(d.getTyp()))
                .map(d -> d.getBetragBrutto() != null ? d.getBetragBrutto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Stornierte Rechnungen sind bereits über !isStorniert() ausgeschlossen,
        // daher werden Storno-Dokumente (negative Beträge) NICHT mehr abgezogen,
        // um doppelte Berücksichtigung zu vermeiden.

        // AB hat Priorität, dann Anfragen
        BigDecimal neuerBruttoPreis = summeAB.compareTo(BigDecimal.ZERO) > 0 ? summeAB : summeAnfragen;

        // Wenn Dokumente (AB/Angebote) vorhanden sind, Preis immer aus Dokumenten übernehmen.
        // Ohne Dokumente bleibt der manuell gesetzte Preis erhalten.
        if (neuerBruttoPreis.compareTo(BigDecimal.ZERO) > 0) {
            projekt.setBruttoPreis(neuerBruttoPreis);
        }

        // bezahlt = Rechnungssumme >= Projektpreis UND alle Offene-Posten-Rechnungen bezahlt
        boolean rechnungssummeAusreichend = neuerBruttoPreis.compareTo(BigDecimal.ZERO) > 0
                && summeRechnungen.compareTo(neuerBruttoPreis.subtract(new BigDecimal("0.01"))) >= 0;
        boolean keineOffenenPosten = !projektDokumentRepository.existsOffenePostenByProjektId(projektId);

        if (rechnungssummeAusreichend && keineOffenenPosten) {
            projekt.setBezahlt(true);
            projekt.setAbgeschlossen(true);
        } else {
            projekt.setBezahlt(false);
            if (!keineOffenenPosten) {
                projekt.setAbgeschlossen(false);
            }
        }

        projektRepository.save(projekt);
    }

    // --- Anfrage-Preis Berechnung aus Dokumenten ---

    /**
     * Berechnet den Betrag (Brutto) eines Anfrages on-the-fly
     * aus den AusgangsGeschaeftsDokumenten.
     *
     * Logik analog zu Projekt:
     * - Auftragsbestätigungen haben Priorität vor Anfragenn für den Brutto-Preis.
     * - Falls keine ABs, wird die Anfragessumme verwendet.
     */
    @Transactional
    public void aktualisiereAnfragePreisAusDokumenten(Long anfrageId) {
        if (anfrageId == null) return;

        Anfrage anfrage = anfrageRepository.findById(anfrageId).orElse(null);
        if (anfrage == null) return;

        List<AusgangsGeschaeftsDokument> alleDokumente =
                dokumentRepository.findByAnfrageIdOrderByDatumDesc(anfrageId);

        // Stornierte Dokumente ausfiltern
        List<AusgangsGeschaeftsDokument> aktive = alleDokumente.stream()
                .filter(d -> !d.isStorniert())
                .toList();

        // Kategorisieren
        BigDecimal summeAB = aktive.stream()
                .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG)
                .map(d -> d.getBetragBrutto() != null ? d.getBetragBrutto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal summeAnfragen = aktive.stream()
                .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.ANGEBOT)
                .map(d -> d.getBetragBrutto() != null ? d.getBetragBrutto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // AB hat Priorität, dann Anfragen
        BigDecimal neuerBetrag = summeAB.compareTo(BigDecimal.ZERO) > 0 ? summeAB : summeAnfragen;

        // Nur überschreiben wenn aktueller Wert null oder 0 ist
        BigDecimal aktuellerBetrag = anfrage.getBetrag();
        if (aktuellerBetrag == null || aktuellerBetrag.compareTo(BigDecimal.ZERO) == 0) {
            anfrage.setBetrag(neuerBetrag);
        }
        anfrageRepository.save(anfrage);
    }

    // --- Projekt-Produktkategorien aus Dokumenten ableiten ---

    /**
     * Aktualisiert die ProjektProduktkategorien eines Projekts anhand der Leistungen
     * in den Anfrages-/AB-Dokumenten.
     *
     * Logik:
     * - Auftragsbestätigungen (Childobjekte) haben Priorität vor Anfragenn.
     * - Wenn ABs existieren, werden NUR deren Leistungen berücksichtigt.
     * - Sonst fallen die Anfrages-Dokumente zurück.
     * - Aus den positionenJson der effektiven Dokumente werden die leistungIds extrahiert.
     * - Die zugehörigen Produktkategorien werden dem Projekt zugewiesen.
     * - Bereits existierende Kategorien bleiben erhalten, neue werden hinzugefügt,
     *   nicht mehr benötigte werden entfernt.
     */
    @Transactional
    public void aktualisiereProjektProduktkategorienAusDokumenten(Long projektId) {
        if (projektId == null) return;

        Projekt projekt = projektRepository.findById(projektId).orElse(null);
        if (projekt == null) return;

        List<AusgangsGeschaeftsDokument> alleDokumente = new ArrayList<>(
                dokumentRepository.findByProjektIdOrderByDatumDesc(projektId));

        // Auch Dokumente einbeziehen, die über Anfragen mit dem Projekt verknüpft sind
        List<AusgangsGeschaeftsDokument> anfrageDokumente =
                dokumentRepository.findByAnfrageProjektIdAndProjektIsNull(projektId);
        alleDokumente.addAll(anfrageDokumente);

        // Nur aktive (nicht stornierte) Dokumente berücksichtigen
        List<AusgangsGeschaeftsDokument> aktive = alleDokumente.stream()
                .filter(d -> !d.isStorniert())
                .filter(d -> KATEGORIE_RELEVANTE_TYPEN.contains(d.getTyp()))
                .toList();

        // Childobjekte (ABs) haben Priorität – wenn vorhanden, nur diese verwenden
        List<AusgangsGeschaeftsDokument> abs = aktive.stream()
                .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG)
                .toList();

        List<AusgangsGeschaeftsDokument> effektiveDokumente = abs.isEmpty()
                ? aktive.stream()
                    .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.ANGEBOT)
                    .toList()
                : abs;

        // LeistungId → aggregierte Menge aus positionenJson extrahieren
        Map<Long, BigDecimal> leistungMengen = new java.util.HashMap<>();
        for (AusgangsGeschaeftsDokument dok : effektiveDokumente) {
            extractLeistungMengenFromPositionenJson(dok.getPositionenJson(), leistungMengen);
        }

        // Leistungen laden und Produktkategorie → aggregierte Menge ermitteln
        Map<Long, BigDecimal> kategorieMengen = new java.util.HashMap<>();
        if (!leistungMengen.isEmpty()) {
            List<Leistung> leistungen = leistungRepository.findAllById(leistungMengen.keySet());
            for (Leistung l : leistungen) {
                if (l.getKategorie() != null) {
                    kategorieMengen.merge(l.getKategorie().getId(),
                            leistungMengen.getOrDefault(l.getId(), BigDecimal.ZERO),
                            BigDecimal::add);
                }
            }
        }

        // Bestehende ProjektProduktkategorien: Map nach kategorieId
        Map<Long, ProjektProduktkategorie> bestehend = projekt.getProjektProduktkategorien().stream()
                .collect(Collectors.toMap(
                        ppk -> ppk.getProduktkategorie().getId(),
                        ppk -> ppk,
                        (a, b) -> a));

        // Nicht mehr benötigte entfernen (nur wenn keine Zeitbuchungen existieren)
        projekt.getProjektProduktkategorien()
                .removeIf(ppk -> !kategorieMengen.containsKey(ppk.getProduktkategorie().getId())
                        && !zeitbuchungRepository.existsByProjektProduktkategorieId(ppk.getId()));

        // Bestehende aktualisieren und neue hinzufügen
        for (Map.Entry<Long, BigDecimal> entry : kategorieMengen.entrySet()) {
            Long katId = entry.getKey();
            BigDecimal menge = entry.getValue();
            ProjektProduktkategorie existing = bestehend.get(katId);
            if (existing != null) {
                existing.setMenge(menge);
            } else {
                Produktkategorie pk = produktkategorieRepository.findById(katId).orElse(null);
                if (pk == null) continue;
                ProjektProduktkategorie ppk = new ProjektProduktkategorie();
                ppk.setProjekt(projekt);
                ppk.setProduktkategorie(pk);
                ppk.setMenge(menge);
                projekt.getProjektProduktkategorien().add(ppk);
            }
        }

        projektRepository.save(projekt);
        log.info("ProjektProduktkategorien für Projekt {} aktualisiert: {} Kategorien",
                projektId, kategorieMengen.size());
    }

    /**
     * Extrahiert alle leistungId-Werte aus einem positionenJson-String.
     * Berücksichtigt sowohl Top-Level SERVICE-Blöcke als auch verschachtelte Blöcke
     * in SECTION_HEADER-Containern.
     */
    private Set<Long> extractLeistungIdsFromPositionenJson(String positionenJson) {
        Set<Long> ids = new HashSet<>();
        if (positionenJson == null || positionenJson.isBlank()) return ids;

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(positionenJson);

            JsonNode blocks;
            if (root.isArray()) {
                blocks = root;
            } else if (root.has("blocks") && root.get("blocks").isArray()) {
                blocks = root.get("blocks");
            } else {
                return ids;
            }

            for (JsonNode block : blocks) {
                collectLeistungIds(block, ids);
            }
        } catch (Exception e) {
            log.warn("Fehler beim Extrahieren der LeistungIds aus positionenJson: {}", e.getMessage());
        }
        return ids;
    }

    /**
     * Extrahiert leistungId → aggregierte Menge (quantity) aus einem positionenJson-String.
     * Optionale Positionen werden nicht berücksichtigt.
     */
    private void extractLeistungMengenFromPositionenJson(String positionenJson, Map<Long, BigDecimal> result) {
        if (positionenJson == null || positionenJson.isBlank()) return;

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(positionenJson);

            JsonNode blocks;
            if (root.isArray()) {
                blocks = root;
            } else if (root.has("blocks") && root.get("blocks").isArray()) {
                blocks = root.get("blocks");
            } else {
                return;
            }

            for (JsonNode block : blocks) {
                collectLeistungMengen(block, result);
            }
        } catch (Exception e) {
            log.warn("Fehler beim Extrahieren der Leistung-Mengen aus positionenJson: {}", e.getMessage());
        }
    }

    private void collectLeistungIds(JsonNode block, Set<Long> ids) {
        String type = block.has("type") ? block.get("type").asText() : "";

        if ("SERVICE".equals(type) && block.has("leistungId") && !block.get("leistungId").isNull()) {
            ids.add(block.get("leistungId").asLong());
        }

        if ("SECTION_HEADER".equals(type) && block.has("children") && block.get("children").isArray()) {
            for (JsonNode child : block.get("children")) {
                collectLeistungIds(child, ids);
            }
        }
    }

    private void collectLeistungMengen(JsonNode block, Map<Long, BigDecimal> result) {
        String type = block.has("type") ? block.get("type").asText() : "";

        if ("SERVICE".equals(type) && block.has("leistungId") && !block.get("leistungId").isNull()) {
            // Optionale Positionen nicht berücksichtigen
            boolean optional = block.has("optional") && block.get("optional").asBoolean(false);
            if (!optional) {
                long leistungId = block.get("leistungId").asLong();
                BigDecimal quantity = block.has("quantity") && !block.get("quantity").isNull()
                        ? BigDecimal.valueOf(block.get("quantity").asDouble())
                        : BigDecimal.ZERO;
                result.merge(leistungId, quantity, BigDecimal::add);
            }
        }

        if ("SECTION_HEADER".equals(type) && block.has("children") && block.get("children").isArray()) {
            for (JsonNode child : block.get("children")) {
                collectLeistungMengen(child, result);
            }
        }
    }
}
