package com.jobhunter.resumes.infrastructure;

import com.jobhunter.resumes.domain.DocumentType;
import com.jobhunter.shared.api.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Local, offline text extraction (no external service): PDFBox for PDF, Apache POI for DOCX. */
@Component
public class TextExtractor {

    public static final int MAX_PAGES = 20;
    private static final int MAX_CHARS = 200_000;

    static {
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(50L * 1024 * 1024);
    }

    public String extract(DocumentType type, byte[] content) {
        String text = switch (type) {
            case PDF -> pdf(content);
            case DOCX -> docx(content);
        };
        text = normalize(text);
        if (text.isBlank()) {
            throw ApiException.badRequest("NO_TEXT",
                    "No se pudo extraer texto. Si el CV es una imagen escaneada, sube una versión con texto seleccionable");
        }
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }

    private static String pdf(byte[] content) {
        RandomAccessStreamCache.StreamCacheCreateFunction cache = MemoryUsageSetting.setupTempFileOnly().streamCache;
        try (PDDocument doc = Loader.loadPDF(content, "", null, null, cache)) {
            if (doc.isEncrypted()) {
                throw ApiException.badRequest("ENCRYPTED_FILE", "No se aceptan PDFs cifrados o protegidos");
            }
            if (doc.getNumberOfPages() > MAX_PAGES) {
                throw ApiException.badRequest("TOO_MANY_PAGES", "El PDF supera " + MAX_PAGES + " páginas");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (InvalidPasswordException e) {
            throw ApiException.badRequest("ENCRYPTED_FILE", "No se aceptan PDFs protegidos con contraseña");
        } catch (IOException e) {
            throw ApiException.badRequest("INVALID_FILE", "El PDF está dañado o no se puede leer");
        }
    }

    private static String docx(byte[] content) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest("INVALID_FILE", "El DOCX está dañado o no se puede leer");
        }
    }

    /** Unifies line breaks, removes control chars, collapses runs of spaces and blank lines. */
    static String normalize(String text) {
        if (text == null) return "";
        return text.replace("\r\n", "\n").replace('\r', '\n')
                .replace(' ', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\n\t]]", "")
                .replaceAll("[ \t]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
