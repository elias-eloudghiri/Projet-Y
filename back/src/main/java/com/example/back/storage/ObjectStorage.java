package com.example.back.storage;

import com.example.back.config.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Files of the application (story PDFs), stored in a single private bucket. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObjectStorage implements ApplicationRunner {

    private final S3Client s3;
    private final StorageProperties properties;

    /** Creates the bucket at startup if it does not exist yet. */
    @Override
    public void run(ApplicationArguments args) {
        try {
            s3.headBucket(b -> b.bucket(properties.bucket()));
        } catch (S3Exception e) {
            // HEAD responses have no body: a missing bucket is only a 404.
            if (e.statusCode() != 404) {
                throw e;
            }
            s3.createBucket(b -> b.bucket(properties.bucket()));
            log.info("Bucket {} created", properties.bucket());
        }
    }

    public void put(String key, InputStream content, long size, String contentType)
            throws IOException {
        try {
            s3.putObject(
                    b -> b.bucket(properties.bucket()).key(key).contentType(contentType),
                    RequestBody.fromInputStream(content, size));
        } catch (SdkException e) {
            throw new IOException("Upload of " + key + " failed", e);
        }
    }

    /** Best effort: a leftover object is only wasted space, never an inconsistency. */
    public void deleteQuietly(String key) {
        try {
            s3.deleteObject(b -> b.bucket(properties.bucket()).key(key));
        } catch (SdkException e) {
            log.warn("Could not delete object {}", key, e);
        }
    }
}
