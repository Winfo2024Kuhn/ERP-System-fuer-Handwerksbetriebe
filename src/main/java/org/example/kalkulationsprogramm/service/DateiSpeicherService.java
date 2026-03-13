package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Dokument;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mahnstufe;
import org.example.kalkulationsprogramm.domain.Materialkosten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.dto.Projekt.ConversionRateDto;
import org.example.kalkulationsprogramm.dto.Projekt.KategorieUmsatzVergleichDto;
import org.example.kalkulationsprogramm.dto.Projekt.MonatsumsatzDto;
import org.example.kalkulationsprogramm.dto.Projekt.OrtHeatmapDto;
import org.example.kalkulationsprogramm.dto.Projekt.UmsatzStatistikDto;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.exception.ForbiddenException;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.transaction.Transactional;
import lombok.Setter;

@Service
public class DateiSpeicherService {
    private final ProjektDokumentRepository dokumentRepository;
    private final ProjektRepository projektRepository;
    private final AnfrageDokumentRepository anfrageDokumentRepository;
    private final AnfrageRepository anfrageRepository;
    private final Path dokumentenSpeicherplatz;
    private final Path hicadSpeicherplatz;
    private final Path anfragenSpeicherplatz;
    private final Path bilderSpeicherplatz;
    private final Path schnittbilderSpeicherplatz;
    private final ProduktkategorieRepository produktkategorieRepository;
    private final KundeRepository kundeRepository;
    private final ZugferdExtractorService zugferdExtractorService;
    private final ProduktkategorieMapper produktkategorieMapper;
    private final String hicadNetworkUrl;
    private final String networkDriveLetter;
    private static final List<String> ERLAUBTE_BILD_TYPEN = List.of("image/jpeg", "image/png", "image/gif",
            "image/webp");
    private static final Pattern AUSGANGSRECHNUNG_INVOICE_PATTERN = Pattern.compile("\\d{4}/\\d{2}/\\d{5}");

    // Für Mitarbeiter-Dokument-Vorschau: Setter-Injection, da Konstruktor schon
    // sehr groß ist
    @Setter(onMethod_ = @org.springframework.beans.factory.annotation.Autowired)
    private org.example.kalkulationsprogramm.repository.MitarbeiterDokumentRepository mitarbeiterDokumentRepository;

    // Für Lieferantenkosten in Erfolgsanalyse
    @Setter(onMethod_ = @org.springframework.beans.factory.annotation.Autowired)
    private org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;

    // Für Bestellungskosten-Zuordnungen zu Projekten
    @Setter(onMethod_ = @org.springframework.beans.factory.annotation.Autowired)
    private org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository lieferantDokumentProjektAnteilRepository;

    // Für Angebot-Dokument-Löschung
    @Setter(onMethod_ = @org.springframework.beans.factory.annotation.Autowired)
    private org.example.kalkulationsprogramm.repository.AngebotDokumentRepository angebotDokumentRepository;

    @Autowired
    public DateiSpeicherService(@Value("${file.upload-dir}") String uploadDir,
            @Value("${file.offer-upload-dir:${file.upload-dir}}") String offerUploadDir,
            @Value("${hicad.local-path}") String hicadLocalPath,
            @Value("${file.image-upload-dir}") String iconUploadDir,
            @Value("${file.cutaway_images-dir:${file.image-upload-dir}}") String cutawayImagesDir,
            @Value("${hicad.network-url}") String hicadNetworkUrl,
            @Value("${hicad.network-drive-letter:}") String networkDriveLetter,
            ProjektDokumentRepository projektDokumentRepository,
            ProjektRepository projektRepository,
            AnfrageDokumentRepository anfrageDokumentRepository,
            AnfrageRepository anfrageRepository,
            ProduktkategorieRepository produktkategorieRepository,
            KundeRepository kundeRepository,
            ZugferdExtractorService zugferdExtractorService,
            ProduktkategorieMapper produktkategorieMapper) {
        this.dokumentRepository = projektDokumentRepository;
        this.projektRepository = projektRepository;
        this.anfrageDokumentRepository = anfrageDokumentRepository;
        this.anfrageRepository = anfrageRepository;
        this.dokumentenSpeicherplatz = Path.of(uploadDir).toAbsolutePath().normalize();
        this.hicadSpeicherplatz = Path.of(hicadLocalPath).toAbsolutePath().normalize();
        this.anfragenSpeicherplatz = Path.of(offerUploadDir).toAbsolutePath().normalize();
        this.bilderSpeicherplatz = Path.of(iconUploadDir).toAbsolutePath().normalize();
        this.schnittbilderSpeicherplatz = Path.of(cutawayImagesDir).toAbsolutePath().normalize();
        this.produktkategorieRepository = produktkategorieRepository;
        this.kundeRepository = kundeRepository;
        this.zugferdExtractorService = zugferdExtractorService;
        this.produktkategorieMapper = produktkategorieMapper;
        this.hicadNetworkUrl = hicadNetworkUrl;
        this.networkDriveLetter = networkDriveLetter != null ? networkDriveLetter.trim() : "";

        try {
            Files.createDirectories(this.bilderSpeicherplatz);
            Files.createDirectories(this.dokumentenSpeicherplatz);
            Files.createDirectories(this.hicadSpeicherplatz);
            Files.createDirectories(this.anfragenSpeicherplatz);
            Files.createDirectories(this.schnittbilderSpeicherplatz);
        } catch (Exception exception) {
            throw new RuntimeException("Konnte das Upload-Verzeichnis nicht erstellen.", exception);
        }
    }

    public ProjektDokument speichereDatei(MultipartFile datei, Long projektID) {
        return speichereDatei(datei, projektID, DokumentGruppe.DIVERSE_DOKUMENTE, null);
    }

    public ProjektDokument speichereDatei(MultipartFile datei, Long projektID, DokumentGruppe gruppe) {
        return speichereDatei(datei, projektID, gruppe, null, null);
    }

    public ProjektDokument speichereDatei(MultipartFile datei, Long projektID, DokumentGruppe gruppe,
            Mitarbeiter uploadedBy) {
        return speichereDatei(datei, projektID, gruppe, uploadedBy, null);
    }

    public ProjektDokument speichereDatei(MultipartFile datei, Long projektID, DokumentGruppe gruppe,
            Mitarbeiter uploadedBy,
            Lieferanten lieferant) {
        return speichereDatei(datei, projektID, gruppe, uploadedBy, lieferant, null);
    }

