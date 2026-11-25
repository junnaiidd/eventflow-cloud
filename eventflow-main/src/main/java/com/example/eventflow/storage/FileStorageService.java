package com.example.eventflow.storage;

/**
 * Where uploaded and generated files (event posters, certificate PDFs) are kept. Only the object key is
 * stored in the database. Implementations: local disk (development) and Amazon S3 (production).
 */
public interface FileStorageService {

    /** Stores (or overwrites) the object under the given key. */
    void put(String key, byte[] content, String contentType);

    /** Reads an object; throws {@link StorageNotFoundException} if it does not exist. */
    StoredFile get(String key);

    /** Deletes an object. Deleting a missing object is not an error. */
    void delete(String key);
}
