package dev.danyil.storage;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Local development and tests only: creates the bucket if it does not exist.
 * New buckets are private by default, which is exactly what we want.
 * In production (R2) the bucket is created in the provider's dashboard.
 */
@Component
@ConditionalOnProperty(name = "storage.auto-create-bucket", havingValue = "true")
@RequiredArgsConstructor
public class StorageBucketInitializer implements ApplicationRunner {

    private final S3Client s3;
    private final StorageProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            s3.headBucket(request -> request.bucket(properties.bucket()));
        } catch (S3Exception e) {
            if (e.statusCode() != 404) throw e;
            s3.createBucket(request -> request.bucket(properties.bucket()));
        }
    }
}