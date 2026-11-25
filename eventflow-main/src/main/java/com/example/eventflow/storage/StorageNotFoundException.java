package com.example.eventflow.storage;

public class StorageNotFoundException extends StorageException {

    public StorageNotFoundException(String key) {
        super("Stored file not found: " + key);
    }
}
