package org.example.kalkulationsprogramm.service.dokumentstand;

import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.util.HtmlKlartext;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Liest das {@code positionenJson} eines Ausgangsdokuments in eine flache, vergleichbare Form.
 *
 * <p>Format: {@code {blocks:[…], globalRabatt, …}} oder das alte reine Array. Leistungen in
 * Bauabschnitten ({@code children}) kommen mit in die Liste. Die Pos.-Bezeichnung ist
 * {@code block.pos}, sonst die laufende Nummer der Leistung. Kaputtes JSON ergibt eine leere,
 * als {@link #lesbar() nicht lesbar} markierte Liste – nie eine Ausnahme.</p>
 */
record DokumentPositionen(boolean lesbar, List<Leistung> leistungen, List<Abschnitt> abschnitte,
        List<Text> texte, double gesamtRabatt) {

    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();

    /** Eine Leistung (SERVICE-Block); Texte schon ohne HTML. */
    record Leistung(String id, String pos, String titel, double menge, String einheit, double preis,
            double rabatt, boolean wahl, String beschreibung) {
    }

    /** Ein Bauabschnitt (SECTION_HEADER). */
    record Abschnitt(String id, String name) {
    }

    /** Ein Textblock (TEXT, CLOSURE) mit seinem Roh-Inhalt. */
    record Text(String id, String inhalt) {
    }

    static DokumentPositionen lies(String positionenJson) {
        if (positionenJson == null || positionenJson.isBlank()) {
            return leer(true);
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(positionenJson);
        } catch (JacksonException e) {
            return leer(false);
        }
        JsonNode bloecke = root.isArray() ? root : root.path("blocks");
        DokumentPositionen ergebnis = new DokumentPositionen(true, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), Math.max(0d, root.path("globalRabatt").asDouble(0)));
        if (bloecke.isArray()) {
            int[] laufend = { 0 };
            for (JsonNode block : bloecke) {
                ergebnis.nimmAuf(block, laufend);
            }
        }
        return ergebnis;
    }

    private static DokumentPositionen leer(boolean lesbar) {
        return new DokumentPositionen(lesbar, List.of(), List.of(), List.of(), 0d);
    }

    private void nimmAuf(JsonNode block, int[] laufend) {
        String id = block.path("id").asString("");
        switch (block.path("type").asString("")) {
            case "SERVICE" -> {
                laufend[0]++;
                String pos = block.path("pos").asString("").trim();
                leistungen.add(new Leistung(id, pos.isEmpty() ? String.valueOf(laufend[0]) : pos,
                        klartext(block.path("title").asString("")),
                        block.path("quantity").asDouble(0), block.path("unit").asString("").trim(),
                        block.path("price").asDouble(0), block.path("discount").asDouble(0),
                        block.path("optional").asBoolean(false),
                        klartext(block.path("description").asString(""))));
            }
            case "SECTION_HEADER" -> {
                String name = block.path("sectionLabel").asString("");
                abschnitte.add(new Abschnitt(id, klartext(name.isBlank() ? block.path("title").asString("") : name)));
                JsonNode kinder = block.path("children");
                if (kinder.isArray()) {
                    for (JsonNode kind : kinder) {
                        nimmAuf(kind, laufend);
                    }
                }
            }
            case "TEXT", "CLOSURE" -> texte.add(new Text(id, block.path("content").asString("")));
            default -> {
                // Trenner und Zwischensummen tragen keinen eigenen Inhalt.
            }
        }
    }

    private static String klartext(String html) {
        String text = HtmlKlartext.zuText(html);
        return text == null ? "" : text.replaceAll("\\s++", " ").trim();
    }
}
