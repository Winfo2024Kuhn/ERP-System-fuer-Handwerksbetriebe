package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegAufteilungsModus;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegKiAnalyseStatus;
import org.example.kalkulationsprogramm.domain.BelegPosition;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.dto.BelegDto;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Kernservice für das Beleg-Modul.
 *
 * - Upload (Mobile + PC): speichert Datei, legt Beleg mit Status=NEU an,
 *   stößt asynchrone KI-Analyse an. KI darf das HTTP-Response NICHT blockieren.
 * - Update: Validierung am PC, korrigiert KI-Vorschläge.
 * - Kassenbuch: chronologische Liste der Bar-Bewegungen mit laufendem Saldo.
 * - Permission-Check: Mitarbeiter darf scannen/sehen, wenn er einer Abteilung
 *   angehört, die für LieferantDokumentTyp.BELEG entsprechend berechtigt ist
 *   (verwaltet unter Administration → Lieferanten-Dokumentenrechte).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BelegService {

    private static final String BELEG_TYP = LieferantDokumentTyp.BELEG.name();

    private final BelegRepository belegRepository;
    private final LieferantenRepository lieferantenRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final AbteilungDokumentBerechtigungRepository berechtigungRepository;
    private final SachkontoRepository sachkontoRepository;
    private final KostenstelleRepository kostenstelleRepository;
    private final BelegKiAnalyseService kiAnalyseService;
    private final LieferantDokumentRepository lieferantDokumentRepository;
    private final FrontendUserProfileRepository frontendUserProfileRepository;
    private final BelegSplitService belegSplitService;
    private final org.example.kalkulationsprogramm.repository.BelegPositionRepository belegPositionRepository;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    /** Whitelist der akzeptierten Beleg-MIME-Types. Alles andere wird abgelehnt. */
    private static final Set<String> ERLAUBTE_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp",
            "image/heic", "image/heif", "application/pdf");

    /** Whitelist der Dateiendungen. Defense-in-Depth zu MIME — Client darf nicht entscheiden. */
    private static final Set<String> ERLAUBTE_ENDUNGEN = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".pdf");

    /** Max. Belegdateigröße. Globale multipart-max-file-size ist zu großzügig. */
    private static final long MAX_DATEI_BYTES = 25L * 1024 * 1024;

    // ===================== Permissions =====================

    public BelegDto.PermissionResponse getPermissions(Mitarbeiter mitarbeiter) {
        if (mitarbeiter == null || mitarbeiter.getAbteilungen() == null
                || mitarbeiter.getAbteilungen().isEmpty()) {
            return BelegDto.PermissionResponse.builder()
                    .darfScannen(false).darfSehen(false).build();
        }
        List<Long> abteilungIds = mitarbeiter.getAbteilungen().stream()
                .map(a -> a.getId()).toList();
        Set<String> sichtbar = Set.copyOf(berechtigungRepository.findSichtbareTypenByAbteilungIds(abteilungIds));
        Set<String> scanbar = Set.copyOf(berechtigungRepository.findScanbarTypenByAbteilungIds(abteilungIds));
        return BelegDto.PermissionResponse.builder()
                .darfSehen(sichtbar.contains(BELEG_TYP))
                .darfScannen(scanbar.contains(BELEG_TYP))
                .build();
    }

    public boolean darfScannen(Mitarbeiter mitarbeiter) {
        return getPermissions(mitarbeiter).isDarfScannen();
    }

    public boolean darfSehen(Mitarbeiter mitarbeiter) {
        return getPermissions(mitarbeiter).isDarfSehen();
    }

    // ===================== Upload =====================

    /**
     * Nimmt eine Datei vom Scanner entgegen, speichert sie und legt einen Beleg
     * mit Status=NEU an. Stößt anschließend die asynchrone KI-Analyse an.
     *
     * Der Aufrufer (Mobile/PC) blockiert nicht auf der KI — sobald die Antwort
     * mit der Beleg-ID zurückkommt, ist der Scanner wieder bereit für den
     * nächsten Beleg.
     */
    @Transactional
    public Beleg uploadBeleg(MultipartFile datei, Mitarbeiter uploader) throws IOException {
        return uploadBeleg(datei, null, BelegAufteilungsModus.VOLLSTAENDIG, uploader);
    }

    /** Backwards-Compat-Overload: ohne expliziten Modus -> VOLLSTAENDIG. */
    @Transactional
    public Beleg uploadBeleg(MultipartFile datei, Long lieferantId, Mitarbeiter uploader) throws IOException {
        return uploadBeleg(datei, lieferantId, BelegAufteilungsModus.VOLLSTAENDIG, uploader);
    }

    /**
     * Wie {@link #uploadBeleg(MultipartFile, Mitarbeiter)}, aber mit optionaler
     * Lieferanten-Vorauswahl vom Mobile-Scanner. Wenn der Scanner einen
     * Lieferanten setzt, wird er sofort am Beleg gespeichert; die KI sieht das
     * als "schon zugeordnet" und versucht nicht mehr per Namens-Match. Ist die
     * KI-Klassifikation spaeter RECHNUNG/GUTSCHRIFT, wird automatisch ein
     * LieferantGeschaeftsdokument angelegt (siehe BelegKiAnalyseService).
     */
    @Transactional
    public Beleg uploadBeleg(MultipartFile datei, Long lieferantId,
                             BelegAufteilungsModus aufteilungsModus,
                             Mitarbeiter uploader) throws IOException {
        if (datei == null || datei.isEmpty()) {
            throw new IllegalArgumentException("Datei fehlt");
        }
        if (datei.getSize() > MAX_DATEI_BYTES) {
            throw new IllegalArgumentException("Datei zu groß (max. 25 MB)");
        }

        String originalFilename = StringUtils.cleanPath(
                Objects.requireNonNullElse(datei.getOriginalFilename(), "beleg"));
        // Pfad-Traversal blocken (analog zu RechnungsuebersichtController)
        if (originalFilename.contains("..") || originalFilename.contains("/")
                || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("Ungueltiger Dateiname");
        }

        // MIME + Extension müssen beide auf der Whitelist stehen.
        // SVG würde z.B. als image/* durchrutschen, ist hier aber nicht in
        // ERLAUBTE_MIME_TYPES — Schutz vor Stored-XSS via Beleg-Vorschau.
        String mimeType = datei.getContentType() != null
                ? datei.getContentType().toLowerCase(Locale.ROOT) : "";
        if (!ERLAUBTE_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException(
                    "Dateityp nicht erlaubt. Erlaubt sind: JPG, PNG, WEBP, HEIC, PDF");
        }
        String lower = originalFilename.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        String endung = dot >= 0 ? lower.substring(dot) : "";
        if (!ERLAUBTE_ENDUNGEN.contains(endung)) {
            throw new IllegalArgumentException(
                    "Dateiendung nicht erlaubt. Erlaubt sind: " + String.join(", ", ERLAUBTE_ENDUNGEN));
        }

        String storedFilename = UUID.randomUUID() + "_" + originalFilename;
        Path belegDir = Paths.get(uploadPath, "belege");
        Files.createDirectories(belegDir);
        Path target = belegDir.resolve(storedFilename);
        datei.transferTo(target);

        Beleg beleg = new Beleg();
        beleg.setStatus(BelegStatus.NEU);
        beleg.setKiAnalyseStatus(BelegKiAnalyseStatus.PENDING);
        beleg.setBelegKategorie(BelegKategorie.UNZUGEORDNET);
        beleg.setAufteilungsModus(aufteilungsModus != null
                ? aufteilungsModus : BelegAufteilungsModus.VOLLSTAENDIG);
        beleg.setOriginalDateiname(originalFilename);
        beleg.setGespeicherterDateiname(storedFilename);
        beleg.setMimeType(mimeType);
        beleg.setUploadDatum(LocalDateTime.now());
        beleg.setUploadedBy(uploader);

        // Optional vom Mobile-Scanner vorausgewaehlten Lieferant zuordnen.
        // findById gibt Optional<> — ungueltige ID wird ignoriert (kein Hard-Fail,
        // damit ein veralteter Mobile-Client den Upload nicht zerstoert).
        if (lieferantId != null) {
            lieferantenRepository.findById(lieferantId).ifPresent(beleg::setLieferant);
        }

        beleg = belegRepository.save(beleg);

        // Async-Start NACH Transaktions-Commit. Sonst würde der Async-Thread
        // den frisch persistierten Beleg in seiner eigenen Transaktion noch
        // nicht sehen und mit "Beleg null" abbrechen.
        final Long belegId = beleg.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        kiAnalyseService.analysiereBelegAsync(belegId);
                    } catch (Exception e) {
                        log.warn("KI-Analyse fuer Beleg {} konnte nicht angestossen werden: {}",
                                belegId, e.getMessage());
                    }
                }
            });
        } else {
            // Falls keine Transaktion aktiv ist (Tests etc.) — direkter Aufruf reicht.
            try {
                kiAnalyseService.analysiereBelegAsync(belegId);
            } catch (Exception e) {
                log.warn("KI-Analyse fuer Beleg {} konnte nicht angestossen werden: {}",
                        belegId, e.getMessage());
            }
        }

        return beleg;
    }

    // ===================== Read =====================

    @Transactional(readOnly = true)
    public List<BelegDto.Response> listBelege(BelegStatus statusFilter, BelegKategorie kategorieFilter) {
        List<Beleg> list;
        if (statusFilter != null && kategorieFilter != null) {
            list = belegRepository.findByStatusAndBelegKategorieOrderByBelegDatumDesc(statusFilter, kategorieFilter);
        } else if (statusFilter != null) {
            list = belegRepository.findByStatusOrderByUploadDatumDesc(statusFilter);
        } else {
            list = belegRepository.findAllByOrderByUploadDatumDesc();
        }
        return list.stream().map(b -> toDto(b, false)).toList();
    }

    @Transactional(readOnly = true)
    public BelegDto.Response getBeleg(Long id) {
        return belegRepository.findById(id).map(b -> toDto(b, true)).orElse(null);
    }

    @Transactional(readOnly = true)
    public Beleg getRawBeleg(Long id) {
        return belegRepository.findById(id).orElse(null);
    }

    public Path getBelegDatei(Beleg beleg) {
        return Paths.get(uploadPath, "belege", beleg.getGespeicherterDateiname());
    }

    // ===================== Update / Validierung =====================

    @Transactional
    public BelegDto.Response updateBeleg(Long id, BelegDto.UpdateRequest req, Mitarbeiter validierer) {
        Beleg beleg = belegRepository.findById(id).orElse(null);
        if (beleg == null) {
            return null;
        }

        if (req.getBelegKategorie() != null) {
            try {
                beleg.setBelegKategorie(BelegKategorie.valueOf(req.getBelegKategorie()));
            } catch (IllegalArgumentException ignored) {
                // ungueltige Kategorie -> Feld unveraendert lassen
            }
        }
        if (req.getAufteilungsModus() != null) {
            try {
                BelegAufteilungsModus neu = BelegAufteilungsModus.valueOf(req.getAufteilungsModus());
                BelegAufteilungsModus alt = beleg.getAufteilungsModus();
                beleg.setAufteilungsModus(neu);
                // Bei Wechsel werden Firma-Summen via SplitService neu berechnet
                // (bei VOLLSTAENDIG -> Felder werden geleert).
                if (alt != neu) {
                    belegSplitService.recomputeFirmaSummen(beleg);
                }
            } catch (IllegalArgumentException ignored) {
                // ungueltiger Modus -> unveraendert lassen
            }
        }
        if (req.getStatus() != null) {
            try {
                BelegStatus neuerStatus = BelegStatus.valueOf(req.getStatus());
                boolean wechselAufValidiert = beleg.getStatus() != BelegStatus.VALIDIERT
                        && neuerStatus == BelegStatus.VALIDIERT;
                beleg.setStatus(neuerStatus);
                if (wechselAufValidiert) {
                    beleg.setValidiertAm(LocalDateTime.now());
                    beleg.setValidiertVon(validierer);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (req.getBelegDatum() != null) beleg.setBelegDatum(req.getBelegDatum());
        if (req.getBelegNummer() != null) beleg.setBelegNummer(req.getBelegNummer());
        if (req.getBeschreibung() != null) beleg.setBeschreibung(req.getBeschreibung());
        if (req.getBetragNetto() != null) beleg.setBetragNetto(req.getBetragNetto());
        if (req.getBetragBrutto() != null) beleg.setBetragBrutto(req.getBetragBrutto());
        if (req.getMwstSatz() != null) beleg.setMwstSatz(req.getMwstSatz());
        if (req.getZahlungsart() != null) beleg.setZahlungsart(req.getZahlungsart());
        if (req.getNotiz() != null) beleg.setNotiz(req.getNotiz());

        if (req.getLieferantId() != null) {
            Lieferanten l = lieferantenRepository.findById(req.getLieferantId()).orElse(null);
            beleg.setLieferant(l);
        }
        if (req.getSachkontoId() != null) {
            Sachkonto sk = sachkontoRepository.findById(req.getSachkontoId()).orElse(null);
            beleg.setSachkonto(sk);
        }
        // Kostenstelle: 0 oder negativ wird als "abwaehlen" interpretiert,
        // ungueltige ID setzt zurueck — sonst Standard-Lookup.
        if (req.getKostenstelleId() != null) {
            if (req.getKostenstelleId() <= 0L) {
                beleg.setKostenstelle(null);
            } else {
                Kostenstelle ks = kostenstelleRepository.findById(req.getKostenstelleId()).orElse(null);
                beleg.setKostenstelle(ks);
            }
        }

        belegRepository.save(beleg);
        return toDto(beleg);
    }

    /**
     * Wrapper fuer den Mobile-Endpoint: nimmt die Checkbox-Auswahl entgegen
     * und gibt direkt das aktualisierte Beleg-DTO inklusive frisch berechneter
     * Firma-Summen zurueck.
     */
    @Transactional
    public BelegDto.Response setzePositionsAuswahl(Long belegId, List<Long> firmaPositionIds) {
        java.util.Set<Long> ids = firmaPositionIds == null
                ? java.util.Set.of()
                : java.util.Set.copyOf(firmaPositionIds);
        Beleg beleg = belegSplitService.aktualisiereAuswahl(belegId, ids);
        return toDto(beleg);
    }

    @Transactional
    public boolean deleteBeleg(Long id) {
        Beleg beleg = belegRepository.findById(id).orElse(null);
        if (beleg == null) {
            return false;
        }
        // Datei zur Sicherheit nicht löschen — falls Steuerprüfung. Soft via VERWORFEN.
        beleg.setStatus(BelegStatus.VERWORFEN);
        belegRepository.save(beleg);
        return true;
    }

    // ===================== Umbuchung (ohne Beleg-Datei) =====================

    /**
     * Legt eine belegfreie Buchhaltungs-Bewegung an (Privatentnahme, Privat->Firma,
     * Kasse->Bank etc.). Wird vom PC ueber POST /api/buchhaltung/umbuchungen
     * erfasst; Mobile-Clients verwenden weiterhin nur den Datei-Upload.
     *
     * Der erzeugte Beleg ist sofort VALIDIERT (es gibt nichts mehr zu pruefen),
     * mit ist_umbuchung=true markiert und ohne Datei. Der Service blockt
     * unzulaessige Kategorien (UNZUGEORDNET, SONSTIGER_BELEG) — Umbuchungen
     * gehoeren in eine Kassen-/Bankkategorie, sonst landen sie nicht im Kassenbuch.
     */
    @Transactional
    public Beleg createUmbuchung(BelegDto.UmbuchungCreateRequest req, Mitarbeiter ersteller) {
        if (req == null) {
            throw new IllegalArgumentException("Anfrage fehlt");
        }
        if (req.getBelegKategorie() == null || req.getBelegKategorie().isBlank()) {
            throw new IllegalArgumentException("Kategorie fehlt");
        }
        BelegKategorie kategorie;
        try {
            kategorie = BelegKategorie.valueOf(req.getBelegKategorie());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ungueltige Kategorie");
        }
        // Umbuchungen nur fuer Kassen-/Bank-Kategorien. UNZUGEORDNET und
        // SONSTIGER_BELEG waeren sonst Geisterbuchungen ohne Wirkung im
        // Kassenbuch / der Auswertung.
        if (kategorie == BelegKategorie.UNZUGEORDNET || kategorie == BelegKategorie.SONSTIGER_BELEG) {
            throw new IllegalArgumentException(
                    "Umbuchungen muessen einer Kassen-, Bank- oder Privat-Kategorie zugeordnet sein");
        }
        if (req.getBetragBrutto() == null || req.getBetragBrutto().signum() <= 0) {
            throw new IllegalArgumentException("Betrag fehlt oder ist nicht positiv");
        }
        if (req.getBelegDatum() == null) {
            throw new IllegalArgumentException("Datum fehlt");
        }
        // Sanity: 10.000-Zeichen-Limit auf Freitextfelder (TESTING_SECURITY 4)
        if (req.getBeschreibung() != null && req.getBeschreibung().length() > 500) {
            throw new IllegalArgumentException("Beschreibung zu lang (max. 500 Zeichen)");
        }
        if (req.getNotiz() != null && req.getNotiz().length() > 1000) {
            throw new IllegalArgumentException("Notiz zu lang (max. 1000 Zeichen)");
        }

        Beleg beleg = new Beleg();
        beleg.setStatus(BelegStatus.VALIDIERT);
        beleg.setKiAnalyseStatus(BelegKiAnalyseStatus.DONE); // keine KI noetig — direkt erfasst
        beleg.setBelegKategorie(kategorie);
        beleg.setIstUmbuchung(true);
        beleg.setBelegDatum(req.getBelegDatum());
        beleg.setBetragBrutto(req.getBetragBrutto());
        beleg.setBeschreibung(req.getBeschreibung());
        beleg.setZahlungsart(req.getZahlungsart());
        beleg.setNotiz(req.getNotiz());
        beleg.setUploadDatum(LocalDateTime.now());
        beleg.setUploadedBy(ersteller);
        beleg.setValidiertAm(LocalDateTime.now());
        beleg.setValidiertVon(ersteller);

        if (req.getSachkontoId() != null) {
            sachkontoRepository.findById(req.getSachkontoId()).ifPresent(beleg::setSachkonto);
        }

        return belegRepository.save(beleg);
    }

    // ===================== Kassenbuch =====================

    @Transactional(readOnly = true)
    public BelegDto.KassenbuchResponse getKassenbuch(LocalDate von, LocalDate bis) {
        List<Beleg> belege = belegRepository.findValidierteByKategorien(
                BelegStatus.VALIDIERT,
                List.of(BelegKategorie.KASSE_EINNAHME,
                        BelegKategorie.KASSE_AUSGABE,
                        BelegKategorie.PRIVATENTNAHME,
                        BelegKategorie.PRIVATEINLAGE));

        // Saldo-Start: Summe aller Bar-Bewegungen VOR dem Startdatum
        BigDecimal saldoStart = BigDecimal.ZERO;
        if (von != null) {
            for (Beleg b : belege) {
                if (b.getBelegDatum() == null) continue;
                if (b.getBelegDatum().isBefore(von)) {
                    saldoStart = saldoStart.add(signedBetrag(b));
                }
            }
        }

        // Bewegungen im Zeitraum, chronologisch aufsteigend für laufenden Saldo
        List<Beleg> imZeitraum = new ArrayList<>();
        for (Beleg b : belege) {
            LocalDate d = b.getBelegDatum();
            if (d == null) continue;
            if (von != null && d.isBefore(von)) continue;
            if (bis != null && d.isAfter(bis)) continue;
            imZeitraum.add(b);
        }
        imZeitraum.sort(Comparator
                .comparing(Beleg::getBelegDatum, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Beleg::getId));

        BigDecimal saldo = saldoStart;
        BigDecimal sumEin = BigDecimal.ZERO;
        BigDecimal sumAus = BigDecimal.ZERO;
        BigDecimal sumPriv = BigDecimal.ZERO;
        BigDecimal sumPrivEinlage = BigDecimal.ZERO;

        List<BelegDto.KassenBewegung> bewegungen = new ArrayList<>(imZeitraum.size());
        for (Beleg b : imZeitraum) {
            BigDecimal signed = signedBetrag(b);
            saldo = saldo.add(signed);
            switch (b.getBelegKategorie()) {
                case KASSE_EINNAHME -> sumEin = sumEin.add(nullSafe(b.getBetragBrutto()));
                case KASSE_AUSGABE -> sumAus = sumAus.add(nullSafe(b.getBetragBrutto()));
                case PRIVATENTNAHME -> sumPriv = sumPriv.add(nullSafe(b.getBetragBrutto()));
                case PRIVATEINLAGE -> sumPrivEinlage = sumPrivEinlage.add(nullSafe(b.getBetragBrutto()));
                default -> {
                    // andere Kategorien sind hier nicht enthalten
                }
            }
            bewegungen.add(BelegDto.KassenBewegung.builder()
                    .belegId(b.getId())
                    .datum(b.getBelegDatum())
                    .kategorie(b.getBelegKategorie().name())
                    .beschreibung(b.getBeschreibung() != null ? b.getBeschreibung() : b.getBelegNummer())
                    .lieferantName(b.getLieferant() != null ? b.getLieferant().getLieferantenname() : null)
                    .betrag(signed.setScale(2, RoundingMode.HALF_UP))
                    .saldoNachher(saldo.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        return BelegDto.KassenbuchResponse.builder()
                .saldoStart(saldoStart.setScale(2, RoundingMode.HALF_UP))
                .saldoEnde(saldo.setScale(2, RoundingMode.HALF_UP))
                .summeEinnahmen(sumEin.setScale(2, RoundingMode.HALF_UP))
                .summeAusgaben(sumAus.setScale(2, RoundingMode.HALF_UP))
                .summePrivatentnahmen(sumPriv.setScale(2, RoundingMode.HALF_UP))
                .summePrivateinlagen(sumPrivEinlage.setScale(2, RoundingMode.HALF_UP))
                .bewegungen(bewegungen)
                .build();
    }

    // ===================== Steuerberater-Beleg-Export (Issue #58) =====================

    /**
     * Liefert eine flache Liste validierter Belege im Zeitraum fuer den
     * Steuerberater-Export. Bei {@code aufteilungsModus=TEILWEISE} stehen in
     * {@code betragNetto/Brutto/Mwst} die Firma-Anteile statt der Gesamt-
     * Belegsummen — der Steuerberater bekommt nur den betrieblichen Anteil
     * gebucht. Die Original-Gesamt-Bruttosumme + ein Kurztext mit den
     * gewaehlten Positionen gehen als Erlaeuterung mit, damit der
     * Steuerberater den Beleg dem physisch vorliegenden Original zuordnen kann.
     */
    @Transactional(readOnly = true)
    public List<BelegDto.SteuerberaterExportEntry> listeFuerSteuerberaterExport(LocalDate von, LocalDate bis) {
        List<Beleg> belege = belegRepository.findValidierteImZeitraumFuerExport(von, bis);
        List<BelegDto.SteuerberaterExportEntry> result = new ArrayList<>(belege.size());
        for (Beleg b : belege) {
            boolean teilweise = b.getAufteilungsModus() == BelegAufteilungsModus.TEILWEISE
                    && b.getBetragFirmaBrutto() != null;

            BigDecimal netto  = teilweise ? b.getBetragFirmaNetto()  : b.getBetragNetto();
            BigDecimal brutto = teilweise ? b.getBetragFirmaBrutto() : b.getBetragBrutto();
            BigDecimal mwst   = teilweise ? b.getBetragFirmaMwst()   : differenzMwst(b);

            Integer positionenGesamt = null;
            Integer positionenFirma  = null;
            String positionenHinweis = null;
            if (teilweise) {
                List<BelegPosition> positionen =
                        belegPositionRepository.findByBelegIdOrderBySortierungAsc(b.getId());
                positionenGesamt = positionen.size();
                List<BelegPosition> firma = positionen.stream()
                        .filter(BelegPosition::isIstFuerFirma)
                        .toList();
                positionenFirma = firma.size();
                positionenHinweis = buildPositionenHinweis(firma);
            }

            result.add(BelegDto.SteuerberaterExportEntry.builder()
                    .belegId(b.getId())
                    .belegDatum(b.getBelegDatum())
                    .belegNummer(b.getBelegNummer())
                    .lieferantName(b.getLieferant() != null ? b.getLieferant().getLieferantenname() : null)
                    .belegKategorie(b.getBelegKategorie() != null ? b.getBelegKategorie().name() : null)
                    .dokumentTyp(b.getDokumentTyp() != null ? b.getDokumentTyp().name() : null)
                    .sachkontoNummer(b.getSachkonto() != null ? b.getSachkonto().getNummer() : null)
                    .sachkontoBezeichnung(b.getSachkonto() != null ? b.getSachkonto().getBezeichnung() : null)
                    .betragNetto(netto)
                    .betragBrutto(brutto)
                    .betragMwst(mwst)
                    .mwstSatz(b.getMwstSatz())
                    .notiz(b.getNotiz())
                    .beschreibung(b.getBeschreibung())
                    .aufteilungsModus(b.getAufteilungsModus() != null ? b.getAufteilungsModus().name() : null)
                    .gesamtBruttoOriginal(teilweise ? b.getBetragBrutto() : null)
                    .anzahlPositionenGesamt(positionenGesamt)
                    .anzahlPositionenFirma(positionenFirma)
                    .positionenHinweis(positionenHinweis)
                    .build());
        }
        return result;
    }

    /**
     * Baut einen Kurztext aus den ersten 3 Positionen, der dem Steuerberater
     * eine Orientierung gibt, welche Bestandteile betrieblich gebucht wurden.
     * Bei mehr als 3 Positionen wird mit "(+N weitere)" abgeschlossen.
     */
    private String buildPositionenHinweis(List<BelegPosition> firmaPositionen) {
        if (firmaPositionen == null || firmaPositionen.isEmpty()) return null;
        int limit = Math.min(3, firmaPositionen.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            BelegPosition p = firmaPositionen.get(i);
            String txt = p.getBeschreibung() != null && !p.getBeschreibung().isBlank()
                    ? p.getBeschreibung().trim()
                    : ("Pos " + p.getSortierung());
            if (i > 0) sb.append(" · ");
            sb.append(txt);
        }
        int weitere = firmaPositionen.size() - limit;
        if (weitere > 0) sb.append(" (+").append(weitere).append(" weitere)");
        return sb.toString();
    }

    /** Falls die MwSt nicht direkt am Beleg liegt: differenzberechnet aus brutto - netto. */
    private BigDecimal differenzMwst(Beleg b) {
        BigDecimal brutto = b.getBetragBrutto();
        BigDecimal netto = b.getBetragNetto();
        if (brutto == null || netto == null) return null;
        return brutto.subtract(netto);
    }

    private BigDecimal signedBetrag(Beleg b) {
        BigDecimal brutto = nullSafe(b.getBetragBrutto());
        return b.getBelegKategorie().istAusgang() ? brutto.negate() : brutto;
    }

    private BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // ===================== DTO-Mapping =====================

    public BelegDto.Response toDto(Beleg b) {
        // Default: mit Positionen — fuer Detail-Views und Auto-Tests, in denen
        // direkt nach Upload das DTO gerendert wird. Listen-Endpoints rufen
        // den (Beleg, boolean)-Overload mit mitPositionen=false.
        return toDto(b, true);
    }

    public BelegDto.Response toDto(Beleg b, boolean mitPositionen) {
        // Sub-Query auf lieferant_dokument(beleg_id) — wird nur fuer Belege benoetigt,
        // die als RECHNUNG/GUTSCHRIFT klassifiziert wurden. Spart eine Spalte am Beleg,
        // ist aber bei Listings ein N+1. Bei groesseren Datenmengen sollte das Repository
        // alle eingangsrechnungIds in einer Query nachladen — vorerst akzeptabel, weil
        // Belege seitenweise + chronologisch gefiltert werden (Eingang/Alle/Privat etc.).
        Long eingangsrechnungId = null;
        if (b.getDokumentTyp() == LieferantDokumentTyp.RECHNUNG
                || b.getDokumentTyp() == LieferantDokumentTyp.GUTSCHRIFT) {
            eingangsrechnungId = lieferantDokumentRepository.findByBelegId(b.getId())
                    .map(d -> d.getGeschaeftsdaten() != null ? d.getGeschaeftsdaten().getId() : null)
                    .orElse(null);
        }
        return BelegDto.Response.builder()
                .id(b.getId())
                .belegKategorie(b.getBelegKategorie() != null ? b.getBelegKategorie().name() : null)
                .dokumentTyp(b.getDokumentTyp() != null ? b.getDokumentTyp().name() : null)
                .istUmbuchung(Boolean.TRUE.equals(b.getIstUmbuchung()))
                .status(b.getStatus() != null ? b.getStatus().name() : null)
                .kiAnalyseStatus(b.getKiAnalyseStatus() != null ? b.getKiAnalyseStatus().name() : null)
                .belegDatum(b.getBelegDatum())
                .belegNummer(b.getBelegNummer())
                .beschreibung(b.getBeschreibung())
                .betragNetto(b.getBetragNetto())
                .betragBrutto(b.getBetragBrutto())
                .mwstSatz(b.getMwstSatz())
                .zahlungsart(b.getZahlungsart())
                .lieferantId(b.getLieferant() != null ? b.getLieferant().getId() : null)
                .lieferantName(b.getLieferant() != null ? b.getLieferant().getLieferantenname() : null)
                .sachkontoId(b.getSachkonto() != null ? b.getSachkonto().getId() : null)
                .sachkontoBezeichnung(b.getSachkonto() != null ? b.getSachkonto().getBezeichnung() : null)
                .sachkontoNummer(b.getSachkonto() != null ? b.getSachkonto().getNummer() : null)
                .sachkontoTyp(b.getSachkonto() != null && b.getSachkonto().getKontoTyp() != null
                        ? b.getSachkonto().getKontoTyp().name() : null)
                .kostenstelleId(b.getKostenstelle() != null ? b.getKostenstelle().getId() : null)
                .kostenstelleBezeichnung(b.getKostenstelle() != null ? b.getKostenstelle().getBezeichnung() : null)
                .kostenstelleTyp(b.getKostenstelle() != null && b.getKostenstelle().getTyp() != null
                        ? b.getKostenstelle().getTyp().name() : null)
                .kostenstelleIstFixkosten(b.getKostenstelle() != null
                        ? b.getKostenstelle().isIstFixkosten() : null)
                .kiVorgeschlagenerLieferant(b.getKiVorgeschlagenerLieferant())
                .kiConfidence(b.getKiConfidence())
                .kiVorgeschlagenerKostenstelleId(b.getKiVorgeschlagenerKostenstelleId())
                .kiVorgeschlagenerKostenstelleBezeichnung(
                        b.getKiVorgeschlagenerKostenstelleId() != null
                                ? kostenstelleRepository.findById(b.getKiVorgeschlagenerKostenstelleId())
                                        .map(Kostenstelle::getBezeichnung).orElse(null)
                                : null)
                .kiVorgeschlagenerSachkontoId(b.getKiVorgeschlagenerSachkontoId())
                .kiVorgeschlagenerSachkontoBezeichnung(
                        b.getKiVorgeschlagenerSachkontoId() != null
                                ? sachkontoRepository.findById(b.getKiVorgeschlagenerSachkontoId())
                                        .map(Sachkonto::getBezeichnung).orElse(null)
                                : null)
                .kiKostenkontoConfidence(b.getKiKostenkontoConfidence())
                .kiKostenkontoBegruendung(b.getKiKostenkontoBegruendung())
                .kiFehlerText(b.getKiFehlerText())
                .originalDateiname(b.getOriginalDateiname())
                .mimeType(b.getMimeType())
                .uploadDatum(b.getUploadDatum())
                .uploadedById(b.getUploadedBy() != null ? b.getUploadedBy().getId() : null)
                .uploadedByName(mitarbeiterName(b.getUploadedBy()))
                .validiertAm(b.getValidiertAm())
                .validiertVonId(b.getValidiertVon() != null ? b.getValidiertVon().getId() : null)
                .validiertVonName(mitarbeiterName(b.getValidiertVon()))
                .notiz(b.getNotiz())
                .eingangsrechnungId(eingangsrechnungId)
                .aufteilungsModus(b.getAufteilungsModus() != null ? b.getAufteilungsModus().name() : null)
                .betragFirmaNetto(b.getBetragFirmaNetto())
                .betragFirmaBrutto(b.getBetragFirmaBrutto())
                .betragFirmaMwst(b.getBetragFirmaMwst())
                .positionen(mitPositionen && b.getAufteilungsModus() == BelegAufteilungsModus.TEILWEISE
                        ? toPositionResponse(belegPositionRepository.findByBelegIdOrderBySortierungAsc(b.getId()))
                        : java.util.Collections.emptyList())
                .build();
    }

    private List<BelegDto.PositionResponse> toPositionResponse(List<BelegPosition> positionen) {
        return positionen.stream()
                .map(p -> BelegDto.PositionResponse.builder()
                        .id(p.getId())
                        .sortierung(p.getSortierung())
                        .beschreibung(p.getBeschreibung())
                        .menge(p.getMenge())
                        .einheit(p.getEinheit())
                        .einzelpreis(p.getEinzelpreis())
                        .betragNetto(p.getBetragNetto())
                        .betragBrutto(p.getBetragBrutto())
                        .mwstSatz(p.getMwstSatz())
                        .istFuerFirma(p.isIstFuerFirma())
                        .build())
                .toList();
    }

    private String mitarbeiterName(Mitarbeiter m) {
        if (m == null) return null;
        String v = m.getVorname() != null ? m.getVorname() : "";
        String n = m.getNachname() != null ? m.getNachname() : "";
        return (v + " " + n).trim();
    }

    // Helper: Mitarbeiter per Login-Token holen — wird vom Controller benutzt,
    // damit alle Token-Lookups an einer Stelle liegen.
    public Mitarbeiter findByToken(String token) {
        if (token == null || token.isBlank()) return null;
        // findByLoginTokenAndAktivTrue: deaktivierte Mitarbeiter mit altem Token
        // verlieren sofort den Zugriff (konsistent mit Zeiterfassung-Login).
        return mitarbeiterRepository.findByLoginTokenAndAktivTrue(token).orElse(null);
    }

    /**
     * Zentraler Auth-Mapping fuer Buchhaltungs-Controller. Reihenfolge:
     * 1) Mobile (token im Querystring) -> Mitarbeiter via loginToken.
     * 2) PC (Session-Auth) -> FrontendUserProfile per ID -> verknuepfter Mitarbeiter
     *    ueber die direkte FK (FrontendUserProfile.mitarbeiter). Das ist der
     *    eigentliche Verknuepfungspfad; das E-Mail-Mapping ist nur ein Fallback
     *    fuer Altdaten, bei denen die FK noch nicht gesetzt wurde.
     * 3) Fallback: Mitarbeiter mit gleicher E-Mail wie der Frontend-Username.
     *
     * Liefert null wenn niemand sicher zugeordnet werden kann.
     */
    @Transactional(readOnly = true)
    public Mitarbeiter findCaller(String token, Authentication auth) {
        Mitarbeiter byToken = findByToken(token);
        if (byToken != null) return byToken;
        if (auth == null || !(auth.getPrincipal() instanceof FrontendUserPrincipal principal)) {
            return null;
        }
        if (principal.getId() != null) {
            Mitarbeiter linked = frontendUserProfileRepository.findById(principal.getId())
                    .map(p -> p.getMitarbeiter())
                    .orElse(null);
            if (linked != null && Boolean.TRUE.equals(linked.getAktiv())) {
                return linked;
            }
        }
        String username = principal.getUsername();
        if (username == null || username.isBlank()) return null;
        return mitarbeiterRepository.findAll().stream()
                .filter(m -> Boolean.TRUE.equals(m.getAktiv()))
                .filter(m -> username.equalsIgnoreCase(m.getEmail()))
                .findFirst().orElse(null);
    }

}
