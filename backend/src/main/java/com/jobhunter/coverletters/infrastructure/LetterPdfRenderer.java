package com.jobhunter.coverletters.infrastructure;

import com.jobhunter.coverletters.domain.CoverLetter;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class LetterPdfRenderer {

    private final SpringTemplateEngine engine = new SpringTemplateEngine();

    public LetterPdfRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/letter/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);
    }

    public byte[] pdf(CoverLetter c, String fullName, String email, String phone) {
        Context ctx = new Context(Locale.forLanguageTag(c.getLanguage()));
        List<String> paragraphs = Arrays.stream(c.getBody().split("\\n\\s*\\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        ctx.setVariable("lang", c.getLanguage());
        ctx.setVariable("name", fullName);
        ctx.setVariable("email", email);
        ctx.setVariable("phone", phone);
        ctx.setVariable("date", LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                .withLocale(Locale.forLanguageTag(c.getLanguage()))));
        ctx.setVariable("subject", c.getSubject());
        ctx.setVariable("paragraphs", paragraphs);
        String html = engine.process("letter", ctx);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();
            for (var f : List.of(new Object[]{"DejaVuSans.ttf", 400}, new Object[]{"DejaVuSans-Bold.ttf", 700})) {
                File file = new File("/usr/share/fonts/truetype/dejavu/" + f[0]);
                if (file.isFile()) b.useFont(file, "DejaVu Sans", (Integer) f[1], PdfRendererBuilder.FontStyle.NORMAL, true);
            }
            b.withW3cDocument(new W3CDom().fromJsoup(Jsoup.parse(html)), null);
            b.toStream(out);
            b.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF rendering failed", e);
        }
    }
}
