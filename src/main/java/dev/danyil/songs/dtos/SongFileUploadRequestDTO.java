package dev.danyil.songs.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Step 1: what the browser is about to upload. */
public record SongFileUploadRequestDTO(

    @NotBlank(message = "File name is required")
    @Size(max = 255, message = "File name must be at most 255 characters")
    String fileName,

    @Positive(message = "The file is empty")
    long sizeBytes
) {}