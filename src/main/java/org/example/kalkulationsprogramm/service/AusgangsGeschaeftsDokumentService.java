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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentCounter;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
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
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.StornoHinweisDto;
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabePositionDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieVorschlagDto;
import org.example.kalkulationsprogramm.util.RabattRechner;
import org.example.kalkulationsprogramm.util.LeistungsPositionen;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AusgangsGeschaeftsDokumentService {

    /** Belegarten, die auch bei aus Datenschutzgründen gesperrten Kunden angelegt werden dürfen. */
    private static final java.util.Set<AusgangsGeschaeftsDokumentTyp> ERLAUBT_BEI_GESPERRTEM_KUNDEN =
            java.util.EnumSet.of(AusgangsGeschaeftsDokumentTyp.STORNO, AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT,
                    AusgangsGeschaeftsDokumentTyp.ZAHLUNGSERINNERUNG, AusgangsGeschaeftsDokumentTyp.ERSTE_MAHNUNG,
                    AusgangsGeschaeftsDokumentTyp.ZWEITE_MAHNUNG);

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

    /**
     * Mapper fuer positionenJson-Auswertungen. Statisch, weil der Korrekturlauf ueber
     * den Gesamtbestand sonst pro Dokument mehrere Mapper-Instanzen erzeugt.
     */
    private static final ObjectMapper POSITIONEN_MAPPER = new ObjectMapper();

    /** Rechnungstypen, die im Abrechnungsverlauf berücksichtigt werden */
    private static final Set<AusgangsGeschaeftsDokumentTyp> RECHNUNGSTYPEN = EnumSet.of(
            AusgangsGeschaeftsDokumentTyp.RECHNUNG,
            AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG
    );

    /** Rechnungstypen, die einen Vorgang abschliessen: danach kommt keine Rechnung mehr. */
    private static final Set<AusgangsGeschaeftsDokumentTyp> ABSCHLIESSENDE_RECHNUNGSTYPEN = EnumSet.of(
            AusgangsGeschaeftsDokumentTyp.RECHNUNG,
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
        return erstellen(dto, null, null);
    }

    /**
     * Wie {@link #erstellen(AusgangsGeschaeftsDokumentErstellenDto)}, setzt aber den
     * Ersteller und schreibt für buchhaltungsrelevante Typen einen Audit-Eintrag mit
     * Ersteller und Aufrufer-IP.
     *
     * @param erstelltVonId ID des angemeldeten FrontendUserProfile (aus der Anmeldung,
     *                      nicht vom Client); {@code null} = "system"
     */
    @Transactional
    public AusgangsGeschaeftsDokument erstellen(AusgangsGeschaeftsDokumentErstellenDto dto,
            Long erstelltVonId, String ipAdresse) {
        // Basisdokument-Regeln (Dokumente ohne Vorgänger = eigene Wurzel-Vorgänge):
        //   - Das ERSTE Basisdokument ist das ANGEBOT (max. eines pro Projekt/Anfrage).
        //   - Jedes WEITERE Basisdokument muss ein NACHTRAGSANGEBOT sein und setzt
        //     ein bereits existierendes Angebot voraus. So entstehen mehrere
        //     parallele Vorgänge (Angebot + n Nachtragsangebote), jeder mit
        //     eigener Folgekette aus AB und Rechnungen.
        //   - Andere Typen (z.B. eine eigenständige Rechnung ohne vorheriges
        //     Angebot) bleiben als einzelnes Basisdokument möglich, solange noch
        //     gar kein Basisdokument existiert.
        if (dto.getTyp() == AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT && dto.getVorgaengerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Eine Gutschrift gehört immer zu einer Rechnung. Bitte die Gutschrift über die Rechnung anlegen.");
        }
        // Ein Betrag ohne Positionen ergäbe ein PDF mit der ganzen Rechnung, gebucht würde aber
        // nur der Teilbetrag (GoBD, § 14 UStG). Der Betrag kommt deshalb immer aus den Positionen.
        if (dto.getTyp() == AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT && dto.getBetragNetto() != null
                && dto.getPositionenJson() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Für einen Teilbetrag bitte die Gutschrift mit eigenen Positionen anlegen.");
        }
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
            if (vorgaenger == null && dto.getTyp() == AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Die Rechnung wurde nicht gefunden.");
            }
            dokument.setVorgaenger(vorgaenger);

            // Bei Umwandlung: Inhalte vom Vorgänger übernehmen wenn nicht explizit gesetzt
            if (vorgaenger != null) {
                // Eine Teil-Gutschrift bringt eigene Positionen mit; der Text der Rechnung passte
                // dann nicht dazu (der Editor erzeugt ihn beim Speichern aus den Positionen).
                boolean eigeneGutschriftPositionen = dto.getTyp() == AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT
                        && dto.getPositionenJson() != null;
                if (dto.getHtmlInhalt() == null && vorgaenger.getHtmlInhalt() != null && !eigeneGutschriftPositionen) {
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

                if (dto.getTyp() == AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG) {
                    validiereEinzigeAuftragsbestaetigung(vorgaenger);
                }

                if (dto.getTyp() == AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT) {
                    bereiteGutschriftVor(dokument, vorgaenger, dto.getPositionenJson());
                }

                if (RECHNUNGSTYPEN.contains(dto.getTyp())) {
                    // Rechnungen gehören an das unterste Dokument des Vorgangs
                    validiereAbrechnungsbasis(vorgaenger);
                    validiereVorgangNichtAbgeschlossen(vorgaenger);

                    // Rechnungsbetrag darf Restbetrag nicht übersteigen
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
        // Die Gutschrift trägt die Anschrift der Rechnung, die sie berichtigt (bereiteGutschriftVor).
        if (dokument.getTyp() != AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT
                && (dokument.getRechnungsadresseOverride() == null || dokument.getRechnungsadresseOverride().isBlank())) {
            String geerbt = findGeerbteRechnungsadresse(
                    dokument.getProjekt() != null ? dokument.getProjekt().getId() : null,
                    dokument.getAnfrage() != null ? dokument.getAnfrage().getId() : null);
            if (geerbt != null) {
                dokument.setRechnungsadresseOverride(geerbt);
            }
        }

        // Gesperrter Kunde (Datenschutz): keine neuen Angebote/Rechnungen. Erlaubt bleibt, was sich
        // auf aufbewahrte Belege bezieht: Storno und Gutschrift berichtigen sie, Mahnungen setzen
        // offene Forderungen durch (Art. 18 Abs. 2 DSGVO).
        if (!ERLAUBT_BEI_GESPERRTEM_KUNDEN.contains(dokument.getTyp())) {
            KundeDatenschutzService.pruefeNichtGesperrt(dokument.getKunde());
        }
        KundeDatenschutzService.pruefeNichtAnonymisiert(dokument.getKunde());

        // Zahlungsziel aus Kunde übernehmen falls nicht explizit gesetzt
        if (dokument.getZahlungszielTage() == null && dokument.getKunde() != null
                && dokument.getKunde().getZahlungsziel() != null) {
            dokument.setZahlungszielTage(dokument.getKunde().getZahlungsziel());
        }

        // Ersteller setzen
        if (erstelltVonId != null) {
            frontendUserProfileRepository.findById(erstelltVonId)
                    .ifPresent(dokument::setErstelltVon);
        }

        // Dokumentnummer generieren
        dokument.setDokumentNummer(generiereNummer(dokument.getTyp()));

        normalisiereStornobetraege(dokument);
        AusgangsGeschaeftsDokument saved = dokumentRepository.save(dokument);
        verlaengereAufbewahrungBeiGesperrtemKunden(saved);

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

    /** Storno, Gutschrift oder Mahnung für einen gesperrten Kunden verlängern dessen Aufbewahrungsfrist. */
    private void verlaengereAufbewahrungBeiGesperrtemKunden(AusgangsGeschaeftsDokument dokument) {
        if (KundeDatenschutzService.verlaengereAufbewahrung(dokument.getKunde(), dokument)) {
            kundeRepository.save(dokument.getKunde());
        }
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
     * Storno und Gutschrift mindern den Umsatz: Netto und Brutto stehen immer negativ, egal
     * mit welchem Vorzeichen Editor oder Aufrufer sie schicken (Positionen bleiben positiv).
     */
    private void normalisiereStornobetraege(AusgangsGeschaeftsDokument dokument) {
        if (dokument.getTyp() == AusgangsGeschaeftsDokumentTyp.STORNO
                || dokument.getTyp() == AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT) {
            dokument.setBetragNetto(DokumentBetragsvorzeichen.alsMinderung(dokument.getBetragNetto()));
            dokument.setBetragBrutto(DokumentBetragsvorzeichen.alsMinderung(dokument.getBetragBrutto()));
        }
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

        // Gutschrift: Steuersatz und Anschrift bleiben die der Rechnung, der Betrag folgt den
        // Positionen – so passen PDF, E-Rechnung und Buchung immer zusammen.
        if (dokument.getTyp() == AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT && dokument.getVorgaenger() != null) {
            dokumentRepository.sperreZumGutschreiben(dokument.getVorgaenger().getId());
            uebernehmeRechnungsbezug(dokument, dokument.getVorgaenger());
            BigDecimal ausPositionen = dokument.getPositionenJson() != null
                    ? berechneNettoAusPositionenJson(dokument.getPositionenJson())
                    : null;
            if (ausPositionen != null) {
                dokument.setBetragNetto(ausPositionen.abs());
            }
        }

        // Bruttobetrag neu berechnen
        if (dokument.getBetragNetto() != null && dokument.getMwstSatz() != null) {
            BigDecimal mwst = dokument.getBetragNetto().multiply(dokument.getMwstSatz());
            dokument.setBetragBrutto(dokument.getBetragNetto().add(mwst).setScale(2, RoundingMode.HALF_UP));
        }

        normalisiereStornobetraege(dokument);
        if (dokument.getTyp() == AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT && dokument.getVorgaenger() != null
                && dokument.getBetragNetto() != null) {
            pruefeGutschriftHoechstbetrag(dokument.getVorgaenger(), dokument.getBetragNetto().abs(), dokument.getId());
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

        var bearbeiter = bearbeiterId != null
                ? frontendUserProfileRepository.findById(bearbeiterId).orElse(null)
                : null;
        uebernehmeAktuellenSchlussrechnungsbetrag(dokument, bearbeiter, ipAdresse);
        pruefeGutschriftVorDemBuchen(dokument);

        dokument.setGebucht(true);
        dokument.setGebuchtAm(LocalDate.now());

        AusgangsGeschaeftsDokument saved = dokumentRepository.save(dokument);
        erstelleOffenenPostenEintrag(saved);
        rechneGutschriftAufOffenenPosten(saved, false);

        if (AUDIT_RELEVANTE_TYPEN.contains(saved.getTyp())) {
            auditService.protokolliereBuchung(saved, bearbeiter, ipAdresse);
        }
        return saved;
    }

    /**
     * Vor dem Festschreiben einer Schlussrechnung: Editor, PDF und Listen zeigen ihren Betrag
     * frisch abgeleitet an ({@link #abgeleiteterSchlussrechnungsNetto}), gespeichert kann aber
     * noch ein älterer stehen – etwa wenn nach dem letzten Speichern eine Abschlagsrechnung
     * storniert wurde. Gebucht wird der Betrag, den Editor und PDF in diesem Moment zeigen; die
     * Anpassung steht mit altem und neuem Betrag im Prüfprotokoll. Scheitert die Berechnung
     * (z. B. weil das Basisdokument fehlt), wird nicht gebucht – sonst stünde still der alte
     * Betrag fest. Sind nur die Positionen unlesbar, zeigen Anzeige und Buchung beide den
     * gespeicherten Betrag.
     */
    private void uebernehmeAktuellenSchlussrechnungsbetrag(AusgangsGeschaeftsDokument dokument,
                                                           FrontendUserProfile bearbeiter, String ipAdresse) {
        BigDecimal abgeleitet;
        try {
            abgeleitet = berechneSchlussrechnungsNetto(dokument);
        } catch (RuntimeException e) {
            log.warn("Schlussrechnung {}: Betrag vor dem Buchen nicht berechenbar",
                    dokument.getDokumentNummer(), e);
            throw new RuntimeException("Der Betrag der Schlussrechnung " + dokument.getDokumentNummer()
                    + " konnte vor dem Buchen nicht geprüft werden. Sie wurde nicht gebucht."
                    + " Bitte wenden Sie sich an Ihren Administrator.", e);
        }
        if (abgeleitet == null) {
            return;
        }
        BigDecimal neuNetto = abgeleitet.setScale(2, RoundingMode.HALF_UP);
        BigDecimal altNetto = dokument.getBetragNetto();
        if (altNetto != null && altNetto.compareTo(neuNetto) == 0) {
            return;
        }

        dokument.setBetragNetto(neuNetto);
        dokument.setBetragBrutto(bruttoAusNetto(neuNetto, dokument.getMwstSatz()));
        dokumentRepository.save(dokument);
        String alterBetrag = altNetto != null ? altNetto.toPlainString() : "(kein Betrag gespeichert)";
        log.info("Schlussrechnung {}: Betrag vor dem Buchen angepasst, netto {} -> {}",
                dokument.getDokumentNummer(), alterBetrag, neuNetto);
        auditService.protokolliereAenderung(dokument, bearbeiter,
                "Betrag vor dem Buchen an die übrigen Rechnungen angepasst: Nettobetrag "
                        + alterBetrag + " -> " + neuNetto
                        + " (z. B. weil eine Abschlags- oder Teilrechnung inzwischen storniert ist)",
                ipAdresse);
        if (dokument.getProjekt() != null) {
            aktualisiereProjektPreisAusDokumenten(dokument.getProjekt().getId());
        }
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

        // Anfragen und ABs werden nicht gebucht – nur Versanddatum setzen
        boolean istNichtBuchbar = NICHT_BUCHBARE_TYPEN.contains(dokument.getTyp());
        boolean warBereitsGebucht = dokument.isGebucht();
        var bearbeiter = bearbeiterId != null
                ? frontendUserProfileRepository.findById(bearbeiterId).orElse(null)
                : null;
        if (!warBereitsGebucht && !istNichtBuchbar) {
            uebernehmeAktuellenSchlussrechnungsbetrag(dokument, bearbeiter, ipAdresse);
            pruefeGutschriftVorDemBuchen(dokument);
        }

        dokument.setVersandDatum(LocalDate.now());
        if (!warBereitsGebucht && !istNichtBuchbar) {
            dokument.setGebucht(true);
            dokument.setGebuchtAm(LocalDate.now());
        }

        AusgangsGeschaeftsDokument saved = dokumentRepository.save(dokument);
        if (!warBereitsGebucht && !istNichtBuchbar) {
            erstelleOffenenPostenEintrag(saved);
            rechneGutschriftAufOffenenPosten(saved, false);
        }

        // Versanddatum auch auf dem Offene-Posten-Eintrag setzen
        aktualisiereOffenenPostenVersandDatum(saved);

        // Fälligkeitsdatum nachträglich setzen falls es fehlt (z.B. bei bereits gebuchten Dokumenten)
        aktualisiereOffenenPostenFaelligkeitsdatum(saved);

        if (AUDIT_RELEVANTE_TYPEN.contains(saved.getTyp())) {
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
        return stornierenMitHinweisen(id, bearbeiterId, ipAdresse, grund).storno();
    }

    /**
     * Ergebnis einer Stornierung: die Stornorechnung und die Hinweise, die der Nutzer
     * über Folgen für andere Rechnungen bekommen soll.
     */
    public record StornoErgebnis(AusgangsGeschaeftsDokument storno, List<StornoHinweisDto> hinweise) {
    }

    /**
     * Wie {@link #stornieren(Long, Long, String, String)}, liefert aber zusätzlich Hinweise
     * zu Schlussrechnungen, die der Storno einer Abschlagsrechnung betrifft: eine gebuchte
     * Schlussrechnung wird mitstorniert, ein Entwurf bleibt stehen und muss geprüft werden.
     */
    @Transactional
    public StornoErgebnis stornierenMitHinweisen(Long id, Long bearbeiterId, String ipAdresse, String grund) {
        AusgangsGeschaeftsDokument original = dokumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dokument nicht gefunden: " + id));
        // Altbelege tragen den Kunden teils nur über Projekt bzw. Anfrage.
        KundeDatenschutzService.pruefeNichtAnonymisiert(original.getKunde() != null ? original.getKunde()
                : original.getProjekt() != null ? original.getProjekt().getKundenId()
                : original.getAnfrage() != null ? original.getAnfrage().getKunde() : null);

        if (original.isStorniert()) {
            throw new RuntimeException("Dokument ist bereits storniert.");
        }

        // Nur Rechnungstypen und Gutschriften dürfen storniert werden. Eine Stornorechnung
        // selbst nicht: ihr Storno würde den Umsatz ein zweites Mal mindern.
        boolean istGutschrift = original.getTyp() == AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT;
        if (!RECHNUNGSTYPEN.contains(original.getTyp()) && !istGutschrift) {
            throw new RuntimeException("Nur Rechnungen und Gutschriften können storniert werden.");
        }

        // Ein Entwurf ist noch nicht im Geschäftsverkehr: löschen statt stornieren. Sonst
        // entstünde eine gebuchte Minderung zu einer Rechnung, die nie gebucht war.
        if (!original.isGebucht()) {
            throw new RuntimeException(istGutschrift
                    ? "Nur gebuchte Gutschriften können storniert werden. Einen Entwurf bitte löschen."
                    : "Nur gebuchte Rechnungen können storniert werden. Einen Entwurf bitte löschen.");
        }

        // Hängt an der Rechnung noch eine Gutschrift, minderte das Storno den Betrag doppelt.
        if (!istGutschrift) {
            dokumentRepository.sperreZumGutschreiben(original.getId());
            pruefeKeineOffeneGutschrift(original);
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
            case GUTSCHRIFT -> "Gutschrift";
            default -> "Dokument";
        };
        storno.setBetreff("Stornorechnung " + storno.getDokumentNummer()
                + " (zu " + originalTypLabel + " " + original.getDokumentNummer() + ")");

        storno.setVorgaenger(original);
        storno.setProjekt(original.getProjekt());
        storno.setAnfrage(original.getAnfrage());
        storno.setKunde(original.getKunde());
        storno.setRechnungsadresseOverride(original.getRechnungsadresseOverride());

        // Stornos mindern den Umsatz unabhängig vom Vorzeichen des Originals. Das Storno einer
        // Gutschrift hebt deren Minderung auf und steht deshalb positiv.
        if (istGutschrift) {
            storno.setBetragNetto(original.getBetragNetto() != null ? original.getBetragNetto().abs() : null);
            storno.setBetragBrutto(original.getBetragBrutto() != null ? original.getBetragBrutto().abs() : null);
        } else {
            storno.setBetragNetto(DokumentBetragsvorzeichen.alsMinderung(original.getBetragNetto()));
            storno.setBetragBrutto(DokumentBetragsvorzeichen.alsMinderung(original.getBetragBrutto()));
        }
        storno.setMwstSatz(original.getMwstSatz());

        // Inhalt und Positionen vom Original übernehmen für PDF-Generierung
        storno.setHtmlInhalt(original.getHtmlInhalt());
        storno.setPositionenJson(original.getPositionenJson());

        storno.setGebucht(true);
        storno.setGebuchtAm(LocalDate.now());

        AusgangsGeschaeftsDokument savedStorno = dokumentRepository.save(storno);
        verlaengereAufbewahrungBeiGesperrtemKunden(savedStorno);

        if (istGutschrift) {
            // Die Rechnung ist wieder um den gutgeschriebenen Betrag offen.
            rechneGutschriftAufOffenenPosten(original, true);
        } else {
            // Offenen-Posten-Eintrag des Originals als bezahlt markieren (storniert)
            markiereOffenenPostenAlsBezahlt(original);
        }

        // Kaskadierende Stornierung: Wenn eine Abschlagsrechnung storniert wird,
        // müssen abhängige Schlussrechnungen (gleicher Vorgänger) ebenfalls storniert werden,
        // da deren Betrag auf Basis der Abschlagsrechnungen berechnet wurde.
        // Ein Schlussrechnungs-Entwurf bleibt stehen: Er ist nicht gebucht und lässt sich
        // noch ändern oder löschen.
        List<StornoHinweisDto> hinweise = new ArrayList<>();
        if (original.getTyp() == AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG
                && original.getVorgaenger() != null) {
            List<AusgangsGeschaeftsDokument> geschwister = dokumentRepository
                    .findByVorgaengerIdOrderByErstelltAmAsc(original.getVorgaenger().getId());
            for (AusgangsGeschaeftsDokument geschwisterDok : geschwister) {
                if (geschwisterDok.getTyp() != AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG
                        || geschwisterDok.isStorniert()) {
                    continue;
                }
                if (!geschwisterDok.isGebucht()) {
                    log.info("Schlussrechnungs-Entwurf {} bleibt stehen (Abschlagsrechnung {} storniert) – Beträge bitte prüfen",
                            geschwisterDok.getDokumentNummer(), original.getDokumentNummer());
                    hinweise.add(new StornoHinweisDto(StornoHinweisDto.Art.ENTWURF_PRUEFEN,
                            "Die Schlussrechnung " + geschwisterDok.getDokumentNummer()
                                    + " ist noch ein Entwurf. Die Abschlagsrechnung " + original.getDokumentNummer()
                                    + " wird dort nicht mehr abgezogen. Bitte öffnen Sie die Schlussrechnung"
                                    + " und prüfen Sie die Beträge, bevor Sie sie buchen."));
                    continue;
                }
                log.info("Kaskadierende Stornierung: Schlussrechnung {} wird mitstorniert (Abschlagsrechnung {} storniert)",
                        geschwisterDok.getDokumentNummer(), original.getDokumentNummer());
                AusgangsGeschaeftsDokument schlussStorno = stornieren(geschwisterDok.getId(), bearbeiterId, ipAdresse,
                        "Kaskadierende Stornierung wegen storniertem Vorgänger " + original.getDokumentNummer());
                hinweise.add(new StornoHinweisDto(StornoHinweisDto.Art.MITSTORNIERT,
                        "Die Schlussrechnung " + geschwisterDok.getDokumentNummer()
                                + " wurde mitstorniert (Stornorechnung " + schlussStorno.getDokumentNummer()
                                + "), weil sie die Abschlagsrechnung " + original.getDokumentNummer() + " abgezogen hat."));
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
        // da nur gebuchte Rechnungstypen storniert werden dürfen.
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

        return new StornoErgebnis(savedStorno, List.copyOf(hinweise));
    }

    // ==================== KUNDEN-GUTSCHRIFT (Issue #223) ====================

    /** Cent-Toleranz für Rundungen zwischen Netto, Brutto und Positionen. */
    private static final BigDecimal GUTSCHRIFT_TOLERANZ = new BigDecimal("0.01");

    /**
     * Rahmen für den Dialog „Gutschrift erstellen“: Rechnungsbetrag, schon gutgeschrieben
     * (gebucht und Entwürfe, ohne stornierte) und was höchstens noch geht.
     */
    @Transactional(readOnly = true)
    public GutschriftRahmenDto gutschriftRahmen(Long rechnungId) {
        AusgangsGeschaeftsDokument rechnung = dokumentRepository.findById(rechnungId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Die Rechnung wurde nicht gefunden."));
        pruefeGutschriftBasis(rechnung);
        BigDecimal mwst = rechnung.getMwstSatz() != null ? rechnung.getMwstSatz() : new BigDecimal("0.19");
        BigDecimal rechnungNetto = betragOderNull(rechnung.getBetragNetto());
        BigDecimal rechnungBrutto = rechnung.getBetragBrutto() != null
                ? rechnung.getBetragBrutto().abs()
                : bruttoAusNetto(rechnungNetto, mwst);
        List<AusgangsGeschaeftsDokument> gutschriften = gueltigeGutschriften(rechnung, null);
        BigDecimal bereitsNetto = summe(gutschriften, AusgangsGeschaeftsDokument::getBetragNetto);
        BigDecimal bereitsBrutto = summe(gutschriften, AusgangsGeschaeftsDokument::getBetragBrutto);
        BigDecimal hoechstensNetto = rechnungNetto.subtract(bereitsNetto).max(BigDecimal.ZERO);
        BigDecimal hoechstensBrutto = rechnungBrutto.subtract(bereitsBrutto).max(BigDecimal.ZERO);
        return new GutschriftRahmenDto(rechnung.getId(), rechnung.getDokumentNummer(), rechnungBrutto,
                rechnungNetto, bereitsBrutto, bereitsNetto, hoechstensNetto, hoechstensBrutto, mwst);
    }

    /** Antwort von {@link #gutschriftRahmen(Long)}; alle Beträge positiv. */
    public record GutschriftRahmenDto(Long rechnungId, String rechnungNummer, BigDecimal rechnungBrutto,
            BigDecimal rechnungNetto, BigDecimal bereitsGutgeschriebenBrutto, BigDecimal bereitsGutgeschriebenNetto,
            BigDecimal hoechstensNetto, BigDecimal hoechstensBrutto, BigDecimal mwstSatz) {
    }

    /**
     * Neue Gutschrift zu einer Rechnung. Kunde, Projekt, Anfrage, Rechnungsanschrift und
     * Steuersatz kommen immer von der Rechnung (§ 17 UStG: berichtigt wird zum Satz des
     * ursprünglichen Umsatzes). Der Betrag kommt aus den eigenen Positionen, ohne sie ist es
     * die ganze Rechnung. Das Minus setzt {@link #normalisiereStornobetraege}.
     */
    private void bereiteGutschriftVor(AusgangsGeschaeftsDokument gutschrift, AusgangsGeschaeftsDokument rechnung,
            String eigenePositionenJson) {
        // Zeile sperren: zwei gleichzeitige Gutschriften dürfen den Rahmen nicht gemeinsam sprengen.
        dokumentRepository.sperreZumGutschreiben(rechnung.getId());
        pruefeGutschriftBasis(rechnung);
        uebernehmeRechnungsbezug(gutschrift, rechnung);
        BigDecimal netto = eigenePositionenJson != null
                ? berechneNettoAusPositionenJson(eigenePositionenJson)
                : rechnung.getBetragNetto();
        gutschrift.setBetragNetto(netto != null ? netto.abs() : null);
        if (gutschrift.getBetragNetto() != null) {
            gutschrift.setBetragBrutto(bruttoAusNetto(gutschrift.getBetragNetto().abs(), gutschrift.getMwstSatz()));
            pruefeGutschriftHoechstbetrag(rechnung, gutschrift.getBetragNetto().abs(), null);
        }
        if (gutschrift.getBetreff() == null || gutschrift.getBetreff().isBlank()) {
            gutschrift.setBetreff("Gutschrift zu " + rechnungsbezeichnung(rechnung) + " " + rechnung.getDokumentNummer());
        }
    }

    /** Kunde, Projekt, Anfrage, Anschrift und Steuersatz der Gutschrift = die der Rechnung. */
    private static void uebernehmeRechnungsbezug(AusgangsGeschaeftsDokument gutschrift,
            AusgangsGeschaeftsDokument rechnung) {
        Kunde kunde = rechnung.getKunde() != null ? rechnung.getKunde()
                : rechnung.getProjekt() != null && rechnung.getProjekt().getKundenId() != null
                        ? rechnung.getProjekt().getKundenId()
                        : rechnung.getAnfrage() != null ? rechnung.getAnfrage().getKunde() : gutschrift.getKunde();
        gutschrift.setKunde(kunde);
        gutschrift.setProjekt(rechnung.getProjekt());
        gutschrift.setAnfrage(rechnung.getAnfrage());
        gutschrift.setRechnungsadresseOverride(rechnung.getRechnungsadresseOverride());
        if (rechnung.getMwstSatz() != null) {
            gutschrift.setMwstSatz(rechnung.getMwstSatz());
        }
    }

    /** Eine Gutschrift gibt es nur zu einer gebuchten, nicht stornierten Rechnung. */
    private static void pruefeGutschriftBasis(AusgangsGeschaeftsDokument rechnung) {
        if (rechnung == null || !RECHNUNGSTYPEN.contains(rechnung.getTyp()) || !rechnung.isGebucht()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Eine Gutschrift lässt sich nur zu einer gebuchten Rechnung erstellen.");
        }
        if (rechnung.isStorniert()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Rechnung ist storniert.");
        }
    }

    /** Alle Gutschriften einer Rechnung zusammen dürfen ihren Nettobetrag nicht übersteigen. */
    private void pruefeGutschriftHoechstbetrag(AusgangsGeschaeftsDokument rechnung, BigDecimal nettoDieser,
            Long eigeneId) {
        BigDecimal rechnungNetto = betragOderNull(rechnung.getBetragNetto());
        BigDecimal rest = rechnungNetto.subtract(summe(gueltigeGutschriften(rechnung, eigeneId),
                AusgangsGeschaeftsDokument::getBetragNetto)).max(BigDecimal.ZERO);
        if (nettoDieser.compareTo(rest.add(GUTSCHRIFT_TOLERANZ)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Die Gutschrift ist höher als der noch offene Rechnungsbetrag. Höchstens "
                            + formatEuro(rest) + " netto sind noch möglich.");
        }
    }

    /** Beim Buchen/Versenden: Rechnung noch gültig und Höchstbetrag eingehalten? */
    private void pruefeGutschriftVorDemBuchen(AusgangsGeschaeftsDokument dokument) {
        if (dokument.getTyp() != AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT) {
            return;
        }
        if (dokument.getVorgaenger() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Eine Gutschrift gehört immer zu einer Rechnung. Bitte die Gutschrift über die Rechnung anlegen.");
        }
        dokumentRepository.sperreZumGutschreiben(dokument.getVorgaenger().getId());
        pruefeGutschriftBasis(dokument.getVorgaenger());
        if (dokument.getBetragNetto() != null) {
            pruefeGutschriftHoechstbetrag(dokument.getVorgaenger(), dokument.getBetragNetto().abs(), dokument.getId());
        }
    }

    /** Eine Rechnung mit nicht stornierter Gutschrift lässt sich erst nach deren Storno stornieren. */
    private void pruefeKeineOffeneGutschrift(AusgangsGeschaeftsDokument rechnung) {
        gueltigeGutschriften(rechnung, null).stream().findFirst().ifPresent(g -> {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, g.isGebucht()
                    ? "Zu dieser Rechnung gibt es die Gutschrift " + g.getDokumentNummer()
                            + ". Bitte zuerst die Gutschrift stornieren."
                    : "Zu dieser Rechnung gibt es den Gutschrift-Entwurf " + g.getDokumentNummer()
                            + ". Bitte zuerst den Entwurf löschen.");
        });
    }

    /**
     * Gebuchte Gutschrift mindert den Offenen Posten ihrer Rechnung; ihr Storno hebt das
     * wieder auf. Deckt die Gutschrift den ganzen Betrag, gilt die Rechnung als erledigt.
     */
    private void rechneGutschriftAufOffenenPosten(AusgangsGeschaeftsDokument gutschrift, boolean storniert) {
        if (gutschrift.getTyp() != AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT || gutschrift.getVorgaenger() == null
                || gutschrift.getBetragBrutto() == null || gutschrift.getVorgaenger().getDokumentNummer() == null) {
            return;
        }
        BigDecimal betrag = gutschrift.getBetragBrutto().abs();
        projektDokumentRepository.findGeschaeftsdokumenteByDokumentid(gutschrift.getVorgaenger().getDokumentNummer())
                .stream().findFirst().ifPresent(posten -> {
                    BigDecimal vorher = betragOderNull(posten.getGutgeschrieben());
                    BigDecimal nachher = storniert ? vorher.subtract(betrag).max(BigDecimal.ZERO) : vorher.add(betrag);
                    boolean warErledigt = durchGutschriftErledigt(posten, vorher);
                    boolean istErledigt = durchGutschriftErledigt(posten, nachher);
                    posten.setGutgeschrieben(nachher);
                    if (!storniert && istErledigt && !posten.isBezahlt()) {
                        setzeOffenenPostenBezahlt(posten, true);
                    } else if (storniert && warErledigt && !istErledigt && posten.isBezahlt()) {
                        setzeOffenenPostenBezahlt(posten, false);
                    } else {
                        projektDokumentRepository.save(posten);
                    }
                });
    }

    private static boolean durchGutschriftErledigt(ProjektGeschaeftsdokument posten, BigDecimal gutgeschrieben) {
        return posten.getBruttoBetrag() != null && gutgeschrieben.signum() > 0
                && gutgeschrieben.compareTo(posten.getBruttoBetrag().abs().subtract(GUTSCHRIFT_TOLERANZ)) >= 0;
    }

    /** Nicht stornierte Gutschriften (gebucht und Entwürfe) einer Rechnung, ohne {@code ausserId}. */
    private List<AusgangsGeschaeftsDokument> gueltigeGutschriften(AusgangsGeschaeftsDokument rechnung, Long ausserId) {
        if (rechnung.getId() == null) {
            return List.of();
        }
        return dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(rechnung.getId()).stream()
                .filter(d -> d.getTyp() == AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT)
                .filter(d -> !d.isStorniert())
                .filter(d -> ausserId == null || !ausserId.equals(d.getId()))
                .toList();
    }

    private static BigDecimal summe(List<AusgangsGeschaeftsDokument> dokumente,
            java.util.function.Function<AusgangsGeschaeftsDokument, BigDecimal> betrag) {
        return dokumente.stream().map(betrag).map(AusgangsGeschaeftsDokumentService::betragOderNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Betrag ohne Vorzeichen, null = 0. */
    private static BigDecimal betragOderNull(BigDecimal betrag) {
        return betrag == null ? BigDecimal.ZERO : betrag.abs();
    }

    private static String formatEuro(BigDecimal betrag) {
        return java.text.NumberFormat.getCurrencyInstance(java.util.Locale.GERMANY)
                .format(betrag.setScale(2, RoundingMode.HALF_UP));
    }

    private static String rechnungsbezeichnung(AusgangsGeschaeftsDokument rechnung) {
        return switch (rechnung.getTyp()) {
            case TEILRECHNUNG -> "Teilrechnung";
            case ABSCHLAGSRECHNUNG -> "Abschlagsrechnung";
            case SCHLUSSRECHNUNG -> "Schlussrechnung";
            default -> "Rechnung";
        };
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

        // Die PDF gehört zum Ausgangsdokument, auch ohne Offene-Posten-Eintrag.
        dokument.setPdfDateiname(gespeicherterDateiname);
        dokumentRepository.save(dokument);

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
     * Setzt den Bezahlt-Haken eines Offenen Postens und zieht den Projektstatus
     * ("Bezahlt", automatisch "Beendet") sofort nach.
     *
     * <p>Jede Stelle, die den Haken eines bestehenden Postens umschaltet –
     * Offene-Posten-Center, Barzahlung im Kassenbuch, Storno einer Barzahlung –, geht
     * hier durch. Sonst bliebe am Projekt ein veralteter Status stehen, bis jemand das
     * Projekt öffnet.</p>
     */
    @Transactional
    public void setzeOffenenPostenBezahlt(ProjektGeschaeftsdokument offenerPosten, boolean bezahlt) {
        offenerPosten.setBezahlt(bezahlt);
        // Sofort schreiben: Die Regel fragt die offenen Posten per JPQL ab und muss
        // den neuen Haken schon sehen.
        projektDokumentRepository.saveAndFlush(offenerPosten);
        if (offenerPosten.getProjekt() != null) {
            berechneProjektPreisUndStatus(offenerPosten.getProjekt().getId());
        }
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
     * - Digital angenommene Dokumente sind verbindlich und dürfen nicht gelöscht werden.
     * - Stornierte Dokumente müssen als Nachweis der Korrektur erhalten bleiben.
     * - STORNO-Dokumente sind selbst Korrekturbuchungen und dürfen nie gelöscht werden.
     * - Nur Entwürfe (nicht gebucht, nicht versandt, nicht angenommen, nicht storniert) dürfen
     *   mit Begründung gelöscht werden.
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

        // Digital angenommen = verbindlich (Angebot und die daraus erzeugte AB), auch wenn
        // der automatische AB-Versand gescheitert ist und deshalb kein Versanddatum steht.
        if (dokument.isDigitalAngenommen()) {
            throw new RuntimeException("Vom Kunden digital angenommene Dokumente sind verbindlich und dürfen nicht gelöscht werden.");
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
        // Eine einfache Rechnung oder eine Schlussrechnung schliesst den Vorgang ab:
        // was darin fehlt, ist nicht angefallen, und es kommt keine Rechnung mehr.
        // Sonst boete das Projekt eine weitere Rechnung ueber die nicht angefallenen
        // Positionen an, waehrend auf dem PDF "Noch offen 0,00 €" steht.
        AusgangsGeschaeftsDokument abschliessend = findeAbschliessendeRechnung(basis, nachfolger).orElse(null);
        verlauf.setAbschliessendeRechnungNummer(abschliessend != null ? abschliessend.getDokumentNummer() : null);
        verlauf.setRestbetrag(abschliessend != null
                ? BigDecimal.ZERO
                : verlauf.getBasisdokumentBetragNetto().subtract(bereitsAbgerechnet));

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

        // Rohes positionenJson des Basisdokuments mitgeben: der Dokument-Editor
        // benennt daraus in der Differenzzeile der Schlussrechnung die entfallenen
        // und die zusaetzlich angefallenen Leistungen.
        verlauf.setBasisdokumentPositionenJson(basis.getPositionenJson());

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
     *
     * <p>Summiert alle nicht-optionalen SERVICE-Bloecke (auch in SECTION_HEADER
     * verschachtelt) inklusive Positions-Rabatt und zieht anschliessend den
     * Dokument-Pauschalrabatt ab. Muss deckungsgleich mit
     * {@code helpers.ts#calculateNettoNachRabatt} und
     * {@link RechnungPdfService} rechnen — dieser Wert wird vom Korrekturlauf
     * auch geschrieben, nicht nur angezeigt.</p>
     */
    // Paketsichtbar statt private: wird direkt in AusgangsGeschaeftsDokumentServiceTest geprueft.
    BigDecimal berechneNettoAusPositionenJson(String positionenJson) {
        try {
            JsonNode root = POSITIONEN_MAPPER.readTree(positionenJson);

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

            // Dokument-Pauschalrabatt liegt neben den Bloecken im Wrapper-Objekt
            // ({blocks, globalRabatt, ...}) und gilt auf die gesamte Nettosumme.
            // Rundung MUSS ueber RabattRechner laufen: eine eigene Faktor-Rechnung hier
            // weicht um einen Cent vom PDF ab, und der Korrekturlauf wuerde dann bereits
            // korrekte, festgeschriebene Rechnungen umschreiben.
            summe = summe.setScale(2, RoundingMode.HALF_UP);
            return RabattRechner.nettoNachRabatt(summe, leseGlobalRabattProzent(positionenJson));
        } catch (Exception e) {
            log.warn("Fehler beim Berechnen des Nettobetrags aus positionenJson: {}", e.getMessage());
            return null;
        }
    }

    private BigDecimal summeServiceBlock(JsonNode block) {
        BigDecimal summe = BigDecimal.ZERO;
        String type = block.has("type") ? block.get("type").asText() : "";

        // Optionale Positionen (Wahl-/Alternativpositionen) zaehlen NICHT zur Nettosumme —
        // genau wie im Editor (helpers.ts#calculateNetto) und im PDF
        // (RechnungPdfService#addSummenBlock). Fehlte der Filter hier, wuerde der
        // Korrekturlauf Angebote mit Alternativpositionen auf einen zu hohen Betrag
        // umschreiben — schreibend, auf festgeschriebenen Belegen.
        if ("SERVICE".equals(type) && !block.path("optional").asBoolean(false)) {
            double quantity = block.has("quantity") ? block.get("quantity").asDouble(0) : 0;
            double price = block.has("price") ? block.get("price").asDouble(0) : 0;
            // Verbindliche Zeilenregel, identisch im Editor (helpers.ts#serviceLineTotal):
            //     round2( round2(menge × preis) × (1 − rabatt/100) )
            // Die ERSTE Rundung ist entscheidend: das Frontend rechnet in Doubles und
            // kann ein exaktes x,xx5 nicht darstellen (4,10 × 0,85 = 3.4849999…).
            // Erst wenn beide Seiten auf demselben Cent-Betrag aufsetzen, kommen sie
            // zwingend zum selben Ergebnis — sonst schreibt der Korrekturlauf korrekte,
            // festgeschriebene Belege um.
            BigDecimal zeile = BigDecimal.valueOf(quantity).multiply(BigDecimal.valueOf(price))
                    .setScale(2, RoundingMode.HALF_UP);
            double discount = block.has("discount") ? block.get("discount").asDouble(0) : 0;
            if (discount > 0) {
                BigDecimal faktor = BigDecimal.ONE.subtract(
                        BigDecimal.valueOf(Math.min(100d, discount)).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
                zeile = zeile.multiply(faktor).setScale(2, RoundingMode.HALF_UP);
            }
            summe = summe.add(zeile);
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
     * Sucht die nicht stornierte einfache Rechnung bzw. Schlussrechnung, die den
     * Vorgang dieses Basisdokuments abschliesst.
     *
     * <p>Angebot und Auftragsbestaetigung zaehlen als ein Vorgang: Eine AB darf auch
     * nach der Rechnung am Angebot noch entstehen. Ohne den Blick ueber die Kette
     * liesse sich an dieser AB eine zweite Rechnung ueber denselben Auftrag stellen.
     * Geloeschte Entwuerfe tauchen hier nicht mehr auf, stornierte zaehlen nicht.
     *
     * @param nachfolger die bereits geladenen direkten Nachfolger von {@code basis}
     */
    private Optional<AusgangsGeschaeftsDokument> findeAbschliessendeRechnung(
            AusgangsGeschaeftsDokument basis, List<AusgangsGeschaeftsDokument> nachfolger) {
        List<AusgangsGeschaeftsDokument> kandidaten = new ArrayList<>(nachfolger);
        AusgangsGeschaeftsDokument oben = basis.getVorgaenger();
        if (basis.getTyp() == AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG && oben != null) {
            // AB unter einem Angebot: dort gestellte Rechnungen gehoeren zum selben Vorgang.
            kandidaten.addAll(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(oben.getId()));
        }
        for (AusgangsGeschaeftsDokument dok : nachfolger) {
            if (dok.getTyp() == AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG && !dok.isStorniert()) {
                // Angebot mit AB: an der AB gestellte Rechnungen gehoeren zum selben Vorgang.
                kandidaten.addAll(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(dok.getId()));
            }
        }
        return kandidaten.stream()
                .filter(dok -> ABSCHLIESSENDE_RECHNUNGSTYPEN.contains(dok.getTyp()) && !dok.isStorniert())
                .findFirst();
    }

    /**
     * Nach einer einfachen Rechnung oder Schlussrechnung ist der Vorgang
     * abgerechnet. Eine weitere Rechnung gibt es erst wieder, wenn diese
     * storniert oder ihr Entwurf geloescht wurde. Eine AB bleibt moeglich.
     */
    private void validiereVorgangNichtAbgeschlossen(AusgangsGeschaeftsDokument vorgaenger) {
        List<AusgangsGeschaeftsDokument> nachfolger =
                dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(vorgaenger.getId());
        findeAbschliessendeRechnung(vorgaenger, nachfolger).ifPresent(rechnung -> {
            throw new IllegalStateException(
                    "Zu " + vorgaenger.getDokumentNummer() + " gibt es schon die Rechnung "
                            + rechnung.getDokumentNummer() + ", die den Auftrag abschließt. "
                            + "Eine weitere Rechnung ist erst möglich, wenn diese storniert "
                            + "oder ihr Entwurf gelöscht wurde.");
        });
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
     * Prüft die Regeln für ein neues Basisdokument (ohne Vorgänger).
     * Siehe Kommentar in {@link #erstellen(AusgangsGeschaeftsDokumentErstellenDto, Long, String)}.
     */
    private void validiereBasisdokument(AusgangsGeschaeftsDokumentTyp typ, Long projektId, Long anfrageId) {
        if (typ == AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT) {
            // Nachtragsangebote bauen auf einem bestehenden Angebot auf.
            boolean hatAngebotBasis = (projektId != null
                        && dokumentRepository.existsByProjektIdAndVorgaengerIsNullAndTypAndStorniertFalse(projektId, AusgangsGeschaeftsDokumentTyp.ANGEBOT))
                    || (anfrageId != null
                        && dokumentRepository.existsByAnfrageIdAndVorgaengerIsNullAndTypAndStorniertFalse(anfrageId, AusgangsGeschaeftsDokumentTyp.ANGEBOT));
            if (!hatAngebotBasis) {
                throw new IllegalStateException(
                        "Ein Nachtragsangebot benötigt ein bestehendes Angebot als Basisdokument.");
            }
            return;
        }

        // Alle übrigen Typen (Angebot, AB, Rechnung, …) sind nur als allererstes
        // Basisdokument erlaubt. Sobald irgendein Basisdokument existiert, ist das
        // einzige zulässige weitere Wurzel-Dokument ein Nachtragsangebot (oben).
        // Wurzeln mit storniert=true zählen nicht mit, sonst bliebe der Vorgang
        // blockiert (Issue #262). Nachfolger prüfen wir bewusst nicht: Folgedokumente
        // entstehen nur an Angebot oder AB, unter einer solchen Wurzel hängen nur
        // Gegenbuchung und Mahnungen.
        boolean hatBasis = (projektId != null && dokumentRepository.existsByProjektIdAndVorgaengerIsNullAndStorniertFalse(projektId))
                || (anfrageId != null && dokumentRepository.existsByAnfrageIdAndVorgaengerIsNullAndStorniertFalse(anfrageId));
        if (hatBasis) {
            if (typ == AusgangsGeschaeftsDokumentTyp.ANGEBOT) {
                throw new IllegalStateException(
                        "Es existiert bereits ein Basisdokument. Weitere Basisdokumente sind nur als Nachtragsangebot möglich.");
            }
            throw new IllegalStateException("Es existiert bereits ein Basisdokument.");
        }
    }

    /**
     * Ein Angebot oder Nachtragsangebot trägt höchstens eine aktive
     * Auftragsbestätigung.
     *
     * <p>Zwei ABs am selben Angebot erzeugen dasselbe Problem wie Rechnungen an
     * Angebot und AB gleichzeitig, nur eine Ebene tiefer: Beide hätten ihren
     * eigenen Abrechnungsverlauf über denselben Auftragswert, und keiner der
     * beiden Restbeträge stimmte. Braucht ein Vorgang mehr Volumen, ist das
     * ein Nachtragsangebot mit eigener Kette (siehe
     * {@link AusgangsGeschaeftsDokumentTyp#NACHTRAGSANGEBOT}).</p>
     *
     * <p>Stornierte ABs zählen nicht mit — sonst bliebe der Vorgang nach einer
     * Stornierung dauerhaft ohne Auftragsbestätigung.</p>
     */
    private void validiereEinzigeAuftragsbestaetigung(AusgangsGeschaeftsDokument vorgaenger) {
        boolean hatAktiveAuftragsbestaetigung = dokumentRepository
                .findByVorgaengerIdOrderByErstelltAmAsc(vorgaenger.getId()).stream()
                .anyMatch(n -> n.getTyp() == AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG
                        && !n.isStorniert());
        if (hatAktiveAuftragsbestaetigung) {
            throw new IllegalStateException(
                    "Zu " + vorgaenger.getDokumentNummer() + " gibt es bereits eine Auftragsbestätigung. "
                            + "Für zusätzliche Leistungen legen Sie bitte ein Nachtragsangebot an.");
        }
    }

    /**
     * Rechnungen gehören an das unterste Dokument eines Vorgangs.
     *
     * <p>Sobald unter einem Angebot oder Nachtragsangebot eine Auftragsbestätigung
     * hängt, wird ausschließlich dort abgerechnet. Andernfalls liefen zwei
     * Abrechnungsstände nebeneinander — einer am Angebot, einer an der AB — und der
     * Restbetrag (siehe {@link #getAbrechnungsverlauf(Long)}) stimmte in keinem von
     * beiden, weil er nur die direkten Nachfolger des jeweiligen Basisdokuments zählt.</p>
     *
     * <p><strong>Ausnahme für Bestandsvorgänge:</strong> Wurde am Angebot bereits
     * abgerechnet und erst danach eine AB angelegt, bleibt die Abrechnung am Angebot.
     * Ein erzwungener Wechsel auf die AB würde dort einen Restbetrag in voller
     * Angebotshöhe ausweisen — die schon am Angebot abgerechneten Beträge zählt der
     * Verlauf der AB nicht mit, und die Schlussrechnung würde still zu hoch
     * vorbefüllt. Solche Vorgänge rechnen deshalb dort weiter ab, wo ihr Verlauf
     * vollständig ist.</p>
     *
     * <p>Ein nachträglicher Typwechsel kann die Regel nicht umgehen:
     * {@code AusgangsGeschaeftsDokumentUpdateDto} führt kein {@code typ}-Feld, und
     * der Controller bietet keinen entsprechenden Endpoint. Wird das je geändert,
     * muss dieser Guard auch im Update-Pfad greifen.</p>
     */
    private void validiereAbrechnungsbasis(AusgangsGeschaeftsDokument vorgaenger) {
        if (vorgaenger.getTyp() != AusgangsGeschaeftsDokumentTyp.ANGEBOT
                && vorgaenger.getTyp() != AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT) {
            return;
        }

        List<AusgangsGeschaeftsDokument> nachfolger =
                dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(vorgaenger.getId());

        boolean hatAuftragsbestaetigung = nachfolger.stream()
                .anyMatch(n -> n.getTyp() == AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG
                        && !n.isStorniert());
        if (!hatAuftragsbestaetigung) {
            return;
        }

        boolean bereitsAmVorgaengerAbgerechnet = nachfolger.stream()
                .anyMatch(n -> RECHNUNGSTYPEN.contains(n.getTyp()) && !n.isStorniert());
        if (bereitsAmVorgaengerAbgerechnet) {
            return;
        }

        throw new IllegalStateException(
                "Zu " + vorgaenger.getDokumentNummer() + " gibt es bereits eine Auftragsbestätigung. "
                        + "Bitte legen Sie die Rechnung dort an.");
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
     * Nettobetrag einer Schlussrechnung, so wie Editor, PDF und Listen ihn zeigen – oder
     * {@code null}, wenn der gespeicherte Betrag gilt.
     *
     * <p>Der gespeicherte Betrag ist im Editor als "Summe der eigenen Positionen minus bereits
     * gestellte Rechnungen" entstanden. Bis 2026-09 wurde er durch "Basisbetrag minus andere
     * Rechnungen" ersetzt; das rechnete jede aus der Schlussrechnung geloeschte Position (z.B.
     * eine nicht benoetigte Geruststellung) wieder hinein und ignorierte Zusatzpositionen.
     *
     * <ul>
     *   <li>Festgeschrieben (gebucht oder digital angenommen): {@code null} – der gespeicherte
     *       Betrag ist der Beleg und steht so auch in den Offenen Posten (GoBD, §147 AO). Gilt
     *       auch für Altdokumente ohne positionenJson; nur wenn dort gar kein Betrag gespeichert
     *       ist, greift die Ableitung darunter, damit die Anzeige nicht leer bleibt.</li>
     *   <li>Altdokument ohne positionenJson: Auftragssumme minus die anderen Rechnungen (es gibt
     *       keine Positionen, aus denen sich der Betrag ableiten liesse).</li>
     *   <li>Entwurf: aus den eigenen Positionen neu ableiten. Wird nach dem Anlegen noch eine
     *       Teil- oder Abschlagsrechnung storniert oder gestellt, zieht der Betrag mit.
     *       Deckungsgleich mit dem, was der Editor in handleSave berechnet.</li>
     * </ul>
     *
     * <p>Für die Anzeige: Kann der Betrag nicht abgeleitet werden, gilt der gespeicherte.
     */
    private BigDecimal abgeleiteterSchlussrechnungsNetto(AusgangsGeschaeftsDokument dokument) {
        try {
            return berechneSchlussrechnungsNetto(dokument);
        } catch (RuntimeException e) {
            log.warn("Fehler beim Berechnen des Schlussrechnungsbetrags: {}", e.getMessage());
            return null;
        }
    }

    /** Wie {@link #abgeleiteterSchlussrechnungsNetto}, reicht Fehler aber an den Aufrufer weiter. */
    private BigDecimal berechneSchlussrechnungsNetto(AusgangsGeschaeftsDokument dokument) {
        if (dokument.getTyp() != AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG
                || dokument.getVorgaenger() == null
                || dokument.isStorniert()) {
            return null;
        }
        if (!dokument.istBearbeitbar() && dokument.getBetragNetto() != null) {
            return null;
        }
        AbrechnungsverlaufDto verlauf = getAbrechnungsverlauf(dokument.getVorgaenger().getId());
        // Was die ANDEREN, nicht stornierten Rechnungen am Vorgänger schon abgerechnet haben.
        // Direkt über die IDs statt "Summe minus eigener Betrag": Fehlt der gespeicherte
        // eigene Betrag, zaehlt der Verlauf ihn aus den Positionen – die Differenz waere falsch.
        BigDecimal andereAbgerechnet = verlauf.getPositionen().stream()
                .filter(p -> !p.isStorniert() && !Objects.equals(p.getId(), dokument.getId()))
                .map(AbrechnungsverlaufDto.AbrechnungspositionDto::getBetragNetto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String positionen = dokument.getPositionenJson();

        if (positionen == null || positionen.isBlank()) {
            return verlauf.getBasisdokumentBetragNetto().subtract(andereAbgerechnet);
        }
        if (dokument.istBearbeitbar()) {
            BigDecimal ausPositionen = berechneNettoAusPositionenJson(positionen);
            if (ausPositionen != null) {
                return ausPositionen.subtract(andereAbgerechnet);
            }
        }
        return null;
    }

    /** Brutto = Netto + MwSt, auf Cent gerundet; ohne Satz gelten 19 %. */
    private static BigDecimal bruttoAusNetto(BigDecimal netto, BigDecimal mwstSatz) {
        BigDecimal satz = mwstSatz != null ? mwstSatz : new BigDecimal("0.19");
        return netto.add(netto.multiply(satz)).setScale(2, RoundingMode.HALF_UP);
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

        BigDecimal schlussrechnungsNetto = abgeleiteterSchlussrechnungsNetto(dokument);
        if (schlussrechnungsNetto != null) {
            netto = schlussrechnungsNetto;
        }

        dto.setBetragNetto(netto);

        // Bruttobetrag: immer aus aktuellem Netto berechnen für Konsistenz
        BigDecimal brutto = netto != null ? bruttoAusNetto(netto, dokument.getMwstSatz()) : dokument.getBetragBrutto();
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
     * Summiert die Rechnungen eines Projekts, die <strong>nicht</strong> aus einem
     * AusgangsGeschaeftsDokument stammen – also die im Offene-Posten-Bereich von Hand
     * nacherfassten Rechnungen (z.B. wenn die Rechnung außerhalb des Programms
     * geschrieben wurde).
     *
     * <p>Jede Rechnungsnummer zählt genau einmal: Nummern, die bereits als internes
     * Dokument erfasst sind, werden übersprungen (sonst würde derselbe Betrag doppelt
     * gezählt, weil intern gebuchte Rechnungen zusätzlich einen Offene-Posten-Eintrag
     * bekommen), und Dubletten innerhalb der nacherfassten Rechnungen ebenso.</p>
     *
     * <p>Einträge ohne Dokumentnummer werden übersprungen: Sie lassen sich weder gegen
     * die internen Dokumente abgleichen noch untereinander unterscheiden – mitzuzählen
     * hieße, eine Doppelzählung in Kauf zu nehmen.</p>
     *
     * @param bekannteRechnungsnummern Dokumentnummern der internen Rechnungen des Projekts,
     *                                 <strong>einschließlich stornierter</strong> – deren
     *                                 Offene-Posten-Eintrag bleibt beim Stornieren bestehen
     *                                 und darf den Auftragspreis nicht wiederbeleben
     */
    private BigDecimal summiereNacherfassteRechnungen(Long projektId, Set<String> bekannteRechnungsnummern) {
        Set<String> bereitsGezaehlt = new HashSet<>();
        BigDecimal summe = BigDecimal.ZERO;

        for (ProjektGeschaeftsdokument rechnung : projektDokumentRepository.findRechnungenFuerPreisberechnung(projektId)) {
            String nummer = rechnung.getDokumentid();
            if (nummer == null) continue;
            if (bekannteRechnungsnummern.contains(nummer)) continue;
            if (!bereitsGezaehlt.add(nummer)) continue;
            if (rechnung.getBruttoBetrag() != null) {
                summe = summe.add(rechnung.getBruttoBetrag());
            }
        }
        return summe;
    }

    /**
     * Berechnet bruttoPreis und bezahlt-Status eines Projekts on-the-fly
     * aus den Dokumenten. Der Preis wird nie von Hand gepflegt.
     *
     * Logik:
     * - Gibt es Angebot/Auftragsbestätigung/Nachtragsangebot, ist deren aktueller Stand
     *   maßgeblich ({@link #berechneAktuellenBruttoPreis}: Childobjekt zählt,
     *   Nachtragsangebote addieren sich).
     * - Gibt es keines davon (z.B. Reparatur, die direkt berechnet wird, oder eine
     *   extern geschriebene und hier nur nacherfasste Rechnung), ist die Summe aller
     *   eindeutigen Rechnungen der Auftragspreis.
     * - bezahlt = true erst, wenn die (nicht-stornierten) Rechnungen den Projekt-Preis
     *   inkl. Nachträgen abdecken, keine davon mehr ein ungebuchter Entwurf ist, kein
     *   Offener Posten mehr unbezahlt ist und die bezahlten Rechnungen den Preis erreichen.
     * - abgeschlossen = true wenn bezahlt = true (außer der Haken wurde von Hand gesetzt).
     */
    @Transactional
    public void aktualisiereProjektPreisAusDokumenten(Long projektId) {
        berechneProjektPreisUndStatus(projektId);
    }

    /**
     * Kern von {@link #aktualisiereProjektPreisAusDokumenten(Long)}. Bewusst ohne
     * {@code @Transactional} und privat, damit der interne Aufruf aus
     * {@link #trageFehlendePreiseNach()} nicht am Proxy vorbeiläuft.
     *
     * @return der Auftragspreis, der danach am Projekt steht, oder {@code null},
     *         wenn es das Projekt nicht gibt
     */
    private BigDecimal berechneProjektPreisUndStatus(Long projektId) {
        if (projektId == null) return null;

        Projekt projekt = projektRepository.findById(projektId).orElse(null);
        if (projekt == null) return null;

        List<AusgangsGeschaeftsDokument> alleDokumente =
                dokumentRepository.findByProjektIdOrderByDatumDesc(projektId);

        // Stornierte Dokumente ausfiltern
        List<AusgangsGeschaeftsDokument> aktive = alleDokumente.stream()
                .filter(d -> !d.isStorniert())
                .toList();

        // Aktuellen Brutto-Preis aus den Vorgängen berechnen (Childobjekt zählt,
        // Nachtragsangebote addieren sich).
        BigDecimal vorgangsPreis = berechneAktuellenBruttoPreis(aktive);

        // Stornierte Rechnungen sind bereits über !isStorniert() ausgeschlossen,
        // daher werden Storno-Dokumente (negative Beträge) NICHT mehr abgezogen,
        // um doppelte Berücksichtigung zu vermeiden.
        BigDecimal summeInterneRechnungen = aktive.stream()
                .filter(d -> RECHNUNGSTYPEN.contains(d.getTyp()))
                .map(d -> d.getBetragBrutto() != null ? d.getBetragBrutto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Bewusst aus ALLEN Dokumenten (auch stornierten): Beim Stornieren bleibt der
        // Offene-Posten-Eintrag mit derselben Nummer bestehen. Stünde die Nummer hier
        // nicht drin, käme der stornierte Betrag über den Umweg der "nacherfassten"
        // Rechnungen wieder in den Auftragspreis zurück.
        Set<String> bekannteRechnungsnummern = alleDokumente.stream()
                .filter(d -> RECHNUNGSTYPEN.contains(d.getTyp()))
                .map(AusgangsGeschaeftsDokument::getDokumentNummer)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        BigDecimal summeRechnungen = summeInterneRechnungen
                .add(summiereNacherfassteRechnungen(projektId, bekannteRechnungsnummern));

        // Angebot/AB/Nachtragsangebot sind maßgeblich, sobald es sie gibt. Sonst zählt
        // die Summe der Rechnungen – so bekommen auch Projekte ohne Angebot (Reparatur,
        // extern geschriebene Rechnung) ihren Preis automatisch.
        BigDecimal neuerBruttoPreis = vorgangsPreis.compareTo(BigDecimal.ZERO) > 0
                ? vorgangsPreis
                : summeRechnungen;

        // Ohne jedes preisrelevante Dokument bleibt der bisherige Wert stehen –
        // sonst würden Altprojekte ohne Dokumente auf 0 zurückfallen.
        if (neuerBruttoPreis.compareTo(BigDecimal.ZERO) > 0) {
            projekt.setBruttoPreis(neuerBruttoPreis);
        }

        // Maßgeblich ist der Preis, der tatsächlich am Projekt steht. Sonst würde ein
        // Projekt mit hinterlegtem Preis, aber ohne preisrelevante Dokumente, bei jedem
        // beliebigen Datei-Upload auf "nicht bezahlt" zurückfallen.
        BigDecimal massgeblicherPreis = projekt.getBruttoPreis() != null
                ? projekt.getBruttoPreis()
                : BigDecimal.ZERO;

        // Bezahlt (und damit automatisch beendet) ist ein Projekt erst, wenn
        //  1. die Rechnungen den kompletten Auftragspreis inkl. Nachträgen abdecken
        //     (alle Teil-/Schlussrechnungen sind geschrieben),
        //  2. keine Rechnung mehr ein ungebuchter Entwurf ist – der Offene-Posten-Eintrag
        //     entsteht erst beim Buchen, vorher würde die Prüfung unten sie übersehen,
        //  3. kein Offener Posten mehr unbezahlt ist und
        //  4. die bezahlten Rechnungen den kompletten Auftragspreis erreichen.
        // Das bloße Schreiben einer Rechnung beendet ein Projekt also nicht.
        BigDecimal preisMitToleranz = massgeblicherPreis.subtract(new BigDecimal("0.01"));
        boolean alleRechnungenGeschrieben = massgeblicherPreis.compareTo(BigDecimal.ZERO) > 0
                && summeRechnungen.compareTo(preisMitToleranz) >= 0;
        boolean ungebuchteRechnungVorhanden = aktive.stream()
                .anyMatch(d -> RECHNUNGSTYPEN.contains(d.getTyp()) && !d.isGebucht());
        boolean keineOffenenPosten = !projektDokumentRepository.existsOffenePostenByProjektId(projektId);
        BigDecimal bezahlteSumme = projektDokumentRepository.sumBezahlteRechnungenByProjektId(projektId);
        boolean vollstaendigBezahlt = alleRechnungenGeschrieben
                && !ungebuchteRechnungVorhanden
                && keineOffenenPosten
                && bezahlteSumme != null
                && bezahlteSumme.compareTo(preisMitToleranz) >= 0;

        // Haken "Beendet" von Hand gesetzt/entfernt? Dann hat der Benutzer Vorrang
        // und die Automatik fasst "abgeschlossen" nicht mehr an.
        boolean automatikDarfBeenden = !projekt.isAbgeschlossenManuell();
        if (vollstaendigBezahlt) {
            projekt.setBezahlt(true);
            if (automatikDarfBeenden) {
                projekt.setAbgeschlossen(true);
            }
        } else {
            projekt.setBezahlt(false);
            // Nur bei konkret offenen Rechnungen wieder öffnen – Altprojekte ohne
            // Dokumente sollen durch einen beliebigen Datei-Upload nicht aufgehen.
            boolean offeneRechnungVorhanden = !keineOffenenPosten || ungebuchteRechnungVorhanden;
            if (offeneRechnungVorhanden && automatikDarfBeenden) {
                projekt.setAbgeschlossen(false);
            }
        }

        projektRepository.save(projekt);
        return massgeblicherPreis;
    }

    /**
     * Trägt den Auftragspreis bei allen Projekten nach, bei denen noch keiner
     * hinterlegt ist (Preis 0 oder leer). Projekte mit vorhandenem Preis werden
     * nicht angefasst.
     *
     * <p>Gedacht als einmaliger Nachlauf für Altbestände, die vor der automatischen
     * Preisableitung angelegt wurden – etwa Reparaturen, die direkt berechnet wurden
     * und deshalb nie einen Preis bekommen haben.</p>
     *
     * <p>Die Menge ist durch die Bedingung "Preis 0 oder leer" begrenzt und schrumpft mit
     * jedem Lauf, weil befüllte Projekte beim nächsten Mal nicht mehr in die Auswahl
     * fallen. Der Lauf läuft in einer Transaktion – bei einem Fehler bleibt der Bestand
     * unverändert, was für eine Korrekturaktion an Auftragspreisen gewollt ist.</p>
     *
     * @return wie viele Projekte geprüft wurden und bei wie vielen ein Preis gesetzt wurde
     */
    @Transactional
    public PreisNachtragErgebnis trageFehlendePreiseNach() {
        List<Long> projektIds = projektRepository.findIdsOhneBruttoPreis();
        int nachgetragen = 0;

        for (Long projektId : projektIds) {
            BigDecimal preis = berechneProjektPreisUndStatus(projektId);
            if (preis != null && preis.compareTo(BigDecimal.ZERO) > 0) {
                nachgetragen++;
            }
        }

        log.info("Preis-Nachlauf abgeschlossen: {} von {} Projekten ohne Preis konnten aus Dokumenten befüllt werden.",
                nachgetragen, projektIds.size());
        return new PreisNachtragErgebnis(projektIds.size(), nachgetragen);
    }

    /**
     * Ergebnis von {@link #trageFehlendePreiseNach()}.
     *
     * @param geprueft    Projekte ohne Auftragspreis, die durchgerechnet wurden
     * @param nachgetragen Projekte, die daraufhin einen Preis bekommen haben
     */
    public record PreisNachtragErgebnis(int geprueft, int nachgetragen) {
    }

    /**
     * Dokumenttypen, deren {@code betragNetto} sich direkt aus den Positionen ergibt
     * und daher aus {@code positionenJson} nachgerechnet werden darf.
     *
     * <p>Bewusst NICHT enthalten:
     * {@code ABSCHLAGSRECHNUNG} (Betrag gibt der Benutzer frei vor),
     * {@code SCHLUSSRECHNUNG} (Betrag ist die Summe ihrer Positionen MINUS der
     * bereits gestellten Rechnungen) und {@code STORNO} (Betrag ist das negierte
     * Original). Wer einen dieser Typen ergaenzt, laesst den Korrekturlauf
     * festgeschriebene Belege umschreiben — bei der Schlussrechnung um die volle
     * Summe der Vorrechnungen zu hoch.</p>
     */
    private static final Set<AusgangsGeschaeftsDokumentTyp> POSITIONSABGELEITETE_TYPEN = EnumSet.of(
            AusgangsGeschaeftsDokumentTyp.ANGEBOT,
            AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT,
            AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG,
            AusgangsGeschaeftsDokumentTyp.RECHNUNG,
            AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT);

    /**
     * Rechnet bei Bestandsdokumenten mit Rabatt den {@code betragNetto} aus
     * {@code positionenJson} neu.
     *
     * <p>Hintergrund: Der Dokument-Pauschalrabatt ({@code globalRabatt}) lag nur als
     * Metadatum in {@code positionenJson} und floss nie in den gespeicherten
     * {@code betragNetto}. Editor-Footer und PDF rechneten ihn heraus, alles andere —
     * Projekt-Dokumentliste, Offene Posten, Projekt-Bruttopreis und die
     * Rechnungs-E-Mail — zeigte den unrabattierten Betrag. Dokumente, die seit dem Fix
     * gespeichert wurden, sind bereits korrekt; dieser Lauf holt den Altbestand nach.</p>
     *
     * <p><strong>GoBD:</strong> Der Lauf fasst bewusst auch gebuchte Dokumente an, weil
     * sonst genau die festgeschriebenen Rechnungen falsch blieben. Jede Korrektur wird
     * als {@code GEAENDERT} mit Begruendung in den Audit-Trail geschrieben, ist also
     * nachvollziehbar. Angefasst wird nur, was tatsaechlich einen Rabatt traegt und wo
     * der nachgerechnete Betrag vom gespeicherten abweicht — alles andere bleibt
     * unveraendert. Der Lauf ist idempotent: ein zweiter Durchlauf findet nichts mehr.</p>
     *
     * <p><strong>Betriebshinweis — der Lauf sperrt kurzzeitig das gesamte System:</strong>
     * Er laeuft in EINER Transaktion ueber den Gesamtbestand. Der erste korrigierte Beleg
     * nimmt ueber {@code AusgangsGeschaeftsDokumentAuditService} einen
     * {@code PESSIMISTIC_WRITE} auf die Singleton-Zeile {@code audit_chain_state} und
     * haelt ihn bis zum Commit. Solange blockiert <em>jede</em> Aktion, die einen
     * Audit-Eintrag schreibt:</p>
     * <ul>
     *   <li>Rechnung buchen, versenden oder stornieren</li>
     *   <li>digitale Annahme durch den Kunden ueber die Freigabe-Seite
     *       ({@code protokolliereDigitaleAnnahme}) — also auch von aussen</li>
     * </ul>
     * <p>Deshalb: <strong>einmalig, manuell durch einen Admin, ausserhalb der Arbeitszeit
     * und nicht, waehrend eine Freigabe beim Kunden offen ist.</strong> Fuer den Bestand
     * eines Handwerksbetriebs (Groessenordnung 10³–10⁴ Dokumente, davon ein Bruchteil mit
     * Rabatt) liegt die Laufzeit im Sekundenbereich. Ab etwa 50.000 Dokumenten oder
     * spuerbar mehr als einer Minute Laufzeit ist Chunking Pflicht — bewusst nicht
     * vorweggenommen, weil es eine zusaetzliche Bean mit
     * {@code @Transactional(REQUIRES_NEW)} pro Dokument braucht, die ohne laufende
     * Datenbank nicht verifizierbar war. Der Lauf ist idempotent, ein Abbruch
     * mittendrin ist also unkritisch.</p>
     *
     * <p>Nicht abgedeckt: Rechnungen ohne {@code positionenJson} (z.B. direkt aus einer
     * AB erzeugt, Betrag vom Vorgaenger uebernommen) — dort gibt es nichts nachzurechnen,
     * sie behalten ihren gespeicherten Betrag.</p>
     *
     * @param bearbeiterId ausloesender Benutzer fuer den Audit-Eintrag (darf null sein)
     * @param ipAdresse   IP fuer den Audit-Eintrag (darf null sein)
     * @return wie viele Dokumente mit Rabatt geprueft und wie viele korrigiert wurden
     */
    @Transactional
    public RabattKorrekturErgebnis korrigiereRabattBetraege(Long bearbeiterId, String ipAdresse) {
        int geprueft = 0;
        int korrigiert = 0;
        Set<Long> betroffeneProjekte = new LinkedHashSet<>();
        // GoBD: Bei einer Aenderung an festgeschriebenen Belegen ist das "wer" der
        // wichtigste Teil des Audit-Eintrags — sonst steht dort nur "system".
        var bearbeiter = bearbeiterId != null
                ? frontendUserProfileRepository.findById(bearbeiterId).orElse(null)
                : null;

        for (AusgangsGeschaeftsDokument dokument : dokumentRepository.findAll()) {
            String json = dokument.getPositionenJson();
            if (json == null || json.isBlank()) continue;
            if (dokument.isStorniert()) continue;
            // Digital angenommene Dokumente bleiben unangetastet: der Kunde hat genau
            // diesen Betrag bestaetigt. Bei einer AB mit mitbeauftragten Alternativen
            // setzt DokumentFreigabeService den betragNetto ausserdem explizit
            // (Basis rabattiert + Delta unrabattiert) — der waere aus den Positionen
            // gar nicht reproduzierbar und wuerde hier still nach unten korrigiert.
            if (dokument.isDigitalAngenommen()) continue;
            if (!POSITIONSABGELEITETE_TYPEN.contains(dokument.getTyp())) continue;
            if (!enthaeltRabatt(json)) continue;

            geprueft++;

            BigDecimal neuNetto = berechneNettoAusPositionenJson(json);
            if (neuNetto == null) continue;
            if (dokument.getTyp() == AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT) {
                // Positionen sind positiv, die Gutschrift steht als Minderung.
                neuNetto = DokumentBetragsvorzeichen.alsMinderung(neuNetto);
            }

            // Cent-Toleranz statt exaktem Vergleich: ein echter Rabatt-Fehler ist immer
            // r% der Summe, also um Groessenordnungen groesser. Ein einzelner Cent kann
            // dagegen aus einer Rundungsdifferenz stammen — und dafuer eine
            // festgeschriebene Rechnung umzuschreiben waere unverhaeltnismaessig.
            BigDecimal altNetto = dokument.getBetragNetto();
            if (altNetto != null
                    && altNetto.subtract(neuNetto).abs().compareTo(new BigDecimal("0.01")) <= 0) {
                continue;
            }

            BigDecimal neuBrutto = bruttoAusNetto(neuNetto, dokument.getMwstSatz());

            dokument.setBetragNetto(neuNetto);
            dokument.setBetragBrutto(neuBrutto);
            AusgangsGeschaeftsDokument gespeichert = dokumentRepository.save(dokument);

            auditService.protokolliereAenderung(gespeichert, bearbeiter,
                    "Automatische Rabatt-Korrektur: Nettobetrag " + altNetto + " -> " + neuNetto
                            + " (Rabatt war nicht im gespeicherten Betrag enthalten)",
                    ipAdresse);

            // Offenen Posten nachziehen — er traegt eine eigene Kopie des Bruttobetrags.
            if (gespeichert.getDokumentNummer() != null) {
                for (ProjektGeschaeftsdokument posten :
                        projektDokumentRepository.findGeschaeftsdokumenteByDokumentid(gespeichert.getDokumentNummer())) {
                    posten.setBruttoBetrag(neuBrutto);
                    projektDokumentRepository.save(posten);
                }
            }

            if (gespeichert.getProjekt() != null) {
                betroffeneProjekte.add(gespeichert.getProjekt().getId());
            }
            korrigiert++;
        }

        // Erst nach allen Dokumenten: der Projektpreis summiert ueber die Dokumente,
        // eine Neuberechnung pro Dokument waere teuer und teils auf halbem Stand.
        for (Long projektId : betroffeneProjekte) {
            berechneProjektPreisUndStatus(projektId);
        }

        log.info("Rabatt-Korrektur abgeschlossen: {} von {} Dokumenten mit Rabatt korrigiert, {} Projektpreise neu berechnet.",
                korrigiert, geprueft, betroffeneProjekte.size());
        return new RabattKorrekturErgebnis(geprueft, korrigiert, betroffeneProjekte.size());
    }

    /**
     * Liest den Dokument-Pauschalrabatt aus dem {@code positionenJson}.
     *
     * <p>Der Wert liegt neben den Bloecken im Wrapper-Objekt
     * ({@code {blocks, globalRabatt, ...}}) und gilt auf die gesamte Nettosumme.
     * Legacy-Dokumente im nackten Array-Format haben ihn nicht.</p>
     *
     * @return Prozentsatz (0-100) oder {@code null}, wenn kein Rabatt hinterlegt ist
     */
    public static BigDecimal leseGlobalRabattProzent(String positionenJson) {
        if (positionenJson == null || positionenJson.isBlank()) return null;
        try {
            JsonNode root = POSITIONEN_MAPPER.readTree(positionenJson);
            double rabatt = root.path("globalRabatt").asDouble(0);
            if (rabatt <= 0) return null;
            return BigDecimal.valueOf(Math.min(100d, rabatt));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Traegt das positionenJson einen Rabatt — entweder den Dokument-Pauschalrabatt
     * oder einen Rabatt auf einer einzelnen Position?
     *
     * <p>Grenzt den Korrekturlauf bewusst eng ein: ohne Rabatt gibt es den Bug nicht,
     * und ein abweichender Betrag haette dann eine andere, hier nicht zu behandelnde
     * Ursache (z.B. ein von Hand gesetzter Betrag).</p>
     */
    boolean enthaeltRabatt(String positionenJson) {
        try {
            JsonNode root = POSITIONEN_MAPPER.readTree(positionenJson);

            if (root.path("globalRabatt").asDouble(0) > 0) return true;

            JsonNode blocks;
            if (root.isArray()) {
                blocks = root;
            } else if (root.has("blocks") && root.get("blocks").isArray()) {
                blocks = root.get("blocks");
            } else {
                return false;
            }
            for (JsonNode block : blocks) {
                if (blockHatRabatt(block)) return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Rabatt-Pruefung auf positionenJson fehlgeschlagen: {}", e.getMessage());
            return false;
        }
    }

    private boolean blockHatRabatt(JsonNode block) {
        if (block.path("discount").asDouble(0) > 0) return true;
        if (block.has("children") && block.get("children").isArray()) {
            for (JsonNode child : block.get("children")) {
                if (blockHatRabatt(child)) return true;
            }
        }
        return false;
    }

    /**
     * Ergebnis von {@link #korrigiereRabattBetraege(Long, String)}.
     *
     * @param geprueft         Dokumente mit Rabatt, die durchgerechnet wurden
     * @param korrigiert       Dokumente, deren Betrag abwich und angepasst wurde
     * @param projektePreisNeu Projekte, deren Auftragspreis daraufhin neu berechnet wurde
     */
    public record RabattKorrekturErgebnis(int geprueft, int korrigiert, int projektePreisNeu) {
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
        // Ohne gültiges Dokument (neuerBetrag == 0) bleibt der zuletzt berechnete Betrag stehen
        // (z. B. aus einem hochgeladenen ZUGFeRD-Angebot). Von Hand wird er nicht mehr erfasst.
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

        Map<Long, BigDecimal> kategorieMengen = kategorieMengenAus(effektiveDokumente);

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

        Map<Long, BigDecimal> kategorieMengen = kategorieMengenAus(effektive);
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

    /**
     * Summiert die Mengen der Leistungs-Positionen je Produktkategorie. Maßgeblich ist der
     * aktuelle Ordner der Leistung; ist die Leistung gelöscht, zählt die Kategorie, die beim
     * Einfügen in der Position gespeichert wurde (sofern es den Ordner noch gibt). So verliert
     * ein Projekt seine Kategorie nicht, nur weil eine Leistung aus dem Katalog verschwindet.
     */
    private Map<Long, BigDecimal> kategorieMengenAus(List<AusgangsGeschaeftsDokument> dokumente) {
        Map<Long, BigDecimal> leistungMengen = new java.util.HashMap<>();
        Map<Long, Long> kategorieAusPosition = new java.util.HashMap<>();
        for (AusgangsGeschaeftsDokument dok : dokumente) {
            LeistungsPositionen.sammleMengen(dok.getPositionenJson(), leistungMengen, kategorieAusPosition);
        }
        Map<Long, BigDecimal> kategorieMengen = new java.util.HashMap<>();
        if (leistungMengen.isEmpty()) {
            return kategorieMengen;
        }
        Set<Long> gefunden = new HashSet<>();
        for (Leistung l : leistungRepository.findAllById(leistungMengen.keySet())) {
            gefunden.add(l.getId());
            if (l.getKategorie() != null) {
                kategorieMengen.merge(l.getKategorie().getId(), leistungMengen.get(l.getId()), BigDecimal::add);
            }
        }
        Map<Long, BigDecimal> ohneLeistung = new java.util.HashMap<>();
        leistungMengen.forEach((leistungId, menge) -> {
            Long kategorieId = kategorieAusPosition.get(leistungId);
            if (!gefunden.contains(leistungId) && kategorieId != null) {
                ohneLeistung.merge(kategorieId, menge, BigDecimal::add);
            }
        });
        if (!ohneLeistung.isEmpty()) {
            for (Produktkategorie k : produktkategorieRepository.findAllById(ohneLeistung.keySet())) {
                kategorieMengen.merge(k.getId(), ohneLeistung.get(k.getId()), BigDecimal::add);
            }
        }
        return kategorieMengen;
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
                .optional(block.path("optional").asBoolean(false))
                .alternativGruppe(textOrNull(block, "alternativGruppe"));

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
     * Sammelt die Entweder-Oder-Gruppen: Gruppenname → blockIds der Varianten.
     * Gleiche Traversierung wie {@link #sammleOptionaleAlternativIds} (Top-Level +
     * eine Ebene SECTION_HEADER-children). Nur SERVICE-Blöcke, die {@code optional}
     * sind UND eine nicht-leere {@code alternativGruppe} tragen.
     *
     * <p>Reihenfolge ist stabil (LinkedHashMap), damit Fehlermeldungen an den Kunden
     * immer dieselbe Gruppe zuerst nennen.</p>
     */
    public Map<String, Set<String>> sammleAlternativGruppen(String positionenJson) {
        Map<String, Set<String>> gruppen = new LinkedHashMap<>();
        JsonNode blocks = leseBlocks(positionenJson);
        if (blocks == null) return gruppen;
        for (JsonNode block : blocks) {
            sammleAlternativGruppen(block, gruppen);
        }
        return gruppen;
    }

    private void sammleAlternativGruppen(JsonNode block, Map<String, Set<String>> gruppen) {
        String typ = textOrNull(block, "type");
        if ("SERVICE".equals(typ) && block.path("optional").asBoolean(false)) {
            String gruppe = textOrNull(block, "alternativGruppe");
            String id = textOrNull(block, "id");
            if (gruppe != null && !gruppe.isBlank() && id != null) {
                gruppen.computeIfAbsent(gruppe, k -> new LinkedHashSet<>()).add(id);
            }
        }
        if ("SECTION_HEADER".equals(typ) && block.has("children") && block.get("children").isArray()) {
            for (JsonNode child : block.get("children")) {
                sammleAlternativGruppen(child, gruppen);
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
     * Bereitet das positionenJson für die Auftragsbestätigung auf:
     *
     * <ol>
     *   <li>Gewählte Wahlpositionen werden zu festen Positionen ({@code optional=false}).</li>
     *   <li>Alles übrige Abgewählte wird ENTFERNT — Varianten wie Zusatzpositionen.</li>
     * </ol>
     *
     * <p>Punkt 2 ist bewusst eine einheitliche Regel: Die AB geht automatisch und
     * verbindlich raus. Eine abgewählte Variante als durchgestrichene 0,00-Zeile neben
     * der gewählten wäre in einem Vertragsdokument irreführend. Was der Kunde nicht
     * beauftragt hat, steht weiterhin im Angebot.</p>
     *
     * <p>{@code blockIds} darf leer sein — dann fliegen alle Wahlpositionen raus.</p>
     */
    public String markiereAlternativenAlsBeauftragt(String positionenJson, Set<String> blockIds) {
        if (positionenJson == null || positionenJson.isBlank()) {
            return positionenJson;
        }
        Set<String> gewaehlt = blockIds != null ? blockIds : Set.of();
        try {
            JsonNode root = ALTERNATIV_MAPPER.readTree(positionenJson);
            JsonNode blocks = root.isArray() ? root
                    : (root.isObject() && root.has("blocks") && root.get("blocks").isArray() ? root.get("blocks") : null);
            if (blocks == null) return positionenJson;
            for (JsonNode block : blocks) {
                markiereBlock(block, gewaehlt);
            }
            entferneAbgewaehlte(blocks);
            return ALTERNATIV_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Fehler beim Aufbereiten der beauftragten Positionen: {}", e.getMessage());
            return positionenJson;
        }
    }

    /**
     * Entfernt alle SERVICE-Blöcke, die nach {@link #markiereBlock} noch {@code optional}
     * tragen — das sind genau die, die der Kunde nicht beauftragt hat.
     */
    private void entferneAbgewaehlte(JsonNode ebene) {
        if (!(ebene instanceof ArrayNode arr)) return;
        Iterator<JsonNode> it = arr.elements();
        while (it.hasNext()) {
            JsonNode block = it.next();
            String typ = textOrNull(block, "type");
            if ("SERVICE".equals(typ) && block.path("optional").asBoolean(false)) {
                it.remove();
                continue;
            }
            if ("SECTION_HEADER".equals(typ)) {
                entferneAbgewaehlte(block.get("children"));
            }
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
