package com.jobhunter.jobs.domain;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Country names (EN/ES + common aliases) → ISO 3166-1 alpha-2, and ISO2 → region. */
public final class Geo {

    public static final String BOLIVIA = "BO";

    public static final Set<String> LATAM = Set.of("AR", "BO", "BR", "CL", "CO", "CR", "CU", "DO", "EC", "SV", "GT",
            "HN", "MX", "NI", "PA", "PY", "PE", "PR", "UY", "VE", "BZ", "GY", "SR", "JM", "HT", "TT", "BS", "BB");
    public static final Set<String> NORTH_AMERICA = Set.of("US", "CA");
    public static final Set<String> EUROPE = Set.of("AL", "AD", "AT", "BY", "BE", "BA", "BG", "HR", "CY", "CZ", "DK",
            "EE", "FI", "FR", "DE", "GR", "HU", "IS", "IE", "IT", "XK", "LV", "LI", "LT", "LU", "MT", "MD", "MC", "ME",
            "NL", "MK", "NO", "PL", "PT", "RO", "RU", "SM", "RS", "SK", "SI", "ES", "SE", "CH", "UA", "GB", "VA");
    public static final Set<String> OCEANIA = Set.of("AU", "NZ", "FJ", "PG", "WS", "TO");
    public static final Set<String> AFRICA = Set.of("DZ", "AO", "BJ", "BW", "BF", "BI", "CM", "CV", "CF", "TD", "KM",
            "CD", "CG", "CI", "DJ", "EG", "GQ", "ER", "SZ", "ET", "GA", "GM", "GH", "GN", "GW", "KE", "LS", "LR", "LY",
            "MG", "MW", "ML", "MR", "MU", "MA", "MZ", "NA", "NE", "NG", "RW", "ST", "SN", "SC", "SL", "SO", "ZA", "SS",
            "SD", "TZ", "TG", "TN", "UG", "ZM", "ZW");

    private static final Map<String, String> NAMES = new HashMap<>();

    static {
        for (String iso : Locale.getISOCountries()) {
            Locale country = Locale.of("", iso);
            NAMES.put(fold(country.getDisplayCountry(Locale.ENGLISH)), iso);
            NAMES.put(fold(country.getDisplayCountry(Locale.forLanguageTag("es"))), iso);
        }
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("usa", "US"), Map.entry("u.s.", "US"), Map.entry("u.s.a.", "US"), Map.entry("us", "US"),
                Map.entry("united states of america", "US"), Map.entry("estados unidos", "US"), Map.entry("eeuu", "US"),
                Map.entry("uk", "GB"), Map.entry("u.k.", "GB"), Map.entry("england", "GB"), Map.entry("scotland", "GB"),
                Map.entry("great britain", "GB"), Map.entry("reino unido", "GB"), Map.entry("czech republic", "CZ"),
                Map.entry("czechia", "CZ"), Map.entry("south korea", "KR"), Map.entry("korea", "KR"),
                Map.entry("holland", "NL"), Map.entry("the netherlands", "NL"), Map.entry("brasil", "BR"),
                Map.entry("méxico", "MX"), Map.entry("mexico", "MX"), Map.entry("perú", "PE"), Map.entry("deutschland", "DE"),
                Map.entry("españa", "ES"), Map.entry("espana", "ES"), Map.entry("turkiye", "TR"), Map.entry("türkiye", "TR"),
                Map.entry("uae", "AE"), Map.entry("vietnam", "VN"), Map.entry("russia", "RU"), Map.entry("bolivia", "BO"),
                Map.entry("plurinational state of bolivia", "BO"), Map.entry("venezuela", "VE"), Map.entry("italia", "IT"),
                Map.entry("france", "FR"), Map.entry("portugal", "PT"), Map.entry("canada", "CA"), Map.entry("canadá", "CA"));
        aliases.forEach((k, v) -> NAMES.put(fold(k), v));
    }

    private Geo() {
    }

    public static Optional<String> iso(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String f = fold(name);
        if (f.length() == 2 && NAMES.containsValue(f.toUpperCase(Locale.ROOT))) return Optional.of(f.toUpperCase(Locale.ROOT));
        return Optional.ofNullable(NAMES.get(f));
    }

    /** Finds the first country name mentioned in a free-text location ("Berlin, Germany" → DE). */
    public static Optional<String> findIn(String text) {
        if (text == null) return Optional.empty();
        String[] parts = text.split("[,/|;()·•-]");
        for (int i = parts.length - 1; i >= 0; i--) {
            Optional<String> iso = iso(parts[i]);
            if (iso.isPresent()) return iso;
        }
        return Optional.empty();
    }

    public static String region(String iso) {
        if (iso == null) return null;
        if (LATAM.contains(iso)) return "LATAM";
        if (NORTH_AMERICA.contains(iso)) return "NORTH_AMERICA";
        if (EUROPE.contains(iso)) return "EUROPE";
        if (OCEANIA.contains(iso)) return "OCEANIA";
        if (AFRICA.contains(iso)) return "AFRICA";
        return "ASIA";
    }

    public static Set<String> countriesOf(String region) {
        return switch (region) {
            case "LATAM" -> LATAM;
            case "NORTH_AMERICA" -> NORTH_AMERICA;
            case "EUROPE" -> EUROPE;
            case "OCEANIA" -> OCEANIA;
            case "AFRICA" -> AFRICA;
            default -> Set.of();
        };
    }

    public static List<String> all() {
        return List.of(Locale.getISOCountries());
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
