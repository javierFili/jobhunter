package com.jobhunter.ai.domain;

/**
 * Wraps third-party content (job postings, CV text) as clearly delimited DATA. Delimiter-like sequences inside the
 * content are neutralized so the text cannot "close" the block and smuggle instructions; content is length-capped.
 */
public final class UntrustedText {

    private UntrustedText() {
    }

    public static String wrap(String tag, String content, int maxChars) {
        String c = content == null ? "" : content;
        if (c.length() > maxChars) c = c.substring(0, maxChars) + "\n[...truncado...]";
        c = c.replaceAll("(?i)</?\\s*untrusted[^>]*>", "[etiqueta eliminada]")
                .replaceAll("(?i)</?\\s*(system|assistant|instructions?)\\s*>", "[etiqueta eliminada]")
                .replace("```", "'''");
        return "<untrusted_" + tag + ">\n" + c + "\n</untrusted_" + tag + ">";
    }

    /** Removes e-mails, phone numbers and URLs (data minimization before sending CV text to an AI provider). */
    public static String stripContactData(String text) {
        if (text == null) return null;
        return text.replaceAll("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+", "[email]")
                .replaceAll("(?<![\\w/])\\+?\\d[\\d\\s().-]{7,18}\\d(?![\\w/])", "[teléfono]")
                .replaceAll("(?i)\\b(?:https?://|www\\.)\\S+", "[enlace]")
                .replaceAll("(?i)\\b(?:linkedin|github)\\.com/\\S+", "[enlace]");
    }
}
