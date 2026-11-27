package com.jobhunter.jobs.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses free-text salaries such as "$57K - $67K", "80K–100K a year", "€40–€50 an hour", "USD 3,000/mes". */
public final class SalaryParser {

    public record Salary(BigDecimal min, BigDecimal max, String currency, String period) {
    }

    private static final Pattern AMOUNT = Pattern.compile("(\\d{1,3}(?:[.,\\s]\\d{3})+|\\d+(?:[.,]\\d+)?)\\s*([kK])?");

    private SalaryParser() {
    }

    public static Optional<Salary> parse(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String t = text.toLowerCase(Locale.ROOT);
        String currency = currency(t);
        Matcher m = AMOUNT.matcher(t);
        BigDecimal min = null;
        BigDecimal max = null;
        while (m.find()) {
            BigDecimal value = number(m.group(1), m.group(2) != null);
            if (value == null || value.signum() <= 0) continue;
            if (min == null) min = value;
            else {
                max = value;
                break;
            }
        }
        if (min == null) return Optional.empty();
        String period = period(t, min);
        if (max != null && max.compareTo(min) < 0) {
            BigDecimal tmp = min;
            min = max;
            max = tmp;
        }
        return Optional.of(new Salary(min, max, currency, period));
    }

    static String currency(String t) {
        if (t.contains("€") || t.contains("eur")) return "EUR";
        if (t.contains("£") || t.contains("gbp")) return "GBP";
        if (t.contains("cad") || t.contains("c$")) return "CAD";
        if (t.contains("aud") || t.contains("a$")) return "AUD";
        if (t.contains("chf")) return "CHF";
        if (t.contains("brl") || t.contains("r$")) return "BRL";
        if (t.contains("mxn")) return "MXN";
        if (t.contains("bob") || t.contains("bs.")) return "BOB";
        if (t.contains("$") || t.contains("usd")) return "USD";
        return null;
    }

    static String period(String t, BigDecimal sample) {
        if (t.matches(".*\\b(hour|hr|hora|hourly|/h|por hora|stunde)\\b.*") || t.contains("/hr") || t.contains("an hour")) return "HOUR";
        if (t.matches(".*\\b(day|día|dia|daily|tag)\\b.*")) return "DAY";
        if (t.matches(".*\\b(month|mes|mensual|monthly|mois|monat)\\b.*") || t.contains("/mo")) return "MONTH";
        if (t.matches(".*\\b(year|yr|annual|annually|año|anual|jahr|an|pa|p\\.a\\.)\\b.*") || t.contains("/yr")) return "YEAR";
        // no explicit period: infer from magnitude (conservative)
        if (sample.compareTo(BigDecimal.valueOf(300)) < 0) return "HOUR";
        if (sample.compareTo(BigDecimal.valueOf(20_000)) >= 0) return "YEAR";
        return null;
    }

    private static BigDecimal number(String raw, boolean thousands) {
        String digits = raw.replaceAll("\\s", "");
        // "57,000" / "57.000" are thousand separators; "57.5" with k is a decimal
        if (digits.matches("\\d{1,3}([.,]\\d{3})+")) digits = digits.replaceAll("[.,]", "");
        else digits = digits.replace(',', '.');
        try {
            BigDecimal v = new BigDecimal(digits);
            return thousands ? v.multiply(BigDecimal.valueOf(1000)) : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Monthly amount in the same currency, or null when the period is unknown. */
    public static BigDecimal monthly(BigDecimal amount, String period) {
        if (amount == null || period == null) return null;
        return switch (period) {
            case "HOUR" -> amount.multiply(BigDecimal.valueOf(160));
            case "DAY" -> amount.multiply(BigDecimal.valueOf(21));
            case "MONTH" -> amount;
            case "YEAR" -> amount.divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP);
            default -> null;
        };
    }
}
