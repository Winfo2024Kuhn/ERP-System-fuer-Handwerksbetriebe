package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegKiAnalyseStatus;
import org.example.kalkulationsprogramm.domain.BelegKostenstellenAnteil;
import org.example.kalkulationsprogramm.domain.BelegQuelle;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.KasseEinstellung;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.dto.KassenbuchungDto;
import org.example.kalkulationsprogramm.dto.BelegDto;
import org.example.kalkulationsprogramm.dto.KassenbuchungDto.Art;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.KasseEinstellungRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Gemeinsamer, transaktionaler Schreibpfad der sechs Kassenbuch-Kacheln. */
@Slf4j
@Service
@RequiredArgsConstructor
public class KassenbuchungService {
    private final BelegRepository belegRepository;
    private final SachkontoRepository sachkontoRepository;
    private final KostenstelleRepository kostenstelleRepository;
    private final BelegKostenstellenAnteilRepository belegKostenstellenAnteilRepository;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final KasseSaldoService kasseSaldoService;
    private final KassenbuchSchreibschutz schreibschutz;
    private final BelegAuditService auditService;
    private final BelegPdfService belegPdfService;
    private final KasseEinstellungRepository kasseEinstellungRepository;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    // Identisch zu BelegService.uploadBeleg; dortige private Upload-Helfer sind
    // nicht wiederverwendbar. Bei Änderungen die beiden Whitelists synchron halten.
    private static final Set<String> ERLAUBTE_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif", "application/pdf");
    private static final Set<String> ERLAUBTE_ENDUNGEN = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".pdf");
    private static final long MAX_DATEI_BYTES = 25L * 1024 * 1024;

    @Transactional
    public Beleg buche(KassenbuchungDto.CreateRequest req, MultipartFile datei, Mitarbeiter ersteller) {
        return buche(req, datei, ersteller, null);
    }

    /**
     * Adapter für bestehende Clients: Konto und Zahlungsart bleiben erhalten.
     * Die neuen Kacheln verwenden weiterhin ausschließlich ihre Ableitungstabelle.
     */
    @Transactional
    public Beleg bucheUmbuchung(BelegDto.UmbuchungCreateRequest alt, Mitarbeiter ersteller) {
        KassenbuchungDto.CreateRequest req = new KassenbuchungDto.CreateRequest();
        req.setArt(switch (alt.getBelegKategorie()) {
            case "KASSE_EINNAHME" -> Art.VON_BANK_GEHOLT.name();
            case "KASSE_AUSGABE" -> Art.ZUR_BANK_GEBRACHT.name();
            case "PRIVATEINLAGE" -> Art.EIGENES_GELD_EINGELEGT.name();
            case "PRIVATENTNAHME" -> Art.GELD_PRIVAT_ENTNOMMEN.name();
            default -> throw new IllegalArgumentException("Ungueltige Kategorie");
        });
        req.setBelegDatum(alt.getBelegDatum());
        req.setBetragBrutto(alt.getBetragBrutto());
        req.setBeschreibung(alt.getBeschreibung());
        req.setNotiz(alt.getNotiz());
        return buche(req, null, ersteller, alt);
    }

    private Beleg buche(KassenbuchungDto.CreateRequest req, MultipartFile datei,
                         Mitarbeiter ersteller, BelegDto.UmbuchungCreateRequest legacy) {
        Art art = validiere(req, datei);
        Ableitung ableitung = leiteAb(art, Boolean.TRUE.equals(req.getKeinBelegVorhanden()));
        if (ableitung.umbuchung() && req.getKostenstelleId() != null) {
            throw new IllegalArgumentException("Kassen-Umbuchungen koennen keiner Kostenstelle zugeordnet werden");
        }
        Sachkonto konto = legacy == null ? ladeKonto(req, art, ableitung)
                : legacy.getSachkontoId() == null ? null
                : sachkontoRepository.findById(legacy.getSachkontoId()).orElse(null);
        Kostenstelle kostenstelle = req.getKostenstelleId() == null ? null
                : kostenstelleRepository.findById(req.getKostenstelleId())
                    .orElseThrow(() -> new IllegalArgumentException("Baustelle / Bereich wurde nicht gefunden"));
        ProjektGeschaeftsdokument rechnung = ladeRechnung(req);

        // Erst Eingaben, dann abgeschlossenen Monat, dann den Mindestbestand prüfen.
        schreibschutz.assertMonatOffen(req.getBelegDatum());
        if (ableitung.kategorie().istKassenBewegung()) {
            BigDecimal projiziert = kasseSaldoService.projiziereSaldo(
                    null, null, ableitung.kategorie(), req.getBetragBrutto());
            kasseSaldoService.assertSaldoMindestensMindestbestand(projiziert);
        }

        BigDecimal satz = ableitung.umbuchung() ? BigDecimal.ZERO
                : req.getMwstSatz() == null ? BigDecimal.ZERO : req.getMwstSatz();
        Beleg beleg = new Beleg();
        beleg.setBelegKategorie(ableitung.kategorie());
        beleg.setQuelle(ableitung.quelle());
        beleg.setStatus(BelegStatus.VALIDIERT);
        beleg.setKiAnalyseStatus(BelegKiAnalyseStatus.DONE);
        beleg.setIstUmbuchung(ableitung.umbuchung());
        beleg.setBelegDatum(req.getBelegDatum());
        beleg.setBetragBrutto(req.getBetragBrutto());
        beleg.setMwstSatz(satz);
        beleg.setBetragNetto(req.getBetragBrutto().divide(BigDecimal.ONE.add(satz.movePointLeft(2)), 2, RoundingMode.HALF_UP));
        beleg.setBeschreibung(req.getBeschreibung());
        beleg.setGegenpartei(req.getGegenpartei());
        beleg.setNotiz(req.getNotiz());
        beleg.setZahlungsart(legacy == null ? "Bar" : legacy.getZahlungsart());
        beleg.setSachkonto(konto);
        beleg.setKostenstelle(kostenstelle);
        beleg.setAusgangsrechnungId(req.getAusgangsrechnungId());
        LocalDateTime jetzt = LocalDateTime.now();
        beleg.setUploadDatum(jetzt);
        beleg.setValidiertAm(jetzt);
        beleg.setUploadedBy(ersteller);
        beleg.setValidiertVon(ersteller);

        Path dateipfad = null;
        try {
            if (ableitung.quelle() == BelegQuelle.SCAN) {
                String original = sichererDateiname(datei);
                String gespeichert = UUID.randomUUID() + "_" + original;
                dateipfad = belegPfad(gespeichert);
                registriereRollback(dateipfad);
                Files.createDirectories(dateipfad.getParent());
                datei.transferTo(dateipfad);
                beleg.setOriginalDateiname(original);
                beleg.setGespeicherterDateiname(gespeichert);
                beleg.setMimeType(datei.getContentType().toLowerCase(Locale.ROOT));
                beleg.setDateiHash(berechneDateiHash(dateipfad));
            } else {
                String kontoLabel = konto == null ? null : konto.getNummer() + " " + konto.getBezeichnung();
                BelegPdfService.ErzeugtesPdf erzeugt = ableitung.quelle() == BelegQuelle.QUITTUNG
                        ? belegPdfService.erzeugeQuittung(new BelegPdfService.QuittungDaten(
                                beleg.getBetragBrutto(), satz, beleg.getBelegDatum(), req.getGegenpartei(),
                                req.getBeschreibung(), kontoLabel, rechnung == null ? null : rechnung.getDokumentid(), ersteller))
                        : belegPdfService.erzeugeEigenbeleg(new BelegPdfService.EigenbelegDaten(
                                beleg.getBetragBrutto(), beleg.getBelegDatum(), req.getGegenpartei(),
                                req.getBeschreibung() == null ? ableitung.beschreibung() : req.getBeschreibung(),
                                ableitung.quelle() == BelegQuelle.EIGENBELEG ? req.getGrundOhneBeleg() : ableitung.beschreibung(),
                                beleg.getZahlungsart(), kontoLabel, ersteller));
                dateipfad = belegPfad(erzeugt.gespeicherterDateiname());
                registriereRollback(dateipfad);
                if (erzeugt.sha256() == null || erzeugt.sha256().isBlank()) {
                    throw new IllegalStateException("Fingerabdruck der Belegdatei konnte nicht erstellt werden");
                }
                beleg.setGespeicherterDateiname(erzeugt.gespeicherterDateiname());
                beleg.setOriginalDateiname(erzeugt.originalDateiname());
                beleg.setMimeType(erzeugt.mimeType());
                beleg.setDateiHash(erzeugt.sha256());
            }
            Beleg gespeichert = belegRepository.save(beleg);
            if (kostenstelle != null) {
                BelegKostenstellenAnteil anteil = new BelegKostenstellenAnteil();
                anteil.setBeleg(gespeichert);
                anteil.setKostenstelle(kostenstelle);
                anteil.setProzent(100);
                anteil.berechneAnteil(beleg.getBetragNetto(), beleg.getBetragBrutto());
                belegKostenstellenAnteilRepository.save(anteil);
            }
            if (rechnung != null) {
                rechnung.setBezahlt(true);
                projektDokumentRepository.save(rechnung);
            }
            auditService.protokolliereErfassung(gespeichert, ersteller, null);
            return gespeichert;
        } catch (IOException e) {
            loescheDatei(dateipfad);
            throw new UncheckedIOException("Belegdatei konnte nicht gespeichert werden", e);
        } catch (RuntimeException e) {
            loescheDatei(dateipfad);
            throw e;
        }
    }

    private Art validiere(KassenbuchungDto.CreateRequest req, MultipartFile datei) {
        if (req == null) throw new IllegalArgumentException("Anfrage fehlt");
        Art art;
        try { art = Art.valueOf(req.getArt() == null ? "" : req.getArt()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Bitte eine gültige Buchungsart wählen"); }
        if (req.getBetragBrutto() == null || req.getBetragBrutto().signum() <= 0) {
            throw new IllegalArgumentException("Betrag fehlt oder ist nicht positiv");
        }
        if (req.getBetragBrutto().stripTrailingZeros().scale() > 2
                || req.getBetragBrutto().compareTo(new BigDecimal("9999999999999.99")) > 0) {
            throw new IllegalArgumentException("Betrag ist zu groß oder hat mehr als zwei Nachkommastellen");
        }
        if (req.getBelegDatum() == null) throw new IllegalArgumentException("Datum fehlt");
        laenge(req.getGegenpartei(), 120, "Von wem / An wen");
        laenge(req.getBeschreibung(), 500, "Beschreibung");
        laenge(req.getNotiz(), 1000, "Notiz");
        laenge(req.getGrundOhneBeleg(), 255, "Grund ohne Beleg");
        positiveId(req.getSachkontoId(), "Konto");
        positiveId(req.getKostenstelleId(), "Baustelle / Bereich");
        positiveId(req.getAusgangsrechnungId(), "Rechnung");
        boolean ersatz = Boolean.TRUE.equals(req.getKeinBelegVorhanden());
        if (ersatz && art != Art.GELD_AUSGEGEBEN) {
            throw new IllegalArgumentException("Ein Ersatzbeleg ist nur bei Geld ausgegeben möglich");
        }
        if (ersatz && (req.getGrundOhneBeleg() == null || req.getGrundOhneBeleg().isBlank())) {
            throw new IllegalArgumentException("Bitte angeben, warum kein Beleg vorhanden ist");
        }
        if (req.getAusgangsrechnungId() != null && art != Art.GELD_EINGENOMMEN) {
            throw new IllegalArgumentException("Eine Kundenrechnung kann nur bei Geld eingenommen zugeordnet werden");
        }
        if (req.getMwstSatz() != null && List.of(BigDecimal.ZERO, new BigDecimal("7"), new BigDecimal("19"))
                .stream().noneMatch(s -> s.compareTo(req.getMwstSatz()) == 0)) {
            throw new IllegalArgumentException("Mehrwertsteuer muss 0, 7 oder 19 Prozent sein");
        }
        if (datei != null && !datei.isEmpty() && (art != Art.GELD_AUSGEGEBEN || ersatz)) {
            throw new IllegalArgumentException("Eine Belegdatei ist nur bei Geld ausgegeben mit Beleg möglich");
        }
        if (art == Art.GELD_AUSGEGEBEN && !ersatz) validiereDatei(datei);
        if (art == Art.GELD_AUSGEGEBEN && req.getSachkontoId() == null) {
            throw new IllegalArgumentException("Bitte unter Wofür? ein Konto wählen");
        }
        return art;
    }

    private Ableitung leiteAb(Art art, boolean ersatz) {
        return switch (art) {
            case GELD_EINGENOMMEN -> new Ableitung(BelegKategorie.KASSE_EINNAHME, BelegQuelle.QUITTUNG, "8400", false, "Geld eingenommen");
            case GELD_AUSGEGEBEN -> new Ableitung(BelegKategorie.KASSE_AUSGABE, ersatz ? BelegQuelle.EIGENBELEG : BelegQuelle.SCAN, null, ersatz, "Geld ausgegeben");
            case VON_BANK_GEHOLT -> new Ableitung(BelegKategorie.KASSE_EINNAHME, BelegQuelle.TRANSFER, "1200", true, "Geld von der Bank geholt");
            case ZUR_BANK_GEBRACHT -> new Ableitung(BelegKategorie.KASSE_AUSGABE, BelegQuelle.TRANSFER, "1200", true, "Geld zur Bank gebracht");
            case EIGENES_GELD_EINGELEGT -> new Ableitung(BelegKategorie.PRIVATEINLAGE, BelegQuelle.TRANSFER, "1810", true, "Eigenes Geld eingelegt");
            case GELD_PRIVAT_ENTNOMMEN -> new Ableitung(BelegKategorie.PRIVATENTNAHME, BelegQuelle.TRANSFER, "1800", true, "Geld privat entnommen");
        };
    }

    private Sachkonto ladeKonto(KassenbuchungDto.CreateRequest req, Art art, Ableitung ableitung) {
        if ((art == Art.GELD_EINGENOMMEN || art == Art.GELD_AUSGEGEBEN) && req.getSachkontoId() != null) {
            return sachkontoRepository.findById(req.getSachkontoId())
                    .orElseThrow(() -> new IllegalArgumentException("Das gewählte Konto wurde nicht gefunden"));
        }
        if (art == Art.EIGENES_GELD_EINGELEGT) {
            Sachkonto konfiguriert = kasseEinstellungRepository.findSingleton()
                    .map(KasseEinstellung::getPrivateinlageSachkonto).orElse(null);
            if (konfiguriert != null) return konfiguriert;
        }
        return sachkontoRepository.findByNummer(ableitung.kontonummer())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Konto " + ableitung.kontonummer() + " fehlt – bitte in den Stammdaten anlegen"));
    }

    private ProjektGeschaeftsdokument ladeRechnung(KassenbuchungDto.CreateRequest req) {
        if (req.getAusgangsrechnungId() == null) return null;
        // Lock bleibt bis zum Commit von buche bestehen. Eine parallele Zahlung
        // liest danach bezahlt=true und kann keine zweite Einnahme erzeugen.
        var rechnung = projektDokumentRepository.findGeschaeftsdokumentByIdForUpdate(req.getAusgangsrechnungId())
                .orElseThrow(() -> new IllegalArgumentException(
                        projektDokumentRepository.existsById(req.getAusgangsrechnungId())
                                ? "Bitte eine Kundenrechnung wählen" : "Die Kundenrechnung wurde nicht gefunden"));
        if (!istZuordenbareRechnung(rechnung)) {
            throw new IllegalArgumentException("Bitte eine Kundenrechnung wählen");
        }
        if (rechnung.isBezahlt()) throw new IllegalArgumentException("Diese Kundenrechnung ist bereits bezahlt");
        return rechnung;
    }

    private boolean istZuordenbareRechnung(ProjektGeschaeftsdokument dokument) {
        return dokument.getMahnstufe() == null
                && dokument.getGeschaeftsdokumentart() != null
                && dokument.getGeschaeftsdokumentart().toLowerCase(Locale.ROOT).contains("rechnung");
    }

    @Transactional(readOnly = true)
    public List<KassenbuchungDto.OffeneRechnung> offeneAusgangsrechnungen() {
        return projektDokumentRepository.findOffeneGeschaeftsdokumente().stream()
                .filter(this::istZuordenbareRechnung)
                .sorted(Comparator.comparing(ProjektGeschaeftsdokument::getRechnungsdatum,
                        Comparator.nullsLast(Comparator.<LocalDate>reverseOrder())))
                .limit(100)
                .map(g -> KassenbuchungDto.OffeneRechnung.builder()
                        .id(g.getId()).dokumentNummer(g.getDokumentid()).datum(g.getRechnungsdatum())
                        .bruttoBetrag(g.getBruttoBetrag())
                        .kundeName(g.getProjekt() == null ? null : g.getProjekt().getKunde()).build())
                .toList();
    }

    private void validiereDatei(MultipartFile datei) {
        if (datei == null || datei.isEmpty()) throw new IllegalArgumentException("Bitte den Beleg hochladen oder Kein Beleg vorhanden wählen");
        if (datei.getSize() > MAX_DATEI_BYTES) throw new IllegalArgumentException("Datei zu groß (max. 25 MB)");
        String name = sichererDateiname(datei);
        String mime = datei.getContentType() == null ? "" : datei.getContentType().toLowerCase(Locale.ROOT);
        if (!ERLAUBTE_MIME_TYPES.contains(mime)) {
            throw new IllegalArgumentException("Dateityp nicht erlaubt. Erlaubt sind: JPG, PNG, WEBP, HEIC, PDF");
        }
        String lower = name.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (!ERLAUBTE_ENDUNGEN.contains(dot < 0 ? "" : lower.substring(dot))) {
            throw new IllegalArgumentException("Dateiendung nicht erlaubt. Erlaubt sind: JPG, PNG, WEBP, HEIC, PDF");
        }
    }

    private String sichererDateiname(MultipartFile datei) {
        String name = datei.getOriginalFilename();
        if (name == null || name.isBlank() || name.contains("..") || name.contains("/") || name.contains("\\")
                || name.indexOf('\0') >= 0) throw new IllegalArgumentException("Ungueltiger Dateiname");
        return Path.of(name).getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private Path belegPfad(String name) {
        Path basis = Path.of(uploadPath, "belege").toAbsolutePath().normalize();
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")) {
            throw new IllegalStateException("Ungültiger gespeicherter Dateiname");
        }
        Path ziel = basis.resolve(name).normalize();
        if (!ziel.startsWith(basis) || ziel.equals(basis)) throw new IllegalStateException("Ungültiger Belegpfad");
        return ziel;
    }

    private String berechneDateiHash(Path datei) throws IOException {
        try (InputStream input = Files.newInputStream(datei)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] puffer = new byte[8192];
            int gelesen;
            while ((gelesen = input.read(puffer)) != -1) digest.update(puffer, 0, gelesen);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 nicht verfügbar", e); }
    }

    private void registriereRollback(Path datei) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) loescheDatei(datei);
                }
            });
        }
    }

    private void loescheDatei(Path datei) {
        if (datei == null) return;
        try { Files.deleteIfExists(datei); }
        catch (IOException e) { log.error("Belegdatei nach fehlgeschlagener Buchung konnte nicht gelöscht werden", e); }
    }

    private static void laenge(String wert, int max, String feld) {
        if (wert != null && wert.length() > max) throw new IllegalArgumentException(feld + " zu lang (max. " + max + " Zeichen)");
    }
    private static void positiveId(Long id, String feld) {
        if (id != null && id <= 0) throw new IllegalArgumentException(feld + " ist ungültig");
    }
    private record Ableitung(BelegKategorie kategorie, BelegQuelle quelle, String kontonummer,
                             boolean umbuchung, String beschreibung) {}
}
