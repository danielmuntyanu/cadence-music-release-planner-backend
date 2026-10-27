package dev.danyil.songs;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Limits for song audio uploaded directly to storage through signed URLs. */
@ConfigurationProperties(prefix = "storage.audio")
public record AudioUploadProperties(long maxBytes, Duration uploadUrlTtl) {}