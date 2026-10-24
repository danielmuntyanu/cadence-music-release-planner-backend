package dev.danyil.storage;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Fails at startup with a clear message when no storage profile (minio / r2) is active. */
@Validated
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
    @NotNull URI endpoint,
    @NotNull URI publicEndpoint,
    @NotBlank String region,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    @NotBlank String bucket,
    @NotNull Duration signedUrlTtl,
    boolean autoCreateBucket
) {}