    public ProjektDokument speichereDatei(MultipartFile datei, Long projektID, DokumentGruppe gruppe,
            Mitarbeiter uploadedBy, Lieferanten lieferant, String overrideFilename) {
        Projekt projekt = projektRepository.findById(projektID)
                .orElseThrow(() -> new RuntimeException("Projekt nicht gefunden!"));
        String originalDateiname = overrideFilename != null ? overrideFilename
                : StringUtils.cleanPath(Objects.requireNonNull(datei.getOriginalFilename()));
        String gespeicherterDateiname = generiereEinzigartigenDateinamen(originalDateiname);
        String lowerName = originalDateiname.toLowerCase();
        boolean isDrawing = lowerName.contains("zeichnung") || lowerName.contains("entwurf");
        boolean useHicadStorage = lowerName.endsWith(".sza") || lowerName.endsWith(".tcd")
                || lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") || lowerName.endsWith(".xlsm")
                || lowerName.endsWith(".csv") || lowerName.endsWith(".ods") || lowerName.endsWith(".xlsb");
        Path basisPfad = useHicadStorage ? this.hicadSpeicherplatz : this.dokumentenSpeicherplatz;
        Path zielPfad = basisPfad.resolve(gespeicherterDateiname);

        try {
            Files.copy(datei.getInputStream(), zielPfad, StandardCopyOption.REPLACE_EXISTING);
            if (useHicadStorage) {
                kopiereNachNetzwerkFreigabeFallsAbweichend(gespeicherterDateiname, zielPfad);
            }
        } catch (IOException ioException) {
            throw new RuntimeException("Datei konnte nicht gespeichert werden.", ioException);
        }
        ProjektDokument dokument;
        // Bei PDFs wird versucht, ZUGFeRD-Metadaten zu extrahieren
        boolean istPdf = "application/pdf".equalsIgnoreCase(datei.getContentType());
        if (istPdf) {
            try {
                var zugferdDaten = this.zugferdExtractorService.extract(zielPfad.toString(), originalDateiname);
                boolean istAusgangsrechnung = isAusgangsrechnungInvoiceId(zugferdDaten.getRechnungsnummer());
                if (istAusgangsrechnung) {
                    var geschaeftsdokument = new ProjektGeschaeftsdokument();
                    String dokumentart = normalizeGeschaeftsdokumentart(zugferdDaten.getGeschaeftsdokumentart());
                    geschaeftsdokument.setDokumentid(zugferdDaten.getRechnungsnummer());
                    geschaeftsdokument.setGeschaeftsdokumentart(dokumentart);
                    geschaeftsdokument.setRechnungsdatum(zugferdDaten.getRechnungsdatum());
                    geschaeftsdokument.setFaelligkeitsdatum(
                            (istRechnung(dokumentart) || istMahnung(dokumentart)) ? zugferdDaten.getFaelligkeitsdatum()
                                    : null);
                    geschaeftsdokument.setBruttoBetrag(zugferdDaten.getBetrag());
                    geschaeftsdokument.setBezahlt(false);
                    dokument = geschaeftsdokument;
                    if (dokumentart.toLowerCase().contains("auftragsbest")) {
                        if (projekt.getAuftragsnummer() == null || projekt.getAuftragsnummer().isBlank()) {
                            projekt.setAuftragsnummer(zugferdDaten.getRechnungsnummer());
                            projektRepository.save(projekt);
                        }
                    }
                } else {
                    if (isDrawing) {
                        var geschaeftsdokument = new ProjektGeschaeftsdokument();
                        // Dokument-ID aus Dateiname ableiten (ohne Endung)
                        String base = originalDateiname;
                        int dot = base.lastIndexOf('.');
                        if (dot > 0)
                            base = base.substring(0, dot);
                        geschaeftsdokument.setDokumentid(base);
                        geschaeftsdokument.setGeschaeftsdokumentart("Zeichnung");
                        dokument = geschaeftsdokument;
                    } else {
                        dokument = new ProjektDokument();
                    }
                }
            } catch (Exception ignored) {
                if (isDrawing) {
                    var geschaeftsdokument = new ProjektGeschaeftsdokument();
                    String base = originalDateiname;
                    int dot = base.lastIndexOf('.');
                    if (dot > 0)
                        base = base.substring(0, dot);
                    geschaeftsdokument.setDokumentid(base);
                    geschaeftsdokument.setGeschaeftsdokumentart("Zeichnung");
                    dokument = geschaeftsdokument;
                } else {
                    dokument = new ProjektDokument();
                }
            }
        } else {
            dokument = new ProjektDokument();
        }

        dokument.setProjekt(projekt);
        dokument.setOriginalDateiname(originalDateiname);
        dokument.setGespeicherterDateiname(gespeicherterDateiname);
        dokument.setDateityp(datei.getContentType());
        dokument.setDateigroesse(datei.getSize());
        dokument.setUploadDatum(LocalDate.now());
        DokumentGruppe verwendeteGruppe = gruppe;
        if (dokument instanceof ProjektGeschaeftsdokument && isDrawing) {
            verwendeteGruppe = DokumentGruppe.GESCHAEFTSDOKUMENTE;
        }
        dokument.setDokumentGruppe(verwendeteGruppe);
        dokument.setUploadedBy(uploadedBy);
        if (lieferant != null) {
            dokument.setLieferant(lieferant);
        }

        ProjektDokument gespeichertesDokument = dokumentRepository.save(dokument);
        aktualisiereProjektFinanzstatus(projektID);
        return gespeichertesDokument;
    }

    public AnfrageDokument speichereAnfragesDatei(MultipartFile datei, Long anfrageID, DokumentGruppe gruppe) {
        Anfrage anfrage = anfrageRepository.findById(anfrageID)
                .orElseThrow(() -> new RuntimeException("Anfrage nicht gefunden!"));
        String originalDateiname = StringUtils.cleanPath(Objects.requireNonNull(datei.getOriginalFilename()));
        String gespeicherterDateiname = generiereEinzigartigenDateinamen(originalDateiname);
        String lowerName = originalDateiname.toLowerCase();
        boolean isDrawing = lowerName.contains("zeichnung") || lowerName.contains("entwurf");
        boolean useHicadStorage = lowerName.endsWith(".sza") || lowerName.endsWith(".tcd")
                || lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") || lowerName.endsWith(".xlsm")
                || lowerName.endsWith(".csv") || lowerName.endsWith(".ods") || lowerName.endsWith(".xlsb");
        Path basisPfad = useHicadStorage ? this.hicadSpeicherplatz : this.anfragenSpeicherplatz;
        Path zielPfad = basisPfad.resolve(gespeicherterDateiname);
        try {
            Files.copy(datei.getInputStream(), zielPfad, StandardCopyOption.REPLACE_EXISTING);
            if (useHicadStorage) {
                kopiereNachNetzwerkFreigabeFallsAbweichend(gespeicherterDateiname, zielPfad);
            }
        } catch (IOException ioException) {
            throw new RuntimeException("Datei konnte nicht gespeichert werden.", ioException);
        }
        AnfrageDokument dokument;
        boolean istPdf = "application/pdf".equalsIgnoreCase(datei.getContentType());
        if (istPdf) {
            try {
                var zugferdDaten = this.zugferdExtractorService.extract(zielPfad.toString(), originalDateiname);
                if (zugferdDaten.getKundennummer() != null && anfrage.getKunde() != null) {
                    anfrage.getKunde().setKundennummer(zugferdDaten.getKundennummer());
                }
                if (zugferdDaten.getBetrag() != null) {
                    anfrage.setBetrag(zugferdDaten.getBetrag());
                }
                anfrageRepository.save(anfrage);
                boolean hatDaten = zugferdDaten.getRechnungsnummer() != null;
                if (hatDaten) {
                    var geschaeftsdokument = new AnfrageGeschaeftsdokument();
                    geschaeftsdokument.setDokumentid(zugferdDaten.getRechnungsnummer());
                    geschaeftsdokument.setGeschaeftsdokumentart(zugferdDaten.getGeschaeftsdokumentart());
                    geschaeftsdokument.setBruttoBetrag(zugferdDaten.getBetrag());
                    dokument = geschaeftsdokument;
                } else {
                    if (isDrawing) {
                        var geschaeftsdokument = new AnfrageGeschaeftsdokument();
                        String base = originalDateiname;
                        int dot = base.lastIndexOf('.');
                        if (dot > 0)
                            base = base.substring(0, dot);
                        geschaeftsdokument.setDokumentid(base);
                        geschaeftsdokument.setGeschaeftsdokumentart("Zeichnung");
                        dokument = geschaeftsdokument;
                    } else {
                        dokument = new AnfrageDokument();
                    }
                }
            } catch (Exception ignored) {
                if (isDrawing) {
                    var geschaeftsdokument = new AnfrageGeschaeftsdokument();
                    String base = originalDateiname;
                    int dot = base.lastIndexOf('.');
                    if (dot > 0)
                        base = base.substring(0, dot);
                    geschaeftsdokument.setDokumentid(base);
                    geschaeftsdokument.setGeschaeftsdokumentart("Zeichnung");
                    dokument = geschaeftsdokument;
                } else {
                    dokument = new AnfrageDokument();
                }
            }
        } else {
            dokument = new AnfrageDokument();
        }
        dokument.setAnfrage(anfrage);
        dokument.setOriginalDateiname(originalDateiname);
        dokument.setGespeicherterDateiname(gespeicherterDateiname);
        dokument.setDateityp(datei.getContentType());
        dokument.setDateigroesse(datei.getSize());
        dokument.setUploadDatum(LocalDate.now());
        DokumentGruppe verwendeteGruppe = gruppe;
        if (dokument instanceof AnfrageGeschaeftsdokument && isDrawing) {
            verwendeteGruppe = DokumentGruppe.GESCHAEFTSDOKUMENTE;
        }
        dokument.setDokumentGruppe(verwendeteGruppe);
        return anfrageDokumentRepository.save(dokument);
    }

