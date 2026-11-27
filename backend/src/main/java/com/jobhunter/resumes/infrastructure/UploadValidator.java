package com.jobhunter.resumes.infrastructure;

import com.jobhunter.resumes.domain.DocumentType;
import com.jobhunter.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Accepts only PDF and DOCX. Extension, declared content type and magic bytes must all agree.
 * DOCX is checked structurally (ZIP with [Content_Types].xml + word/document.xml, no macros, bounded size).
 */
@Component
public class UploadValidator {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED = 50L * 1024 * 1024;
    private static final int MAX_ENTRIES = 2_000;

    public DocumentType validate(String filename, String declaredType, byte[] content) {
        if (content == null || content.length == 0) {
            throw invalid("El archivo está vacío");
        }
        if (content.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "El archivo supera 5 MB");
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            requireType(declaredType, DocumentType.PDF);
            if (!startsWith(content, "%PDF-")) throw invalid("El archivo no es un PDF válido");
            return DocumentType.PDF;
        }
        if (name.endsWith(".docx")) {
            requireType(declaredType, DocumentType.DOCX);
            if (!startsWith(content, "PK\u0003\u0004")) throw invalid("El archivo no es un DOCX válido");
            checkDocxStructure(content);
            return DocumentType.DOCX;
        }
        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE",
                "Solo se aceptan archivos PDF o DOCX");
    }

    private static void requireType(String declared, DocumentType expected) {
        // browsers sometimes send application/octet-stream; anything else must match the extension
        if (declared != null && !declared.isBlank() && !declared.equals("application/octet-stream")
                && !declared.equalsIgnoreCase(expected.mediaType())) {
            throw invalid("El tipo de archivo declarado no coincide con la extensión");
        }
    }

    private static void checkDocxStructure(byte[] content) {
        boolean contentTypes = false;
        boolean document = false;
        long total = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw invalid("El DOCX tiene demasiadas entradas");
                String n = entry.getName();
                if (n.contains("..") || n.startsWith("/")) throw invalid("El DOCX contiene rutas no válidas");
                if (n.equals("[Content_Types].xml")) contentTypes = true;
                if (n.equals("word/document.xml")) document = true;
                if (n.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")) {
                    throw invalid("No se aceptan documentos con macros");
                }
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_UNCOMPRESSED) throw invalid("El DOCX descomprimido es demasiado grande");
                }
            }
        } catch (IOException e) {
            throw invalid("El DOCX está dañado");
        }
        if (!contentTypes || !document) {
            throw invalid("El archivo no es un documento de Word válido");
        }
    }

    private static boolean startsWith(byte[] content, String magic) {
        if (content.length < magic.length()) return false;
        for (int i = 0; i < magic.length(); i++) {
            if (content[i] != (byte) magic.charAt(i)) return false;
        }
        return true;
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest("INVALID_FILE", message);
    }
}
