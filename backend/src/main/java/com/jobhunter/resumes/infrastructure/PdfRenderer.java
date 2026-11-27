package com.jobhunter.resumes.infrastructure;

import com.jobhunter.resumes.domain.ResumeDocument;
import com.jobhunter.resumes.domain.ResumeLabels;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.stereotype.Component;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders resumes with HTML templates (Thymeleaf, auto-escaping) and converts them to PDF (openhtmltopdf).
 * Templates are single-column with real text (no images/tables for layout) so ATS parsers read them correctly.
 */
@Component
public class PdfRenderer {

    public record Template(String id, String name, String description) {
    }

    public static final List<Template> TEMPLATES = List.of(
            new Template("classic", "Clásica ATS", "Una columna, tipografía sobria, máxima compatibilidad con ATS"),
            new Template("modern", "Moderna ATS", "Una columna con acentos de color y encabezados destacados"));

    private static final String FONT_DIR = "/usr/share/fonts/truetype/dejavu/";

    private final SpringTemplateEngine engine;

    public PdfRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/resume/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        engine = new SpringTemplateEngine();
        engine.setEnableSpringELCompiler(false);
        engine.setTemplateResolver(resolver);
    }

    public static boolean isKnownTemplate(String id) {
        return TEMPLATES.stream().anyMatch(t -> t.id().equals(id));
    }

    public String html(String templateId, ResumeDocument doc, String language) {
        Context ctx = new Context(Locale.forLanguageTag(language));
        ctx.setVariable("doc", doc);
        ctx.setVariable("lang", language);
        ctx.setVariable("labels", ResumeLabels.all(language));
        ctx.setVariable("fmt", new Formatter(language));
        return engine.process(isKnownTemplate(templateId) ? templateId : "classic", ctx);
    }

    public byte[] pdf(String templateId, ResumeDocument doc, String language) {
        String html = html(templateId, doc, language);
        org.jsoup.nodes.Document parsed = Jsoup.parse(html);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFonts(builder);
            builder.withW3cDocument(new W3CDom().fromJsoup(parsed), null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF rendering failed", e);
        }
    }

    private static void registerFonts(PdfRendererBuilder builder) {
        Map<String, Integer> faces = Map.of("DejaVuSans.ttf", 400, "DejaVuSans-Bold.ttf", 700);
        faces.forEach((file, weight) -> {
            File f = new File(FONT_DIR + file);
            if (f.isFile()) {
                builder.useFont(f, "DejaVu Sans", weight, PdfRendererBuilder.FontStyle.NORMAL, true);
            }
        });
    }

    /** Date/label helpers callable from templates. */
    public static final class Formatter {
        private final String language;

        Formatter(String language) {
            this.language = language;
        }

        /** "2022-01" → "ene. 2022" / "Jan 2022"; null → "". */
        public String ym(String value) {
            if (value == null || value.isBlank()) return "";
            try {
                YearMonth ym = YearMonth.parse(value, DateTimeFormatter.ofPattern("yyyy-MM"));
                String month = ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.forLanguageTag(language));
                return month.substring(0, 1).toUpperCase(Locale.ROOT) + month.substring(1) + " " + ym.getYear();
            } catch (Exception e) {
                return value;
            }
        }

        public String range(String start, String end, boolean current) {
            String s = ym(start);
            String e = current ? ResumeLabels.get(language, "present") : ym(end);
            if (s.isEmpty()) return e;
            return e.isEmpty() ? s : s + " – " + e;
        }

        public String languageName(String code) {
            return ResumeLabels.languageName(language, code);
        }

        public String level(String level) {
            return "NATIVE".equals(level) ? ResumeLabels.get(language, "NATIVE") : (level == null ? "" : level);
        }

        public String kind(String kind) {
            return kind == null ? "" : ResumeLabels.get(language, kind);
        }
    }
}
