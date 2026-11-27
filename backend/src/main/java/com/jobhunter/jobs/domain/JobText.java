package com.jobhunter.jobs.domain;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Text normalization, HTML sanitizing, hashing and near-duplicate fingerprints for job offers. */
public final class JobText {

    private static final Set<String> TRACKING_PARAMS = Set.of("utm_source", "utm_medium", "utm_campaign", "utm_term",
            "utm_content", "ref", "source", "gh_src", "lever-source", "lever-origin", "src", "trk", "refid", "fbclid",
            "gclid", "utm_id", "ref_source");
    private static final Pattern LEGAL_SUFFIX = Pattern.compile(
            "\\b(inc|llc|ltd|limited|gmbh|ag|s\\.?a\\.?s?|s\\.?r\\.?l\\.?|s\\.?l\\.?|b\\.?v\\.?|plc|corp|corporation|co|company|srl|sas|spa|s\\.?p\\.?a\\.?|oy|ab|as|pty)\\.?$");
    private static final Pattern GENDER = Pattern.compile("\\((m|w|f|d|h|x)(\\s*/\\s*(m|w|f|d|h|x|div))+\\)|\\b(m/w/d|m/f/d|w/m/d|h/m|h/f|f/h)\\b");
    private static final Safelist SAFE = Safelist.relaxed()
            .removeTags("img").removeAttributes(":all", "style", "class", "id")
            .addAttributes("a", "rel").preserveRelativeLinks(false);

    private JobText() {
    }

    /** Sanitized HTML safe to render in the UI (no scripts, styles, images, event handlers). */
    public static String sanitizeHtml(String html) {
        if (html == null || html.isBlank()) return null;
        String decoded = html.contains("&lt;") && !html.contains("<") ? Parser.unescapeEntities(html, false) : html;
        String clean = Jsoup.clean(decoded, "", SAFE, new Document.OutputSettings().prettyPrint(false));
        clean = clean.replaceAll("<a ", "<a rel=\"noopener noreferrer nofollow\" target=\"_blank\" ");
        return clean.length() > 200_000 ? clean.substring(0, 200_000) : clean;
    }

    public static String htmlToText(String html) {
        if (html == null || html.isBlank()) return null;
        String decoded = html.contains("&lt;") && !html.contains("<") ? Parser.unescapeEntities(html, false) : html;
        Document doc = Jsoup.parse(decoded);
        doc.select("br").after("\\n");
        doc.select("p, li, h1, h2, h3, h4, div").before("\\n");
        String text = doc.text().replace("\\n", "\n");
        text = text.replaceAll("[ \t]+", " ").replaceAll(" *\n *", "\n").replaceAll("\n{3,}", "\n\n").trim();
        return text.length() > 100_000 ? text.substring(0, 100_000) : text;
    }

    public static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}#+.]+", " ").replaceAll("\\s+", " ").trim();
    }

    public static String normalizeTitle(String title) {
        String t = GENDER.matcher(title == null ? "" : title.toLowerCase(Locale.ROOT)).replaceAll(" ");
        t = fold(t).replaceAll("\\bsr\\b\\.?", "senior").replaceAll("\\bjr\\b\\.?", "junior")
                .replaceAll("\\bssr\\b", "semi senior").replaceAll("\\bdev\\b", "developer")
                .replaceAll("\\bengr?\\b", "engineer").replaceAll("\\s+", " ").trim();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    public static String normalizeCompany(String company) {
        if (company == null) return null;
        String c = fold(company);
        String previous;
        do {
            previous = c;
            c = LEGAL_SUFFIX.matcher(c).replaceAll("").trim();
        } while (!c.equals(previous));
        return c.isEmpty() ? null : (c.length() > 200 ? c.substring(0, 200) : c);
    }

    /** Lower-case host, no fragment, no tracking params, no trailing slash. Null if not http(s). */
    public static String canonicalUrl(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI u = URI.create(url.trim());
            if (u.getScheme() == null || !u.getScheme().toLowerCase(Locale.ROOT).startsWith("http") || u.getHost() == null) {
                return null;
            }
            String host = u.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            String path = u.getRawPath() == null ? "" : u.getRawPath().replaceAll("/+$", "");
            StringBuilder query = new StringBuilder();
            if (u.getRawQuery() != null) {
                for (String part : u.getRawQuery().split("&")) {
                    String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
                    if (!TRACKING_PARAMS.contains(key) && !key.startsWith("utm_") && !part.isBlank()) {
                        query.append(query.isEmpty() ? "?" : "&").append(part);
                    }
                }
            }
            return "https://" + host + path + query;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Same company + same normalized title + same country/remote scope → probably the same vacancy. */
    public static byte[] fingerprint(String normalizedCompany, String normalizedTitle, String locationKey) {
        return sha256((normalizedCompany == null ? "" : normalizedCompany) + "|" + normalizedTitle + "|"
                + (locationKey == null ? "" : locationKey));
    }

    /** 64-bit SimHash over 3-word shingles; near-duplicates have a small Hamming distance. */
    public static long simhash(String text) {
        String[] words = fold(text).split(" ");
        if (words.length < 3) return 0L;
        int[] v = new int[64];
        for (int i = 0; i + 2 < words.length; i++) {
            long h = fnv64(words[i] + " " + words[i + 1] + " " + words[i + 2]);
            for (int b = 0; b < 64; b++) {
                v[b] += ((h >>> b) & 1L) == 1L ? 1 : -1;
            }
        }
        long result = 0L;
        for (int b = 0; b < 64; b++) {
            if (v[b] > 0) result |= 1L << b;
        }
        return result;
    }

    public static int hamming(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    private static long fnv64(String s) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b;
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
