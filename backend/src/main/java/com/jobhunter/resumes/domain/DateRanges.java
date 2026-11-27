package com.jobhunter.resumes.domain;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects date ranges such as "Ene 2020 – Actualidad", "01/2019 - 12/2021", "2018 to 2020" (ES/EN/IT/DE/FR). */
public final class DateRanges {

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("ene", 1), Map.entry("gen", 1), Map.entry("janv", 1), Map.entry("jän", 1),
            Map.entry("feb", 2), Map.entry("fév", 2), Map.entry("fev", 2), Map.entry("febr", 2),
            Map.entry("mar", 3), Map.entry("mär", 3), Map.entry("mrz", 3), Map.entry("mars", 3),
            Map.entry("apr", 4), Map.entry("abr", 4), Map.entry("avr", 4),
            Map.entry("may", 5), Map.entry("mai", 5), Map.entry("mag", 5),
            Map.entry("jun", 6), Map.entry("giu", 6), Map.entry("juin", 6),
            Map.entry("jul", 7), Map.entry("lug", 7), Map.entry("juil", 7),
            Map.entry("aug", 8), Map.entry("ago", 8), Map.entry("aoû", 8), Map.entry("aou", 8),
            Map.entry("sep", 9), Map.entry("set", 9), Map.entry("sept", 9),
            Map.entry("oct", 10), Map.entry("okt", 10), Map.entry("ott", 10),
            Map.entry("nov", 11),
            Map.entry("dec", 12), Map.entry("dic", 12), Map.entry("dez", 12), Map.entry("déc", 12));

    private static final String MONTH_WORD = "(?:[A-Za-zÀ-ÿ]{3,10}\\.?)";
    private static final String POINT = "(?:" + MONTH_WORD + "\\s+(?:de\\s+)?\\d{4}|\\d{1,2}[/.-]\\d{4}|\\d{4}[/.-]\\d{1,2}|\\d{4})";
    private static final String PRESENT = "(?:present|presente|actualidad|actual|current|now|today|hoy|oggi|heute|aujourd'hui|en curso|a la fecha)";
    private static final Pattern RANGE = Pattern.compile(
            "(" + POINT + ")\\s*(?:-|–|—|to|a|al|hasta|bis|à|au)\\s*(" + POINT + "|" + PRESENT + ")",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private DateRanges() {
    }

    public record Range(LocalDate start, LocalDate end, boolean current, int matchStart, int matchEnd) {
    }

    public static Optional<Range> find(String line) {
        if (line == null) return Optional.empty();
        Matcher m = RANGE.matcher(line);
        while (m.find()) {
            LocalDate start = point(m.group(1));
            if (start == null) continue;
            String endText = m.group(2).trim().toLowerCase(Locale.ROOT);
            boolean current = endText.matches(PRESENT);
            LocalDate end = current ? null : point(m.group(2));
            if (!current && end == null) continue;
            if (end != null && end.isBefore(start)) continue;
            return Optional.of(new Range(start, end, current, m.start(), m.end()));
        }
        return Optional.empty();
    }

    /** Parses a single date point to the first day of the month (or January when only a year is given). */
    static LocalDate point(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT).replace("de ", "").replace(".", " ").trim();
        try {
            Matcher numeric = Pattern.compile("^(\\d{1,2})[/ -](\\d{4})$").matcher(t);
            if (numeric.matches()) return safe(Integer.parseInt(numeric.group(2)), Integer.parseInt(numeric.group(1)));
            Matcher iso = Pattern.compile("^(\\d{4})[/ -](\\d{1,2})$").matcher(t);
            if (iso.matches()) return safe(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)));
            Matcher year = Pattern.compile("^(\\d{4})$").matcher(t);
            if (year.matches()) return safe(Integer.parseInt(year.group(1)), 1);
            Matcher word = Pattern.compile("^([a-zà-ÿ]{3,10})\\s+(\\d{4})$").matcher(t);
            if (word.matches()) {
                Integer month = month(word.group(1));
                return month == null ? null : safe(Integer.parseInt(word.group(2)), month);
            }
        } catch (NumberFormatException ignored) {
            // falls through
        }
        return null;
    }

    private static Integer month(String word) {
        for (int len = Math.min(5, word.length()); len >= 3; len--) {
            Integer m = MONTHS.get(word.substring(0, len));
            if (m != null) return m;
        }
        return null;
    }

    private static LocalDate safe(int year, int month) {
        if (year < 1950 || year > 2100 || month < 1 || month > 12) return null;
        return LocalDate.of(year, month, 1);
    }
}
