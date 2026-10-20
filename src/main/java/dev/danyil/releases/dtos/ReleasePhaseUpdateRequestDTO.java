package dev.danyil.releases.dtos;

import dev.danyil.enums.ReleasePhase;
import jakarta.validation.constraints.NotNull;

public record ReleasePhaseUpdateRequestDTO(
    @NotNull(message = "Phase is required")
    ReleasePhase phase
) {}