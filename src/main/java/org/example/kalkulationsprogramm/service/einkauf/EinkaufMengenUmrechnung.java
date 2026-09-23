package org.example.kalkulationsprogramm.service.einkauf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;

/** Belegte, deterministic quantity and price-unit interpretation for purchasing. */
public class EinkaufMengenUmrechnung {
    private static final int SCALE = 6;
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final Pattern AMOUNT = Pattern.compile("^([0-9]+(?:[.,][0-9]+)?)\\s*(.*)$");
    private static final Pattern FILLER = Pattern.compile("\\b(je|pro)\\b");

    public Umrechnung normalisieren(Mengenbasis source, Einheit target) {
        if (source == null || target == null || source.einheit() == null || source.menge() == null || source.menge().signum() <= 0)
            return incomplete(source == null ? null : source.faktorQuelle(), "Menge oder Einheit fehlt.");
        BigDecimal qty = source.menge();
        Einheit from = source.einheit();
        if (from == target) return complete(qty, target, BigDecimal.ONE, "Einheit identisch");
        if (from == Einheit.TONNE && target == Einheit.KILOGRAMM) return complete(qty.multiply(THOUSAND), target, THOUSAND, "1 t = 1000 kg");
        if (from == Einheit.KILOGRAMM && target == Einheit.TONNE) return complete(qty.divide(THOUSAND, SCALE, RoundingMode.HALF_UP), target, BigDecimal.ONE.divide(THOUSAND, SCALE, RoundingMode.HALF_UP), "1000 kg = 1 t");
        if (from == Einheit.STUECK && target == Einheit.METER && positive(source.einzelLaengeMm())) {
            BigDecimal factor = source.einzelLaengeMm().divide(THOUSAND, SCALE, RoundingMode.HALF_UP);
            return complete(qty.multiply(factor), target, factor, source.faktorQuelle());
        }
        if (from == Einheit.METER && target == Einheit.STUECK && positive(source.einzelLaengeMm())) {
            BigDecimal factor = THOUSAND.divide(source.einzelLaengeMm(), SCALE, RoundingMode.HALF_UP);
            return complete(qty.multiply(factor), target, factor, source.faktorQuelle());
        }
        if (from == Einheit.KILOGRAMM && target == Einheit.METER && positive(source.kgJeMeter()) && belegteQuelle(source.faktorQuelle())) {
            BigDecimal factor = BigDecimal.ONE.divide(source.kgJeMeter(), SCALE, RoundingMode.HALF_UP);
            return complete(qty.multiply(factor), target, factor, source.faktorQuelle());
        }
        if (from == Einheit.METER && target == Einheit.KILOGRAMM && positive(source.kgJeMeter()) && belegteQuelle(source.faktorQuelle()))
            return complete(qty.multiply(source.kgJeMeter()), target, source.kgJeMeter(), source.faktorQuelle());
        return incomplete(source.faktorQuelle(), "Für diese Einheit fehlt ein belegter Umrechnungsfaktor.");
    }

    public static PreisbasisParse parsePreisBasis(String raw) {
        if (raw == null || raw.isBlank()) return new PreisbasisParse(BigDecimal.ONE, null, false);
        String text = raw.toLowerCase(Locale.ROOT).replace("€", " ").replace("eur", " ").replace("/", " ");
        text = FILLER.matcher(text).replaceAll(" ").trim();
        BigDecimal quantity = BigDecimal.ONE;
        String code = text;
        Matcher matcher = AMOUNT.matcher(text);
        if (matcher.matches()) {
            quantity = parseAmount(matcher.group(1));
            code = matcher.group(2).trim();
        }
        Einheit unit = switch (code) {
            case "kg", "kgm", "kilo", "kilogramm" -> Einheit.KILOGRAMM;
            case "t", "to", "tne", "ton", "tonne", "tonnen" -> { quantity = quantity.multiply(THOUSAND); yield Einheit.KILOGRAMM; }
            case "g", "gr", "grm", "gramm" -> { quantity = quantity.divide(THOUSAND, SCALE, RoundingMode.HALF_UP); yield Einheit.KILOGRAMM; }
            case "c62", "h87", "ea", "pce", "pcs", "st", "stk", "stck", "stueck", "stück", "piece" -> Einheit.STUECK;
            case "mtr", "m", "lm", "lfm", "lfdm", "meter", "laufmeter" -> Einheit.METER;
            case "mtk", "m2", "m²", "qm", "quadratmeter" -> Einheit.QUADRATMETER;
            default -> null;
        };
        return new PreisbasisParse(quantity, unit, unit != null);
    }

    public record Umrechnung(BigDecimal menge, Einheit einheit, BigDecimal faktor, String quelle,
            boolean vollstaendig, String hinweis) {}
    public record PreisbasisParse(BigDecimal menge, Einheit einheit, boolean vollstaendig) {}
    private static Umrechnung complete(BigDecimal qty, Einheit unit, BigDecimal factor, String source) {
        return new Umrechnung(qty.setScale(SCALE, RoundingMode.HALF_UP), unit, factor, source, true, null);
    }
    private static Umrechnung incomplete(String source, String hint) { return new Umrechnung(null, null, null, source, false, hint); }
    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private static boolean belegteQuelle(String source) { return source != null && !source.isBlank(); }
    private static BigDecimal parseAmount(String raw) {
        String value = raw.trim();
        if (value.matches("\\d{1,3}\\.\\d{3}")) value = value.replace(".", "");
        else value = value.replace(',', '.');
        try { BigDecimal amount = new BigDecimal(value); return amount.signum() > 0 ? amount : BigDecimal.ONE; }
        catch (NumberFormatException ex) { return BigDecimal.ONE; }
    }
}
