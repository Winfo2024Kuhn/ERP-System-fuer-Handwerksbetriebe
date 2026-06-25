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
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabePositionDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieVorschlagDto;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
    private final AusgangsGeschaeftsDokumentAuditService auditService;

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
            AusgangsGeschaeftsDokumentAuditService auditService) {
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
        this.auditService = auditService;
    }

    /** Rechnungstypen, die im Abrechnungsverlauf berücksichtigt werden */
    private static final Set<AusgangsGeschaeftsDokumentTyp> RECHNUNGSTYPEN = EnumSet.of(
            AusgangsGeschaeftsDokumentTyp.RECHNUNG,
            AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG
    );

    /**
     * Buchhaltungsrelevante Typen, für die ein GoBD-konformer Audit-Trail (§147 AO,
     * 10-Jahres-Aufbewahrung) zwingend ist. Angebot/AB sind nur Geschäftsbriefe
     * (§147 Abs. 1 Nr. 2 AO, 6 Jahre) und brauchen keinen Audit-Eintrag.
     */
    private static final Set<AusgangsGeschaeftsDokumentTyp> AUDIT_RELEVANTE_TYPEN = EnumSet.of(
            AusgangsGeschaeftsDokumentTyp.RECHNUNG,
            AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT,
            AusgangsGeschaeftsDokumentTyp.STORNO
    );

    /** Dokumenttypen, die für die Produktkategorie-Zuordnung relevant sind */
    private static final Set<AusgangsGeschaeftsDokumentTyp> KATEGORIE_RELEVANTE_TYPEN = EnumSet.of(
            AusgangsGeschaeftsDokumentTyp.ANGEBOT,
            AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT,
            AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG
    );

    /**
     * Erstellt ein neues Dokument mit automatisch generierter Nummer.
     */
    @Transactional
    public AusgangsGeschaeftsDokument erstellen(AusgangsGeschaeftsDokumentErstellenDto dto) {
        return erstellen(dto, null);
    }

    /**
     * Wie {@link #erstellen(AusgangsGeschaeftsDokumentErstellenDto)}, schreibt aber
     * für buchhaltungsrelevante Typen einen Audit-Eintrag mit Aufrufer-IP.
     */
    @Transactional
    public AusgangsGeschaeftsDokument erstellen(AusgangsGeschaeftsDokumentErstellenDto dto, String ipAdresse) {
        // Basisdokument-Regeln (Dokumente ohne Vorgänger = eigene Wurzel-Vorgänge):
        //   - Das ERSTE Basisdokument ist das ANGEBOT (max. eines pro Projekt/Anfrage).
        //   - Jedes WEITERE Basisdokument muss ein NACHTRAGSANGEBOT sein und setzt
        //     ein bereits existierendes Angebot voraus. So entstehen mehrere
        //     parallele Vorgänge (Angebot + n Nachtragsangebote), jeder mit
        //     eigener Folgekette aus AB und Rechnungen.
        //   - Andere Typen (z.B. eine eigenständige Rechnung ohne vorheriges
        //     Angebot) bleiben als einzelnes Basisdokument möglich, solange noch
        //     gar kein Basisdokument existiert.
        if (dto.getVorgaengerId() == null) {
            validiereBasisdokument(dto.getTyp(), dto.getProjektId(), dto.getAnfrageId());
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
                // Beim Umwandeln (Typwechsel, z.B. AB -> Abschlagsrechnung) die alten
                // Standard-Textbausteine (VOR/NACH) entfernen und das Flag
                // "standardTextbausteineErneuern" setzen — der DocumentEditor laedt
                // damit beim ersten Oeffnen einmalig die fuer den neuen Typ
                // konfigurierten Textbausteine nach. Gilt fuer geerbtes UND explizit
                // mitgeschicktes positionenJson (z.B. Teil-/Abschlagsrechnung aus dem
                // ProjektEditor-Dialog). Leistungen und Mengen bleiben.
                if (vorgaenger.getTyp() != dokument.getTyp() && dokument.getPositionenJson() != null) {
                    dokument.setPositionenJson(entferneStandardTextbausteine(dokument.getPositionenJson()));
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

        // Bearbeitete Rechnungsanschrift übernehmen: Hat der User in einem
        // Dokument dieses Vorgangs (z.B. dem Angebot) die Rechnungsanschrift
        // geändert, erbt jedes NEUE Dokument die zuletzt geänderte Fassung.
        // Nur beim Erstellen — danach hat jedes Dokument seine eigene,
        // unabhängig editierbare Kopie. Hängt das Dokument nur an einer
        // Anfrage, ist deren Projekt oben bereits auf das Dokument übernommen
        // worden — der Hook nutzt damit denselben Auflösungsweg wie
        // findGeerbteRechnungsadresseFuerAnfrage() (GET-Endpoint).
        if (dokument.getRechnungsadresseOverride() == null || dokument.getRechnungsadresseOverride().isBlank()) {
            String geerbt = findGeerbteRechnungsadresse(
                    dokument.getProjekt() != null ? dokument.getProjekt().getId() : null,
                    dokument.getAnfrage() != null ? dokument.getAnfrage().getId() : null);
            if (geerbt != null) {
                dokument.setRechnungsadresseOverride(geerbt);
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

        if (AUDIT_RELEVANTE_TYPEN.contains(saved.getTyp())) {
            auditService.protokolliereErstellung(saved, saved.getErstelltVon(), ipAdresse);
        }

        return saved;
    }

    /**
     * Ermittelt die Rechnungsanschrift, die ein neues Dokument in diesem
     * Vorgang erben würde: die zuletzt geänderte, vom User bearbeitete
     * Anschrift aller Dokumente des Projekts (bzw. der Anfrage, wenn kein
     * Projekt verknüpft ist). Liefert {@code null}, wenn nirgends eine
     * bearbeitete Anschrift existiert.
     */
    @Transactional(readOnly = true)
    public String findGeerbteRechnungsadresse(Long projektId, Long anfrageId) {
        List<String> overrides;
        if (projektId != null) {
            overrides = dokumentRepository.findRechnungsadresseOverridesByProjektId(projektId);
        } else if (anfrageId != null) {
            overrides = dokumentRepository.findRechnungsadresseOverridesByAnfrageId(anfrageId);
        } else {
            return null;
        }
        return overrides.isEmpty() ? null : overrides.get(0);
    }

    /**
     * Wie {@link #findGeerbteRechnungsadresse(Long, Long)}, löst aber vorher
     * das Projekt der Anfrage auf, damit auch Dokumente des verknüpften
     * Projekts als Quelle dienen.
     */
    @Transactional(readOnly = true)
    public String findGeerbteRechnungsadresseFuerAnfrage(Long anfrageId) {
        Anfrage anfrage = anfrageRepository.findById(anfrageId).orElse(null);
        Long projektId = (anfrage != null && anfrage.getProjekt() != null)
                ? anfrage.getProjekt().getId()
                : null;
        return findGeerbteRechnungsadresse(projektId, anfrageId);
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
            AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT,
            AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG
    );

    /**
     * Bucht ein Dokument (nach Export).
     * Nur Rechnungstypen werden dadurch gesperrt (GoBD).
     * Anfragen/ABs werden NICHT gebucht, da sie nachträglich angepasst werden können.
     */
    @Transactional
    public AusgangsGeschaeftsDokument buchen(Long id) {
        return buchen(id, null, null);
    }

    /**
     * Wie {@link #buchen(Long)}, schreibt aber zusätzlich einen GoBD-Audit-Eintrag
     * mit Bearbeiter und IP-Adresse.
     */
    @Transactional
    public AusgangsGeschaeftsDokument buchen(Long id, Long bearbeiterId, String ipAdresse) {
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

        if (AUDIT_RELEVANTE_TYPEN.contains(saved.getTyp())) {
            var bearbeiter = bearbeiterId != null
                    ? frontendUserProfileRepository.findById(bearbeiterId).orElse(null)
                    : null;
            auditService.protokolliereBuchung(saved, bearbeiter, ipAdresse);
        }
        return saved;
    }

    /**
     * Bucht ein Dokument nach E-Mail-Versand (GoBD-konform).
     * Setzt Versanddatum. Nur Rechnungstypen werden dadurch gesperrt.
     * Anfragen/ABs bekommen das Versanddatum, werden aber NICHT gebucht.
     */
    @Transactional
    public AusgangsGeschaeftsDokument buchenNachEmailVersand(Long id) {
        return buchenNachEmailVersand(id, null, null);
    }

    /**
     * Wie {@link #buchenNachEmailVersand(Long)}, schreibt aber zusätzlich einen
     * GoBD-Audit-Eintrag (Versand und ggf. Buchung) mit Bearbeiter und IP-Adresse.
     */
    @Transactional
    public AusgangsGeschaeftsDokument buchenNachEmailVersand(Long id, Long bearbeiterId, String ipAdresse) {
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

        if (AUDIT_RELEVANTE_TYPEN.contains(saved.getTyp())) {
            var bearbeiter = bearbeiterId != null
                    ? frontendUserProfileRepository.findById(bearbeiterId).orElse(null)
                    : null;
            // Buchung und Versand sind hier ein gemeinsamer Akt: Wenn das Dokument
            // gerade frisch gebucht wurde, wird das als BUCHUNG protokolliert; der
            // Versand kommt als zweiter Eintrag dazu, damit Prüfer beide Aktionen
            // einzeln in der Hash-Kette nachvollziehen kann.
            if (!warBereitsGebucht && !istNichtBuchbar) {
                auditService.protokolliereBuchung(saved, bearbeiter, ipAdresse);
            }
            auditService.protokolliereVersand(saved, bearbeiter, ipAdresse);
        }

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
        return stornieren(id, null, null, null);
    }

    /**
     * Wie {@link #stornieren(Long)}, schreibt aber zusätzlich einen GoBD-Audit-Eintrag
     * (Stornierung des Originals + Erstellung des Storno-Gegendokuments) mit
     * Bearbeiter, IP-Adresse und optional einem fachlichen Stornogrund. Wenn kein
     * Grund mitkommt, wird der Standardtext "Stornierung des Originaldokuments"
     * genutzt — der Audit-Service verlangt zwingend einen Grund (GoBD).
     */
    @Transactional
    public AusgangsGeschaeftsDokument stornieren(Long id, Long bearbeiterId, String ipAdresse, String grund) {
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
                    stornieren(geschwisterDok.getId(), bearbeiterId, ipAdresse,
                            "Kaskadierende Stornierung wegen storniertem Vorgänger " + original.getDokumentNummer());
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

        // GoBD-Audit: Storno + Erstellung des Gegendokuments protokollieren.
        // Ist nur bei AUDIT_RELEVANTE_TYPEN nötig — was hier ohnehin der Fall ist,
        // da nur Rechnungstypen + STORNO storniert werden dürfen.
        if (AUDIT_RELEVANTE_TYPEN.contains(original.getTyp())) {
            var bearbeiter = bearbeiterId != null
                    ? frontendUserProfileRepository.findById(bearbeiterId).orElse(null)
                    : null;
            String stornoGrund = (grund == null || grund.isBlank())
                    ? "Stornierung des Originaldokuments"
                    : grund;
            auditService.protokolliereStornierung(original, bearbeiter, stornoGrund, ipAdresse);
            auditService.protokolliereErstellung(savedStorno, bearbeiter, ipAdresse);
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

        // Vom System erzeugte Einträge erhalten automatische Zahlungserinnerungen per E-Mail
        offenerPosten.setSystemGeneriert(true);

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
    public String speicherePdfFuerDokument(Long dokumentId, byte[] pdfBytes) {
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
        return gespeicherterDateiname;
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
     * Findet alle Dokumente für ein Projekt – inklusive Mahnungen, die
     * weiterhin in {@code ProjektGeschaeftsdokument} persistiert sind und hier
     * als virtuelle Child-Einträge der zugehörigen Rechnung mitgeliefert werden,
     * damit die Hierarchie Rechnung → Zahlungserinnerung → 1. Mahnung →
     * 2. Mahnung im Ausgangs-Dokumente-Tab sichtbar ist.
     */
    @Transactional(readOnly = true)
    public List<AusgangsGeschaeftsDokumentResponseDto> findByProjekt(Long projektId) {
        List<AusgangsGeschaeftsDokument> dokumente =
                dokumentRepository.findByProjektIdOrderByDatumDesc(projektId);

        // Mahnungen aus alter Domaene gruppieren nach Dokumentnummer der Original-Rechnung.
        // Match-Schluessel ist die Dokumentnummer, weil erstelleOffenenPostenEintrag()
        // beide Domaenen synchron mit derselben Nummer haelt.
        java.util.Map<String, java.util.EnumMap<org.example.kalkulationsprogramm.domain.Mahnstufe,
                ProjektGeschaeftsdokument>> mahnungenJeRechnung = new java.util.HashMap<>();
        for (ProjektGeschaeftsdokument m : projektDokumentRepository.findMahnungenByProjektId(projektId)) {
            if (m.getMahnstufe() == null || m.getReferenzDokument() == null) continue;
            String rechnungNummer = m.getReferenzDokument().getDokumentid();
            if (rechnungNummer == null) continue;
            mahnungenJeRechnung
                    .computeIfAbsent(rechnungNummer, k -> new java.util.EnumMap<>(
                            org.example.kalkulationsprogramm.domain.Mahnstufe.class))
                    .put(m.getMahnstufe(), m);
        }

        List<AusgangsGeschaeftsDokumentResponseDto> result = new ArrayList<>();
        for (AusgangsGeschaeftsDokument dok : dokumente) {
            result.add(toResponseDto(dok));

            if (!RECHNUNGSTYPEN.contains(dok.getTyp())) continue;
            java.util.EnumMap<org.example.kalkulationsprogramm.domain.Mahnstufe,
                    ProjektGeschaeftsdokument> stufen = mahnungenJeRechnung.get(dok.getDokumentNummer());
            if (stufen == null || stufen.isEmpty()) continue;

            // Kette aufbauen: Rechnung -> Zahlungserinnerung -> 1. Mahnung -> 2. Mahnung
            Long parentId = dok.getId();
            String parentNummer = dok.getDokumentNummer();
            for (org.example.kalkulationsprogramm.domain.Mahnstufe stufe :
                    org.example.kalkulationsprogramm.domain.Mahnstufe.values()) {
                ProjektGeschaeftsdokument m = stufen.get(stufe);
                if (m == null) continue;
                AusgangsGeschaeftsDokumentResponseDto mDto = mappeMahnungZuDto(m, stufe, dok, parentId, parentNummer);
                result.add(mDto);
                parentId = mDto.getId();
                parentNummer = mDto.getDokumentNummer();
            }
        }
        return result;
    }

    /**
     * Mappt ein Mahn-{@link ProjektGeschaeftsdokument} auf einen virtuellen
     * {@link AusgangsGeschaeftsDokumentResponseDto}-Eintrag fuer den Tree im
     * Projekt-Editor. Die ID wird als negierte ProjektGeschaeftsdokument-ID
     * vergeben, damit sie nicht mit echten AusgangsGeschaeftsDokument-IDs
     * kollidiert; das Frontend erkennt Mahnungen am negativen ID-Vorzeichen
     * und behandelt sie nicht-editierbar.
     */
    private AusgangsGeschaeftsDokumentResponseDto mappeMahnungZuDto(
            ProjektGeschaeftsdokument mahnung,
            org.example.kalkulationsprogramm.domain.Mahnstufe stufe,
            AusgangsGeschaeftsDokument originalRechnung,
            Long vorgaengerId,
            String vorgaengerNummer) {
        AusgangsGeschaeftsDokumentResponseDto dto = new AusgangsGeschaeftsDokumentResponseDto();
        dto.setId(-mahnung.getId());
        dto.setDokumentNummer(mahnung.getDokumentid());
        dto.setTyp(mappeMahnstufeAufTyp(stufe));
        dto.setDatum(mahnung.getRechnungsdatum() != null
                ? mahnung.getRechnungsdatum()
                : mahnung.getUploadDatum());
        dto.setBetreff(mahnstufeLabel(stufe) + " zu Rechnung " + originalRechnung.getDokumentNummer());
        dto.setBetragBrutto(mahnung.getBruttoBetrag());
        dto.setVersandDatum(mahnung.getEmailVersandDatum());
        dto.setVorgaengerId(vorgaengerId);
        dto.setVorgaengerNummer(vorgaengerNummer);

        // Mahnungen sind nach Versand fix — keine Bearbeitung im DocumentEditor.
        dto.setBearbeitbar(false);
        dto.setGebucht(true);
        dto.setStorniert(false);
        dto.setDigitalAngenommen(false);

        if (originalRechnung.getProjekt() != null) {
            dto.setProjektId(originalRechnung.getProjekt().getId());
            dto.setProjektBauvorhaben(originalRechnung.getProjekt().getBauvorhaben());
            dto.setProjektnummer(originalRechnung.getProjekt().getAuftragsnummer());
        }
        if (originalRechnung.getKunde() != null) {
            dto.setKundeId(originalRechnung.getKunde().getId());
            dto.setKundennummer(originalRechnung.getKunde().getKundennummer());
            dto.setKundenName(originalRechnung.getKunde().getName());
        }

        // PDF-Direkt-URL: Mahn-PDFs liegen unter /api/dokumente/{gespeicherterDateiname}.
        if (mahnung.getGespeicherterDateiname() != null && !mahnung.getGespeicherterDateiname().isBlank()) {
            dto.setPdfUrl("/api/dokumente/" + mahnung.getGespeicherterDateiname());
        }
        return dto;
    }

    private static AusgangsGeschaeftsDokumentTyp mappeMahnstufeAufTyp(
            org.example.kalkulationsprogramm.domain.Mahnstufe stufe) {
        return switch (stufe) {
            case ZAHLUNGSERINNERUNG -> AusgangsGeschaeftsDokumentTyp.ZAHLUNGSERINNERUNG;
            case ERSTE_MAHNUNG -> AusgangsGeschaeftsDokumentTyp.ERSTE_MAHNUNG;
            case ZWEITE_MAHNUNG -> AusgangsGeschaeftsDokumentTyp.ZWEITE_MAHNUNG;
        };
    }

    private static String mahnstufeLabel(org.example.kalkulationsprogramm.domain.Mahnstufe stufe) {
        return switch (stufe) {
            case ZAHLUNGSERINNERUNG -> "Zahlungserinnerung";
            case ERSTE_MAHNUNG -> "1. Mahnung";
            case ZWEITE_MAHNUNG -> "2. Mahnung";
        };
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
        loeschen(id, begruendung, null, null);
    }

    /**
     * Löscht ein Dokument mit GoBD-konformem Audit-Eintrag.
     *
     * @param id           Dokument-ID
     * @param begruendung  Pflicht-Begründung (wird in Audit-Tabelle persistiert)
     * @param geloeschtVonId Optional: ID des löschenden FrontendUserProfile
     * @param ipAdresse    Optional: IP-Adresse des Aufrufers
     */
    @Transactional
    public void loeschen(Long id, String begruendung, Long geloeschtVonId, String ipAdresse) {
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

        // GoBD: Vor dem Hard-Delete einen unveränderlichen Audit-Eintrag mit
        // Snapshot, Begründung und Bearbeiter persistieren (revisionssicher).
        var bearbeiter = geloeschtVonId != null
                ? frontendUserProfileRepository.findById(geloeschtVonId).orElse(null)
                : null;
        auditService.protokolliereLoeschung(dokument, bearbeiter, begruendung, ipAdresse);

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

        // Basisbetrag: gespeicherter Wert oder aus positionenJson berechnen.
        // Why: AB/Angebot speichern den Betrag oft nur in positionenJson — ohne Fallback
        // wäre der Restbetrag fälschlich 0 und die Rechnungserstellung würde blockiert.
        BigDecimal basisNetto = basis.getBetragNetto();
        if (basisNetto == null && basis.getPositionenJson() != null) {
            basisNetto = berechneNettoAusPositionenJson(basis.getPositionenJson());
        }
        verlauf.setBasisdokumentBetragNetto(basisNetto != null ? basisNetto : BigDecimal.ZERO);

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
     * Entfernt alle TEXT-Bloecke mit gesetzter "textbausteinRolle" (VOR/NACH) aus einem
     * positionenJson. Wird beim Umwandeln eines Dokuments (z.B. Angebot -> AB) verwendet,
     * damit das Frontend die fuer den neuen Typ konfigurierten Standard-Textbausteine
     * frisch generieren kann. Leistungen, Section-Header, Subtotals und manuell
     * hinzugefuegte Textbausteine (ohne Rolle) bleiben unveraendert erhalten.
     *
     * <p>Zusaetzlich wird das Flag {@code standardTextbausteineErneuern} auf dem
     * JSON-Root gesetzt. Der DocumentEditor laedt beim Oeffnen genau dann die fuer
     * den neuen Typ konfigurierten Default-Textbausteine nach — und nur dann: ohne
     * Flag bleibt der Inhalt bestehender Dokumente beim Oeffnen unangetastet (auch
     * wenn der User Vor-/Nachtexte geloescht hat). Das Flag verschwindet beim
     * ersten Speichern automatisch, weil der Editor das positionenJson nur aus
     * {blocks, globalRabatt, abschlagInfo} neu aufbaut. Legacy-Dokumente im
     * Array-Format werden dafuer in das Objekt-Format {blocks: [...]} gehoben.</p>
     */
    private String entferneStandardTextbausteine(String positionenJson) {
        if (positionenJson == null || positionenJson.isBlank()) return positionenJson;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(positionenJson);

            ArrayNode blocksNode;
            ObjectNode rootObject = null;

            if (root.isArray()) {
                blocksNode = (ArrayNode) root;
            } else if (root.isObject() && root.has("blocks") && root.get("blocks").isArray()) {
                rootObject = (ObjectNode) root;
                blocksNode = (ArrayNode) root.get("blocks");
            } else {
                return positionenJson;
            }

            ArrayNode gefiltert = mapper.createArrayNode();
            for (JsonNode block : blocksNode) {
                if (istStandardTextbaustein(block)) continue;
                // Auch Kinder von SECTION_HEADER bereinigen, falls dort Textbausteine liegen
                if (block.isObject() && block.has("children") && block.get("children").isArray()) {
                    ArrayNode kinder = (ArrayNode) block.get("children");
                    ArrayNode kinderGefiltert = mapper.createArrayNode();
                    for (JsonNode kind : kinder) {
                        if (!istStandardTextbaustein(kind)) kinderGefiltert.add(kind);
                    }
                    ((ObjectNode) block).set("children", kinderGefiltert);
                }
                gefiltert.add(block);
            }

            if (rootObject == null) {
                rootObject = mapper.createObjectNode();
            }
            rootObject.set("blocks", gefiltert);
            rootObject.put("standardTextbausteineErneuern", true);
            return mapper.writeValueAsString(rootObject);
        } catch (Exception e) {
            log.warn("Fehler beim Entfernen der Standard-Textbausteine aus positionenJson: {}", e.getMessage());
            return positionenJson;
        }
    }

    private boolean istStandardTextbaustein(JsonNode block) {
        if (block == null || !block.isObject()) return false;
        JsonNode rolle = block.get("textbausteinRolle");
        return rolle != null && !rolle.isNull() && !rolle.asText().isBlank();
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
    /**
     * Prüft die Regeln für ein neues Basisdokument (ohne Vorgänger).
     * Siehe Kommentar in {@link #erstellen(AusgangsGeschaeftsDokumentErstellenDto, String)}.
     */
    private void validiereBasisdokument(AusgangsGeschaeftsDokumentTyp typ, Long projektId, Long anfrageId) {
        if (typ == AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT) {
            // Nachtragsangebote bauen auf einem bestehenden Angebot auf.
            boolean hatAngebotBasis = (projektId != null
                        && dokumentRepository.existsByProjektIdAndVorgaengerIsNullAndTyp(projektId, AusgangsGeschaeftsDokumentTyp.ANGEBOT))
                    || (anfrageId != null
                        && dokumentRepository.existsByAnfrageIdAndVorgaengerIsNullAndTyp(anfrageId, AusgangsGeschaeftsDokumentTyp.ANGEBOT));
            if (!hatAngebotBasis) {
                throw new IllegalStateException(
                        "Ein Nachtragsangebot benötigt ein bestehendes Angebot als Basisdokument.");
            }
            return;
        }

        // Alle übrigen Typen (Angebot, AB, Rechnung, …) sind nur als allererstes
        // Basisdokument erlaubt. Sobald irgendein Basisdokument existiert, ist das
        // einzige zulässige weitere Wurzel-Dokument ein Nachtragsangebot (oben).
        boolean hatBasis = (projektId != null && dokumentRepository.existsByProjektIdAndVorgaengerIsNull(projektId))
                || (anfrageId != null && dokumentRepository.existsByAnfrageIdAndVorgaengerIsNull(anfrageId));
        if (hatBasis) {
            if (typ == AusgangsGeschaeftsDokumentTyp.ANGEBOT) {
                throw new IllegalStateException(
                        "Es existiert bereits ein Basisdokument. Weitere Basisdokumente sind nur als Nachtragsangebot möglich.");
            }
            throw new IllegalStateException("Es existiert bereits ein Basisdokument.");
        }
    }

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
            case NACHTRAGSANGEBOT -> "NA";
            case AUFTRAGSBESTAETIGUNG -> "AB";
            case RECHNUNG -> "RE";
            case TEILRECHNUNG -> "TR";
            case ABSCHLAGSRECHNUNG -> "AR";
            case SCHLUSSRECHNUNG -> "SR";
            case GUTSCHRIFT -> "GU";
            case STORNO -> "ST";
            // Mahn-Typen werden nie hier persistiert (sie sind virtuelle DTO-Werte
            // und kommen aus ProjektGeschaeftsdokument), benoetigen aber einen
            // case-Eintrag damit der exhaustive switch kompiliert.
            case ZAHLUNGSERINNERUNG, ERSTE_MAHNUNG, ZWEITE_MAHNUNG ->
                    throw new IllegalStateException(
                            "Mahn-Typen werden nicht im AusgangsGeschaeftsDokument-Nummernkreis vergeben: " + typ);
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
        dto.setDigitalAngenommen(dokument.isDigitalAngenommen());
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
                dto.setRechnungsadresseOverride(dokument.getRechnungsadresseOverride());
            } else {
                dto.setRechnungsadresse(buildRechnungsadresse(dokument.getKunde()));
            }
        } else if (dokument.getRechnungsadresseOverride() != null && !dokument.getRechnungsadresseOverride().isBlank()) {
            dto.setRechnungsadresse(dokument.getRechnungsadresseOverride());
            dto.setRechnungsadresseOverride(dokument.getRechnungsadresseOverride());
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
     * Berechnet den aktuellen Brutto-Preis eines Projekts/Anfrages aus seinen
     * aktiven (nicht stornierten) Dokumenten.
     *
     * <p>Maßgeblich ist immer das <strong>Childobjekt</strong> eines Vorgangs:
     * Wird aus einem Angebot eine Auftragsbestätigung erstellt, ersetzt deren
     * (ggf. nachträglich geänderter) Preis das Angebot. Pro Vorgang zählt also
     * die AB, sofern vorhanden, sonst das Basisdokument (Angebot/Nachtragsangebot).</p>
     *
     * <p>Mehrere parallele Vorgänge – das ursprüngliche Angebot plus jedes
     * Nachtragsangebot – werden <strong>aufsummiert</strong>. So bleibt der Preis
     * aktuell, wenn sich AB-Preise ändern oder Nachtragsangebote hinzukommen.</p>
     */
    private BigDecimal berechneAktuellenBruttoPreis(List<AusgangsGeschaeftsDokument> aktive) {
        // Basisdokumente, die bereits durch eine Auftragsbestätigung ersetzt wurden,
        // dürfen nicht zusätzlich gezählt werden – das Childobjekt (die AB) ist maßgeblich.
        java.util.Set<Long> ersetzteBasisIds = aktive.stream()
                .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG)
                .map(AusgangsGeschaeftsDokument::getVorgaenger)
                .filter(java.util.Objects::nonNull)
                .map(AusgangsGeschaeftsDokument::getId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        // Childobjekte: alle Auftragsbestätigungen (ihr Preis ist immer aktuell)
        BigDecimal summeAB = aktive.stream()
                .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG)
                .map(d -> d.getBetragBrutto() != null ? d.getBetragBrutto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Noch nicht in eine AB überführte Basisdokumente (Angebot + Nachtragsangebote)
        BigDecimal summeOffeneBasis = aktive.stream()
                .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.ANGEBOT
                        || d.getTyp() == AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT)
                .filter(d -> !ersetzteBasisIds.contains(d.getId()))
                .map(d -> d.getBetragBrutto() != null ? d.getBetragBrutto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return summeAB.add(summeOffeneBasis);
    }

    /**
     * Berechnet bruttoPreis und bezahlt-Status eines Projekts on-the-fly
     * aus den AusgangsGeschaeftsDokumenten.
     *
     * Logik:
     * - Der Brutto-Preis ergibt sich aus {@link #berechneAktuellenBruttoPreis}
     *   (Childobjekt zählt, Nachtragsangebote addieren sich).
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

        // Aktuellen Brutto-Preis aus den Vorgängen berechnen (Childobjekt zählt,
        // Nachtragsangebote addieren sich).
        BigDecimal neuerBruttoPreis = berechneAktuellenBruttoPreis(aktive);

        BigDecimal summeRechnungen = aktive.stream()
                .filter(d -> RECHNUNGSTYPEN.contains(d.getTyp()))
                .map(d -> d.getBetragBrutto() != null ? d.getBetragBrutto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Stornierte Rechnungen sind bereits über !isStorniert() ausgeschlossen,
        // daher werden Storno-Dokumente (negative Beträge) NICHT mehr abgezogen,
        // um doppelte Berücksichtigung zu vermeiden.

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
     * Logik analog zu Projekt: Der Betrag ergibt sich aus
     * {@link #berechneAktuellenBruttoPreis} (Childobjekt zählt, Nachtragsangebote
     * addieren sich) und wird immer aktuell gehalten, sobald Dokumente einen
     * Preis liefern.
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

        // Aktuellen Betrag (Brutto) aus den Vorgängen berechnen.
        BigDecimal neuerBetrag = berechneAktuellenBruttoPreis(aktive);

        // Betrag immer aktuell halten, sobald Dokumente einen Preis liefern.
        // Ohne Dokumente (neuerBetrag == 0) bleibt ein manuell erfasster Betrag erhalten.
        if (neuerBetrag.compareTo(BigDecimal.ZERO) > 0) {
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
     * Liefert für eine Anfrage einen Vorschlag der Produktkategorien (inkl. aggregierter Mengen),
     * die sich aus den Leistungen der zugehörigen Auftragsbestätigungen bzw. – falls keine AB
     * existiert – aus dem Angebot ergeben.
     *
     * Wird beim Anlegen eines Projekts aus einer Anfrage als Vorbelegung verwendet.
     * Es werden ausschließlich Leaf-Kategorien zurückgegeben.
     */
    @Transactional(readOnly = true)
    public List<KategorieVorschlagDto> berechneKategorieVorschlagFuerAnfrage(Long anfrageId) {
        if (anfrageId == null) return List.of();

        List<AusgangsGeschaeftsDokument> aktive = dokumentRepository.findByAnfrageIdOrderByDatumDesc(anfrageId).stream()
                .filter(d -> !d.isStorniert())
                .filter(d -> KATEGORIE_RELEVANTE_TYPEN.contains(d.getTyp()))
                .toList();

        // ABs haben Priorität – wenn vorhanden, nur diese verwenden
        List<AusgangsGeschaeftsDokument> abs = aktive.stream()
                .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG)
                .toList();

        List<AusgangsGeschaeftsDokument> effektive = abs.isEmpty()
                ? aktive.stream()
                    .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.ANGEBOT)
                    .toList()
                : abs;

        if (effektive.isEmpty()) return List.of();

        String quelle = abs.isEmpty()
                ? AusgangsGeschaeftsDokumentTyp.ANGEBOT.name()
                : AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG.name();

        Map<Long, BigDecimal> leistungMengen = new java.util.HashMap<>();
        for (AusgangsGeschaeftsDokument dok : effektive) {
            extractLeistungMengenFromPositionenJson(dok.getPositionenJson(), leistungMengen);
        }
        if (leistungMengen.isEmpty()) return List.of();

        // Leistung → aggregierte Menge ihrer Kategorie
        Map<Long, BigDecimal> kategorieMengen = new java.util.HashMap<>();
        List<Leistung> leistungen = leistungRepository.findAllById(leistungMengen.keySet());
        for (Leistung l : leistungen) {
            if (l.getKategorie() != null) {
                kategorieMengen.merge(l.getKategorie().getId(),
                        leistungMengen.getOrDefault(l.getId(), BigDecimal.ZERO),
                        BigDecimal::add);
            }
        }
        if (kategorieMengen.isEmpty()) return List.of();

        List<Produktkategorie> kategorien = produktkategorieRepository.findAllById(kategorieMengen.keySet());
        return kategorien.stream()
                // Nur Leaf-Kategorien zurückgeben (gefordert vom Nutzer)
                .filter(k -> k.getUnterkategorien() == null || k.getUnterkategorien().isEmpty())
                .map(k -> {
                    KategorieVorschlagDto dto = new KategorieVorschlagDto();
                    dto.setKategorieId(k.getId());
                    dto.setBezeichnung(k.getBezeichnung());
                    dto.setPfad(bauePfad(k));
                    dto.setVerrechnungseinheit(k.getVerrechnungseinheit());
                    dto.setMenge(kategorieMengen.getOrDefault(k.getId(), BigDecimal.ZERO));
                    dto.setQuelle(quelle);
                    return dto;
                })
                .sorted((a, b) -> a.getPfad().compareToIgnoreCase(b.getPfad()))
                .toList();
    }

    private String bauePfad(Produktkategorie kategorie) {
        java.util.Deque<String> namen = new java.util.ArrayDeque<>();
        Produktkategorie current = kategorie;
        while (current != null) {
            namen.addFirst(current.getBezeichnung());
            current = current.getUebergeordneteKategorie();
        }
        return String.join(" > ", namen);
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

    // === Digitale Freigabe: Alternativ-Positionen ===========================
    // Helfer für die kundenseitige Freigabe-Ansicht und das Mitbeauftragen optionaler
    // (alternativer) Positionen. Die Traversierung (Top-Level + eine Ebene
    // SECTION_HEADER-children) spiegelt bewusst die Frontend-Logik in
    // document-editor/helpers.ts (calculateNetto), damit serverseitig berechnete Beträge
    // mit der Editor-Anzeige übereinstimmen.

    /** Thread-safe und wiederverwendbar — einmalig statt pro Aufruf neu instanziieren. */
    private static final ObjectMapper ALTERNATIV_MAPPER = new ObjectMapper();

    /**
     * Baut die kundensichere Positions-Liste für die Freigabe-Ansicht aus dem
     * positionenJson. Liefert {@code null}, wenn kein/ungültiges JSON vorliegt.
     */
    public List<FreigabePositionDto> baueKundenPositionen(String positionenJson) {
        JsonNode blocks = leseBlocks(positionenJson);
        if (blocks == null) return null;
        List<FreigabePositionDto> result = new ArrayList<>();
        for (JsonNode block : blocks) {
            result.add(mapBlockZuPositionDto(block));
        }
        return result;
    }

    private FreigabePositionDto mapBlockZuPositionDto(JsonNode block) {
        String typ = textOrNull(block, "type");
        FreigabePositionDto.FreigabePositionDtoBuilder b = FreigabePositionDto.builder()
                .blockId(textOrNull(block, "id"))
                .typ(typ)
                .pos(textOrNull(block, "pos"))
                .bezeichnung(textOrNull(block, "title"))
                .beschreibungHtml(textOrNull(block, "description"))
                .sectionLabel(textOrNull(block, "sectionLabel"))
                .optional(block.path("optional").asBoolean(false));

        if ("SERVICE".equals(typ)) {
            BigDecimal menge = decimalOrNull(block, "quantity");
            BigDecimal preis = decimalOrNull(block, "price");
            BigDecimal rabatt = decimalOrNull(block, "discount");
            b.menge(menge)
             .einheit(textOrNull(block, "unit"))
             .einzelpreisNetto(preis)
             .rabattProzent(rabatt)
             .gesamtpreisNetto(zeilenSummeNetto(menge, preis, rabatt));
        }

        if ("SECTION_HEADER".equals(typ) && block.has("children") && block.get("children").isArray()) {
            List<FreigabePositionDto> children = new ArrayList<>();
            for (JsonNode child : block.get("children")) {
                children.add(mapBlockZuPositionDto(child));
            }
            b.children(children);
        }
        return b.build();
    }

    /**
     * Sammelt die blockIds aller optionalen (alternativen) SERVICE-Positionen.
     * Dient der serverseitigen Validierung der vom Kunden gewählten Alternativen.
     */
    public Set<String> sammleOptionaleAlternativIds(String positionenJson) {
        Set<String> ids = new HashSet<>();
        JsonNode blocks = leseBlocks(positionenJson);
        if (blocks == null) return ids;
        for (JsonNode block : blocks) {
            sammleOptionaleAlternativIds(block, ids);
        }
        return ids;
    }

    private void sammleOptionaleAlternativIds(JsonNode block, Set<String> ids) {
        String typ = textOrNull(block, "type");
        if ("SERVICE".equals(typ) && block.path("optional").asBoolean(false)) {
            String id = textOrNull(block, "id");
            if (id != null) ids.add(id);
        }
        if ("SECTION_HEADER".equals(typ) && block.has("children") && block.get("children").isArray()) {
            for (JsonNode child : block.get("children")) {
                sammleOptionaleAlternativIds(child, ids);
            }
        }
    }

    /**
     * Summiert die Netto-Zeilensummen der ausgewählten optionalen Positionen (nur SERVICE-
     * Blöcke, die tatsächlich optional sind und deren id ausgewählt wurde). Defensive
     * Filterung: nicht-optionale oder unbekannte IDs werden ignoriert.
     */
    public BigDecimal summeAusgewaehlterAlternativenNetto(String positionenJson, Set<String> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) return BigDecimal.ZERO;
        JsonNode blocks = leseBlocks(positionenJson);
        if (blocks == null) return BigDecimal.ZERO;
        BigDecimal summe = BigDecimal.ZERO;
        for (JsonNode block : blocks) {
            summe = summe.add(summeAusgewaehlt(block, blockIds));
        }
        return summe.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal summeAusgewaehlt(JsonNode block, Set<String> blockIds) {
        BigDecimal summe = BigDecimal.ZERO;
        String typ = textOrNull(block, "type");
        if ("SERVICE".equals(typ) && block.path("optional").asBoolean(false)) {
            String id = textOrNull(block, "id");
            if (id != null && blockIds.contains(id)) {
                summe = summe.add(nonNull(zeilenSummeNetto(
                        decimalOrNull(block, "quantity"),
                        decimalOrNull(block, "price"),
                        decimalOrNull(block, "discount"))));
            }
        }
        if ("SECTION_HEADER".equals(typ) && block.has("children") && block.get("children").isArray()) {
            for (JsonNode child : block.get("children")) {
                summe = summe.add(summeAusgewaehlt(child, blockIds));
            }
        }
        return summe;
    }

    /**
     * Setzt {@code optional=false} auf allen SERVICE-Blöcken, deren id ausgewählt wurde,
     * sodass sie in einem Folgedokument (Auftragsbestätigung) als feste Positionen zählen.
     * Liefert das modifizierte JSON; bei Fehlern unverändert das Eingangs-JSON.
     */
    public String markiereAlternativenAlsBeauftragt(String positionenJson, Set<String> blockIds) {
        if (positionenJson == null || positionenJson.isBlank() || blockIds == null || blockIds.isEmpty()) {
            return positionenJson;
        }
        try {
            JsonNode root = ALTERNATIV_MAPPER.readTree(positionenJson);
            JsonNode blocks = root.isArray() ? root
                    : (root.isObject() && root.has("blocks") && root.get("blocks").isArray() ? root.get("blocks") : null);
            if (blocks == null) return positionenJson;
            for (JsonNode block : blocks) {
                markiereBlock(block, blockIds);
            }
            return ALTERNATIV_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Fehler beim Markieren beauftragter Alternativen: {}", e.getMessage());
            return positionenJson;
        }
    }

    private void markiereBlock(JsonNode block, Set<String> blockIds) {
        String typ = textOrNull(block, "type");
        if ("SERVICE".equals(typ) && block.path("optional").asBoolean(false)
                && block instanceof ObjectNode on) {
            String id = textOrNull(block, "id");
            if (id != null && blockIds.contains(id)) {
                on.put("optional", false);
            }
        }
        if ("SECTION_HEADER".equals(typ) && block.has("children") && block.get("children").isArray()) {
            for (JsonNode child : block.get("children")) {
                markiereBlock(child, blockIds);
            }
        }
    }

    /**
     * Entfernt die Standard-Textbausteine (VOR/NACH) aus einem positionenJson — öffentliche
     * Variante für die Auftragsbestätigungs-Erzeugung mit Alternativen (gleiche Logik wie
     * beim Typwechsel in {@link #erstellen}).
     */
    public String bereitePositionenFuerTypwechsel(String positionenJson) {
        return entferneStandardTextbausteine(positionenJson);
    }

    // --- kleine JSON-Helfer für die Alternativ-Logik ---

    private JsonNode leseBlocks(String positionenJson) {
        if (positionenJson == null || positionenJson.isBlank()) return null;
        try {
            JsonNode root = ALTERNATIV_MAPPER.readTree(positionenJson);
            if (root.isArray()) return root;
            if (root.isObject() && root.has("blocks") && root.get("blocks").isArray()) return root.get("blocks");
            return null;
        } catch (Exception e) {
            log.warn("positionenJson nicht lesbar: {}", e.getMessage());
            return null;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || (!v.isNumber() && !v.isTextual())) return null;
        try {
            return new BigDecimal(v.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal zeilenSummeNetto(BigDecimal menge, BigDecimal preis, BigDecimal rabattProzent) {
        if (menge == null || preis == null) return null;
        BigDecimal base = menge.multiply(preis);
        if (rabattProzent != null && rabattProzent.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal faktor = BigDecimal.ONE.subtract(rabattProzent.divide(new BigDecimal("100")));
            base = base.multiply(faktor);
        }
        return base.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
