package dev.danyil.songs.dtos;

import java.time.Instant;
import java.util.Map;

/** Step 1 answer: where and how to PUT the file, and the key to confirm afterwards. */
public record SongFileUploadResponseDTO(String key, String uploadUrl, Map<String, String> headers, Instant expiresAt) {}