    public ProjektGeschaeftsdokument speichereZugferdDatei(Path zugferdPfad, String originalDateiname, Long projektID,
            ZugferdDaten daten) {
        Projekt projekt = projektRepository.findById(projektID)
                .orElseThrow(() -> new RuntimeException("Projekt nicht gefunden!"));
        String gespeicherterDateiname = generiereEinzigartigenDateinamen(originalDateiname);
        Path zielPfad = this.dokumentenSpeicherplatz.resolve(gespeicherterDateiname);
        try {
            Files.copy(zugferdPfad, zielPfad, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Datei konnte nicht gespeichert werden.", e);
        }
        ProjektGeschaeftsdokument dokument = new ProjektGeschaeftsdokument();
        dokument.setProjekt(projekt);
        dokument.setOriginalDateiname(originalDateiname);
        dokument.setGespeicherterDateiname(gespeicherterDateiname);
        dokument.setDateityp("application/pdf");
        try {
            dokument.setDateigroesse(Files.size(zielPfad));
        } catch (IOException ignored) {
        }
        dokument.setUploadDatum(LocalDate.now());
        dokument.setDokumentGruppe(DokumentGruppe.GESCHAEFTSDOKUMENTE);
        String dokumentart = normalizeGeschaeftsdokumentart(daten.getGeschaeftsdokumentart());
        dokument.setGeschaeftsdokumentart(dokumentart);
        dokument.setMahnstufe(null);
        ProjektGeschaeftsdokument referenz = null;
        if (istMahnung(dokumentart)) {
            if (daten.getReferenzDokumentId() == null) {
                throw new IllegalArgumentException("Für Mahnungen muss eine Referenzrechnung gewählt werden.");
            }
            ProjektDokument referenzDok = dokumentRepository.findById(daten.getReferenzDokumentId())
                    .orElseThrow(() -> new RuntimeException("Referenzrechnung nicht gefunden."));
            if (!(referenzDok instanceof ProjektGeschaeftsdokument referenzGeschaeftsdokument)) {
                throw new RuntimeException("Referenzdokument ist kein Geschäfts­dokument.");
            }
            referenz = referenzGeschaeftsdokument;
            if (referenz.getProjekt() == null || !referenz.getProjekt().getId().equals(projektID)) {
                throw new RuntimeException("Referenzrechnung gehört zu einem anderen Projekt.");
            }
            if (!istRechnung(referenz.getGeschaeftsdokumentart())) {
                throw new IllegalArgumentException("Mahnung muss sich auf eine Rechnung beziehen.");
            }
            dokument.setReferenzDokument(referenz);
            Mahnstufe mahnstufe = parseMahnstufe(daten.getMahnstufe());
            if (mahnstufe == null) {
                mahnstufe = Mahnstufe.ZAHLUNGSERINNERUNG;
            }
            dokument.setMahnstufe(mahnstufe);
        } else {
            dokument.setReferenzDokument(null);
        }
        String rechnungsnummer = daten.getRechnungsnummer();
        if ((rechnungsnummer == null || rechnungsnummer.isBlank()) && referenz != null) {
            rechnungsnummer = referenz.getDokumentid();
        }
        if (rechnungsnummer == null || rechnungsnummer.isBlank()) {
            throw new IllegalArgumentException("Rechnungsnummer muss angegeben werden.");
        }

        // Duplikat-Prüfung: dokumentid muss eindeutig sein
        if (dokumentRepository.existsByDokumentid(rechnungsnummer)) {
            throw new IllegalArgumentException("Ein Dokument mit der Rechnungsnummer '" +
                    rechnungsnummer + "' existiert bereits.");
        }

        dokument.setDokumentid(rechnungsnummer);

        LocalDate rechnungsdatum = daten.getRechnungsdatum() != null ? daten.getRechnungsdatum()
                : referenz != null ? referenz.getRechnungsdatum() : null;
        dokument.setRechnungsdatum(rechnungsdatum);

        if ((istRechnung(dokumentart) || istMahnung(dokumentart))) {
            dokument.setFaelligkeitsdatum(daten.getFaelligkeitsdatum());
            if (istMahnung(dokumentart) && dokument.getFaelligkeitsdatum() == null) {
                throw new IllegalArgumentException("Für Mahnungen muss ein neues Zahlungsziel eingetragen werden.");
            }
        } else {
            dokument.setFaelligkeitsdatum(null);
        }

        BigDecimal betrag = daten.getBetrag() != null ? daten.getBetrag()
                : referenz != null ? referenz.getBruttoBetrag() : null;
        if (istMahnung(dokumentart) && betrag == null) {
            throw new IllegalArgumentException(
                    "Für Mahnungen muss ein Betrag angegeben oder in der Referenzrechnung hinterlegt sein.");
        }
        dokument.setBruttoBetrag(betrag);
        dokument.setBezahlt(false);
        ProjektGeschaeftsdokument gespeichertesDokument = (ProjektGeschaeftsdokument) dokumentRepository.save(dokument);
        aktualisiereProjektFinanzstatus(projektID);
        return gespeichertesDokument;
    }

    public AnfrageGeschaeftsdokument speichereAnfragesZugferdDatei(Path zugferdPfad,
            String originalDateiname,
            Long anfrageID,
            ZugferdDaten daten) {
        Anfrage anfrage = anfrageRepository.findById(anfrageID)
                .orElseThrow(() -> new RuntimeException("Anfrage nicht gefunden!"));
        String gespeicherterDateiname = generiereEinzigartigenDateinamen(originalDateiname);
        Path zielPfad = this.anfragenSpeicherplatz.resolve(gespeicherterDateiname);
        try {
            Files.copy(zugferdPfad, zielPfad, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Datei konnte nicht gespeichert werden.", e);
        }

        AnfrageGeschaeftsdokument dokument = new AnfrageGeschaeftsdokument();
        dokument.setAnfrage(anfrage);
        dokument.setOriginalDateiname(originalDateiname);
        dokument.setGespeicherterDateiname(gespeicherterDateiname);
        dokument.setDateityp("application/pdf");
        try {
            dokument.setDateigroesse(Files.size(zielPfad));
        } catch (IOException ignored) {
        }
        dokument.setUploadDatum(LocalDate.now());
        dokument.setDokumentGruppe(DokumentGruppe.GESCHAEFTSDOKUMENTE);

        String dokumentart = normalizeGeschaeftsdokumentart(daten.getGeschaeftsdokumentart());
        if (istRechnung(dokumentart)) {
            dokumentart = "Angebot";
        }

        // DokumentID: verwende rechnungsnummer, falls null verwende Dateinamen als
        // Fallback
        String dokumentId = daten.getRechnungsnummer();
        if (dokumentId == null || dokumentId.isBlank()) {
            // Fallback: Dateiname ohne Erweiterung als ID
            String base = originalDateiname;
            int dot = base.lastIndexOf('.');
            if (dot > 0) {
                base = base.substring(0, dot);
            }
            dokumentId = base;
        }
        dokument.setDokumentid(dokumentId);
        dokument.setGeschaeftsdokumentart(dokumentart);
        dokument.setBruttoBetrag(daten.getBetrag());

        AnfrageGeschaeftsdokument gespeichertesDokument = (AnfrageGeschaeftsdokument) anfrageDokumentRepository
                .save(dokument);

        boolean geaendert = false;
        if (daten.getBetrag() != null) {
            anfrage.setBetrag(daten.getBetrag());
            geaendert = true;
        }

        if (daten.getKundennummer() != null && !daten.getKundennummer().isBlank()) {
            var existingKunde = kundeRepository.findByKundennummerIgnoreCase(daten.getKundennummer());
            if (existingKunde.isPresent()) {
                anfrage.setKunde(existingKunde.get());
                geaendert = true;
            } else {
                if (daten.getKundenName() != null && !daten.getKundenName().isBlank()) {
                    Kunde k = new Kunde();
                    k.setName(daten.getKundenName());
                    k.setKundennummer(daten.getKundennummer());
                    try {
                        k = kundeRepository.save(k);
                        anfrage.setKunde(k);
                        geaendert = true;
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }

        if (geaendert) {
            anfrageRepository.save(anfrage);
        }
        return gespeichertesDokument;
    }

    public ProjektDokument speichereErzeugteDatei(byte[] inhalt,
            String originalDateiname,
            Long projektID,
            DokumentGruppe gruppe,
            String contentType) {
        Projekt projekt = projektRepository.findById(projektID)
                .orElseThrow(() -> new RuntimeException("Projekt nicht gefunden!"));
        String gespeicherterDateiname = generiereEinzigartigenDateinamen(originalDateiname);
        Path zielPfad = this.dokumentenSpeicherplatz.resolve(gespeicherterDateiname);
        try {
            Files.write(zielPfad, inhalt);
        } catch (IOException e) {
            throw new RuntimeException("Datei konnte nicht gespeichert werden.", e);
        }
        ProjektDokument dokument = new ProjektDokument();
        dokument.setProjekt(projekt);
        dokument.setOriginalDateiname(originalDateiname);
        dokument.setGespeicherterDateiname(gespeicherterDateiname);
        dokument.setDateityp(contentType != null ? contentType : "application/pdf");
        try {
            dokument.setDateigroesse(Files.size(zielPfad));
        } catch (IOException ignored) {
        }
        dokument.setUploadDatum(LocalDate.now());
        dokument.setDokumentGruppe(gruppe != null ? gruppe : DokumentGruppe.PLANUNGSDOKUMENTE);
        return dokumentRepository.save(dokument);
    }

    public void verschiebeAnfragesDatei(AnfrageDokument anfrageDokument, Projekt projekt) {
        ProjektDokument dokument;
        if (anfrageDokument instanceof AnfrageGeschaeftsdokument gesDoc) {
            ProjektGeschaeftsdokument geschaeftsDok = new ProjektGeschaeftsdokument();
            geschaeftsDok.setDokumentid(gesDoc.getDokumentid());
            geschaeftsDok.setGeschaeftsdokumentart(gesDoc.getGeschaeftsdokumentart());
            geschaeftsDok.setBruttoBetrag(gesDoc.getBruttoBetrag());
            dokument = geschaeftsDok;
        } else {
            dokument = new ProjektDokument();
        }

        dokument.setProjekt(projekt);
        dokument.setOriginalDateiname(anfrageDokument.getOriginalDateiname());
        dokument.setGespeicherterDateiname(anfrageDokument.getGespeicherterDateiname());
        dokument.setDateityp(anfrageDokument.getDateityp());
        dokument.setDateigroesse(anfrageDokument.getDateigroesse());
        dokument.setUploadDatum(anfrageDokument.getUploadDatum());
        dokument.setEmailVersandDatum(anfrageDokument.getEmailVersandDatum());
        dokument.setDokumentGruppe(anfrageDokument.getDokumentGruppe());

        dokumentRepository.save(dokument);
        aktualisiereProjektFinanzstatus(projekt.getId());
    }

    public void aktualisiereProjektFinanzstatus(Long projektID) {
        // bruttoPreis und bezahlt werden jetzt ausschließlich von
        // AusgangsGeschaeftsDokumentService.aktualisiereProjektPreisAusDokumenten() berechnet.
        // Diese Methode ist nur noch ein No-Op-Stub für Aufrufer im alten Dokumentsystem.
    }

    public List<ProjektDokument> holeDokumenteZuProjekt(Long projektID) {
        return dokumentRepository.findByProjektId(projektID);
    }

    public List<AnfrageDokument> holeDokumenteZuAnfrage(Long anfrageID) {
        return anfrageDokumentRepository.findByAnfrageId(anfrageID);
    }

    public List<ProjektGeschaeftsdokument> holeOffeneGeschaeftsdokumente() {
        return dokumentRepository.findOffeneGeschaeftsdokumente();
    }

    public List<ProjektGeschaeftsdokument> holeRechnungenZuProjekt(Long projektId) {
        return dokumentRepository.findRechnungenByProjektId(projektId);
    }

    @Transactional
    public List<ProjektGeschaeftsdokument> holeGeschaeftsdokumenteNachJahrUndFilter(int jahr,
            Integer monat,
            String rechnungsnummer,
            String auftragsnummer,
            String kunde,
            Long kategorieId) {
        var start = java.time.LocalDate.of(jahr, 1, 1);
        var end = java.time.LocalDate.of(jahr, 12, 31);
        List<ProjektGeschaeftsdokument> docs = dokumentRepository.findGeschaeftsdokumenteByRechnungsdatumBetween(start,
                end);
        return docs.stream()
                .filter(ProjektGeschaeftsdokument::isBezahlt)
                .filter(d -> monat == null
                        || (d.getRechnungsdatum() != null && d.getRechnungsdatum().getMonthValue() == monat))
                .filter(d -> rechnungsnummer == null || rechnungsnummer.isBlank() ||
                        (d.getDokumentid() != null
                                && d.getDokumentid().toLowerCase().contains(rechnungsnummer.toLowerCase())))
                .filter(d -> auftragsnummer == null || auftragsnummer.isBlank() ||
                        (d.getProjekt() != null && d.getProjekt().getAuftragsnummer() != null &&
                                d.getProjekt().getAuftragsnummer().toLowerCase()
                                        .contains(auftragsnummer.toLowerCase())))
                .filter(d -> kunde == null || kunde.isBlank() ||
                        (d.getProjekt() != null && d.getProjekt().getKunde() != null &&
                                d.getProjekt().getKunde().toLowerCase(java.util.Locale.ROOT)
                                        .contains(kunde.toLowerCase(java.util.Locale.ROOT))))
                .filter(d -> kategorieId == null ||
                        (d.getProjekt() != null && d.getProjekt().getProjektProduktkategorien() != null &&
                                d.getProjekt().getProjektProduktkategorien().stream()
                                        .anyMatch(ppk -> ppk.getProduktkategorie() != null
                                                && kategorieId.equals(ppk.getProduktkategorie().getId()))))
                .collect(java.util.stream.Collectors.toList());
    }

    public double berechneProjektArbeitskosten(Projekt projekt) {
        if (projekt == null || projekt.getId() == null) {
            return 0d;
        }
        Projekt voller = projektRepository.findById(projekt.getId()).orElse(null);
        if (voller == null) {
            return 0d;
        }
        return voller.getZeitbuchungen() != null ? voller.getZeitbuchungen().stream()
                .mapToDouble(z -> {
                    java.math.BigDecimal anzahl = z.getAnzahlInStunden() != null ? z.getAnzahlInStunden()
                            : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal satz = z.getArbeitsgangStundensatz() != null
                            ? z.getArbeitsgangStundensatz().getSatz()
                            : java.math.BigDecimal.ZERO;
                    return anzahl.multiply(satz).doubleValue();
                }).sum() : 0d;
    }

    public double berechneProjektMaterialkosten(Projekt projekt) {
        return berechneProjektMaterialkosten(projekt, null);
    }

    public double berechneProjektMaterialkosten(Projekt projekt, Integer monat) {
        if (projekt == null || projekt.getId() == null) {
            return 0d;
        }
        Projekt voller = projektRepository.findById(projekt.getId()).orElse(null);
        if (voller == null) {
            return 0d;
        }
        // Manuelle Materialkosten
        double material = voller.getMaterialkosten() != null ? voller.getMaterialkosten().stream()
                .filter(m -> monat == null || (m.getMonat() != null && m.getMonat().equals(monat)))
                .mapToDouble(m -> m.getBetrag() != null ? m.getBetrag().doubleValue() : 0d).sum() : 0d;

        // Artikel im Projekt
        double artikel = voller.getArtikelInProjekt() != null ? voller.getArtikelInProjekt().stream()
                .mapToDouble(a -> a.getPreisProStueck() != null && a.getStueckzahl() != null
                        ? a.getPreisProStueck().multiply(BigDecimal.valueOf(a.getStueckzahl())).doubleValue()
                        : 0d)
                .sum() : 0d;

        // Zugeordnete Bestellungskosten (Lieferanten-Rechnungen)
        double bestellungen = 0d;
        if (lieferantDokumentProjektAnteilRepository != null) {
            var zuordnungen = lieferantDokumentProjektAnteilRepository.findByProjektId(projekt.getId());
            bestellungen = zuordnungen.stream()
                    .filter(z -> monat == null || (z.getZugeordnetAm() != null &&
                            z.getZugeordnetAm().getMonthValue() == monat))
                    .mapToDouble(z -> z.getBerechneterBetrag() != null ? z.getBerechneterBetrag().doubleValue() : 0d)
                    .sum();
        }

        return material + artikel + bestellungen;
    }

    public double berechneProjektKosten(Projekt projekt) {
        return berechneProjektArbeitskosten(projekt) + berechneProjektMaterialkosten(projekt);
    }

    private double anteiligeArbeitskosten(Projekt projekt, int jahr, int monat) {
        if (projekt == null) {
            return 0d;
        }
        double gesamt = berechneProjektArbeitskosten(projekt);
        LocalDate start = projekt.getAnlegedatum();
        if (start == null) {
            return 0d;
        }
        LocalDate ende = projekt.getAbschlussdatum();
        if (ende == null) {
            ende = LocalDate.now();
        }
        if (ende.isBefore(start)) {
            ende = start;
        }
        YearMonth startYm = YearMonth.from(start);
        YearMonth endYm = YearMonth.from(ende);
        long monate = ChronoUnit.MONTHS.between(startYm, endYm) + 1;
        if (monate <= 0) {
            monate = 1;
        }
        YearMonth ziel = YearMonth.of(jahr, monat);
        if (ziel.isBefore(startYm) || ziel.isAfter(endYm)) {
            return 0d;
        }
        return gesamt / monate;
    }

    public UmsatzStatistikDto holeUmsatzStatistiken(int jahr, Integer monat) {
        var startDiesesJahr = java.time.LocalDate.of(jahr, 1, 1);
        var endDiesesJahr = java.time.LocalDate.of(jahr, 12, 31);
        var startLetztesJahr = startDiesesJahr.minusYears(1);
        var endLetztesJahr = endDiesesJahr.minusYears(1);

        List<ProjektGeschaeftsdokument> docsDiesesJahr = dokumentRepository
                .findGeschaeftsdokumenteByRechnungsdatumBetween(startDiesesJahr, endDiesesJahr).stream()
                .filter(ProjektGeschaeftsdokument::isBezahlt)
                .toList();
        List<ProjektGeschaeftsdokument> docsLetztesJahr = dokumentRepository
                .findGeschaeftsdokumenteByRechnungsdatumBetween(startLetztesJahr, endLetztesJahr).stream()
                .filter(ProjektGeschaeftsdokument::isBezahlt)
                .toList();

        // Lieferantenrechnungen für Erfolgsanalyse (nach Rechnungsdatum für steuerliche
        // Korrektheit!)
        // Für die Vorsteuer ist das Rechnungsdatum maßgebend, nicht das Bezahlt-Datum
        List<org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument> liefRechnungenDiesesJahr = lieferantGeschaeftsdokumentRepository != null
                ? lieferantGeschaeftsdokumentRepository.findRechnungenByDatumBetween(startDiesesJahr, endDiesesJahr)
                        .stream()
                        .filter(gd -> Boolean.TRUE.equals(gd.getBezahlt())
                                || Boolean.TRUE.equals(gd.getBereitsGezahlt()))
                        .toList()
                : java.util.Collections.emptyList();
        List<org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument> liefRechnungenLetztesJahr = lieferantGeschaeftsdokumentRepository != null
                ? lieferantGeschaeftsdokumentRepository.findRechnungenByDatumBetween(startLetztesJahr, endLetztesJahr)
                        .stream()
                        .filter(gd -> Boolean.TRUE.equals(gd.getBezahlt())
                                || Boolean.TRUE.equals(gd.getBereitsGezahlt()))
                        .toList()
                : java.util.Collections.emptyList();

        List<ProjektGeschaeftsdokument> docsDiesesJahrKategorie = docsDiesesJahr;
        List<ProjektGeschaeftsdokument> docsLetztesJahrKategorie = docsLetztesJahr;
        if (monat != null) {
            docsDiesesJahrKategorie = docsDiesesJahr.stream()
                    .filter(d -> d.getRechnungsdatum() != null && d.getRechnungsdatum().getMonthValue() == monat)
                    .toList();
            docsLetztesJahrKategorie = docsLetztesJahr.stream()
                    .filter(d -> d.getRechnungsdatum() != null && d.getRechnungsdatum().getMonthValue() == monat)
                    .toList();
        }

        java.util.Map<Long, Projekt> projDieses = docsDiesesJahrKategorie.stream()
                .map(ProjektGeschaeftsdokument::getProjekt)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(Projekt::getId, p -> p, (a, b) -> a));
        java.util.Map<Long, Projekt> projLetztes = docsLetztesJahrKategorie.stream()
                .map(ProjektGeschaeftsdokument::getProjekt)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(Projekt::getId, p -> p, (a, b) -> a));

        java.util.Map<String, Produktkategorie> kategorieEinheiten = new java.util.HashMap<>();
        java.util.Map<String, Long> katDieses = projDieses.values().stream()
                .filter(p -> p.getProjektProduktkategorien() != null && !p.getProjektProduktkategorien().isEmpty())
                .flatMap(p -> p.getProjektProduktkategorien().stream()
                        .map(ppk -> {
                            Produktkategorie k = ppk.getProduktkategorie();
                            while (k.getUebergeordneteKategorie() != null) {
                                k = k.getUebergeordneteKategorie();
                            }
                            kategorieEinheiten.putIfAbsent(k.getBezeichnung(), k);
                            return k.getBezeichnung();
                        })
                        .distinct())
                .collect(java.util.stream.Collectors.groupingBy(k -> k, java.util.stream.Collectors.counting()));
        java.util.Map<String, Long> katLetztes = projLetztes.values().stream()
                .filter(p -> p.getProjektProduktkategorien() != null && !p.getProjektProduktkategorien().isEmpty())
                .flatMap(p -> p.getProjektProduktkategorien().stream()
                        .map(ppk -> {
                            Produktkategorie k = ppk.getProduktkategorie();
                            while (k.getUebergeordneteKategorie() != null) {
                                k = k.getUebergeordneteKategorie();
                            }
                            kategorieEinheiten.putIfAbsent(k.getBezeichnung(), k);
                            return k.getBezeichnung();
                        })
                        .distinct())
                .collect(java.util.stream.Collectors.groupingBy(k -> k, java.util.stream.Collectors.counting()));

        java.util.Set<String> alleKategorien = new java.util.TreeSet<>();
        alleKategorien.addAll(katDieses.keySet());
        alleKategorien.addAll(katLetztes.keySet());

        java.util.List<KategorieUmsatzVergleichDto> katResult = alleKategorien.stream().map(name -> {
            KategorieUmsatzVergleichDto dto = new KategorieUmsatzVergleichDto();
            dto.setKategorie(name);
            dto.setLetztesJahr(katLetztes.getOrDefault(name, 0L));
            dto.setDiesesJahr(katDieses.getOrDefault(name, 0L));
            Produktkategorie kat = kategorieEinheiten.get(name);
            if (kat != null && kat.getVerrechnungseinheit() != null) {
                dto.setVerrechnungseinheit(kat.getVerrechnungseinheit().getAnzeigename());
            }
            return dto;
        }).toList();

        int limit = monat != null ? monat : 12;
        java.util.List<MonatsumsatzDto> monats = new java.util.ArrayList<>();
        java.util.Collection<Projekt> projAlleDieses = docsDiesesJahr.stream()
                .map(ProjektGeschaeftsdokument::getProjekt)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(Projekt::getId, p -> p, (a, b) -> a))
                .values();
        java.util.Collection<Projekt> projAlleVorjahr = docsLetztesJahr.stream()
                .map(ProjektGeschaeftsdokument::getProjekt)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(Projekt::getId, p -> p, (a, b) -> a))
                .values();
        java.util.Set<Long> bereitsBerechneteMaterialProjekte = new java.util.HashSet<>();
        java.util.Set<Long> bereitsBerechneteMaterialProjekteVorjahr = new java.util.HashSet<>();
        for (int m = 1; m <= limit; m++) {
            int finalM = m;
            double sumDieses = docsDiesesJahr.stream()
                    .filter(d -> d.getRechnungsdatum() != null && d.getRechnungsdatum().getMonthValue() == finalM)
                    .mapToDouble(d -> d.getBruttoBetrag() != null ? d.getBruttoBetrag().doubleValue() : 0d)
                    .sum();
            int finalM1 = m;
            double sumLetztes = docsLetztesJahr.stream()
                    .filter(d -> d.getRechnungsdatum() != null && d.getRechnungsdatum().getMonthValue() == finalM1)
                    .mapToDouble(d -> d.getBruttoBetrag() != null ? d.getBruttoBetrag().doubleValue() : 0d)
                    .sum();

            java.util.Set<Long> projektIds = docsDiesesJahr.stream()
                    .filter(d -> d.getRechnungsdatum() != null && d.getRechnungsdatum().getMonthValue() == finalM)
                    .map(d -> d.getProjekt() != null ? d.getProjekt().getId() : null)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            java.util.Set<Long> projektIdsVorjahr = docsLetztesJahr.stream()
                    .filter(d -> d.getRechnungsdatum() != null && d.getRechnungsdatum().getMonthValue() == finalM)
                    .map(d -> d.getProjekt() != null ? d.getProjekt().getId() : null)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());

            java.util.Set<Long> neueMaterialProjekte = projektIds.stream()
                    .filter(id -> !bereitsBerechneteMaterialProjekte.contains(id))
                    .collect(java.util.stream.Collectors.toSet());
            java.util.Set<Long> neueMaterialProjekteVorjahr = projektIdsVorjahr.stream()
                    .filter(id -> !bereitsBerechneteMaterialProjekteVorjahr.contains(id))
                    .collect(java.util.stream.Collectors.toSet());

            int finalM2 = m;
            double arbeitskosten = projAlleDieses.stream()
                    .mapToDouble(p -> anteiligeArbeitskosten(p, jahr, finalM2))
                    .sum();
            double materialkosten = neueMaterialProjekte.stream()
                    .map(id -> {
                        Projekt p = new Projekt();
                        p.setId(id);
                        return berechneProjektMaterialkosten(p);
                    }).mapToDouble(Double::doubleValue)
                    .sum();
            int finalM3 = m;
            double arbeitskostenVorjahr = projAlleVorjahr.stream()
                    .mapToDouble(p -> anteiligeArbeitskosten(p, jahr - 1, finalM3))
                    .sum();
            double materialkostenVorjahr = neueMaterialProjekteVorjahr.stream()
                    .map(id -> {
                        Projekt p = new Projekt();
                        p.setId(id);
                        return berechneProjektMaterialkosten(p);
                    }).mapToDouble(Double::doubleValue)
                    .sum();

            bereitsBerechneteMaterialProjekte.addAll(neueMaterialProjekte);
            bereitsBerechneteMaterialProjekteVorjahr.addAll(neueMaterialProjekteVorjahr);

            double kosten = arbeitskosten + materialkosten;
            double kostenVorjahr = arbeitskostenVorjahr + materialkostenVorjahr;

            // Lieferantenkosten (Eingangsrechnungen Netto) für diesen Monat - basierend auf
            // Rechnungsdatum!
            int finalMonat = m;
            double lieferantenkosten = liefRechnungenDiesesJahr.stream()
                    .filter(gd -> gd.getDokumentDatum() != null && gd.getDokumentDatum().getMonthValue() == finalMonat)
                    .mapToDouble(gd -> {
                        // Wenn Skonto genutzt: tatsächlich gezahlten Brutto in Netto umrechnen
                        if (Boolean.TRUE.equals(gd.getMitSkonto()) && gd.getTatsaechlichGezahlt() != null) {
                            double mwst = gd.getMwstSatz() != null ? gd.getMwstSatz().doubleValue() : 0.19;
                            return gd.getTatsaechlichGezahlt().doubleValue() / (1 + mwst);
                        }
                        return gd.getBetragNetto() != null ? gd.getBetragNetto().doubleValue() : 0d;
                    })
                    .sum();
            double lieferantenkostenVorjahr = liefRechnungenLetztesJahr.stream()
                    .filter(gd -> gd.getDokumentDatum() != null && gd.getDokumentDatum().getMonthValue() == finalMonat)
                    .mapToDouble(gd -> {
                        if (Boolean.TRUE.equals(gd.getMitSkonto()) && gd.getTatsaechlichGezahlt() != null) {
                            double mwst = gd.getMwstSatz() != null ? gd.getMwstSatz().doubleValue() : 0.19;
                            return gd.getTatsaechlichGezahlt().doubleValue() / (1 + mwst);
                        }
                        return gd.getBetragNetto() != null ? gd.getBetragNetto().doubleValue() : 0d;
                    })
                    .sum();

            MonatsumsatzDto dto = new MonatsumsatzDto();
            dto.setMonat(m);
            dto.setLetztesJahr(sumLetztes);
            dto.setDiesesJahr(sumDieses);
            dto.setArbeitskosten(arbeitskosten);
            dto.setMaterialkosten(materialkosten);
            dto.setKosten(kosten);
            dto.setArbeitskostenVorjahr(arbeitskostenVorjahr);
            dto.setMaterialkostenVorjahr(materialkostenVorjahr);
            dto.setKostenVorjahr(kostenVorjahr);
            dto.setLieferantenkosten(lieferantenkosten);
            dto.setLieferantenkostenVorjahr(lieferantenkostenVorjahr);
            monats.add(dto);
        }

        UmsatzStatistikDto statistikDto = new UmsatzStatistikDto();
        statistikDto.setKategorien(katResult);
        statistikDto.setMonatsUmsaetze(monats);
        statistikDto.setKonversion(berechneKonversion(jahr));
        statistikDto.setOrtHeatmap(berechneOrtHeatmap(docsDiesesJahr, monat));
        statistikDto.setKategoriePerformance(berechneKategoriePerformance(docsDiesesJahr, docsLetztesJahr, monat));
        statistikDto.setTopKunden(berechneTopKunden(docsDiesesJahr, monat));
        return statistikDto;
    }

