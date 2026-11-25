package com.example.eventflow.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Production storage on a PRIVATE S3 bucket. The browser never talks to S3 and never sees AWS
 * credentials: every download goes through the application, which checks the user's permission first.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorageService.class);

    private final S3Client s3;
    private final String bucket;

    public S3FileStorageService(S3Client s3, @Value("${app.storage.s3.bucket:}") String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("app.storage.s3.bucket (env S3_BUCKET) must be set when app.storage.type=s3");
        }
        this.s3 = s3;
        this.bucket = bucket;
        log.info("Using S3 file storage, bucket '{}'", bucket);
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        StorageKeys.validate(key);
        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build(), RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new StorageException("Could not upload file to S3", e);
        }
    }

    @Override
    public StoredFile get(String key) {
        StorageKeys.validate(key);
        try {
            ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return new StoredFile(bytes.asByteArray(), StorageKeys.contentTypeOf(key));
        } catch (NoSuchKeyException e) {
            throw new StorageNotFoundException(key);
        } catch (SdkException e) {
            throw new StorageException("Could not read file from S3", e);
        }
    }

    @Override
    public void delete(String key) {
        StorageKeys.validate(key);
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            throw new StorageException("Could not delete file from S3", e);
        }
    }
}
