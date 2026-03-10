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

import org.example.kalkulationsprogramm.domain.Angebot;
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
import org.example.kalkulationsprogramm.repository.AngebotRepository;
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
    private final AngebotRepository angebotRepository;
    private final KundeRepository kundeRepository;
    private final FrontendUserProfileRepository frontendUserProfileRepository;
    private final LeistungRepository leistungRepository;
    private final ProduktkategorieRepository produktkategorieRepository;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;

    public AusgangsGeschaeftsDokumentService(
            @Value("${file.upload-dir}") String uploadDir,
            AusgangsGeschaeftsDokumentRepository dokumentRepository,
            AusgangsGeschaeftsDokumentCounterRepository counterRepository,
            ProjektRepository projektRepository,
            AngebotRepository angebotRepository,
            KundeRepository kundeRepository,
            FrontendUserProfileRepository frontendUserProfileRepository,
            LeistungRepository leistungRepository,
            ProduktkategorieRepository produktkategorieRepository,
            ProjektDokumentRepository projektDokumentRepository,
            ZeitbuchungRepository zeitbuchungRepository) {
        this.dokumentenSpeicherplatz = Path.of(uploadDir).toAbsolutePath().normalize();
        this.dokumentRepository = dokumentRepository;
        this.counterRepository = counterRepository;
        this.projektRepository = projektRepository;
        this.angebotRepository = angebotRepository;
        this.kundeRepository = kundeRepository;
        this.frontendUserProfileRepository = frontendUserProfileRepository;
        this.leistungRepository = leistungRepository;
        this.produktkategorieRepository = produktkategorieRepository;
        this.projektDokumentRepository = projektDokumentRepository;
        this.zeitbuchungRepository = zeitbuchungRepository;
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
        // Nur ein Basisdokument (ohne Vorgänger) pro Projekt/Angebot erlaubt
        if (dto.getVorgaengerId() == null) {
            if (dto.getProjektId() != null && dokumentRepository.existsByProjektIdAndVorgaengerIsNull(dto.getProjektId())) {
                throw new IllegalStateException("Es existiert bereits ein Basisdokument für dieses Projekt.");
            }
            if (dto.getAngebotId() != null && dokumentRepository.existsByAngebotIdAndVorgaengerIsNull(dto.getAngebotId())) {
                throw new IllegalStateException("Es existiert bereits ein Basisdokument für dieses Angebot.");
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

        if (dto.getAngebotId() != null) {
            Angebot angebot = angebotRepository.findById(dto.getAngebotId()).orElse(null);
            dokument.setAngebot(angebot);
            // Kunde aus Angebot übernehmen falls nicht explizit gesetzt
            if (dto.getKundeId() == null && angebot != null && angebot.getKunde() != null) {
                dokument.setKunde(angebot.getKunde());
            }
            // Projekt aus Angebot übernehmen falls nicht explizit gesetzt
            if (dokument.getProjekt() == null && angebot != null && angebot.getProjekt() != null) {
                dokument.setProjekt(angebot.getProjekt());
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
                // Angebot vom Vorgänger übernehmen falls nicht gesetzt
                if (dokument.getAngebot() == null && vorgaenger.getAngebot() != null) {
                    dokument.setAngebot(vorgaenger.getAngebot());
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
        dokument.setDokumentNummer(generiereNummer());

        AusgangsGeschaeftsDokument saved = dokumentRepository.save(dokument);

        // Projekt-Preis aktualisieren
        if (saved.getProjekt() != null) {
            aktualisiereProjektPreisAusDokumenten(saved.getProjekt().getId());
        }

        // Angebot-Preis aktualisieren
        if (saved.getAngebot() != null) {
            aktualisiereAngebotPreisAusDokumenten(saved.getAngebot().getId());
        }

        // ProjektProduktkategorien automatisch zuweisen bei Angebot/AB
        if (saved.getProjekt() != null && KATEGORIE_RELEVANTE_TYPEN.contains(saved.getTyp())) {
            aktualisiereProjektProduktkategorienAusDokumenten(saved.getProjekt().getId());
        } else if (saved.getAngebot() != null && saved.getAngebot().getProjekt() != null
                && KATEGORIE_RELEVANTE_TYPEN.contains(saved.getTyp())) {
            aktualisiereProjektProduktkategorienAusDokumenten(saved.getAngebot().getProjekt().getId());
        }

        return saved;
    }

    /**
     * Stellt sicher, dass ein ANGEBOT-Dokument für das gegebene Angebot existiert.
     * Wird automatisch aufgerufen, wenn der AngebotEditor geöffnet wird.
     * Pro Angebot darf nur ein ANGEBOT-Dokument existieren.
     *
     * @return die dokumentNummer des (ggf. neu erstellten) ANGEBOT-Dokuments, oder null
     */
    @Transactional
    public String ensureAngebotDokument(Long angebotId) {
        if (angebotId == null) return null;

        // Prüfen ob bereits ein ANGEBOT-Dokument existiert
        Optional<AusgangsGeschaeftsDokument> existing = dokumentRepository
                .findFirstByAngebotIdAndTyp(angebotId, AusgangsGeschaeftsDokumentTyp.ANGEBOT);
        if (existing.isPresent()) {
            return existing.get().getDokumentNummer();
        }

        // Angebot laden
        Angebot angebot = angebotRepository.findById(angebotId).orElse(null);
        if (angebot == null) return null;

        // Neues ANGEBOT-Dokument automatisch erstellen
        AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
        dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
        dto.setAngebotId(angebotId);
        dto.setBetreff(angebot.getBauvorhaben());
        if (angebot.getKunde() != null) {
            dto.setKundeId(angebot.getKunde().getId());
        }

        AusgangsGeschaeftsDokument created = erstellen(dto);
        return created.getDokumentNummer();
    }

    /**
     * Gibt die Angebotsnummer (= dokumentNummer des ANGEBOT-Dokuments) zurück, falls vorhanden.
     */
    public String resolveAngebotsnummer(Long angebotId) {
        if (angebotId == null) return null;
        return dokumentRepository
                .findFirstByAngebotIdAndTyp(angebotId, AusgangsGeschaeftsDokumentTyp.ANGEBOT)
                .map(AusgangsGeschaeftsDokument::getDokumentNummer)
                .orElse(null);
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

        // Angebot-Preis aktualisieren
        if (saved.getAngebot() != null) {
            aktualisiereAngebotPreisAusDokumenten(saved.getAngebot().getId());
        }

        // ProjektProduktkategorien automatisch aktualisieren bei Angebot/AB-Änderung
        if (saved.getProjekt() != null && KATEGORIE_RELEVANTE_TYPEN.contains(saved.getTyp())) {
            aktualisiereProjektProduktkategorienAusDokumenten(saved.getProjekt().getId());
        } else if (saved.getAngebot() != null && saved.getAngebot().getProjekt() != null
                && KATEGORIE_RELEVANTE_TYPEN.contains(saved.getTyp())) {
            aktualisiereProjektProduktkategorienAusDokumenten(saved.getAngebot().getProjekt().getId());
        }

        return saved;
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
     * Angebote/ABs werden NICHT gebucht, da sie nachträglich angepasst werden können.
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

        // Angebote und ABs werden nicht gebucht – sie sollen nachträglich anpassbar bleiben
        if (NICHT_BUCHBARE_TYPEN.contains(dokument.getTyp())) {
            log.info("Dokument {} (Typ: {}) wird nicht gebucht – Angebote/ABs bleiben immer bearbeitbar",
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
     * Angebote/ABs bekommen das Versanddatum, werden aber NICHT gebucht.
     */
    @Transactional
    public AusgangsGeschaeftsDokument buchenNachEmailVersand(Long id) {
        AusgangsGeschaeftsDokument dokument = dokumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dokument nicht gefunden: " + id));

        if (dokument.isStorniert()) {
            throw new RuntimeException("Storniertes Dokument kann nicht versendet werden.");
        }

        dokument.setVersandDatum(LocalDate.now());

        // Angebote und ABs werden nicht gebucht – nur Versanddatum setzen
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
        storno.setDokumentNummer(generiereNummer());

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
        storno.setAngebot(original.getAngebot());
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

        // Angebot-Preis aktualisieren
        if (original.getAngebot() != null) {
            aktualisiereAngebotPreisAusDokumenten(original.getAngebot().getId());
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
     * Findet alle Dokumente für ein Angebot.
     */
    public List<AusgangsGeschaeftsDokumentResponseDto> findByAngebot(Long angebotId) {
        return dokumentRepository.findByAngebotIdOrderByDatumDesc(angebotId).stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Migriert alle AusgangsGeschaeftsDokumente von einem Angebot zum Projekt.
     * Wird aufgerufen, wenn ein Angebot in ein Projekt überführt wird.
     * Setzt das Projekt und entfernt die Angebot-Referenz, damit das Angebot gelöscht werden kann.
     */
    @Transactional
    public void migrateFromAngebotToProjekt(Long angebotId, Projekt projekt) {
        List<AusgangsGeschaeftsDokument> dokumente = dokumentRepository.findByAngebotIdOrderByDatumDesc(angebotId);
        for (AusgangsGeschaeftsDokument dok : dokumente) {
            dok.setProjekt(projekt);
            dok.setAngebot(null);
            dokumentRepository.save(dok);
        }
        if (!dokumente.isEmpty()) {
            log.info("Migrierte {} Ausgangsgeschäftsdokumente von Angebot {} zu Projekt {}",
                    dokumente.size(), angebotId, projekt.getId());
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
        Long angebotId = dokument.getAngebot() != null ? dokument.getAngebot().getId() : null;
        boolean kategorieRelevant = KATEGORIE_RELEVANTE_TYPEN.contains(dokument.getTyp());
        // Für den Fall dass das Dokument über ein Angebot mit einem Projekt verknüpft ist
        Long angebotProjektId = (dokument.getAngebot() != null && dokument.getAngebot().getProjekt() != null)
                ? dokument.getAngebot().getProjekt().getId() : null;
        dokumentRepository.delete(dokument);

        // Projekt-Preis aktualisieren
        if (projektId != null) {
            aktualisiereProjektPreisAusDokumenten(projektId);
        }

        // Angebot-Preis aktualisieren
        if (angebotId != null) {
            aktualisiereAngebotPreisAusDokumenten(angebotId);
        }

        // ProjektProduktkategorien dynamisch aktualisieren nach Löschung
        if (kategorieRelevant) {
            if (projektId != null) {
                aktualisiereProjektProduktkategorienAusDokumenten(projektId);
            } else if (angebotProjektId != null) {
                aktualisiereProjektProduktkategorienAusDokumenten(angebotProjektId);
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
     * Generiert eine neue Dokumentnummer im Format YYYY/MM/NNNNN.
     * Thread-sicher durch pessimistisches Locking.
     */
    private String generiereNummer() {
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

        // Nummer formatieren: YYYY/MM/NNNNN
        return String.format("%s/%05d", monatKey, counter.getZaehler());
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

        if (dokument.getAngebot() != null) {
            dto.setAngebotId(dokument.getAngebot().getId());
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
     * - Auftragsbestätigungen haben Priorität vor Angeboten für den Brutto-Preis.
     * - Falls keine ABs, wird die Angebotssumme verwendet.
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

        BigDecimal summeAngebote = aktive.stream()
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

        // AB hat Priorität, dann Angebote
        BigDecimal neuerBruttoPreis = summeAB.compareTo(BigDecimal.ZERO) > 0 ? summeAB : summeAngebote;

        // Nur überschreiben wenn aktueller Wert null oder 0 ist
        BigDecimal aktuellerPreis = projekt.getBruttoPreis();
        if (aktuellerPreis == null || aktuellerPreis.compareTo(BigDecimal.ZERO) == 0) {
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
            // abgeschlossen wird NICHT automatisch zurückgesetzt –
            // der Benutzer steuert dies manuell über die Checkbox
        }

        projektRepository.save(projekt);
    }

    // --- Angebot-Preis Berechnung aus Dokumenten ---

    /**
     * Berechnet den Betrag (Brutto) eines Angebots on-the-fly
     * aus den AusgangsGeschaeftsDokumenten.
     *
     * Logik analog zu Projekt:
     * - Auftragsbestätigungen haben Priorität vor Angeboten für den Brutto-Preis.
     * - Falls keine ABs, wird die Angebotssumme verwendet.
     */
    @Transactional
    public void aktualisiereAngebotPreisAusDokumenten(Long angebotId) {
        if (angebotId == null) return;

        Angebot angebot = angebotRepository.findById(angebotId).orElse(null);
        if (angebot == null) return;

        List<AusgangsGeschaeftsDokument> alleDokumente =
                dokumentRepository.findByAngebotIdOrderByDatumDesc(angebotId);

        // Stornierte Dokumente ausfiltern
        List<AusgangsGeschaeftsDokument> aktive = alleDokumente.stream()
                .filter(d -> !d.isStorniert())
                .toList();

        // Kategorisieren
        BigDecimal summeAB = aktive.stream()
                .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG)
                .map(d -> d.getBetragBrutto() != null ? d.getBetragBrutto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal summeAngebote = aktive.stream()
                .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.ANGEBOT)
                .map(d -> d.getBetragBrutto() != null ? d.getBetragBrutto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // AB hat Priorität, dann Angebote
        BigDecimal neuerBetrag = summeAB.compareTo(BigDecimal.ZERO) > 0 ? summeAB : summeAngebote;

        // Nur überschreiben wenn aktueller Wert null oder 0 ist
        BigDecimal aktuellerBetrag = angebot.getBetrag();
        if (aktuellerBetrag == null || aktuellerBetrag.compareTo(BigDecimal.ZERO) == 0) {
            angebot.setBetrag(neuerBetrag);
        }
        angebotRepository.save(angebot);
    }

    // --- Projekt-Produktkategorien aus Dokumenten ableiten ---

    /**
     * Aktualisiert die ProjektProduktkategorien eines Projekts anhand der Leistungen
     * in den Angebots-/AB-Dokumenten.
     *
     * Logik:
     * - Auftragsbestätigungen (Childobjekte) haben Priorität vor Angeboten.
     * - Wenn ABs existieren, werden NUR deren Leistungen berücksichtigt.
     * - Sonst fallen die Angebots-Dokumente zurück.
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

        // Auch Dokumente einbeziehen, die über Angebote mit dem Projekt verknüpft sind
        List<AusgangsGeschaeftsDokument> angebotDokumente =
                dokumentRepository.findByAngebotProjektIdAndProjektIsNull(projektId);
        alleDokumente.addAll(angebotDokumente);

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
