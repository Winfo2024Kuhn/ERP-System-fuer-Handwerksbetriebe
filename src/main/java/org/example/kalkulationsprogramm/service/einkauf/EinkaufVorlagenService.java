package org.example.kalkulationsprogramm.service.einkauf;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.kalkulationsprogramm.domain.EmailTextTemplate;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.DokumentSoll;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVorlagenDto.Gerendert;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVorlagenDto.Platzhalter;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVorlagenDto.VorlagenKontext;
import org.example.kalkulationsprogramm.repository.EmailTextTemplateRepository;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EinkaufVorlagenService {
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Z0-9_]+)\\s*}}", Pattern.UNICODE_CASE);
    private static final Pattern ANY_TOKEN = Pattern.compile("\\{\\{[^{}]*}}", Pattern.DOTALL);
    private static final Set<String> FIRMA = Set.of("BANK", "IBAN", "BIC");
    private static final Set<String> EMPFAENGER = Set.of("LIEFERANTENNAME", "ANSPRECHPARTNER", "ANREDE",
            "LIEFERADRESSE", "EIGENE_KUNDENNUMMER_BEIM_LIEFERANTEN");
    private static final Set<String> PROJEKT = Set.of("PROJEKTNUMMER", "BAUVORHABEN");
    private static final Set<String> ZEIT = Set.of("ANTWORTFRIST", "LIEFERTERMIN");
    private static final Set<String> ANFRAGE = Set.of("ANFRAGENUMMER");
    private static final Set<String> BESTELLUNG = Set.of("BESTELLNUMMER");
    private static final Set<String> ANGEBOT = Set.of("LIEFERANTEN_ANGEBOTSNUMMER");
    private static final Set<String> FIX = Set.of("RUECKMELDECODE");
    private static final Set<String> LISTEN = Set.of("POSITIONEN", "ZEUGNISSE");
    private static final Map<String, Set<String>> ERLAUBT = erlaubteTokens();

    private final EmailTextTemplateRepository repository;

    public EinkaufVorlagenService(EmailTextTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Gerendert rendern(Long templateId, VorlagenKontext kontext) {
        EmailTextTemplate template = repository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Einkaufsvorlage nicht gefunden"));
        return rendern(template, kontext);
    }

    public Gerendert entwurfVorschau(String typ, String betreff, String html) {
        if (typ == null) throw new IllegalArgumentException("Bitte eine Einkaufs-Vorlagenart angeben.");
        typ = typ.trim().toUpperCase(java.util.Locale.ROOT);
        var erlaubt = ERLAUBT.get(typ);
        if (erlaubt == null) throw new IllegalArgumentException("Unbekannte Einkaufs-Vorlagenart.");
        if (betreff == null || betreff.isBlank() || betreff.length() > 500 || html == null || html.length() > 100000)
            throw new IllegalArgumentException("Bitte Betreff (höchstens 500 Zeichen) und Nachricht (höchstens 100.000 Zeichen) prüfen.");
        var template = new EmailTextTemplate();
        template.setId(0L); template.setDokumentTyp(typ); template.setSubjectTemplate(betreff); template.setHtmlBody(html);
        var werte = new LinkedHashMap<String, String>();
        for (String token : erlaubt) if (!LISTEN.contains(token)) werte.put(token, "Beispiel " + token.replace('_', ' '));
        if (erlaubt.contains("ANFRAGENUMMER")) werte.put("ANFRAGENUMMER", "PA-2026-00001");
        if (erlaubt.contains("BESTELLNUMMER")) werte.put("BESTELLNUMMER", "B-2026-00001");
        werte.put("LIEFERANTENNAME", "Musterlieferant"); werte.put("ANREDE", "Guten Tag");
        werte.put("EIGENE_KUNDENNUMMER_BEIM_LIEFERANTEN", "00017");
        var position = new PositionSnapshot(org.example.kalkulationsprogramm.domain.einkauf.Positionsart.ARTIKEL,
                1L, "A-00001", null, null, "Beispielprofil", "S235", "40 × 40 mm", null,
                null, null, null, null, null,
                List.of(new DokumentSoll(org.example.kalkulationsprogramm.domain.einkauf.Dokumentart.ZEUGNIS_3_1, "EN 10204", "2004", false)), List.of());
        return rendern(template, new VorlagenKontext(typ, werte, List.of(position), "VORSCHAU-KEIN-VERSAND"));
    }

    private Gerendert rendern(EmailTextTemplate template, VorlagenKontext kontext) {
        if (!template.isAktiv()) throw new IllegalArgumentException("Einkaufsvorlage ist inaktiv");
        if (kontext == null || kontext.typ() == null || !template.getDokumentTyp().equals(kontext.typ())) {
            throw new IllegalArgumentException("Vorlagenart und Einkaufsvorgang passen nicht zusammen");
        }
        if (kontext.rueckmeldecode() == null || kontext.rueckmeldecode().isBlank()) {
            throw new IllegalArgumentException("Der geschützte Zuordnungscode fehlt");
        }
        Set<String> allowed = ERLAUBT.get(kontext.typ());
        if (allowed == null) throw new IllegalArgumentException("Unbekannte Einkaufs-Vorlagenart: " + kontext.typ());

        String subjectSource = template.getSubjectTemplate() == null ? "" : template.getSubjectTemplate();
        if (subjectSource.contains("\r") || subjectSource.contains("\n")) throw new IllegalArgumentException("Der Betreff darf keine Zeilenumbrüche enthalten");
        Map<String, String> values = new LinkedHashMap<>();
        if (kontext.skalare() != null) values.putAll(kontext.skalare());
        if (kontext.rueckmeldecode() != null && !kontext.rueckmeldecode().isBlank()) values.put("RUECKMELDECODE", kontext.rueckmeldecode());
        for (String supplied : values.keySet()) {
            if (!allowed.contains(supplied)) throw new IllegalArgumentException("Platzhalter " + supplied + " ist für diese Einkaufs-Vorlage nicht zulässig");
        }
        validateTokens(subjectSource, allowed, true, values);
        validateTokens(template.getHtmlBody(), allowed, false, values);
        validateStructuredLists(template.getHtmlBody(), kontext.positionen());

        String subject = substitute(subjectSource, values, false, kontext.positionen());
        if (subject.contains("\r") || subject.contains("\n")) throw new IllegalArgumentException("Der Betreff darf keine Zeilenumbrüche enthalten");
        String body = substitute(template.getHtmlBody(), values, true, kontext.positionen());
        body = Jsoup.clean(body, "", Safelist.relaxed().addProtocols("a", "href", "http", "https", "mailto"), new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));
        String code = kontext.rueckmeldecode() == null ? "" : kontext.rueckmeldecode().trim();
        if (!code.isEmpty()) body += "<p>Zuordnungscode: " + escape(code) + "</p>";
        String hash = sha256(template.getId() + ":" + template.getVersion() + "\n" + subject + "\n" + body);
        return new Gerendert(template.getId(), template.getVersion(), subject, body, hash);
    }

    public List<Platzhalter> placeholders(String dokumentTyp) {
        Set<String> allowed = ERLAUBT.get(dokumentTyp);
        if (allowed == null) throw new IllegalArgumentException("Unbekannte Einkaufs-Vorlagenart: " + dokumentTyp);
        List<Platzhalter> result = new ArrayList<>();
        allowed.stream().sorted().forEach(token -> result.add(new Platzhalter(token, token.replace('_', ' ').toLowerCase(),
                token.equals("RUECKMELDECODE") || ANFRAGE.contains(token) || BESTELLUNG.contains(token),
                !LISTEN.contains(token))));
        return List.copyOf(result);
    }

    private static void validateTokens(String source, Set<String> allowed, boolean subject, Map<String, String> values) {
        if (source == null) return;
        Matcher matcher = TOKEN.matcher(source);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (!allowed.contains(token)) throw new IllegalArgumentException("Platzhalter {{" + token + "}} ist für diese Einkaufs-Vorlage nicht zulässig");
            if (subject && LISTEN.contains(token)) throw new IllegalArgumentException("Positions- und Zeugnislisten dürfen nicht im Betreff stehen");
            if (!LISTEN.contains(token) && (values.get(token) == null || values.get(token).isBlank())) {
                throw new IllegalArgumentException("Für {{" + token + "}} fehlt ein Wert");
            }
        }
        Matcher malformed = ANY_TOKEN.matcher(source);
        while (malformed.find()) {
            if (!TOKEN.matcher(malformed.group()).matches()) throw new IllegalArgumentException("Unbekannter oder ungültiger Platzhalter: " + malformed.group());
        }
    }

    private static String substitute(String source, Map<String, String> values, boolean html, List<PositionSnapshot> positions) {
        if (source == null) return "";
        Matcher matcher = TOKEN.matcher(source);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = LISTEN.contains(token)
                    ? (html ? (token.equals("ZEUGNISSE") ? zeugnisseHtml(positions) : positionenHtml(positions)) : "")
                    : values.getOrDefault(token, "");
            if (html && !LISTEN.contains(token)) replacement = escape(replacement);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String positionenHtml(List<PositionSnapshot> positions) {
        StringBuilder html = new StringBuilder("<ul>");
        for (PositionSnapshot p : positions) html.append("<li>").append(escape(p.bezeichnung())).append(" — ")
                .append(escape(p.abmessung())).append("</li>");
        return html.append("</ul>").toString();
    }

    private static String zeugnisseHtml(List<PositionSnapshot> positions) {
        StringBuilder html = new StringBuilder("<ul>");
        for (PositionSnapshot position : positions) {
            for (DokumentSoll dokument : position.dokumente()) {
                html.append("<li>").append(escape(position.bezeichnung())).append(": ")
                        .append(escape(dokument.art() == null ? "" : dokument.art().name()));
                if (dokument.grundlage() != null && !dokument.grundlage().isBlank()) {
                    html.append(" — ").append(escape(dokument.grundlage()));
                }
                html.append("</li>");
            }
        }
        return html.append("</ul>").toString();
    }

    private static void validateStructuredLists(String body, List<PositionSnapshot> positions) {
        if (body == null) return;
        Matcher matcher = TOKEN.matcher(body);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token.equals("POSITIONEN") && (positions == null || positions.isEmpty()
                    || positions.stream().anyMatch(java.util.Objects::isNull))) {
                throw new IllegalArgumentException("Für {{POSITIONEN}} fehlen Positionen");
            }
            if (token.equals("ZEUGNISSE") && (positions == null || positions.stream()
                    .filter(java.util.Objects::nonNull).flatMap(position -> position.dokumente().stream()).findAny().isEmpty())) {
                throw new IllegalArgumentException("Für {{ZEUGNISSE}} fehlen geforderte Zeugnisse");
            }
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#x27;");
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 nicht verfügbar", ex); }
    }

    private static Map<String, Set<String>> erlaubteTokens() {
        Set<String> base = new java.util.HashSet<>();
        base.addAll(FIRMA); base.addAll(EMPFAENGER); base.addAll(PROJEKT); base.addAll(ZEIT); base.addAll(FIX); base.addAll(LISTEN);
        Map<String, Set<String>> map = new LinkedHashMap<>();
        map.put("EINKAUF_ANFRAGE", combine(base, ANFRAGE));
        map.put("EINKAUF_BESTELLUNG", combine(base, ANFRAGE, BESTELLUNG, ANGEBOT));
        map.put("EINKAUF_DIREKTBESTELLUNG", combine(base, BESTELLUNG));
        map.put("EINKAUF_NACHFRAGE", combine(base, ANFRAGE, ANGEBOT));
        map.put("EINKAUF_ZEUGNIS_NACHFORDERUNG", combine(base, ANFRAGE, BESTELLUNG));
        map.put("EINKAUF_BESTAETIGUNG_NACHFRAGE", combine(base, ANFRAGE, BESTELLUNG));
        map.put("EINKAUF_LIEFERUNG_NACHFRAGE", combine(base, ANFRAGE, BESTELLUNG));
        return Map.copyOf(map);
    }

    @SafeVarargs private static Set<String> combine(Set<String>... sets) {
        Set<String> result = new java.util.HashSet<>();
        for (Set<String> set : sets) result.addAll(set);
        return Set.copyOf(result);
    }
}
