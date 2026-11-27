package com.jobhunter.shared.files;

import com.jobhunter.shared.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Local filesystem storage outside the web root. Files are named by random UUID (never by user input) and are only
 * served through authorized endpoints that look them up by id + owner.
 */
@Service
public class FileStorageService {

    private final Path root;
    private final StoredFileRepository repository;

    public FileStorageService(@Value("${jobhunter.files.root}") String root, StoredFileRepository repository) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.repository = repository;
    }

    @Transactional
    public StoredFile store(long userId, byte[] content, String originalName, String contentType,
                            StoredFile.Purpose purpose) {
        String key = UUID.randomUUID().toString();
        Path target = pathFor(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store file", e);
        }
        return repository.save(new StoredFile(userId, key, sanitizeName(originalName), contentType, content.length,
                sha256(content), purpose));
    }

    @Transactional(readOnly = true)
    public StoredFile get(long userId, long fileId) {
        return repository.findByIdAndUserId(fileId, userId).orElseThrow(() -> ApiException.notFound("Archivo"));
    }

    public byte[] read(StoredFile file) {
        try {
            return Files.readAllBytes(pathFor(file.getStorageKey()));
        } catch (IOException e) {
            throw ApiException.notFound("Contenido del archivo");
        }
    }

    @Transactional
    public void delete(StoredFile file) {
        try {
            Files.deleteIfExists(pathFor(file.getStorageKey()));
        } catch (IOException ignored) {
            // the DB row is the source of truth; an orphan file is harmless and can be swept later
        }
        repository.delete(file);
    }

    private Path pathFor(String key) {
        // two-level fan-out keeps directories small; the key is a UUID we generated, never user input
        Path p = root.resolve(key.substring(0, 2)).resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalStateException("Invalid storage key");
        }
        return p;
    }

    public static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Keeps only a safe display name (used in Content-Disposition), never as a path. */
    public static String sanitizeName(String name) {
        if (name == null || name.isBlank()) return "archivo";
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        return base.length() > 200 ? base.substring(0, 200) : base;
    }
}