    private ConversionRateDto berechneKonversion(int jahr) {
        ConversionRateDto dto = new ConversionRateDto();
        dto.setJahr(jahr);
        java.time.LocalDate start = java.time.LocalDate.of(jahr, 1, 1);
        java.time.LocalDate ende = java.time.LocalDate.of(jahr, 12, 31);
        java.util.List<Anfrage> anfragen = anfrageRepository.findByAnlegedatumBetween(start, ende);
        java.util.List<Projekt> projekte = projektRepository.findByAnlegedatumBetween(start, ende);
        long offeneAnfragen = anfragen != null ? anfragen.size() : 0L;
        long projekteCount = projekte != null ? projekte.size() : 0L;
        long gesamt = projekteCount + offeneAnfragen;
        dto.setAnfragenGesamt(gesamt);
        dto.setAnfragenZuProjekt(projekteCount);
        dto.setConversionRate(gesamt > 0 ? (projekteCount * 100d) / gesamt : 0d);
        return dto;
    }

    private java.util.List<OrtHeatmapDto> berechneOrtHeatmap(java.util.List<ProjektGeschaeftsdokument> docs,
            Integer monat) {
        if (docs == null || docs.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<ProjektGeschaeftsdokument> basis = docs;
        if (monat != null) {
            final int zielMonat = monat;
            basis = docs.stream()
                    .filter(d -> d.getRechnungsdatum() != null && d.getRechnungsdatum().getMonthValue() == zielMonat)
                    .toList();
        }
        if (basis.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.Map<String, HeatmapAggregation> aggregiert = new java.util.LinkedHashMap<>();
        for (ProjektGeschaeftsdokument doc : basis) {
            Projekt projekt = doc.getProjekt();
            if (projekt == null) {
                continue;
            }
            Kunde kunde = projekt.getKundenId();
            String ort = kunde != null ? kunde.getOrt() : null;
            String plz = kunde != null ? kunde.getPlz() : null;
            if (ort == null || ort.isBlank()) {
                ort = "Unbekannter Ort";
            } else {
                ort = ort.trim();
            }
            plz = plz != null ? plz.trim() : "";
            String key = (plz + "|" + ort).toLowerCase(java.util.Locale.ROOT);
            HeatmapAggregation aggregation = aggregiert.get(key);
            if (aggregation == null) {
                aggregation = new HeatmapAggregation(plz, ort);
                aggregiert.put(key, aggregation);
            }
            Long projektId = projekt.getId();
            if (projektId != null) {
                if (aggregation.projektIds.add(projektId)) {
                    aggregation.projekte++;
                }
            } else {
                aggregation.projekte++;
            }
            if (doc.getBruttoBetrag() != null) {
                aggregation.umsatz += doc.getBruttoBetrag().doubleValue();
            }
        }
        long gesamt = aggregiert.values().stream().mapToLong(h -> h.projekte).sum();
        return aggregiert.values().stream()
                .sorted(java.util.Comparator.comparingLong((HeatmapAggregation h) -> h.projekte).reversed())
                .map(h -> {
                    OrtHeatmapDto dto = new OrtHeatmapDto();
                    dto.setPlz(h.plz);
                    dto.setOrt(h.ort);
                    dto.setProjekte(h.projekte);
                    dto.setUmsatz(h.umsatz);
                    dto.setAnteil(gesamt > 0 ? (double) h.projekte / (double) gesamt : 0d);
                    return dto;
                })
                .toList();
    }

    private static class HeatmapAggregation {
        private final String plz;
        private final String ort;
        private final java.util.Set<Long> projektIds = new java.util.HashSet<>();
        private long projekte;
        private double umsatz;

        private HeatmapAggregation(String plz, String ort) {
            this.plz = plz;
            this.ort = ort;
        }
    }

    private java.util.List<org.example.kalkulationsprogramm.dto.Projekt.KategoriePerformanceDto> berechneKategoriePerformance(
            java.util.List<ProjektGeschaeftsdokument> docsDiesesJahr,
            java.util.List<ProjektGeschaeftsdokument> docsLetztesJahr,
            Integer monat) {
        if ((docsDiesesJahr == null || docsDiesesJahr.isEmpty())
                && (docsLetztesJahr == null || docsLetztesJahr.isEmpty())) {
            return java.util.Collections.emptyList();
        }

        java.util.List<ProjektGeschaeftsdokument> basisDiesesJahr = docsDiesesJahr;
        java.util.List<ProjektGeschaeftsdokument> basisLetztesJahr = docsLetztesJahr;

        if (monat != null) {
            final int zielMonat = monat;
            if (basisDiesesJahr != null) {
                basisDiesesJahr = basisDiesesJahr.stream()
                        .filter(d -> d.getRechnungsdatum() != null
                                && d.getRechnungsdatum().getMonthValue() == zielMonat)
                        .toList();
            }
            if (basisLetztesJahr != null) {
                basisLetztesJahr = basisLetztesJahr.stream()
                        .filter(d -> d.getRechnungsdatum() != null
                                && d.getRechnungsdatum().getMonthValue() == zielMonat)
                        .toList();
            }
        }

        java.util.Map<String, CategoryAggregation> aggDiesesJahr = aggregiereKategoriePerformance(basisDiesesJahr);
        java.util.Map<String, CategoryAggregation> aggLetztesJahr = aggregiereKategoriePerformance(basisLetztesJahr);

        java.util.Set<String> alleKategorien = new java.util.TreeSet<>();
        alleKategorien.addAll(aggDiesesJahr.keySet());
        alleKategorien.addAll(aggLetztesJahr.keySet());

        return alleKategorien.stream()
                .map(name -> {
                    CategoryAggregation dieses = aggDiesesJahr.getOrDefault(name, new CategoryAggregation(name));
                    CategoryAggregation letztes = aggLetztesJahr.getOrDefault(name, new CategoryAggregation(name));

                    org.example.kalkulationsprogramm.dto.Projekt.KategoriePerformanceDto dto = new org.example.kalkulationsprogramm.dto.Projekt.KategoriePerformanceDto();
                    dto.setKategorieName(name);
                    dto.setUmsatz(dieses.umsatz);
                    dto.setGewinn(dieses.gewinn);
                    dto.setStueckzahl(dieses.stueckzahl);
                    dto.setUmsatzVorjahr(letztes.umsatz);
                    dto.setGewinnVorjahr(letztes.gewinn);
                    dto.setStueckzahlVorjahr(letztes.stueckzahl);
                    return dto;
                })
                .toList();
    }

    private java.util.Map<String, CategoryAggregation> aggregiereKategoriePerformance(
            java.util.List<ProjektGeschaeftsdokument> docs) {
        java.util.Map<String, CategoryAggregation> aggregated = new java.util.LinkedHashMap<>();
        if (docs == null || docs.isEmpty()) {
            return aggregated;
        }

        for (ProjektGeschaeftsdokument doc : docs) {
            Projekt projekt = doc.getProjekt();
            if (projekt == null) {
                continue;
            }

            String categoryName = getTopLevelCategoryName(projekt);
            if (categoryName == null || categoryName.isBlank()) {
                categoryName = "Ohne Kategorie";
            }

            CategoryAggregation aggregation = aggregated.get(categoryName);
            if (aggregation == null) {
                aggregation = new CategoryAggregation(categoryName);
                aggregated.put(categoryName, aggregation);
            }

            if (doc.getBruttoBetrag() != null) {
                aggregation.umsatz += doc.getBruttoBetrag().doubleValue();
            }

            Long projektId = projekt.getId();
            if (projektId != null && aggregation.processedProjects.add(projektId)) {
                aggregation.stueckzahl++;
                aggregation.gewinn += berechneProjektGewinn(projekt);
            } else if (projektId == null) {
                aggregation.stueckzahl++;
                aggregation.gewinn += berechneProjektGewinn(projekt);
            }
        }

        return aggregated;
    }

    private double berechneProjektGewinn(Projekt projekt) {
        double brutto = 0.0;
        if (projekt.getBruttoPreis() != null) {
            brutto = projekt.getBruttoPreis().doubleValue();
        }
        double netto = brutto / 1.19;

        double arbeitskosten = 0.0;
        if (projekt.getZeitbuchungen() != null) {
            for (Zeitbuchung zeit : projekt.getZeitbuchungen()) {
                if (zeit.getAnzahlInStunden() != null && zeit.getArbeitsgangStundensatz() != null
                        && zeit.getArbeitsgangStundensatz().getSatz() != null) {
                    arbeitskosten += zeit.getAnzahlInStunden().doubleValue()
                            * zeit.getArbeitsgangStundensatz().getSatz().doubleValue();
                }
            }
        }

        double materialkosten = 0.0;
        if (projekt.getMaterialkosten() != null) {
            for (Materialkosten material : projekt.getMaterialkosten()) {
                if (material.getBetrag() != null) {
                    materialkosten += material.getBetrag().doubleValue();
                }
            }
        }

        return netto - arbeitskosten - materialkosten;
    }

    private String getTopLevelCategoryName(Projekt projekt) {
        if (projekt.getProjektProduktkategorien() == null || projekt.getProjektProduktkategorien().isEmpty()) {
            return null;
        }

        // Get the first category (projects typically have one main category)
        ProjektProduktkategorie projektKategorie = projekt.getProjektProduktkategorien().get(0);
        if (projektKategorie == null) {
            return null;
        }

        Produktkategorie produktkategorie = projektKategorie.getProduktkategorie();
        if (produktkategorie == null) {
            return null;
        }

        // Traverse up to find the top-level category (where uebergeordneteKategorie is
        // null)
        Produktkategorie current = produktkategorie;
        while (current.getUebergeordneteKategorie() != null) {
            current = current.getUebergeordneteKategorie();
        }

        return current.getBezeichnung();
    }

    private static class CategoryAggregation {
        private final String kategorieName;
        private double umsatz;
        private double gewinn;
        private final java.util.Set<Long> processedProjects = new java.util.HashSet<>();
        private long stueckzahl;

        private CategoryAggregation(String kategorieName) {
            this.kategorieName = kategorieName;
        }
    }

    private java.util.List<org.example.kalkulationsprogramm.dto.Projekt.TopKundeDto> berechneTopKunden(
            java.util.List<ProjektGeschaeftsdokument> docs,
            Integer monat) {
        if (docs == null || docs.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        java.util.List<ProjektGeschaeftsdokument> basis = docs;
        if (monat != null) {
            final int zielMonat = monat;
            basis = docs.stream()
                    .filter(d -> d.getRechnungsdatum() != null && d.getRechnungsdatum().getMonthValue() == zielMonat)
                    .toList();
        }

        if (basis.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // Aggregate data per customer
        java.util.Map<Long, CustomerAggregation> kundenMap = new java.util.LinkedHashMap<>();

        for (ProjektGeschaeftsdokument doc : basis) {
            Projekt projekt = doc.getProjekt();
            if (projekt == null) {
                continue;
            }

            Kunde kunde = projekt.getKundenId();
            if (kunde == null) {
                continue;
            }

            Long kundenId = kunde.getId();
            CustomerAggregation aggregation = kundenMap.get(kundenId);
            if (aggregation == null) {
                aggregation = new CustomerAggregation(kundenId, kunde.getName());
                kundenMap.put(kundenId, aggregation);
            }

            // Add revenue
            if (doc.getBruttoBetrag() != null) {
                aggregation.umsatz += doc.getBruttoBetrag().doubleValue();
            }

            // Track unique projects and calculate profit
            Long projektId = projekt.getId();
            if (projektId != null && aggregation.projektIds.add(projektId)) {
                aggregation.projektAnzahl++;
                aggregation.gewinn += berechneProjektGewinn(projekt);
            }
        }

        // Return top 10 customers by revenue
        return kundenMap.values().stream()
                .sorted(java.util.Comparator.comparingDouble((CustomerAggregation c) -> c.umsatz).reversed())
                .limit(10)
                .map(c -> {
                    org.example.kalkulationsprogramm.dto.Projekt.TopKundeDto dto = new org.example.kalkulationsprogramm.dto.Projekt.TopKundeDto();
                    dto.setKundenId(c.kundenId);
                    dto.setKundenName(c.kundenName);
                    dto.setUmsatz(c.umsatz);
                    dto.setProjektAnzahl(c.projektAnzahl);
                    dto.setGewinn(c.gewinn);
                    return dto;
                })
                .toList();
    }

    private static class CustomerAggregation {
        private final Long kundenId;
        private final String kundenName;
        private double umsatz;
        private long projektAnzahl;
        private double gewinn;
        private final java.util.Set<Long> projektIds = new java.util.HashSet<>();

        private CustomerAggregation(Long kundenId, String kundenName) {
            this.kundenId = kundenId;
            this.kundenName = kundenName;
        }
    }

    public ProjektDokument holeDokument(Long dokumentID) {
        return this.dokumentRepository.findById(dokumentID)
                .orElseThrow(() -> new RuntimeException("Projektdokument konnte nicht gefunden werden!"));
    }

    @Transactional
    public void setzeGeschaeftsdokumentBezahlt(Long dokumentID, boolean bezahlt) {
        ProjektDokument dokument = dokumentRepository.findById(dokumentID)
                .orElseThrow(() -> new RuntimeException("Projektdokument konnte nicht gefunden werden!"));
        if (dokument instanceof ProjektGeschaeftsdokument geschaeftsdokument) {
            geschaeftsdokument.setBezahlt(bezahlt);
            dokumentRepository.save(geschaeftsdokument);
            if (geschaeftsdokument.getProjekt() != null) {
                Long projektId = geschaeftsdokument.getProjekt().getId();
                pruefeProjektAbschluss(projektId);
            }
        } else {
            throw new RuntimeException("Dokument ist kein Geschäftsdokument.");
        }
    }

    /**
     * Prüft ob ein Projekt als bezahlt gesetzt werden kann.
     * Bedingung: Alle Rechnungen im Offene-Posten-Center sind als bezahlt markiert.
     * Wenn erfüllt → projekt.bezahlt = true, projekt.abgeschlossen = true
     * Wenn nicht → projekt.bezahlt = false (abgeschlossen bleibt unverändert)
     */
    private void pruefeProjektAbschluss(Long projektId) {
        Projekt projekt = projektRepository.findById(projektId).orElse(null);
        if (projekt == null) return;

        boolean keineOffenenPosten = !dokumentRepository.existsOffenePostenByProjektId(projektId);

        if (keineOffenenPosten) {
            projekt.setBezahlt(true);
            projekt.setAbgeschlossen(true);
        } else {
            projekt.setBezahlt(false);
            projekt.setAbgeschlossen(false);
        }
        projektRepository.save(projekt);
    }

    @Transactional
    public void loescheDatei(Long dokumentID) {
        ProjektDokument dokument = this.dokumentRepository.findById(dokumentID)
                .orElseThrow(() -> new RuntimeException("Projektdokument konnte nicht gefunden werden!"));
        Long projektId = dokument.getProjekt() != null ? dokument.getProjekt().getId() : null;
        try {
            Path dateipfad = this.dokumentenSpeicherplatz.resolve(dokument.getGespeicherterDateiname());
            Files.deleteIfExists(dateipfad);
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim löschen der phyisischen Datei: " + dokument.getOriginalDateiname(),
                    e);
        }
        this.dokumentRepository.delete(dokument);
        if (projektId != null) {
            aktualisiereProjektFinanzstatus(projektId);
        }
    }

    @Transactional
    public void loescheAnfrageDatei(Long dokumentID) {
        AnfrageDokument dokument = this.anfrageDokumentRepository.findById(dokumentID)
                .orElseThrow(() -> new RuntimeException("Anfragesdokument konnte nicht gefunden werden!"));
        try {
            Path dateipfad = this.anfragenSpeicherplatz.resolve(dokument.getGespeicherterDateiname());
            if (!Files.deleteIfExists(dateipfad)) {
                Path hicadPfad = this.hicadSpeicherplatz.resolve(dokument.getGespeicherterDateiname());
                if (!Files.deleteIfExists(hicadPfad)) {
                    Path fallbackPfad = this.dokumentenSpeicherplatz.resolve(dokument.getGespeicherterDateiname());
                    Files.deleteIfExists(fallbackPfad);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim löschen der phyisischen Datei: " + dokument.getOriginalDateiname(),
                    e);
        }
        this.anfrageDokumentRepository.delete(dokument);
    }

    @Transactional
    public void loescheAngebotDatei(Long dokumentID) {
        org.example.kalkulationsprogramm.domain.AngebotDokument dokument = this.angebotDokumentRepository.findById(dokumentID)
                .orElseThrow(() -> new RuntimeException("Angebotsdokument konnte nicht gefunden werden!"));
        try {
            Path dateipfad = this.anfragenSpeicherplatz.resolve(dokument.getGespeicherterDateiname());
            if (!Files.deleteIfExists(dateipfad)) {
                Path fallbackPfad = this.dokumentenSpeicherplatz.resolve(dokument.getGespeicherterDateiname());
                Files.deleteIfExists(fallbackPfad);
            }
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Löschen der physischen Datei: " + dokument.getOriginalDateiname(), e);
        }
        this.angebotDokumentRepository.delete(dokument);
    }

    /**
     * Speichert ein Bild und gibt den relativen, über den {@code DateiController}
     * abrufbaren Web-Pfad zurück.
     *
     * @param datei Das hochgeladene Bild
     * @return Der relative Web-Pfad zum Bild, z.B. "/api/images/xyz.png"
     */
    public String speichereBild(MultipartFile datei) {
        String dateityp = datei.getContentType();
        if (dateityp == null || !ERLAUBTE_BILD_TYPEN.contains(dateityp)) {
            throw new RuntimeException("Ungültiger Dateityp! Nur JPEG, PNG, GIF und WebP sind erlaubt.");
        }
        String originalDateiname = StringUtils.cleanPath(Objects.requireNonNull(datei.getOriginalFilename()));
        String speichername = generiereEinzigartigenDateinamen(originalDateiname);
        Path zielPfad = this.bilderSpeicherplatz.resolve(speichername);
        try {
            Files.copy(datei.getInputStream(), zielPfad, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Bild konnte nicht gespeichert werden.", e);
        }
        // Gib den vom Backend bereitgestellten Web-Pfad zurück
        return "/api/images/" + speichername;
    }

    @Transactional
    public void loescheBild(String bildUrl) {
        try {
            String dateiname = Path.of(bildUrl).getFileName().toString();
            Path dateipfad = this.bilderSpeicherplatz.resolve(dateiname);
            Files.deleteIfExists(dateipfad);
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim löschen der phyisischen Datei:", e);
        }
    }

    /**
     * NEU: Lädt eine gespeicherte Bilddatei als Resource.
     *
     * @param dateiname Der einzigartige Name der Datei auf der Festplatte.
     * @return Die Datei als Resource-Objekt.
     */
    public Resource ladeBildAlsResource(String dateiname) {
        try {
            // 1) Zuerst im normalen Bilderordner nachsehen
            Path dateipfad = bilderSpeicherplatz.resolve(dateiname).normalize();
            Resource resource = new UrlResource(dateipfad.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            // 2) Danach im Ordner für Schnittbilder prüfen
            Path altPfad = schnittbilderSpeicherplatz.resolve(dateiname).normalize();
            Resource altRes = new UrlResource(altPfad.toUri());
            if (altRes.exists() && altRes.isReadable()) {
                return altRes;
            }
            throw new RuntimeException("Datei nicht gefunden oder nicht lesbar: " + dateiname);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Fehler beim Lesen der Datei: " + dateiname, e);
        }
    }

    public Resource ladeDokumentAlsResource(String dateiname) {
        try {
            // Try all storage locations in order
            Path[] searchPaths = {
                    dokumentenSpeicherplatz.resolve(dateiname).normalize(),
                    anfragenSpeicherplatz.resolve(dateiname).normalize(),
                    hicadSpeicherplatz.resolve(dateiname).normalize()
            };

            for (Path pfad : searchPaths) {
                if (Files.exists(pfad)) {
                    Resource resource = new UrlResource(pfad.toUri());
                    if (resource.exists() && resource.isReadable()) {
                        return resource;
                    }
                }
            }

            // If not found with exact name, try case-insensitive search in each directory
            for (Path basePath : new Path[] { dokumentenSpeicherplatz, anfragenSpeicherplatz, hicadSpeicherplatz }) {
                try {
                    if (Files.exists(basePath) && Files.isDirectory(basePath)) {
                        java.util.Optional<Path> found = Files.list(basePath)
                                .filter(p -> p.getFileName().toString().equalsIgnoreCase(dateiname))
                                .findFirst();
                        if (found.isPresent()) {
                            Resource resource = new UrlResource(found.get().toUri());
                            if (resource.exists() && resource.isReadable()) {
                                return resource;
                            }
                        }
                    }
                } catch (IOException ignored) {
                    // Continue to next directory
                }
            }

            throw new RuntimeException("Datei nicht gefunden oder nicht lesbar: " + dateiname +
                    " (gesucht in: " + dokumentenSpeicherplatz + ", " + anfragenSpeicherplatz + ", "
                    + hicadSpeicherplatz + ")");
        } catch (MalformedURLException e) {
            throw new RuntimeException("Fehler beim Lesen der Datei: " + dateiname, e);
        }
    }

    /**
     * Prueft, ob eine gespeicherte Datei physisch im HiCAD-Speicherpfad liegt.
     * 
     * @param dateiname gespeicherter Dateiname (UUID + Extension)
     * @return true, wenn die Datei unterhalb von hicadSpeicherplatz existiert
     */
    public boolean liegtInHicadSpeicher(String dateiname) {
        try {
            Path p = hicadSpeicherplatz.resolve(dateiname).normalize();
            return Files.exists(p);
        } catch (Exception ignored) {
            return false;
        }
    }

    public String holeNetzwerkPfad(String relativerPfad) {
        if (relativerPfad.contains("..")) {
            throw new ForbiddenException("Pfad außerhalb des freigegebenen Verzeichnisses");
        }
        if (hicadNetworkUrl == null || !hicadNetworkUrl.startsWith("\\\\")) {
            throw new IllegalStateException("network-url muss UNC sein");
        }
        String cleaned = relativerPfad.replace("/", "\\");
        return hicadNetworkUrl.endsWith("\\") ? hicadNetworkUrl + cleaned : hicadNetworkUrl + "\\" + cleaned;
    }

    public String holeWindowsLaufwerkPfad(String relativerPfad) {
        if (relativerPfad == null || relativerPfad.contains("..")) {
            throw new ForbiddenException("Pfad ausserhalb des freigegebenen Verzeichnisses");
        }
        if (networkDriveLetter == null || networkDriveLetter.isBlank()) {
            return null;
        }
        String letter = networkDriveLetter.endsWith(":") ? networkDriveLetter : (networkDriveLetter + ":");
        String cleaned = relativerPfad.replace("/", "\\");
        return letter + "\\" + cleaned;
    }

    private boolean isAusgangsrechnungInvoiceId(String invoiceId) {
        if (invoiceId == null) {
            return false;
        }
        String normalized = invoiceId.trim();
        return AUSGANGSRECHNUNG_INVOICE_PATTERN.matcher(normalized).matches();
    }

    private String normalizeGeschaeftsdokumentart(String art) {
        if (art == null) {
            return "Rechnung";
        }
        String trimmed = art.trim();
        return trimmed.isEmpty() ? "Rechnung" : trimmed;
    }

    private boolean istRechnung(String art) {
        return art != null && "rechnung".equalsIgnoreCase(art);
    }

    private boolean istMahnung(String art) {
        return art != null && "mahnung".equalsIgnoreCase(art);
    }

    private Mahnstufe parseMahnstufe(String wert) {
        if (wert == null) {
            return null;
        }
        String normalized = wert.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Mahnstufe.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String generiereEinzigartigenDateinamen(String dateiname) {
        String dateiendung = "";
        int findePunkt = dateiname.lastIndexOf(".");
        if (findePunkt > 0 && findePunkt < dateiname.length() - 1) {
            dateiendung = dateiname.substring(findePunkt);
        }
        return UUID.randomUUID() + dateiendung;
    }

    private void kopiereNachNetzwerkFreigabeFallsAbweichend(String gespeicherterDateiname,
            Path bereitsGespeichertPfad) {
        try {
            if (hicadNetworkUrl == null || !hicadNetworkUrl.startsWith("\\\\")) {
                return; // keine UNC-Freigabe konfiguriert
            }
            Path netzBasis = Path.of(hicadNetworkUrl).toAbsolutePath().normalize();
            Path netzZiel = netzBasis.resolve(gespeicherterDateiname).normalize();
            if (!bereitsGespeichertPfad.normalize().equals(netzZiel)) {
                // Erzeuge Zielordner und kopiere falls nicht identisch
                try {
                    Files.createDirectories(netzBasis);
                } catch (Exception ignored) {
                }
                try {
                    Files.copy(bereitsGespeichertPfad, netzZiel, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
            // Keine harten Fehler werfen – falls Netzpfad nicht erreichbar, bleibt Datei
            // lokal vorhanden
        }
    }

    public Dokument ladeDokumentMetadaten(String dateiname) {
        return dokumentRepository.findByGespeicherterDateinameIgnoreCase(dateiname)
                .map(d -> (Dokument) d)
                .or(() -> dokumentRepository.findByOriginalDateinameIgnoreCase(dateiname).map(d -> (Dokument) d))
                .or(() -> anfrageDokumentRepository.findByGespeicherterDateinameIgnoreCase(dateiname)
                        .map(d -> (Dokument) d))
                // Fallback: Mitarbeiter-Dokumente
                .or(() -> mitarbeiterDokumentRepository != null
                        ? mitarbeiterDokumentRepository.findByGespeicherterDateiname(dateiname).map(d -> (Dokument) d)
                        : java.util.Optional.empty())
                .orElseThrow(() -> new NotFoundException("Dokument nicht in der Datenbank gefunden: " + dateiname));
    }

}
