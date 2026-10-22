package dev.danyil.releases.dtos;

import java.util.Set;

import dev.danyil.enums.Genre;
import dev.danyil.enums.ReleasePhase;
import dev.danyil.enums.ReleaseType;

/**
 * Query-string filters for GET /releases. Every field is optional.
 * Different filters combine with AND; values inside one filter combine with OR.
 * Example: ?phase=PRE_RELEASE&phase=PROMO&genre=POP&artistId=5&q=night
 */
public record ReleaseFilterDTO(
    Set<ReleasePhase> phase,
    Set<ReleaseType> type,
    Set<Genre> genre,
    Long artistId,
    String q
) {}