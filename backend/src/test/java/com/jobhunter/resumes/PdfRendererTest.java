package com.jobhunter.resumes;

import com.jobhunter.resumes.domain.ResumeDiff;
import com.jobhunter.resumes.domain.ResumeDocument;
import com.jobhunter.resumes.domain.ResumeDocument.Bullet;
import com.jobhunter.resumes.domain.ResumeDocument.BulletStatus;
import com.jobhunter.resumes.infrastructure.PdfRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfRendererTest {

    /** TEST DATA — fictitious person. */
    static ResumeDocument sample(String extraBullet) {
        List<Bullet> bullets = new java.util.ArrayList<>(List.of(
                new Bullet("Diseñé APIs REST con Spring Boot", List.of("ach:1:0"), BulletStatus.GROUNDED)));
        if (extraBullet != null) bullets.add(new Bullet(extraBullet, List.of(), BulletStatus.SUGGESTED));
        return new ResumeDocument(
                new ResumeDocument.Basics("Ana Prueba Ficticia", "Backend Developer", "ana@example.com", "+591 70000000",
                        "Cochabamba, BO", List.of(new ResumeDocument.Link("GitHub", "https://github.com/ana-test"))),
                "Desarrolladora backend <script>alert(1)</script> con ñ y acentos: áéíóú",
                List.of(new ResumeDocument.ExperienceEntry("exp:1", "TEST DATA Example Corp", "Backend Developer",
                        "Remoto", "2022-01", null, true, bullets)),
                List.of(new ResumeDocument.ProjectEntry("prj:1", "PERSONAL", "TEST DATA Side Project", null, null,
                        List.of(new Bullet("API de prueba", List.of("prj:1"), BulletStatus.GROUNDED)))),
                List.of(new ResumeDocument.EducationEntry("edu:1", "Universidad de Prueba", "Ingeniería de Sistemas",
                        null, "2015-01", "2020-12", "COMPLETED")),
                List.of(),
                List.of(new ResumeDocument.SkillGroup("Lenguajes", List.of(
                        new ResumeDocument.SkillEntry("skill:1", "Java", "PROFESSIONAL"),
                        new ResumeDocument.SkillEntry("skill:2", "PHP", "PROFESSIONAL")))),
                List.of(new ResumeDocument.LanguageEntry("lang:es", "es", "NATIVE"),
                        new ResumeDocument.LanguageEntry("lang:en", "en", "B2")));
    }

    private final PdfRenderer renderer = new PdfRenderer();

    @Test
    void htmlEscapesUserContent() {
        String html = renderer.html("classic", sample(null), "es");
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
        assertThat(html).contains("Experiencia laboral").contains("Java, PHP").contains("Español (Nativo)");
    }

    @Test
    void pdfContainsSelectableTextInEveryTemplateAndLanguage() throws Exception {
        for (PdfRenderer.Template t : PdfRenderer.TEMPLATES) {
            for (String lang : List.of("es", "en", "it", "de", "fr")) {
                byte[] pdf = renderer.pdf(t.id(), sample(null), lang);
                try (PDDocument doc = Loader.loadPDF(pdf)) {
                    String text = new PDFTextStripper().getText(doc);
                    assertThat(text).contains("Ana Prueba Ficticia").contains("TEST DATA Example Corp")
                            .contains("áéíóú");
                }
            }
        }
    }

    @Test
    void diffDetectsAddedBullet() {
        List<ResumeDiff.Change> changes = ResumeDiff.diff(sample(null), sample("Nueva viñeta sugerida"));
        assertThat(changes).filteredOn(c -> c.op() == ResumeDiff.Op.ADDED)
                .extracting(ResumeDiff.Change::text).containsExactly("• Nueva viñeta sugerida");
        assertThat(changes).noneMatch(c -> c.op() == ResumeDiff.Op.REMOVED);
    }
}
