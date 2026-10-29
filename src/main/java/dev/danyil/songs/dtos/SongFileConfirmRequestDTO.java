package dev.danyil.songs.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Step 3: the file is in storage; keep it under this name. A blank name falls back to the file name. */
public record SongFileConfirmRequestDTO(

    @NotBlank(message = "Key is required")
    String key,

    @NotBlank(message = "File name is required")
    @Size(max = 255, message = "File name must be at most 255 characters")
    String fileName,

    @Size(max = 150, message = "Name must be at most 150 characters")
    String displayName
) {}