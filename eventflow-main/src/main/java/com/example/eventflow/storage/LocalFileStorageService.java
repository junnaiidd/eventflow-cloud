package com.example.eventflow.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Development storage: files live in a folder on disk (app.storage.local-dir). No AWS needed. */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final Path root;

    public LocalFileStorageService(@Value("${app.storage.local-dir:./uploads}") String directory) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
        log.info("Using LOCAL file storage at {}", root);
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new StorageException("Could not store file", e);
        }
    }

    @Override
    public StoredFile get(String key) {
        Path target = resolve(key);
        try {
            return new StoredFile(Files.readAllBytes(target), StorageKeys.contentTypeOf(key));
        } catch (NoSuchFileException e) {
            throw new StorageNotFoundException(key);
        } catch (IOException e) {
            throw new StorageException("Could not read file", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Could not delete file", e);
        }
    }

    private Path resolve(String key) {
        StorageKeys.validate(key);
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new StorageException("Invalid storage key");
        }
        return target;
    }
}
