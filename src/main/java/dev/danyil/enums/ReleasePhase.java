package dev.danyil.enums;

public enum ReleasePhase {
    PLANNING,     // concept, tracklist, target date, team
    PRODUCTION,   // tracks are being made (progress comes from tracks)
    PRE_RELEASE,  // masters done: distribution, pitching, pre-saves, teasers
    PROMO,        // out now: active push, usually the first 4–6 weeks
    CATALOG       // campaign over; the release lives in the back catalog
}
